package com.gdimitria.notificationreminders.engine

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.SoundLockState
import com.gdimitria.notificationreminders.model.SoundStream

/**
 * Keeps the phone's sound settings as they were when locked. Android does not let apps disable the volume
 * controls, so every change made elsewhere (volume buttons, Settings, quick settings, other apps) is undone
 * right away. Runs while a long-lived component of the app is alive: the notification listener or the
 * volume button accessibility service.
 */
object SoundLock {

    private const val TAG = "SoundLock"
    private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var users = 0

    /** Volume changes the app itself is making right now (reminders), which the lock must not undo. */
    private var appChanges = 0

    private val restoreRunnable = Runnable { appContext?.let { restore(it) } }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scheduleRestore()
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = scheduleRestore()
    }

    fun isLocked(context: Context): Boolean = Store.get(context).soundProfiles.value.lock != null

    /** Starts watching for changes; balanced by [stop]. */
    fun start(context: Context) {
        users++
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        scheduleRestore()
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        val app = appContext ?: return
        runCatching { app.unregisterReceiver(receiver) }
        app.contentResolver.unregisterContentObserver(observer)
        handler.removeCallbacks(restoreRunnable)
        appContext = null
    }

    /** Locks the phone's current sound state; [wifiRuleId] marks a lock made by a Wi-Fi rule. */
    fun lock(context: Context, wifiRuleId: String? = null) {
        val state = capture(context).copy(byWifiRuleId = wifiRuleId)
        Store.get(context).updateSoundProfiles { it.copy(lock = state) }
        ProfileStatusNotifier.update(context)
    }

    fun unlock(context: Context) {
        Store.get(context).updateSoundProfiles { it.copy(lock = null) }
        ProfileStatusNotifier.update(context)
    }

    /** After a profile was applied from the app while locked, the lock follows the new settings. */
    fun relockIfLocked(context: Context) {
        val lock = Store.get(context).soundProfiles.value.lock ?: return
        lock(context, lock.byWifiRuleId)
    }

    /** The app starts a temporary volume change of its own, for example a reminder raising the volume. */
    fun beginAppChange() {
        appChanges++
    }

    /** The app has put back what it changed; the lock checks the state again. */
    fun endAppChange() {
        appChanges = (appChanges - 1).coerceAtLeast(0)
        if (appChanges == 0) scheduleRestore()
    }

    private fun scheduleRestore() {
        handler.removeCallbacks(restoreRunnable)
        // Short delay so that a burst of changes (holding a volume button) is undone once, at the end.
        handler.postDelayed(restoreRunnable, 150)
    }

    @Suppress("DEPRECATION")
    private val vibrateWhenRinging = Settings.System.VIBRATE_WHEN_RINGING

    @Suppress("DEPRECATION")
    private val touchVibration = Settings.System.HAPTIC_FEEDBACK_ENABLED

    private fun SoundStream.android(): Int = when (this) {
        SoundStream.RING -> AudioManager.STREAM_RING
        SoundStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        SoundStream.MEDIA -> AudioManager.STREAM_MUSIC
        SoundStream.ALARM -> AudioManager.STREAM_ALARM
        SoundStream.SYSTEM -> AudioManager.STREAM_SYSTEM
        SoundStream.CALL -> AudioManager.STREAM_VOICE_CALL
    }

    /** The phone's current sound state. */
    fun capture(context: Context): SoundLockState {
        val audio = context.getSystemService(AudioManager::class.java)
        val resolver = context.contentResolver
        return SoundLockState(
            ringerMode = audio.ringerMode,
            volumes = SoundStream.entries.associateWith { audio.getStreamVolume(it.android()) },
            interruptionFilter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter,
            vibrateWhenRinging = runCatching { Settings.System.getInt(resolver, vibrateWhenRinging) }.getOrNull(),
            touchVibration = runCatching { Settings.System.getInt(resolver, touchVibration) }.getOrNull(),
        )
    }

    /** Puts back every locked setting that differs from the phone's current state. */
    fun restore(context: Context) {
        if (appChanges > 0) return
        val lock = Store.get(context).soundProfiles.value.lock ?: return
        applyState(context, lock)
    }

    /** Puts the phone back into [lock], changing only what differs. */
    fun applyState(context: Context, lock: SoundLockState) {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        try {
            if (notifications.isNotificationPolicyAccessGranted &&
                lock.interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN &&
                notifications.currentInterruptionFilter != lock.interruptionFilter
            ) {
                notifications.setInterruptionFilter(lock.interruptionFilter)
            }
            val quietRinger = lock.ringerMode != AudioManager.RINGER_MODE_NORMAL
            for ((stream, index) in lock.volumes) {
                // In vibrate and silent mode a ring or notification volume change would undo the ringer mode.
                if (quietRinger && (stream == SoundStream.RING || stream == SoundStream.NOTIFICATION)) continue
                if (audio.getStreamVolume(stream.android()) != index) audio.setStreamVolume(stream.android(), index, 0)
            }
            if (audio.ringerMode != lock.ringerMode) audio.ringerMode = lock.ringerMode
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot restore the sound state", e)
        }
        if (Settings.System.canWrite(context)) {
            val resolver = context.contentResolver
            listOf(vibrateWhenRinging to lock.vibrateWhenRinging, touchVibration to lock.touchVibration)
                .forEach { (name, value) ->
                    if (value == null) return@forEach
                    runCatching {
                        if (Settings.System.getInt(resolver, name) != value) Settings.System.putInt(resolver, name, value)
                    }
                }
        }
    }
}
