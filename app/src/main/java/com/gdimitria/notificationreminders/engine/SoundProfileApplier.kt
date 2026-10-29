package com.gdimitria.notificationreminders.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.DndSetting
import com.gdimitria.notificationreminders.model.RingerSetting
import com.gdimitria.notificationreminders.model.SoundProfile
import com.gdimitria.notificationreminders.model.SoundStream
import com.gdimitria.notificationreminders.model.Toggle
import kotlin.math.roundToInt

/** Applies sound profiles to the phone's ringer mode, volumes, vibration settings and Do Not Disturb. */
object SoundProfileApplier {

    private const val TAG = "SoundProfiles"

    /** Settings that could not be changed because a permission is missing. */
    enum class Problem { DND_ACCESS, WRITE_SETTINGS, LOCKED }

    private fun SoundStream.android(): Int = when (this) {
        SoundStream.RING -> AudioManager.STREAM_RING
        SoundStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        SoundStream.MEDIA -> AudioManager.STREAM_MUSIC
        SoundStream.ALARM -> AudioManager.STREAM_ALARM
        SoundStream.SYSTEM -> AudioManager.STREAM_SYSTEM
        SoundStream.CALL -> AudioManager.STREAM_VOICE_CALL
    }

    /** Applies [profile] and makes it the selected profile. */
    fun apply(context: Context, profile: SoundProfile): Set<Problem> {
        // A sound lock that also restricts this app refuses every change until it is unlocked.
        if (Store.get(context).soundProfiles.value.lockedForApp) return setOf(Problem.LOCKED)
        val problems = applySettings(context, profile)
        Store.get(context).updateSoundProfiles { it.copy(activeId = profile.id) }
        SoundLock.relockIfLocked(context)
        ProfileStatusNotifier.update(context)
        return problems
    }

    /** Changes the phone's settings to [profile] without selecting it, for temporary overrides. */
    fun applySettings(context: Context, profile: SoundProfile): Set<Problem> {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val problems = mutableSetOf<Problem>()

        // Do Not Disturb first, so it cannot block the ringer and volume changes that follow.
        if (profile.doNotDisturb != DndSetting.KEEP) {
            if (notifications.isNotificationPolicyAccessGranted) {
                notifications.setInterruptionFilter(
                    when (profile.doNotDisturb) {
                        DndSetting.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        DndSetting.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                        DndSetting.TOTAL_SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
                        else -> NotificationManager.INTERRUPTION_FILTER_ALL
                    },
                )
            } else {
                problems += Problem.DND_ACCESS
            }
        }

        // Volumes before the ringer mode: in vibrate and silent mode a ring or notification volume
        // change would switch the ringer back to normal, so those two are left alone then.
        val quietRinger = profile.ringerMode == RingerSetting.VIBRATE || profile.ringerMode == RingerSetting.SILENT
        for ((stream, percent) in profile.volumes) {
            if (quietRinger && (stream == SoundStream.RING || stream == SoundStream.NOTIFICATION)) continue
            val id = stream.android()
            val max = audio.getStreamMaxVolume(id)
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(id) else 0
            try {
                audio.setStreamVolume(id, (max * percent / 100f).roundToInt().coerceIn(min, max), 0)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot set volume of $stream", e)
                problems += Problem.DND_ACCESS
            }
        }

        if (profile.ringerMode != RingerSetting.KEEP) {
            try {
                audio.ringerMode = when (profile.ringerMode) {
                    RingerSetting.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                    RingerSetting.SILENT -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot set ringer mode", e)
                problems += Problem.DND_ACCESS
            }
        }

        @Suppress("DEPRECATION")
        val systemSettings = listOf(
            Settings.System.VIBRATE_WHEN_RINGING to profile.vibrateWhenRinging,
            Settings.System.HAPTIC_FEEDBACK_ENABLED to profile.touchVibration,
        ).filter { it.second != Toggle.KEEP }
        if (systemSettings.isNotEmpty()) {
            if (Settings.System.canWrite(context)) {
                for ((name, toggle) in systemSettings) {
                    try {
                        Settings.System.putInt(context.contentResolver, name, if (toggle == Toggle.ON) 1 else 0)
                    } catch (e: Exception) {
                        // Some phones keep these settings for system apps only.
                        Log.w(TAG, "Cannot write $name", e)
                    }
                }
            } else {
                problems += Problem.WRITE_SETTINGS
            }
        }
        return problems
    }
}
