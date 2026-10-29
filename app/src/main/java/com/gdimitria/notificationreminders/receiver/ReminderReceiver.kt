package com.gdimitria.notificationreminders.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.engine.ProfileStatusNotifier
import com.gdimitria.notificationreminders.engine.ReminderEngine
import com.gdimitria.notificationreminders.engine.SoundProfileApplier
import com.gdimitria.notificationreminders.engine.StatusNotifier

/** Receives the reminder alarm and the actions of the status notification. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> ReminderEngine.tick(context)
            ACTION_STOP_ALL -> ReminderEngine.stopAll(context)
            ACTION_SNOOZE -> ReminderEngine.snooze(context, StatusNotifier.SNOOZE_MINUTES)
            ACTION_APPLY_SOUND_PROFILE -> applySoundProfile(context, intent.getStringExtra(EXTRA_PROFILE_ID))
            ACTION_PROFILE_STATUS_DISMISSED -> restoreProfileStatus(context)
        }
    }

    /** The status bar notification was cleared: post it again, now and once more shortly after. */
    private fun restoreProfileStatus(context: Context) {
        val app = context.applicationContext
        ProfileStatusNotifier.update(app)
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            ProfileStatusNotifier.update(app)
            pending.finish()
        }, 1000)
    }

    private fun applySoundProfile(context: Context, id: String?) {
        val sound = Store.get(context).soundProfiles.value
        if (sound.lock != null) {
            Toast.makeText(context, R.string.sound_locked_toast, Toast.LENGTH_LONG).show()
            ProfileStatusNotifier.update(context)
            return
        }
        val profile = sound.profile(id) ?: return
        if (SoundProfileApplier.apply(context, profile).isNotEmpty()) {
            Toast.makeText(context, R.string.sound_profile_partly_applied, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_PROFILE_STATUS_DISMISSED = "com.gdimitria.notificationreminders.PROFILE_STATUS_DISMISSED"
        const val ACTION_APPLY_SOUND_PROFILE = "com.gdimitria.notificationreminders.APPLY_SOUND_PROFILE"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val ACTION_TICK = "com.gdimitria.notificationreminders.TICK"
        const val ACTION_STOP_ALL = "com.gdimitria.notificationreminders.STOP_ALL"
        const val ACTION_SNOOZE = "com.gdimitria.notificationreminders.SNOOZE"
    }
}
