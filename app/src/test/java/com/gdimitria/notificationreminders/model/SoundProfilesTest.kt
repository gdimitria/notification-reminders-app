package com.gdimitria.notificationreminders.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundProfilesTest {

    @Test
    fun startsWithLoudNormalSilentAndNormalSelected() {
        val sound = SoundProfiles.initial()
        assertEquals(listOf("loud", "normal", "silent"), sound.profiles.map { it.id })
        assertEquals(SoundProfiles.NORMAL_ID, sound.activeId)

        val loud = sound.profile("loud")!!
        assertEquals(RingerSetting.NORMAL, loud.ringerMode)
        assertEquals(SoundStream.entries.associateWith { 100 }, loud.volumes)

        val silent = sound.profile("silent")!!
        assertEquals(RingerSetting.SILENT, silent.ringerMode)
        assertEquals(emptyMap<SoundStream, Int>(), silent.volumes)
    }

    @Test
    fun normalHasTheRequestedSettings() {
        val normal = SoundProfiles.initial().profile("normal")!!
        assertEquals(
            mapOf(
                SoundStream.RING to 14, SoundStream.NOTIFICATION to 13, SoundStream.MEDIA to 20,
                SoundStream.SYSTEM to 0, SoundStream.ALARM to 46,
            ),
            normal.volumes,
        )
        assertEquals(RingerSetting.NORMAL, normal.ringerMode)
        assertEquals(Toggle.ON, normal.vibrateWhenRinging)
        assertEquals(Toggle.OFF, normal.touchVibration)
        assertEquals(DndSetting.OFF, normal.doNotDisturb)
    }

    @Test
    fun earlierNormalIsUpdatedOnceKeepingItsName() {
        val old = SoundProfiles(
            profiles = listOf(SoundProfile(id = "normal", name = "Everyday", volumes = mapOf(SoundStream.RING to 80))),
        )
        val updated = old.withCurrentDefaults()
        assertEquals("Everyday", updated.profile("normal")!!.name)
        assertEquals(14, updated.profile("normal")!!.volumes[SoundStream.RING])

        // Later edits are left alone.
        val edited = updated.update("normal") { it.copy(volumes = mapOf(SoundStream.RING to 50)) }
        assertEquals(edited, edited.withCurrentDefaults())
    }

    @Test
    fun nextCyclesThroughProfiles() {
        val sound = SoundProfiles.initial()
        assertEquals("silent", sound.next()?.id)
        assertEquals("loud", sound.copy(activeId = "silent").next()?.id)
        assertEquals("loud", sound.copy(activeId = null).next()?.id)
        assertNull(SoundProfiles().next())
    }
}

class SoundLockStateTest {

    @Test
    fun unlockedByDefaultAndLockSurvivesSaving() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        val sound = SoundProfiles.initial()
        assertNull(sound.lock)
        val locked = sound.copy(
            lock = SoundLockState(ringerMode = 2, volumes = mapOf(SoundStream.MEDIA to 7), interruptionFilter = 1),
        )
        assertEquals(locked, json.decodeFromString<SoundProfiles>(json.encodeToString(locked)))
    }
}

class SoundLockScopeTest {

    private val lock = SoundLockState(ringerMode = 2, volumes = emptyMap(), interruptionFilter = 1)

    @Test
    fun lockSparesTheAppByDefault() {
        val sound = SoundProfiles.initial()
        assertEquals(false, sound.lockIncludesApp)
        assertEquals(false, sound.copy(lock = lock).lockedForApp)
        assertEquals(true, sound.copy(lock = lock, lockIncludesApp = true).lockedForApp)
        assertEquals(false, sound.copy(lockIncludesApp = true).lockedForApp)
    }
}

class SoundProfileIconTest {

    @Test
    fun builtInProfilesHaveSpeakerIconsAndOthersTheirFirstLetter() {
        val sound = SoundProfiles.initial()
        assertEquals(true, sound.showInStatusBar)
        assertEquals(SoundIcon.VOLUME_UP, sound.profile("loud")!!.effectiveIcon)
        assertEquals(SoundIcon.VOLUME_DOWN, sound.profile("normal")!!.effectiveIcon)
        assertEquals(SoundIcon.MUTE, sound.profile("silent")!!.effectiveIcon)
        assertEquals(SoundIcon.LETTER, SoundProfile(id = "x", name = "Meeting").effectiveIcon)
        assertEquals(SoundIcon.MOON, SoundProfile(id = "x", name = "Meeting", icon = SoundIcon.MOON).effectiveIcon)
    }
}

