package com.gdimitria.notificationreminders.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.gdimitria.notificationreminders.engine.SoundLock
import com.gdimitria.notificationreminders.engine.WifiWatcher

/** Swallows the volume buttons while the sound settings are locked, and keeps the lock running. */
class SoundLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        SoundLock.start(this)
        WifiWatcher.start(this)
    }

    override fun onDestroy() {
        SoundLock.stop()
        WifiWatcher.stop()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        return volumeKey && SoundLock.isLocked(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    companion object {
        fun isEnabled(context: Context): Boolean {
            val id = ComponentName(context, SoundLockAccessibilityService::class.java).flattenToShortString()
            val idLong = ComponentName(context, SoundLockAccessibilityService::class.java).flattenToString()
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id == id || it.id == idLong }
        }
    }
}
