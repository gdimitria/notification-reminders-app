package com.gdimitria.notificationreminders.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun load(text: String) = json.decodeFromString<AppSettings>(text).migrated()

    private fun assertStable(settings: AppSettings) =
        assertEquals(settings, json.decodeFromString<AppSettings>(json.encodeToString(settings)).migrated())

    @Test
    fun freshInstallGetsOnlyDay() {
        val settings = AppSettings().migrated()
        assertEquals(listOf(Profiles.DAY_ID), settings.profiles.map { it.id })
        val day = settings.profiles.single()
        assertTrue(day.schedule.isEmpty())
        SourceType.entries.forEach { assertEquals(ReminderMode.COMMON, day.type(it).mode) }
        assertStable(settings)
    }

    private val v7WithNight = """
        {"profiles":[
          {"id":"day","name":"Day","calls":{"mode":"COMMON"},"messages":{"mode":"COMMON"},"apps":{"mode":"COMMON"}},
          {"id":"night","name":"Night","schedule":[{"startMinute":1380,"endMinute":420}],
           "defaults":{"intervalSeconds":600,"soundEnabled":false,"vibrationType":"SHORT","maxRepeats":3},
           "calls":{"mode":"COMMON"},"messages":{"mode":"COMMON"},"apps":{"mode":"OFF"}}],
         "manualProfileId":"night"}
    """.trimIndent()

    @Test
    fun untouchedNightOfEarlierVersionsIsRemovedOnce() {
        val settings = load(v7WithNight)
        assertEquals(listOf(Profiles.DAY_ID), settings.profiles.map { it.id })
        assertNull(settings.manualProfileId)
        assertEquals(Profiles.DEFAULTS_VERSION, settings.defaultsVersion)
        assertStable(settings)
    }

    @Test
    fun editedNightIsKept() {
        val settings = load(v7WithNight.replace("\"name\":\"Night\"", "\"name\":\"Sleep\""))
        assertEquals(listOf("day", "night"), settings.profiles.map { it.id })
        assertEquals("night", settings.manualProfileId)
    }

    @Test
    fun version2SettingsBecomeTheDayProfile() {
        val settings = load(
            """
            {"masterEnabled":true,
             "defaults":{"intervalSeconds":300,"activeWindows":[{"startMinute":480,"endMinute":1320,"days":[1,2,3,4,5,6,7]}]},
             "missedCalls":{"packageName":"@missed_calls","label":"Missed calls","enabled":true},
             "messages":{"packageName":"@messages","label":"SMS and MMS","enabled":false},
             "apps":[{"packageName":"com.whatsapp","label":"WhatsApp","useDefaults":false,
                      "config":{"intervalSeconds":30}}],
             "stopOnUnlock":true}
            """.trimIndent(),
        )
        val day = settings.profile(Profiles.DAY_ID)!!
        assertEquals(listOf(TimeWindow(480, 1320)), day.schedule)
        assertEquals(300, day.defaults.intervalSeconds)
        assertEquals(ReminderMode.COMMON, day.type(SourceType.CALLS).mode)
        assertEquals(ReminderMode.OFF, day.type(SourceType.MESSAGES).mode)
        assertEquals(ReminderMode.CUSTOM, day.type(SourceType.APPS).mode)
        assertEquals(30, day.configFor(SourceType.APPS)?.intervalSeconds)
        assertTrue(day.sources.isEmpty())

        assertEquals(listOf(Profiles.DAY_ID), settings.profiles.map { it.id })
        assertEquals(listOf(MonitoredApp("com.whatsapp", "WhatsApp")), settings.apps)
        assertNull(settings.defaults)
        assertTrue(settings.stopOnUnlock)
        assertStable(settings)
    }

    @Test
    fun version4PerSourceSettingsBecomeTypes() {
        val settings = load(
            """
            {"profiles":[{"id":"day","name":"Day","defaults":{"intervalSeconds":120},
               "sources":{"@missed_calls":{"enabled":true},
                          "com.whatsapp":{"enabled":true},
                          "com.viber":{"enabled":false}}}],
             "apps":[{"packageName":"com.whatsapp","label":"WhatsApp"},{"packageName":"com.viber","label":"Viber"}],
             "fixed":{"com.whatsapp":{"config":{"intervalSeconds":30}}}}
            """.trimIndent(),
        )
        val day = settings.profiles.single()
        assertEquals(ReminderMode.COMMON, day.type(SourceType.CALLS).mode)
        assertEquals(ReminderMode.OFF, day.type(SourceType.MESSAGES).mode)
        assertEquals(ReminderMode.COMMON, day.type(SourceType.APPS).mode)
        // Viber was switched off in every profile, so it stays off.
        assertEquals(listOf(true, false), settings.apps.map { it.enabled })
        assertStable(settings)
    }

    @Test
    fun newRemindersRepeatEvery30SecondsWithDoublePulseAtFullStrength() {
        val day = AppSettings().migrated().profiles.single().defaults
        assertEquals(30, day.intervalSeconds)
        assertEquals(VibrationType.DOUBLE, day.vibrationType)
        assertEquals(255, day.vibrationIntensity)
        assertEquals(100, day.volumePercent)
    }

    @Test
    fun version8DefaultsAreUpdatedOnceButEditedValuesKept() {
        val settings = load(
            """
            {"profiles":[{"id":"day","name":"Day",
               "defaults":{"intervalSeconds":120,"volumePercent":80},
               "calls":{"mode":"CUSTOM","custom":{"intervalSeconds":45,"volumePercent":80}},
               "messages":{"mode":"COMMON"},"apps":{"mode":"COMMON"}}],
             "defaultsVersion":1}
            """.trimIndent(),
        )
        val day = settings.profiles.single()
        assertEquals(30, day.defaults.intervalSeconds)
        assertEquals(100, day.defaults.volumePercent)
        val calls = day.type(SourceType.CALLS).custom!!
        assertEquals(45, calls.intervalSeconds)
        assertEquals(100, calls.volumePercent)
        assertEquals(Profiles.DEFAULTS_VERSION, settings.defaultsVersion)

        // Once updated, a deliberate 2-minute interval stays.
        val edited = settings.updateProfile("day") { it.copy(defaults = it.defaults.copy(intervalSeconds = 120)) }
        assertEquals(120, edited.migrated().profiles.single().defaults.intervalSeconds)
    }

    @Test
    fun quietNetworksOfVersions15And16MoveIntoEverySettings() {
        val settings = load(
            """
            {"profiles":[
              {"id":"day","name":"Day","calls":{"mode":"COMMON"},"messages":{"mode":"COMMON"},"apps":{"mode":"COMMON"},
               "wifiQuiet":[{"ssid":"Home","keys":["com.tplink.iot"]},{"ssid":"Office"}]},
              {"id":"work","name":"Work","calls":{"mode":"COMMON"},"messages":{"mode":"COMMON"},"apps":{"mode":"COMMON"},
               "wifiQuiet":[{"ssid":"Home","keys":["@messages"]}]}],
             "wifiQuiet":[{"ssid":"Cafe"}],
             "defaultsVersion":2}
            """.trimIndent(),
        )
        val expected = listOf(
            WifiQuiet("Cafe"), WifiQuiet("Home", setOf("com.tplink.iot", "@messages")), WifiQuiet("Office"),
        )
        settings.profiles.forEach { assertEquals(expected, it.defaults.quietWifi) }
        assertTrue(settings.profiles.all { it.wifiQuiet.isEmpty() })
        assertTrue(settings.wifiQuiet.isEmpty())
        assertStable(settings)
    }
}
