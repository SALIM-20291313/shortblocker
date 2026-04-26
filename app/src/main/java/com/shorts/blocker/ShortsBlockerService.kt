package com.shorts.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShortsBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private val MAX_SHORTS_DAILY = 0
    private var isCurrentlyInShorts = false
    private var lastToastTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("ShortsBlockerPrefs", Context.MODE_PRIVATE)
        checkAndResetDailyCounter()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Sadece YouTube üzerindeysek ilgilenelim
        if (packageName != "com.google.android.youtube") {
            isCurrentlyInShorts = false // Youtube dışı sıfırla
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val foundShortsPlayer = isShortsPlayerActive(rootNode)
        
        if (foundShortsPlayer) {
            handleShortsDetection()
        } else {
            // Eğer Shorts oyuncusu görünmüyorsa "çıktığını" algılar.
            isCurrentlyInShorts = false 
        }
    }

    private fun handleShortsDetection() {
        val currentCount = prefs.getInt("daily_count", 0)

        if (!isCurrentlyInShorts) {
            // İlk kez shorts player'a girdiğinde kotaları düş
            checkAndResetDailyCounter()

            if (currentCount < MAX_SHORTS_DAILY) {
                // Haklarını kullanmaya başla
                val newCount = currentCount + 1
                prefs.edit().putInt("daily_count", newCount).apply()
                isCurrentlyInShorts = true
                showToastMessage("Shorts Hakkı Kullanıldı! (Kalan: ${MAX_SHORTS_DAILY - newCount})")
            } else {
                // Hakkı yoksa daha girerken at
                isCurrentlyInShorts = true
                showToastMessage("🚫 YouTube Shorts Kısıtlandı!")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else {
            // Halihazırda Shorts'un içindeyse sadece limite takılıyorsa engelle
            if (currentCount >= MAX_SHORTS_DAILY) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun checkAndResetDailyCounter() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString("last_date", "")
        
        if (currentDate != savedDate) {
            // Gün gece 00:00 i geçmiş, hakkını 5'e geri doldur
            prefs.edit().putString("last_date", currentDate).putInt("daily_count", 0).apply()
        }
    }

    private fun isShortsPlayerActive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val selectedNodes = HashSet<String>()
        val resourceIds = HashSet<String>()
        
        extractData(node, selectedNodes, resourceIds)

        // SIZMA YAMASI: Yatay raf veya herhangi bir yerden doğrudan Shorts izleyicisine geçilmişse "Ana Sayfa" istisnasını sildik! 
        val hasReelPlayer = resourceIds.any { 
            it.contains("reel_player") || 
            it.contains("shorts_player") ||
            (it.contains("reel") && it.contains("video_player")) ||
            it.contains("reel_recycler") ||
            it.contains("reel_viewer")
        }

        if (hasReelPlayer) return true

        // Alternatif yakalama mekanizması (Alt Shorts Taba basıldıysa)
        val isShortsSelected = selectedNodes.any { it.contains("shorts") || it.contains("kısa videolar") }
        if (isShortsSelected) return true

        return false
    }

    private fun extractData(node: AccessibilityNodeInfo?, selected: HashSet<String>, ids: HashSet<String>) {
        if (node == null) return

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.toString()?.lowercase() ?: ""

        if (viewId.isNotEmpty()) ids.add(viewId)

        if (node.isSelected) {
            if (text.isNotEmpty()) selected.add(text)
            if (contentDesc.isNotEmpty()) selected.add(contentDesc)
        }

        for (i in 0 until node.childCount) {
            extractData(node.getChild(i), selected, ids)
        }
    }

    private fun showToastMessage(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > 3000) { 
            lastToastTime = currentTime
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onInterrupt() {}
}
