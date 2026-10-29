package com.gdimitria.notificationreminders.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.Schedule
import com.gdimitria.notificationreminders.model.SoundIcon
import com.gdimitria.notificationreminders.model.SoundProfile
import com.gdimitria.notificationreminders.receiver.ReminderReceiver
import com.gdimitria.notificationreminders.ui.MainActivity
import com.gdimitria.notificationreminders.ui.profileName
import com.gdimitria.notificationreminders.ui.soundProfileName
import java.time.ZoneId

/**
 * Ongoing notification whose status bar icon shows the selected sound profile. Its text names the sound
 * profile, the lock state and the active reminder profile, and its buttons switch sound profile.
 * Android reserves the right side of the status bar (Wi-Fi, battery) for the system, so the icon appears
 * on the left, with the notification icons.
 */
object ProfileStatusNotifier {

    private const val CHANNEL_ID = "profile_status"
    const val NOTIFICATION_ID = 3
    private const val MAX_ACTIONS = 3

    @DrawableRes
    fun iconRes(icon: SoundIcon): Int = when (icon) {
        SoundIcon.VOLUME_UP -> R.drawable.ic_sound_profile
        SoundIcon.VOLUME_DOWN -> R.drawable.ic_volume_down
        SoundIcon.VIBRATE -> R.drawable.ic_vibration
        SoundIcon.MUTE -> R.drawable.ic_volume_off
        SoundIcon.BELL -> R.drawable.ic_notification
        SoundIcon.MOON -> R.drawable.ic_moon
        SoundIcon.LETTER -> R.drawable.ic_sound_profile
    }

    /** First letter of a name, drawn white on transparent so the status bar can tint it. */
    fun letterBitmap(text: String, size: Int = 96): Bitmap {
        val letter = text.trim().take(1).uppercase().ifEmpty { "?" }
        val bitmap = createBitmap(size, size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.85f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(letter, size / 2f, y, paint)
        return bitmap
    }

    fun icon(context: Context, profile: SoundProfile): IconCompat = when (profile.effectiveIcon) {
        SoundIcon.LETTER -> IconCompat.createWithBitmap(letterBitmap(context.soundProfileName(profile)))
        else -> IconCompat.createWithResource(context, iconRes(profile.effectiveIcon))
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.profile_status_channel),
            // Low, not minimum: minimum-importance notifications get no status bar icon.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val sound = Store.get(context).soundProfiles.value
        val profile = sound.profile(sound.activeId)
        val locked = sound.lock != null
        if (!locked && (!sound.showInStatusBar || profile == null)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val name = profile?.let { context.soundProfileName(it) } ?: context.getString(R.string.sound_profiles)
        val title = if (locked) context.getString(R.string.status_title_locked, name) else name
        val settings = Store.get(context).settings.value
        val reminderProfile = Schedule.activeProfile(settings, System.currentTimeMillis(), ZoneId.systemDefault())
        val text = when {
            !settings.masterEnabled -> context.getString(R.string.status_reminders_off)
            reminderProfile != null -> context.getString(R.string.status_reminder_profile, context.profileName(reminderProfile))
            else -> context.getString(R.string.status_no_reminder_profile)
        }.let { reminders ->
            sound.wifiRule(sound.wifiActiveRuleId)
                ?.let { context.getString(R.string.status_with_wifi, reminders, it.ssid) } ?: reminders
        }
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(profile?.let { icon(context, it) } ?: IconCompat.createWithResource(context, R.drawable.ic_sound_profile))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            // Android 14 lets users clear ongoing notifications; the app is told and puts it back.
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context, NOTIFICATION_ID,
                    Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_PROFILE_STATUS_DISMISSED),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        // Switching from the notification shade is only offered while unlocked, like the quick settings tile.
        if (!locked) {
            sound.profiles.filter { it.id != sound.activeId }.take(MAX_ACTIONS).forEach { other ->
                builder.addAction(0, context.soundProfileName(other), applyIntent(context, other.id))
            }
        }
        val notification = builder.build().apply {
            // Keeps "Clear all" from removing it on phones that honour the flag.
            flags = flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun applyIntent(context: Context, profileId: String): PendingIntent = PendingIntent.getBroadcast(
        context, profileId.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_APPLY_SOUND_PROFILE)
            .putExtra(ReminderReceiver.EXTRA_PROFILE_ID, profileId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
