package com.gdimitria.notificationreminders.model

import kotlinx.serialization.Serializable

@Serializable
enum class VibrationType { NONE, SHORT, LONG, DOUBLE, TRIPLE, HEARTBEAT, SOS, RAMP, CONTINUOUS }

@Serializable
enum class FlashPattern { SHORT, LONG, DOUBLE, STROBE, SOS }

/** Audio stream the reminder sound is played on. */
@Serializable
enum class SoundChannel { NOTIFICATION, ALARM, RINGTONE, MEDIA }

/**
 * A daily time window in which reminders may sound.
 * Minutes are counted from midnight. When [endMinute] is lower than [startMinute] the window
 * crosses midnight; when both are equal the window covers the whole day.
 * [days] holds ISO day-of-week numbers (1 = Monday … 7 = Sunday) of the day the window starts.
 */
@Serializable
data class TimeWindow(
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 22 * 60,
    val days: Set<Int> = (1..7).toSet(),
)

@Serializable
data class ReminderConfig(
    /** Time between two consecutive reminders. */
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    /** How long each reminder plays its sound and vibration. */
    val alertDurationSeconds: Int = 5,
    /** Stop reminding after this long since the notification arrived. 0 = until dismissed. */
    val maxDurationMinutes: Int = 60,
    /** Stop after this many reminders. 0 = unlimited. */
    val maxRepeats: Int = 0,
    /** A new (alerting) notification of the same app restarts the duration and repeat counters. */
    val restartOnNewNotification: Boolean = true,
    /** Legacy (v1–v2) active hours, moved to the profile schedule on upgrade and no longer used. */
    val activeWindows: List<TimeWindow> = emptyList(),
    val vibrationType: VibrationType = VibrationType.DOUBLE,
    /** 1–255. Only used on devices with amplitude control. */
    val vibrationIntensity: Int = 255,
    val soundEnabled: Boolean = true,
    /** null = system default notification sound. */
    val soundUri: String? = null,
    val soundName: String? = null,
    val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
    val soundChannel: SoundChannel = SoundChannel.NOTIFICATION,
    /** Temporarily raise the stream volume of [soundChannel] to [volumePercent] while playing. */
    val forceVolume: Boolean = false,
    /** Repeat the melody for the whole alert duration; false plays it once. */
    val loopSound: Boolean = true,
    /** Honour the phone's silent and vibrate ringer modes and Do Not Disturb. */
    val respectSilentMode: Boolean = true,
    /** Ignore notifications the system ranks below default importance (silent ones). */
    val ignoreSilentNotifications: Boolean = false,
    /** Turn the screen on at each reminder. */
    val screenOn: Boolean = false,
    /** Blink the camera flash at each reminder. */
    val flashEnabled: Boolean = false,
    val flashPattern: FlashPattern = FlashPattern.SHORT,
    /** Blink the notification LED while the reminder is pending (devices with a LED only). */
    val ledEnabled: Boolean = false,
    /** ARGB colour of the LED. */
    val ledColor: Int = LED_WHITE,
    /** Wi-Fi networks on which these settings do not remind, for example home. */
    val quietWifi: List<WifiQuiet> = emptyList(),
) {
    /** Whether the source [key] is kept quiet by these settings while the phone is connected to [ssid]. */
    fun isQuiet(key: String, ssid: String?): Boolean =
        ssid != null && quietWifi.any { it.ssid == ssid && (it.keys.isEmpty() || key in it.keys) }

    /**
     * Same repetition, but with the phone's own notification sound at the phone's notification volume,
     * a standard vibration, and the phone's ring, vibrate or silent mode respected. With [soundProfile]
     * (a contact exception) that profile's ringer mode and notification volume are used instead.
     */
    fun phoneStyle(soundProfile: SoundProfile? = null): ReminderConfig {
        val phone = phoneStyleOfThePhone()
        if (soundProfile == null) return phone
        val volume = soundProfile.volumes[SoundStream.NOTIFICATION] ?: soundProfile.volumes[SoundStream.RING]
        return when (soundProfile.ringerMode) {
            RingerSetting.SILENT -> phone.copy(soundEnabled = false, vibrationType = VibrationType.NONE)
            RingerSetting.VIBRATE -> phone.copy(soundEnabled = false, respectSilentMode = false)
            RingerSetting.NORMAL -> phone.copy(
                respectSilentMode = false,
                forceVolume = volume != null,
                volumePercent = volume ?: 100,
            )
            RingerSetting.KEEP -> if (volume == null) phone else phone.copy(forceVolume = true, volumePercent = volume)
        }
    }

    private fun phoneStyleOfThePhone() = copy(
        soundEnabled = true,
        soundUri = null,
        soundName = null,
        volumePercent = 100,
        soundChannel = SoundChannel.NOTIFICATION,
        forceVolume = false,
        loopSound = false,
        respectSilentMode = true,
        vibrationType = VibrationType.DOUBLE,
        vibrationIntensity = 255,
        screenOn = false,
        flashEnabled = false,
        ledEnabled = false,
    )

    /** Replaces the values that still hold the defaults of versions 1–8 with the current defaults. */
    fun withCurrentDefaults() = copy(
        intervalSeconds = if (intervalSeconds == 120) DEFAULT_INTERVAL_SECONDS else intervalSeconds,
        volumePercent = if (volumePercent == 80) DEFAULT_VOLUME_PERCENT else volumePercent,
    )

    companion object {
        const val LED_WHITE = 0xFFFFFFFF.toInt()
        const val DEFAULT_INTERVAL_SECONDS = 30
        const val DEFAULT_VOLUME_PERCENT = 100
    }
}

