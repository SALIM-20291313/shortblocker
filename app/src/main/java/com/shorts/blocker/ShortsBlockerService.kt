package com.shorts.blocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShortsBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.google.android.youtube") return

        val rootNode = rootInActiveWindow ?: return

        // Ekranda Shorts kelimesi var mı diye mantıklı bir derin tarama yap
        val foundShorts = isShortsVisible(rootNode)
        
        if (foundShorts) {
            // Eğer Shorts yakalanırsa ve Ana Sayfa değilse doğrudan "GERİ" tuşuna bas
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun isShortsVisible(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val textContent = HashSet<String>()
        val selectedNodes = HashSet<String>()
        val resourceIds = HashSet<String>()
        
        extractData(node, textContent, selectedNodes, resourceIds)

        // 1. Ana Menü Kontrolü: 
        // Eğer kullanıcı YouTube uygulamasını ilk açtığında alt menüde "Ana Sayfa" (veya Home) butonu seçili ve aktifse
        // Biz ana sayfadayızdır. Ana sayfada da "Shorts" butonu göründüğü için yanlışlıkla engellemeyi önlemek adına:
        val isHomeSelected = selectedNodes.any { it.contains("ana sayfa") || it.contains("home") || it.contains("abonelikler") }
        if (isHomeSelected) {
            return false 
        }

        // 2. Shorts Sekmesi Kontrolü:
        // Eğer alttaki "Shorts" sekmesi / butonu bizzat DOKUNULUP SEÇİLMİŞSE (isSelected = true)
        val isShortsSelected = selectedNodes.any { it.contains("shorts") || it.contains("kısa videolar") }
        if (isShortsSelected) {
            return true
        }

        // 3. Shorts Player (Oynatıcı) Kontrolü:
        // Eğer sekme görünmüyorsa ama arka plandaki Android bileşenlerinin isimlerinde(ID) "reel" (Shorts'un kod adı) 
        // veya "shorts_player" gibi ifadeler geçiyorsa, kişi tam ekran bir Shorts videosunun içine dalmıştır.
        val hasReelPlayer = resourceIds.any { 
            it.contains("reel_player") || 
            it.contains("reel_recycler") || 
            it.contains("shorts_player") ||
            it.contains("reel_viewer")
        }
        
        // Eğer sadece ekranda shorts yazıyorsa ama sekme seçili değilse 
        // (örneğin arama sonuçlarında shorts başlığı varsa) ana sayfayı kilitlememesi için sadece oynatıcı aktifse engelle.
        if (hasReelPlayer) {
            return true
        }

        return false
    }

    private fun extractData(node: AccessibilityNodeInfo?, texts: HashSet<String>, selected: HashSet<String>, ids: HashSet<String>) {
        if (node == null) return

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.toString()?.lowercase() ?: ""

        if (text.isNotEmpty()) texts.add(text)
        if (contentDesc.isNotEmpty()) texts.add(contentDesc)
        if (viewId.isNotEmpty()) ids.add(viewId)

        // Eğer bu ekran bileşeni "Seçili (Tıklanmış/Aktif)" bir sekme ise onu not alalım:
        if (node.isSelected) {
            if (text.isNotEmpty()) selected.add(text)
            if (contentDesc.isNotEmpty()) selected.add(contentDesc)
        }

        // O ekrandaki tüm düğmeleri (butonlar, yazılar, videolar vb.) teker teker tarayarak gez.
        for (i in 0 until node.childCount) {
            extractData(node.getChild(i), texts, selected, ids)
        }
    }

    override fun onInterrupt() {
        // Servis kesintiye uğradığında
    }
}
