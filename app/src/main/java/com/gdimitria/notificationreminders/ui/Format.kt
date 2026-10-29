package com.gdimitria.notificationreminders.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import com.gdimitria.notificationreminders.R
import com.gdimitria.notificationreminders.model.AppSettings
import com.gdimitria.notificationreminders.model.FlashPattern
import com.gdimitria.notificationreminders.model.Profile
import com.gdimitria.notificationreminders.model.Profiles
import com.gdimitria.notificationreminders.model.ReminderConfig
import com.gdimitria.notificationreminders.model.SoundChannel
import com.gdimitria.notificationreminders.model.Sources
import com.gdimitria.notificationreminders.model.TimeWindow
import com.gdimitria.notificationreminders.model.VibrationType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

object Options {
    val intervalsSeconds = listOf(
        10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600, 5400, 7200,
    )
    val alertDurationsSeconds = listOf(1, 2, 3, 4, 5, 6, 8, 10, 15, 20, 30, 45, 60)

    /** 0 (until dismissed) is placed last. */
    val maxDurationsMinutes = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440, 0)

    /** 0 (unlimited) is placed last. */
    val maxRepeats = listOf(1, 2, 3, 4, 5, 6, 8, 10, 15, 20, 30, 50, 100, 0)

    /** LED colours with their names. */
    val ledColors = listOf(
        0xFFFFFFFF.toInt() to R.string.color_white,
        0xFFFF0000.toInt() to R.string.color_red,
        0xFF00FF00.toInt() to R.string.color_green,
        0xFF0000FF.toInt() to R.string.color_blue,
        0xFFFFFF00.toInt() to R.string.color_yellow,
        0xFF00FFFF.toInt() to R.string.color_cyan,
        0xFFFF00FF.toInt() to R.string.color_magenta,
    )
}

fun Context.formatSeconds(seconds: Int): String = when {
    seconds < 60 -> getString(R.string.duration_seconds, seconds)
    seconds % 60 != 0 -> getString(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
    else -> formatMinutes(seconds / 60)
}

fun Context.formatMinutes(minutes: Int): String = when {
    minutes < 60 -> getString(R.string.duration_minutes, minutes)
    minutes % 60 == 0 -> getString(R.string.duration_hours, minutes / 60)
    else -> getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

fun Context.formatClock(minuteOfDay: Int): String {
    val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
    return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern(pattern))
}

fun Context.formatTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

fun Context.formatWindow(window: TimeWindow): String =
    "${formatClock(window.startMinute)}–${formatClock(window.endMinute)}"

/** Monday-first list of ISO day numbers with their short localized names. */
fun dayLabels(locale: Locale = Locale.getDefault()): List<Pair<Int, String>> =
    DayOfWeek.entries.map { it.value to it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }

/** Localized label of a source: built-in sources by name, apps by their stored label. */
fun Context.sourceLabel(settings: AppSettings, key: String): String = when (key) {
    Sources.MISSED_CALLS -> getString(R.string.source_missed_calls)
    Sources.MESSAGES -> getString(R.string.source_messages)
    else -> settings.labelFor(key)
}

/** Profile name, localized for the built-in Day profile while it keeps its original name. */
fun Context.profileName(profile: Profile): String = when {
    profile.id == Profiles.DAY_ID && profile.name == Profiles.DAY_NAME -> getString(R.string.profile_day)
    else -> profile.name
}

/** Summary of a profile schedule, such as "23:00–07:00 every day". */
fun Context.scheduleSummary(profile: Profile): String {
    if (profile.schedule.isEmpty()) return getString(R.string.schedule_fallback)
    val days = dayLabels().toMap()
    return profile.schedule.joinToString(", ") { window ->
        val dayText = when (window.days) {
            (1..7).toSet() -> getString(R.string.every_day)
            (1..5).toSet() -> getString(R.string.weekdays)
            setOf(6, 7) -> getString(R.string.weekends)
            else -> window.days.sorted().joinToString(" ") { days[it].orEmpty() }
        }
        "${formatWindow(window)} $dayText"
    }
}

/** One-line summary of a configuration, such as "Every 2 min, sound 80%, Double pulse". */
fun Context.configSummary(config: ReminderConfig): String = buildList {
    add(getString(R.string.summary_every, formatSeconds(config.intervalSeconds)))
    if (config.soundEnabled) add(getString(R.string.summary_sound, config.volumePercent))
    if (config.vibrationType != VibrationType.NONE) add(getString(config.vibrationType.labelRes()))
    if (config.screenOn) add(getString(R.string.screen_on))
    if (config.flashEnabled) add(getString(R.string.flash))
    if (config.ledEnabled) add(getString(R.string.led))
}.joinToString(", ")

@StringRes
fun FlashPattern.labelRes(): Int = when (this) {
    FlashPattern.SHORT -> R.string.flash_short
    FlashPattern.LONG -> R.string.flash_long
    FlashPattern.DOUBLE -> R.string.flash_double
    FlashPattern.STROBE -> R.string.flash_strobe
    FlashPattern.SOS -> R.string.flash_sos
}

@StringRes
fun VibrationType.labelRes(): Int = when (this) {
    VibrationType.NONE -> R.string.vibration_none
    VibrationType.SHORT -> R.string.vibration_short
    VibrationType.LONG -> R.string.vibration_long
    VibrationType.DOUBLE -> R.string.vibration_double
    VibrationType.TRIPLE -> R.string.vibration_triple
    VibrationType.HEARTBEAT -> R.string.vibration_heartbeat
    VibrationType.SOS -> R.string.vibration_sos
    VibrationType.RAMP -> R.string.vibration_ramp
    VibrationType.CONTINUOUS -> R.string.vibration_continuous
}

@StringRes
fun SoundChannel.labelRes(): Int = when (this) {
    SoundChannel.NOTIFICATION -> R.string.channel_notification
    SoundChannel.ALARM -> R.string.channel_alarm
    SoundChannel.RINGTONE -> R.string.channel_ringtone
    SoundChannel.MEDIA -> R.string.channel_media
}