/** Keys of the built-in sources, used in place of a package name. */
object Sources {
    const val MISSED_CALLS = "@missed_calls"
    const val MESSAGES = "@messages"

    fun isBuiltIn(key: String) = key.startsWith("@")

    fun typeOf(key: String): SourceType = when (key) {
        MISSED_CALLS -> SourceType.CALLS
        MESSAGES -> SourceType.MESSAGES
        else -> SourceType.APPS
    }
}

/** The three kinds of notification a profile can remind about. */
@Serializable
enum class SourceType { CALLS, MESSAGES, APPS }

/** How a profile reminds about one type of notification. */
@Serializable
enum class ReminderMode {
    /** No reminder. */
    OFF,

    /** The phone's own notification sound and vibration, repeated as set in the common settings. */
    PHONE,

    /** The profile's common settings, shared by the three types. */
    COMMON,

    /** Settings of this type only. */
    CUSTOM,
}

@Serializable
data class TypeSettings(
    val mode: ReminderMode = ReminderMode.OFF,
    /** Used in [ReminderMode.CUSTOM]; kept when switching to another mode. */
    val custom: ReminderConfig? = null,
)

/**
 * A Wi-Fi network on which a profile does not remind, for example home. [keys] are the sources it covers
 * (Missed calls, SMS and MMS, app packages); empty means all of them.
 */
@Serializable
data class WifiQuiet(
    val ssid: String,
    val keys: Set<String> = emptySet(),
)

/** An app the user selected for the Apps type. Also the legacy (v1–v2) per-app settings record. */
@Serializable
data class MonitoredApp(
    val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    val useDefaults: Boolean = true,
    val config: ReminderConfig? = null,
)

/** Legacy (v3–v4) per-source settings inside a profile, converted to [TypeSettings] on upgrade. */
@Serializable
data class SourceSettings(
    val enabled: Boolean = true,
    val useDefaults: Boolean = true,
    val config: ReminderConfig? = null,
)