class WifiRuleTest {

    private val home = WifiRule(id = "home", ssid = "HomeNet", profileId = "normal")
    private val office = WifiRule(id = "office", ssid = "Office", profileId = "silent", lock = true)
    private val sound = SoundProfiles.initial().copy(wifiRules = listOf(home, office))

    @Test
    fun connectingToARuleNetworkEntersItOnce() {
        assertEquals(WifiTransition(null, home), sound.wifiTransition("HomeNet"))
        val connected = sound.copy(wifiActiveRuleId = "home", wifiPreviousProfileId = "loud")
        // Still on the same network: nothing happens, so a profile chosen by hand is kept.
        assertEquals(WifiTransition(null, null), connected.wifiTransition("HomeNet"))
    }

    @Test
    fun leavingAndSwitchingNetworks() {
        val connected = sound.copy(wifiActiveRuleId = "home", wifiPreviousProfileId = "loud")
        assertEquals(WifiTransition(home, null), connected.wifiTransition(null))
        assertEquals(WifiTransition(home, null), connected.wifiTransition("Cafe"))
        assertEquals(WifiTransition(home, office), connected.wifiTransition("Office"))
    }

    @Test
    fun disabledRulesAndOtherNetworksDoNothing() {
        assertEquals(WifiTransition(null, null), sound.wifiTransition("Cafe"))
        assertEquals(WifiTransition(null, null), sound.wifiTransition(null))
        val disabled = sound.copy(wifiRules = listOf(home.copy(enabled = false)))
        assertEquals(WifiTransition(null, null), disabled.wifiTransition("HomeNet"))
    }
}

class KnownSsidsTest {

    @Test
    fun remembersMostRecentFirstWithoutDuplicates() {
        val sound = SoundProfiles().withKnownSsid("Home").withKnownSsid("Office").withKnownSsid("Home")
        assertEquals(listOf("Home", "Office"), sound.knownSsids)
        val many = (1..40).fold(SoundProfiles()) { s, i -> s.withKnownSsid("Net$i") }
        assertEquals(SoundProfiles.MAX_KNOWN_SSIDS, many.knownSsids.size)
        assertEquals("Net40", many.knownSsids.first())
    }
}

class ContactExceptionTest {

    private val exception = ContactException(
        id = "vip", name = "VIP", profileId = "loud",
        contacts = listOf(ExceptionContact("key-anna", "Anna Papadopoulou", listOf("+30 694 123 4567"))),
    )
    private val people = ContactMatching.peopleOf(exception)

    @Test
    fun matchesByNumberInAnyFormat() {
        assertEquals(true, ContactMatching.matches(people, Sender(numbers = listOf("6941234567"))))
        assertEquals(true, ContactMatching.matches(people, Sender(numbers = listOf("0030-694-123-4567"))))
        assertEquals(false, ContactMatching.matches(people, Sender(numbers = listOf("6949999999"))))
        assertEquals(false, ContactMatching.matches(people, Sender(numbers = listOf("123"))))
    }

    @Test
    fun matchesByNameOrContactKey() {
        assertEquals(true, ContactMatching.matches(people, Sender(names = listOf("  anna papadopoulou "))))
        assertEquals(true, ContactMatching.matches(people, Sender(lookupKeys = listOf("key-anna"))))
        assertEquals(false, ContactMatching.matches(people, Sender(names = listOf("Maria"))))
    }

    @Test
    fun phoneStyleUsesTheExceptionProfile() {
        val base = ReminderConfig(intervalSeconds = 30)
        val loud = SoundProfiles.initial().profile("loud")!!
        val silent = SoundProfiles.initial().profile("silent")!!

        val forLoud = base.phoneStyle(loud)
        assertEquals(30, forLoud.intervalSeconds)
        assertEquals(true, forLoud.soundEnabled)
        assertEquals(true, forLoud.forceVolume)
        assertEquals(100, forLoud.volumePercent)
        assertEquals(false, forLoud.respectSilentMode)

        val forSilent = base.phoneStyle(silent)
        assertEquals(false, forSilent.soundEnabled)
        assertEquals(VibrationType.NONE, forSilent.vibrationType)

        // Without an exception: the phone's own settings.
        assertEquals(true, base.phoneStyle().respectSilentMode)
        assertEquals(false, base.phoneStyle().forceVolume)
    }
}
