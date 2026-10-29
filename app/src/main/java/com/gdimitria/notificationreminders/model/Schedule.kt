package com.gdimitria.notificationreminders.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure scheduling rules, free of Android dependencies so they can be unit tested. */
object Schedule {

    const val NEVER = Long.MAX_VALUE

    fun isInWindow(window: TimeWindow, time: ZonedDateTime): Boolean {
        val minute = time.hour * 60 + time.minute
        val today = time.dayOfWeek.value
        val yesterday = time.minusDays(1).dayOfWeek.value
        val start = window.startMinute
        val end = window.endMinute
        return when {
            start == end -> today in window.days
            start < end -> today in window.days && minute >= start && minute < end
            else -> (today in window.days && minute >= start) ||
                (yesterday in window.days && minute < end)
        }
    }

    fun isAllowed(windows: List<TimeWindow>, millis: Long, zone: ZoneId): Boolean {
        if (windows.isEmpty()) return true
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        return windows.any { isInWindow(it, time) }
    }

    fun isExpired(config: ReminderConfig, reminder: ActiveReminder, now: Long): Boolean {
        if (config.maxRepeats > 0 && reminder.count >= config.maxRepeats) return true
        if (config.maxDurationMinutes > 0 &&
            now - reminder.startedAt >= config.maxDurationMinutes * 60_000L
        ) return true
        return false
    }

    /**
     * Profile in force at [millis]: the manually chosen one, otherwise the first enabled profile whose
     * schedule contains [millis], otherwise the first enabled profile without a schedule.
     */
    fun activeProfile(settings: AppSettings, millis: Long, zone: ZoneId): Profile? {
        settings.manualProfileId?.let { id -> settings.profile(id)?.let { return it } }
        val enabled = settings.profiles.filter { it.enabled }
        return enabled.firstOrNull { it.schedule.isNotEmpty() && isAllowed(it.schedule, millis, zone) }
            ?: enabled.firstOrNull { it.schedule.isEmpty() }
    }

    /**
     * Configuration for the source [key] (a [Sources] key or an app package) under the profile in force at
     * [millis], or null when that profile does not remind about its type. [soundOverride] is the sound profile
     * of a contact exception, used by "Phone's sound and vibration". [ssid] is the connected Wi-Fi network:
     * on a network kept quiet for this source, there is no configuration either.
     */
    fun configAt(
        settings: AppSettings,
        key: String,
        millis: Long,
        zone: ZoneId,
        soundOverride: SoundProfile? = null,
        ssid: String? = null,
    ): ReminderConfig? {
        if (!settings.isSourceOn(key)) return null
        val config = activeProfile(settings, millis, zone)?.configFor(Sources.typeOf(key), soundOverride) ?: return null
        return config.takeUnless { it.isQuiet(key, ssid) }
    }

    /** Whether the settings in force for [key] keep it quiet on the Wi-Fi network [ssid]. */
    fun isQuietAt(settings: AppSettings, key: String, millis: Long, zone: ZoneId, ssid: String?): Boolean {
        if (ssid == null || !settings.isSourceOn(key)) return false
        return activeProfile(settings, millis, zone)?.configFor(Sources.typeOf(key))?.isQuiet(key, ssid) == true
    }

    /** Whether any profile that can become active reminds about the type of [key]. */
    fun isMonitored(settings: AppSettings, key: String): Boolean {
        if (!settings.isSourceOn(key)) return false
        val type = Sources.typeOf(key)
        settings.manualProfileId?.let { id -> settings.profile(id)?.let { return it.configFor(type) != null } }
        return settings.profiles.any { it.enabled && it.configFor(type) != null }
    }

    /** Configuration of the active profile, or of the first profile that reminds about [key]. */
    fun anyConfig(settings: AppSettings, key: String, millis: Long, zone: ZoneId): ReminderConfig? =
        configAt(settings, key, millis, zone)
            ?: settings.profiles.firstNotNullOfOrNull { profile ->
                profile.configFor(Sources.typeOf(key))?.takeIf { profile.enabled }
            }

