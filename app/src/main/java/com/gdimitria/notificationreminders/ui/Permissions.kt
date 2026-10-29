package com.gdimitria.notificationreminders.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.gdimitria.notificationreminders.engine.AlarmScheduler
import com.gdimitria.notificationreminders.service.ReminderListenerService

data class PermissionState(
    val notificationAccess: Boolean,
    val postNotifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
    val dndAccess: Boolean,
    val writeSettings: Boolean,
)

object Permissions {

    fun state(context: Context) = PermissionState(
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName),
        postNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        exactAlarms = AlarmScheduler.canScheduleExact(context),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        dndAccess = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
        writeSettings = Settings.System.canWrite(context),
    )

    fun dndAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

    fun notificationAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, ReminderListenerService::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        } else {
            appDetailsIntent(context)
        }

    @SuppressLint("BatteryLife")
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** Opens [intent], falling back to the app details page when the screen does not exist. */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(appDetailsIntent(context))
        }
    }
}
