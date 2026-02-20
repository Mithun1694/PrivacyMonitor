package com.yourname.privacyshield

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PrivacyAccessibilityService : AccessibilityService() {

    companion object {
        var currentForegroundPackage: String = "Unknown"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Listen to multiple event types for better accuracy
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != this.packageName && !packageName.contains("systemui")) {
                    currentForegroundPackage = packageName
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        val intent = Intent(this, PrivacyMonitorService::class.java)
        startForegroundService(intent)
    }
}
