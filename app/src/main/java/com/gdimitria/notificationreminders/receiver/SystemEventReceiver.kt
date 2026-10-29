package com.gdimitria.notificationreminders.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gdimitria.notificationreminders.engine.ReminderEngine

/** Re-arms the reminder alarm after a reboot, an update, a clock change or an exact-alarm permission change. */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ReminderEngine.onBoot(context)
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> ReminderEngine.refresh(context)
        }
    }
}
