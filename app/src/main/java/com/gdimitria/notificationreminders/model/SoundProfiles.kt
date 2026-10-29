package com.gdimitria.notificationreminders.model

import kotlinx.serialization.Serializable

/** Phone volume streams a sound profile can set. */
@Serializable
enum class SoundStream { RING, NOTIFICATION, MEDIA, ALARM, SYSTEM, CALL }

@Serializable
enum class RingerSetting { KEEP, NORMAL, VIBRATE, SILENT }

@Serializable
enum class DndSetting { KEEP, OFF, PRIORITY, ALARMS, TOTAL_SILENCE }

/** Icon of a sound profile, shown in the status bar, the quick settings tile and the app. */
@Serializable
enum class SoundIcon { VOLUME_UP, VOLUME_DOWN, VIBRATE, MUTE, BELL, MOON, LETTER }

/** A phone setting a profile turns on, turns off or leaves as it is. */
@Serializable
enum class Toggle { KEEP, ON, OFF }

/**
 * A set of phone sound and vibration settings, applied on demand. Independent of the reminders.
 * [volumes] holds percentages per stream; a stream missing from the map keeps its volume.
 */
@Serializable
data class SoundProfile(
    val id: String,
    val name: String,
    val ringerMode: RingerSetting = RingerSetting.NORMAL,
    val volumes: Map<SoundStream, Int> = emptyMap(),
    val vibrateWhenRinging: Toggle = Toggle.KEEP,
    val touchVibration: Toggle = Toggle.KEEP,
    val doNotDisturb: DndSetting = DndSetting.KEEP,
    /** Chosen icon, or null for the default of the profile. */
    val icon: SoundIcon? = null,
) {
    /** Loud, Normal and Silent have speaker icons; other profiles show their first letter. */
    val effectiveIcon: SoundIcon
        get() = icon ?: when (id) {
            SoundProfiles.LOUD_ID -> SoundIcon.VOLUME_UP
            SoundProfiles.NORMAL_ID -> SoundIcon.VOLUME_DOWN
            SoundProfiles.SILENT_ID -> SoundIcon.MUTE
            else -> SoundIcon.LETTER
        }
}

/**
 * The phone's sound state captured when the lock was switched on, restored whenever something else changes it.
 * Volumes are raw stream indexes; null settings were not readable and are not enforced.
 */
@Serializable
data class SoundLockState(
    val ringerMode: Int,
    val volumes: Map<SoundStream, Int>,
    val interruptionFilter: Int,
    val vibrateWhenRinging: Int? = null,
    val touchVibration: Int? = null,
    /** Set when a Wi-Fi rule locked the sound; that rule unlocks it again when the network is left. */
    val byWifiRuleId: String? = null,
)

/**
 * Applies [profileId] when the phone connects to the Wi-Fi network [ssid]; leaving the network restores the
 * profile that was selected before connecting. With [lock] the sound settings stay locked while connected;
 * otherwise profiles can still be changed by hand.
 */
@Serializable
data class WifiRule(
    val id: String,
    val ssid: String,
    val profileId: String,
    val lock: Boolean = false,
    val enabled: Boolean = true,
)

/** A contact chosen for an exception, with the details needed to recognise it without reading the contacts. */
@Serializable
data class ExceptionContact(
    val lookupKey: String,
    val name: String,
    val numbers: List<String> = emptyList(),
)

/** A contact group chosen for an exception; its members are read from the contacts when matching. */
@Serializable
data class ExceptionGroup(val id: Long, val title: String)

/**
 * Uses [profileId] instead of the current sound profile for the listed contacts and contact groups:
 * for their incoming calls ([calls]), and for their messages and app notifications ([messages]), which get an
 * alert of their own with that profile. Reminders set to "Phone's sound and vibration" also use it.
 */
@Serializable
data class ContactException(
    val id: String,
    val name: String,
    val profileId: String,
    val contacts: List<ExceptionContact> = emptyList(),
    val groups: List<ExceptionGroup> = emptyList(),
    val calls: Boolean = true,
    val messages: Boolean = true,
    val enabled: Boolean = true,
)

/** Everything that identifies the people of an exception: numbers (normalised), names and contact keys. */
data class ExceptionPeople(
    val numbers: Set<String> = emptySet(),
    val names: Set<String> = emptySet(),
    val lookupKeys: Set<String> = emptySet(),
)

/** Who a call or notification comes from, as far as Android tells. */
data class Sender(
    val numbers: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    val lookupKeys: List<String> = emptyList(),
)

object ContactMatching {

    /** Digits only; numbers are compared by their last 9 digits so that +30 69… and 69… match. */
    fun normalize(number: String): String = number.filter { it.isDigit() }.takeLast(9)

    fun normalizeName(name: String): String = name.trim().lowercase()

    fun matches(people: ExceptionPeople, sender: Sender): Boolean {
        val numbers = sender.numbers.map(::normalize).filter { it.length >= 6 }
        if (numbers.any { it in people.numbers }) return true
        if (sender.lookupKeys.any { it in people.lookupKeys }) return true
        return sender.names.map(::normalizeName).any { it.isNotEmpty() && it in people.names }
    }

    /** People of the directly chosen contacts; group members are added by the caller. */
    fun peopleOf(exception: ContactException): ExceptionPeople = ExceptionPeople(
        numbers = exception.contacts.flatMap { it.numbers }.map(::normalize).filter { it.length >= 6 }.toSet(),
        names = exception.contacts.map { normalizeName(it.name) }.filter { it.isNotEmpty() }.toSet(),
        lookupKeys = exception.contacts.map { it.lookupKey }.toSet(),
    )
}

