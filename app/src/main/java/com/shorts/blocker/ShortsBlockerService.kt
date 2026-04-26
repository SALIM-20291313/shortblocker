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

        // Ekranda Shorts kelimesi var mı diye derin tarama yap
        val foundShorts = isShortsVisible(rootNode)
        
        if (foundShorts) {
            // Eğer Shorts yakalanırsa doğrudan "GERİ" tuşuna basılıyormuş gibi yap
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun isShortsVisible(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // İçerik açıklamasında veya text'te Shorts kelimesi geçiyorsa true döner
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        val text = node.text?.toString()?.lowercase()

        // YouTube, Shorts tabında ve oynatıcısında "shorts" kelimesini sıkça identifier olarak kullanır
        if ((contentDesc != null && contentDesc.contains("shorts")) ||
            (text != null && text.contains("shorts"))) {
            
            // Eğer alt menüdeki genel Shorts butonu değil de, 
            // gerçekten Shorts izleniyorsa ya da Shorts sayfası açıldıysa engelle
            // Agresif modda olduğu için her türlü "Shorts" yazan yere tıklandığında geri atar.
            return true
        }

        // Alt elemanları özyinelemeli olarak kontrol et
        for (i in 0 until node.childCount) {
            if (isShortsVisible(node.getChild(i))) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        // Servis kesintiye uğradığında yapılacak işlemler (boş bırakılabilir)
    }
}