    /** Next moment after [fromMillis] at which a scheduled profile starts or ends, or [NEVER]. */
    fun nextProfileBoundary(settings: AppSettings, fromMillis: Long, zone: ZoneId): Long {
        if (settings.profile(settings.manualProfileId) != null) return NEVER
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        var best = NEVER
        for (profile in settings.profiles) {
            if (!profile.enabled) continue
            for (window in profile.schedule) {
                for (offset in -1..8L) {
                    val date = from.plusDays(offset)
                    if (date.dayOfWeek.value !in window.days) continue
                    val start = ZonedDateTime.of(
                        date, LocalTime.of(window.startMinute / 60, window.startMinute % 60), zone,
                    )
                    val end = ZonedDateTime.of(
                        if (window.endMinute > window.startMinute) date else date.plusDays(1),
                        LocalTime.of(window.endMinute / 60, window.endMinute % 60), zone,
                    )
                    for (t in listOf(start, end).map { it.toInstant().toEpochMilli() }) {
                        if (t > fromMillis && t < best) best = t
                    }
                }
            }
        }
        return best
    }

    /**
     * A reminder for a notification of [key] that arrived at [now]. A notification that arrives on a Wi-Fi network
     * that is kept quiet is never reminded, so its reminder starts acknowledged.
     */
    fun newReminder(
        settings: AppSettings,
        key: String,
        now: Long,
        text: String?,
        zone: ZoneId,
        ssid: String? = null,
    ): ActiveReminder {
        val config = configAt(settings, key, now, zone, ssid = ssid)
        return ActiveReminder(
            packageName = key,
            startedAt = now,
            nextFireAt = if (config != null) now + config.intervalSeconds * 1000L else nextProfileBoundary(settings, now, zone),
            lastText = text,
            waiting = config == null,
            stopped = isQuietAt(settings, key, now, zone, ssid),
        )
    }

    /** Re-evaluates a reminder after the settings, the active profile or the Wi-Fi network changed. */
    fun realign(
        settings: AppSettings,
        key: String,
        reminder: ActiveReminder,
        now: Long,
        zone: ZoneId,
        ssid: String? = null,
    ): ActiveReminder {
        if (reminder.stopped) return reminder
        val config = configAt(settings, key, now, zone, ssid = ssid)
        return when {
            config == null -> reminder.copy(waiting = true, nextFireAt = nextProfileBoundary(settings, now, zone))
            // Back from a quiet network or a profile without this source: the countdown starts again.
            reminder.waiting -> reminder.copy(
                waiting = false,
                startedAt = now,
                count = 0,
                nextFireAt = now + config.intervalSeconds * 1000L,
            )
            else -> reminder
        }
    }

    /**
     * Applies one engine tick to the reminder of [key].
     * Returns the updated reminder (null when it must be removed) and the configuration to alert with,
     * or null when it does not alert now.
     */
    fun step(
        settings: AppSettings,
        key: String,
        reminder: ActiveReminder,
        now: Long,
        zone: ZoneId,
        soundOverride: SoundProfile? = null,
        ssid: String? = null,
    ): Pair<ActiveReminder?, ReminderConfig?> {
        if (reminder.stopped) return reminder to null
        // Fire up to one second early to absorb alarm jitter.
        if (reminder.nextFireAt > now + 1000) return reminder to null
        // On a quiet Wi-Fi network the reminder pauses; leaving the network realigns it.
        if (isQuietAt(settings, key, now, zone, ssid)) return reminder.copy(waiting = true, nextFireAt = NEVER) to null
        val config = configAt(settings, key, now, zone, soundOverride, ssid)
        if (config == null) {
            // The active profile does not remind about this type: wait for the next profile switch.
            val boundary = nextProfileBoundary(settings, now, zone)
            return (if (boundary == NEVER) null else reminder.copy(nextFireAt = boundary, waiting = true)) to null
        }
        // A profile that reminds about this source took over: count duration and repeats from now on.
        val current = if (reminder.waiting) reminder.copy(startedAt = now, count = 0, waiting = false) else reminder
        if (isExpired(config, current, now)) return null to null
        if (settings.snoozeUntil > now) return current.copy(nextFireAt = settings.snoozeUntil) to null

        val fired = current.copy(count = current.count + 1, nextFireAt = now + config.intervalSeconds * 1000L)
        val keep = !isExpired(config, fired, fired.nextFireAt)
        return (if (keep) fired else null) to config
    }
}