/** What to do after the connected Wi-Fi network changed. */
data class WifiTransition(val leave: WifiRule?, val enter: WifiRule?)

@Serializable
data class SoundProfiles(
    val profiles: List<SoundProfile> = emptyList(),
    /** Last applied profile. */
    val activeId: String? = null,
    /** Locked sound state, or null when unlocked (the default). */
    val lock: SoundLockState? = null,
    /**
     * Whether the lock also restricts this app. When false (the default) sound profiles applied in the app
     * and reminders that raise the volume still work while locked.
     */
    val lockIncludesApp: Boolean = false,
    /** Show the selected profile in the status bar through an ongoing notification. */
    val showInStatusBar: Boolean = true,
    val wifiRules: List<WifiRule> = emptyList(),
    /** Rule of the network the phone is connected to, applied when it connected. */
    val wifiActiveRuleId: String? = null,
    /** Profile selected before [wifiActiveRuleId] was applied, restored when the network is left. */
    val wifiPreviousProfileId: String? = null,
    /**
     * Wi-Fi networks the phone connected to while the app was installed, most recent first. Android does not
     * let apps read the list of saved networks, so the app keeps its own.
     */
    val knownSsids: List<String> = emptyList(),
    /** Contacts and groups that get their own sound profile, checked in list order. */
    val contactExceptions: List<ContactException> = emptyList(),
    /** Version of the built-in defaults these settings were last brought up to, see [withCurrentDefaults]. */
    val defaultsVersion: Int = 0,
) {
    /** Locked, and the lock also restricts this app. */
    val lockedForApp: Boolean get() = lock != null && lockIncludesApp

    fun profile(id: String?): SoundProfile? = profiles.firstOrNull { it.id == id }

    fun update(id: String, transform: (SoundProfile) -> SoundProfile) =
        copy(profiles = profiles.map { if (it.id == id) transform(it) else it })

    /** The profile after the active one, wrapping around; used by the quick settings tile. */
    fun next(): SoundProfile? {
        if (profiles.isEmpty()) return null
        val index = profiles.indexOfFirst { it.id == activeId }
        return profiles[(index + 1) % profiles.size]
    }

    fun wifiRule(id: String?): WifiRule? = wifiRules.firstOrNull { it.id == id }

    fun contactException(id: String?): ContactException? = contactExceptions.firstOrNull { it.id == id }

    fun updateContactException(id: String, transform: (ContactException) -> ContactException) =
        copy(contactExceptions = contactExceptions.map { if (it.id == id) transform(it) else it })

    /** Remembers [ssid] as the most recent known network. */
    fun withKnownSsid(ssid: String): SoundProfiles =
        copy(knownSsids = (listOf(ssid) + knownSsids.filter { it != ssid }).take(MAX_KNOWN_SSIDS))

    /**
     * Rules to leave and enter when the phone is now connected to [ssid] (null = no Wi-Fi). A rule applies only
     * once per connection, so profiles chosen by hand while connected are kept.
     */
    fun wifiTransition(ssid: String?): WifiTransition {
        val target = ssid?.let { name -> wifiRules.firstOrNull { it.enabled && it.ssid == name } }
        if (target?.id == wifiActiveRuleId) return WifiTransition(null, null)
        return WifiTransition(leave = wifiRule(wifiActiveRuleId), enter = target)
    }

    /** Brings settings of earlier versions up to the current built-in defaults, once. */
    fun withCurrentDefaults(): SoundProfiles {
        if (defaultsVersion >= DEFAULTS_VERSION) return this
        // Version 1: Normal uses fixed volumes instead of the phone's volumes at first start.
        return update(NORMAL_ID) { normal().copy(name = it.name) }.copy(defaultsVersion = DEFAULTS_VERSION)
    }

    companion object {
        const val LOUD_ID = "loud"
        const val NORMAL_ID = "normal"
        const val SILENT_ID = "silent"
        const val LOUD_NAME = "Loud"
        const val NORMAL_NAME = "Normal"
        const val SILENT_NAME = "Silent"

        const val MAX_KNOWN_SSIDS = 30

        /** Version 1: fixed Normal volumes. */
        const val DEFAULTS_VERSION = 1

        /** Ringtone 14%, notifications 13%, media 20%, system 0%, alarm 46%, vibrate for calls, no touch vibration. */
        fun normal() = SoundProfile(
            id = NORMAL_ID,
            name = NORMAL_NAME,
            ringerMode = RingerSetting.NORMAL,
            volumes = mapOf(
                SoundStream.RING to 14,
                SoundStream.NOTIFICATION to 13,
                SoundStream.MEDIA to 20,
                SoundStream.SYSTEM to 0,
                SoundStream.ALARM to 46,
            ),
            vibrateWhenRinging = Toggle.ON,
            touchVibration = Toggle.OFF,
            doNotDisturb = DndSetting.OFF,
        )

        /** Loud, Normal and Silent, with Normal selected. */
        fun initial() = SoundProfiles(
            profiles = listOf(
                SoundProfile(
                    id = LOUD_ID,
                    name = LOUD_NAME,
                    ringerMode = RingerSetting.NORMAL,
                    volumes = SoundStream.entries.associateWith { 100 },
                    vibrateWhenRinging = Toggle.ON,
                    doNotDisturb = DndSetting.OFF,
                ),
                normal(),
                SoundProfile(
                    id = SILENT_ID,
                    name = SILENT_NAME,
                    ringerMode = RingerSetting.SILENT,
                    vibrateWhenRinging = Toggle.OFF,
                ),
            ),
            activeId = NORMAL_ID,
            defaultsVersion = DEFAULTS_VERSION,
        )
    }
}