/**
 * A named set of reminder settings, such as Day or Night.
 * A profile with a [schedule] is active inside its time windows. A profile without a schedule is the
 * fallback, active whenever no scheduled profile applies.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val schedule: List<TimeWindow> = emptyList(),
    /** Common settings, shared by every type set to [ReminderMode.COMMON]. */
    val defaults: ReminderConfig = ReminderConfig(),
    val calls: TypeSettings? = null,
    val messages: TypeSettings? = null,
    val apps: TypeSettings? = null,
    /** Legacy (v3–v4) per-source settings, converted by [AppSettings.migrated]. */
    val sources: Map<String, SourceSettings> = emptyMap(),
    /** Legacy (v15) quiet networks of the profile, moved into its settings by [AppSettings.migrated]. */
    val wifiQuiet: List<WifiQuiet> = emptyList(),
) {
    fun type(type: SourceType): TypeSettings = when (type) {
        SourceType.CALLS -> calls
        SourceType.MESSAGES -> messages
        SourceType.APPS -> apps
    } ?: TypeSettings()

    fun updateType(type: SourceType, transform: (TypeSettings) -> TypeSettings): Profile = when (type) {
        SourceType.CALLS -> copy(calls = transform(type(type)))
        SourceType.MESSAGES -> copy(messages = transform(type(type)))
        SourceType.APPS -> copy(apps = transform(type(type)))
    }

    /**
     * Effective configuration for a type, or null when this profile does not remind about it.
     * [soundOverride] is the sound profile of a contact exception for the notification's sender.
     */
    fun configFor(type: SourceType, soundOverride: SoundProfile? = null): ReminderConfig? {
        val settings = type(type)
        return when (settings.mode) {
            ReminderMode.OFF -> null
            ReminderMode.PHONE -> defaults.phoneStyle(soundOverride)
            ReminderMode.COMMON -> defaults
            ReminderMode.CUSTOM -> settings.custom ?: defaults
        }
    }

    /** Converts legacy per-source settings into the three types. */
    fun withTypesFromSources(): Profile {
        if (calls != null && messages != null && apps != null) return copy(sources = emptyMap())
        fun typeOf(entries: List<SourceSettings>): TypeSettings {
            val on = entries.filter { it.enabled }
            val custom = on.firstOrNull { !it.useDefaults && it.config != null }?.config
            return when {
                on.isEmpty() -> TypeSettings()
                custom != null -> TypeSettings(ReminderMode.CUSTOM, custom.copy(activeWindows = emptyList()))
                else -> TypeSettings(ReminderMode.COMMON)
            }
        }
        return copy(
            calls = calls ?: typeOf(listOfNotNull(sources[Sources.MISSED_CALLS])),
            messages = messages ?: typeOf(listOfNotNull(sources[Sources.MESSAGES])),
            apps = apps ?: typeOf(sources.filterKeys { !Sources.isBuiltIn(it) }.values.toList()),
            sources = emptyMap(),
        )
    }
}

@Serializable
data class AppSettings(
    val masterEnabled: Boolean = true,
    val profiles: List<Profile> = emptyList(),
    /** Apps reminded about through the Apps type, shared by all profiles. */
    val apps: List<MonitoredApp> = emptyList(),
    /** Profile chosen by hand, or null to switch profiles automatically by schedule. */
    val manualProfileId: String? = null,
    /** Legacy (v16) general quiet networks, moved into the settings of every profile by [migrated]. */
    val wifiQuiet: List<WifiQuiet> = emptyList(),
    /** Epoch millis until which all reminders are paused. */
    val snoozeUntil: Long = 0,
    /** Acknowledge all reminders when the phone is unlocked. */
    val stopOnUnlock: Boolean = false,
    /** Schedule with AlarmManager.setAlarmClock, which is immune to Doze (shows an alarm icon). */
    val alarmClockMode: Boolean = false,
    /** Legacy (v1–v2) single-profile settings, moved into [profiles] by [migrated]. */
    val defaults: ReminderConfig? = null,
    val missedCalls: MonitoredApp? = null,
    val messages: MonitoredApp? = null,
    /** Version of the built-in defaults these settings were last brought up to, see [migrated]. */
    val defaultsVersion: Int = 0,
) {
    fun profile(id: String?): Profile? = profiles.firstOrNull { it.id == id }


    fun labelFor(key: String): String = apps.firstOrNull { it.packageName == key }?.label ?: key

    /** Whether [key] is a built-in source or a selected app that is switched on. */
    fun isSourceOn(key: String): Boolean =
        Sources.isBuiltIn(key) || apps.any { it.packageName == key && it.enabled }

    fun updateProfile(id: String, transform: (Profile) -> Profile): AppSettings =
        copy(profiles = profiles.map { if (it.id == id) transform(it) else it })

    /** Converts settings of older versions, or creates the default Day profile on a fresh install. */
    fun migrated(): AppSettings {
        val v3 = if (profiles.isNotEmpty()) this else fromLegacy()
        val usedInProfiles = v3.profiles.flatMap { p -> p.sources.filterValues { it.enabled }.keys }.toSet()
        val hadSources = v3.profiles.any { it.sources.isNotEmpty() }
        // Quiet networks of versions 15 (per profile) and 16 (general) move into every settings of the profiles.
        val legacyQuiet = mergeQuiet(v3.wifiQuiet + v3.profiles.flatMap { it.wifiQuiet })
        fun ReminderConfig.withLegacyQuiet() =
            if (legacyQuiet.isEmpty()) this else copy(quietWifi = mergeQuiet(quietWifi + legacyQuiet))
        val v5 = v3.copy(
            profiles = v3.profiles.map { profile ->
                val converted = profile.withTypesFromSources()
                SourceType.entries.fold(
                    converted.copy(defaults = converted.defaults.withLegacyQuiet(), wifiQuiet = emptyList()),
                ) { p, type -> p.updateType(type) { it.copy(custom = it.custom?.withLegacyQuiet()) } }
            },
            // Before v5 apps were switched on per profile; keep an app on if any profile used it.
            apps = if (hadSources) v3.apps.map { it.copy(enabled = it.packageName in usedInProfiles) } else v3.apps,
            wifiQuiet = emptyList(),
        )
        if (v5.defaultsVersion >= Profiles.DEFAULTS_VERSION) return v5
        var profiles = v5.profiles
        if (v5.defaultsVersion < 1) {
            // Day is now the only default profile: drop the Night profile of earlier versions unless it was edited.
            profiles = profiles.filterNot { Profiles.isUntouchedNight(it) }
        }
        if (v5.defaultsVersion < 2) {
            // Repeat every 30 seconds at 100% volume, where the old defaults (2 minutes, 80%) were never changed.
            profiles = profiles.map { profile ->
                SourceType.entries.fold(profile.copy(defaults = profile.defaults.withCurrentDefaults())) { p, type ->
                    p.updateType(type) { it.copy(custom = it.custom?.withCurrentDefaults()) }
                }
            }
        }
        return v5.copy(
            profiles = profiles,
            manualProfileId = v5.manualProfileId?.takeIf { id -> profiles.any { it.id == id } },
            defaultsVersion = Profiles.DEFAULTS_VERSION,
        )
    }

    /** One entry per network, covering everything any of the merged entries covered. */
    private fun mergeQuiet(all: List<WifiQuiet>): List<WifiQuiet> =
        all.groupBy { it.ssid }.map { (ssid, entries) ->
            WifiQuiet(ssid, if (entries.any { it.keys.isEmpty() }) emptySet() else entries.flatMap { it.keys }.toSet())
        }

    private fun fromLegacy(): AppSettings {
        val legacyDefaults = defaults ?: return copy(profiles = Profiles.initial())
        fun MonitoredApp.toSource() = SourceSettings(enabled, useDefaults, config)
        val sources = buildMap {
            missedCalls?.let { put(Sources.MISSED_CALLS, it.toSource()) }
            messages?.let { put(Sources.MESSAGES, it.toSource()) }
            apps.forEach { put(it.packageName, it.toSource()) }
        }
        val day = Profile(
            id = Profiles.DAY_ID,
            name = Profiles.DAY_NAME,
            schedule = legacyDefaults.activeWindows,
            defaults = legacyDefaults.copy(activeWindows = emptyList()),
            sources = sources,
        )
        return copy(
            profiles = listOf(day),
            apps = apps.map { MonitoredApp(it.packageName, it.label, it.enabled) },
            defaults = null,
            missedCalls = null,
            messages = null,
        )
    }
}

