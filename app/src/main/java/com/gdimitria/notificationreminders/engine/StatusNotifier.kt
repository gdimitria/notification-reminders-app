package com.gdimitria.notificationreminders.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.util.Locale
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.receiver.ReminderReceiver
import com.gdimitria.notificationreminders.ui.MainActivity
import com.gdimitria.notificationreminders.model.Schedule
import com.gdimitria.notificationreminders.ui.profileName
import com.gdimitria.notificationreminders.ui.sourceLabel

/**
 * A silent notification listing the sources being reminded about, with Stop and Snooze actions.
 * When a pending reminder has the LED enabled, the notification is posted on a channel with lights,
 * so the notification LED blinks until the reminders end.
 */
object StatusNotifier {

    const val CHANNEL_ID = "status"
    private const val LED_CHANNEL_PREFIX = "led_"
    private const val NOTIFICATION_ID = 1
    const val SNOOZE_MINUTES = 30

    private var lastChannelId: String? = null

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.status_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Channel lights are fixed once created, so each LED colour has its own channel. */
    private fun ensureLedChannel(context: Context, color: Int): String {
        val id = LED_CHANNEL_PREFIX + "%08X".format(Locale.ROOT, color)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(
                id,
                context.getString(R.string.led_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                lightColor = color
            }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    fun update(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val store = Store.get(context)
        val settings = store.settings.value
        val running = store.active.value.values.filter { !it.stopped }
        if (running.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            lastChannelId = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val ledColor = running.firstNotNullOfOrNull { reminder ->
            Schedule.configAt(settings, reminder.packageName, now, zone)?.takeIf { it.ledEnabled }?.ledColor
        }
        val profile = Schedule.activeProfile(settings, now, zone)
        val channelId = ledColor?.let { ensureLedChannel(context, it) } ?: CHANNEL_ID
        val labels = running.joinToString(", ") { context.sourceLabel(settings, it.packageName) }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Lights start only when a notification alerts, so a channel change needs a fresh notification.
        if (lastChannelId != null && lastChannelId != channelId) manager.cancel(NOTIFICATION_ID)
        lastChannelId = channelId
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.status_title, running.size, running.size))
            .setContentText(labels)
            .setSubText(profile?.let { context.profileName(it) })
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, context.getString(R.string.action_stop_all), actionIntent(context, ReminderReceiver.ACTION_STOP_ALL))
            .addAction(
                0,
                context.getString(R.string.action_snooze_minutes, SNOOZE_MINUTES),
                actionIntent(context, ReminderReceiver.ACTION_SNOOZE),
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
