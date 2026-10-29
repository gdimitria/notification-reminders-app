package com.gdimitria.notificationreminders.engine

import android.content.Context
import com.gdimitria.notificationreminders.data.Store
import com.gdimitria.notificationreminders.model.ReminderConfig
import com.gdimitria.notificationreminders.model.Schedule
import com.gdimitria.notificationreminders.service.ReminderListenerService
import java.time.ZoneId

/**
 * Keeps one reminder per monitored source while it has a pending notification,
 * and fires the reminders on schedule with the settings of the active profile.
 */
object ReminderEngine {

    /** A notification of a monitored source was posted. */
    /** @param soundProfileId sound profile of a contact exception matching the sender, if any. */
    fun onNotificationPosted(
        context: Context,
        key: String,
        text: String?,
        isNewAlert: Boolean,
        soundProfileId: String? = null,
    ) {
        val store = Store.get(context)
        val settings = store.settings.value
        if (!settings.masterEnabled || !Schedule.isMonitored(settings, key)) return
        val now = System.currentTimeMillis()
        val restart = Schedule.anyConfig(settings, key, now, zone())?.restartOnNewNotification ?: true
        store.updateActive { current ->
            val existing = current[key]
            val updated = when {
                existing == null || (existing.stopped && isNewAlert) || (isNewAlert && restart) ->
                    Schedule.newReminder(settings, key, now, text, zone(), ssid()).copy(soundProfileId = soundProfileId)
                else -> existing.copy(
                    lastText = text ?: existing.lastText,
                    soundProfileId = if (isNewAlert) soundProfileId else existing.soundProfileId,
                )
            }
            current + (key to updated)
        }
        refresh(context)
    }

    /** The source has no qualifying notification left: it was opened or dismissed. */
    fun onAllNotificationsCleared(context: Context, key: String) {
        Store.get(context).updateActive { it - key }
        refresh(context)
    }

    /**
     * Aligns active reminders with the notifications currently shown and with the active profile.
     * @param pending source key to latest notification text for every monitored source with a pending notification.
     */
    fun reconcile(context: Context, pending: Map<String, String?>) {
        val store = Store.get(context)
        val settings = store.settings.value
        val now = System.currentTimeMillis()
        store.updateActive { current ->
            if (!settings.masterEnabled) return@updateActive emptyMap()
            buildMap {
                for ((key, text) in pending) {
                    if (!Schedule.isMonitored(settings, key)) continue
                    val reminder = current[key]?.let { Schedule.realign(settings, key, it, now, zone(), ssid()) }
                    put(key, reminder ?: Schedule.newReminder(settings, key, now, text, zone(), ssid()))
                }
            }
        }
        refresh(context)
    }

    /** Called by the alarm: plays due reminders and schedules the next alarm. */
    fun tick(context: Context) {
        val store = Store.get(context)
        val settings = store.settings.value
        val now = System.currentTimeMillis()
        val stillPending = ReminderListenerService.pendingPackages()
        var toPlay: ReminderConfig? = null
        store.updateActive { current ->
            if (!settings.masterEnabled) return@updateActive emptyMap()
            buildMap {
                for ((key, reminder) in current) {
                    if (!Schedule.isMonitored(settings, key)) continue
                    // Guards against a missed removal callback.
                    if (stillPending != null && key !in stillPending) continue
                    val soundOverride = store.soundProfiles.value.profile(reminder.soundProfileId)
                    val (next, fireWith) = Schedule.step(settings, key, reminder, now, zone(), soundOverride, ssid())
                    if (toPlay == null) toPlay = fireWith
                    if (next != null) put(key, next)
                }
            }
        }
        toPlay?.let { AlertPlayer.play(context, it) }
        refresh(context)
    }

    /** Acknowledges a single source's reminder until its next new notification. */
    fun stop(context: Context, key: String) {
        Store.get(context).updateActive { current ->
            val reminder = current[key] ?: return@updateActive current
            current + (key to reminder.copy(stopped = true))
        }
        AlertPlayer.stop()
        refresh(context)
    }

    fun stopAll(context: Context) {
        Store.get(context).updateActive { current -> current.mapValues { it.value.copy(stopped = true) } }
        AlertPlayer.stop()
        refresh(context)
    }

    fun snooze(context: Context, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        Store.get(context).updateSettings { it.copy(snoozeUntil = until) }
        Store.get(context).updateActive { current ->
            current.mapValues { (_, r) -> if (r.nextFireAt < until) r.copy(nextFireAt = until) else r }
        }
        AlertPlayer.stop()
        refresh(context)
    }

    fun resume(context: Context) {
        val now = System.currentTimeMillis()
        Store.get(context).updateSettings { it.copy(snoozeUntil = 0) }
        val settings = Store.get(context).settings.value
        Store.get(context).updateActive { current ->
            current.mapValues { (key, r) ->
                val config = Schedule.configAt(settings, key, now, zone()) ?: return@mapValues r
                r.copy(nextFireAt = minOf(r.nextFireAt, now + config.intervalSeconds * 1000L))
            }
        }
        refresh(context)
    }

    /** Settings changed in the UI: re-read pending notifications if possible, then reschedule. */
    fun onSettingsChanged(context: Context) {
        val pending = ReminderListenerService.pendingNotifications()
        if (pending != null) reconcile(context, pending) else refresh(context)
    }

    /** Device rebooted: notifications do not survive a reboot, so neither do reminders. */
    fun onBoot(context: Context) {
        Store.get(context).updateActive { emptyMap() }
        refresh(context)
    }

    /** Schedules the alarm for the earliest due reminder and refreshes the status notification. */
    fun refresh(context: Context) {
        val store = Store.get(context)
        val settings = store.settings.value
        val now = System.currentTimeMillis()
        val next = store.active.value.values
            .filter { !it.stopped && it.nextFireAt != Schedule.NEVER }
            .minOfOrNull { it.nextFireAt }
            ?.takeIf { settings.masterEnabled }
        // The status bar notification names the active reminder profile, so wake up when it switches too.
        val boundary = Schedule.nextProfileBoundary(settings, now, zone())
            .takeIf { it != Schedule.NEVER && Store.get(context).soundProfiles.value.showInStatusBar }
        when {
            next != null -> AlarmScheduler.schedule(
                context, maxOf(minOf(next, boundary ?: next), now + 500), settings.alarmClockMode,
            )
            // A profile switch alone never needs the alarm clock icon.
            boundary != null -> AlarmScheduler.schedule(context, maxOf(boundary, now + 500), alarmClockMode = false)
            else -> AlarmScheduler.cancel(context)
        }
        StatusNotifier.update(context)
        ProfileStatusNotifier.update(context)
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** Connected Wi-Fi network, for profiles that do not remind on some networks. */
    private fun ssid(): String? = WifiWatcher.ssid.value
}