object Profiles {
    const val DAY_ID = "day"
    const val DAY_NAME = "Day"

    /** Version 1: Day is the only default profile. Version 2: repeat every 30 seconds at 100% volume. */
    const val DEFAULTS_VERSION = 2

    private const val NIGHT_ID = "night"
    private const val NIGHT_NAME = "Night"
    private val NIGHT_SCHEDULE = listOf(TimeWindow(23 * 60, 7 * 60))
    private val NIGHT_DEFAULTS = ReminderConfig(
        intervalSeconds = 600,
        soundEnabled = false,
        vibrationType = VibrationType.SHORT,
        maxRepeats = 3,
    )

    /** The Night profile created by versions 3–7, with its original name, schedule and common settings. */
    fun isUntouchedNight(profile: Profile) =
        profile.id == NIGHT_ID && profile.name == NIGHT_NAME &&
            profile.schedule == NIGHT_SCHEDULE && profile.defaults == NIGHT_DEFAULTS

    fun day() = Profile(
        id = DAY_ID,
        name = DAY_NAME,
        calls = TypeSettings(ReminderMode.COMMON),
        messages = TypeSettings(ReminderMode.COMMON),
        apps = TypeSettings(ReminderMode.COMMON),
    )

    fun initial() = listOf(day())
}

@Serializable
data class ActiveReminder(
    /** Package name of the app, or a [Sources] key. */
    val packageName: String,
    val startedAt: Long,
    val nextFireAt: Long,
    val count: Int = 0,
    val lastText: String? = null,
    /** Acknowledged by the user; stays silent until a new notification of the app arrives. */
    val stopped: Boolean = false,
    /** The active profile ignores this source; the reminder waits for a profile that reminds about it. */
    val waiting: Boolean = false,
    /** Sound profile of the contact exception matching the latest notification's sender, if any. */
    val soundProfileId: String? = null,
)
