package com.gdimitria.notificationreminders.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {

    private val zone = ZoneId.of("Europe/Athens")

    /** 2026-10-26 is a Monday. */
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val common = TypeSettings(ReminderMode.COMMON)
    private val day = Profile(id = "day", name = "Day", calls = common, messages = common, apps = common)
    private val night = Profile(
        id = "night", name = "Night",
        schedule = listOf(TimeWindow(23 * 60, 7 * 60)),
        defaults = ReminderConfig(intervalSeconds = 600, maxDurationMinutes = 0),
        calls = common, messages = common, apps = common,
    )

    /** "pkg" is a selected app, so it belongs to the Apps type. */
    private fun settingsWith(vararg profiles: Profile) =
        AppSettings(profiles = profiles.toList(), apps = listOf(MonitoredApp("pkg", "Pkg")))

    private fun config(intervalSeconds: Int = 60, maxDurationMinutes: Int = 0, maxRepeats: Int = 0) =
        ReminderConfig(intervalSeconds = intervalSeconds, maxDurationMinutes = maxDurationMinutes, maxRepeats = maxRepeats)

    private fun single(config: ReminderConfig) =
        settingsWith(Profile(id = "p", name = "P", defaults = config, apps = common))

    @Test
    fun emptyWindowsAreAlwaysAllowed() {
        assertTrue(Schedule.isAllowed(emptyList(), at(26, 3), zone))
    }

    @Test
    fun sameDayWindow() {
        val window = listOf(TimeWindow(8 * 60, 22 * 60))
        assertTrue(Schedule.isAllowed(window, at(26, 8), zone))
        assertTrue(Schedule.isAllowed(window, at(26, 21, 59), zone))
        assertFalse(Schedule.isAllowed(window, at(26, 22), zone))
        assertFalse(Schedule.isAllowed(window, at(26, 6), zone))
    }

    @Test
    fun windowCrossingMidnightBelongsToItsStartDay() {
        // Monday only, 22:00 – 02:00.
        val window = listOf(TimeWindow(22 * 60, 2 * 60, setOf(1)))
        assertTrue(Schedule.isAllowed(window, at(26, 23), zone))
        assertTrue(Schedule.isAllowed(window, at(27, 1, 30), zone))
        assertFalse(Schedule.isAllowed(window, at(26, 1), zone))
        assertFalse(Schedule.isAllowed(window, at(27, 23), zone))
    }

    @Test
    fun scheduledProfileWinsInsideItsWindowFallbackOutside() {
        val settings = settingsWith(day, night)
        assertEquals("night", Schedule.activeProfile(settings, at(26, 23, 30), zone)?.id)
        assertEquals("night", Schedule.activeProfile(settings, at(27, 6, 59), zone)?.id)
        assertEquals("day", Schedule.activeProfile(settings, at(27, 7), zone)?.id)
        assertEquals("day", Schedule.activeProfile(settings, at(27, 12), zone)?.id)
    }

    @Test
    fun disabledProfileIsSkippedAndManualChoiceOverridesSchedule() {
        val disabledNight = settingsWith(day, night.copy(enabled = false))
        assertEquals("day", Schedule.activeProfile(disabledNight, at(26, 23, 30), zone)?.id)

        val manual = settingsWith(day, night).copy(manualProfileId = "night")
        assertEquals("night", Schedule.activeProfile(manual, at(27, 12), zone)?.id)
        assertEquals(Schedule.NEVER, Schedule.nextProfileBoundary(manual, at(27, 12), zone))
    }

    @Test
    fun noProfileOutsideScheduleWithoutFallback() {
        val settings = settingsWith(night)
        assertNull(Schedule.activeProfile(settings, at(27, 12), zone))
        assertEquals(at(27, 23), Schedule.nextProfileBoundary(settings, at(27, 12), zone))
    }

    @Test
    fun nextProfileBoundaryFindsStartAndEnd() {
        val settings = settingsWith(day, night)
        assertEquals(at(26, 23), Schedule.nextProfileBoundary(settings, at(26, 12), zone))
        assertEquals(at(27, 7), Schedule.nextProfileBoundary(settings, at(26, 23, 30), zone))
        assertEquals(at(27, 7), Schedule.nextProfileBoundary(settings, at(27, 3), zone))
    }

    @Test
    fun weekdayScheduleSkipsTheWeekend() {
        val work = Profile(id = "work", name = "Work", schedule = listOf(TimeWindow(9 * 60, 17 * 60, setOf(1, 2, 3, 4, 5))))
        // Saturday 31 October → Monday 2 November at 09:00.
        assertEquals(
            ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            Schedule.nextProfileBoundary(settingsWith(work), at(31, 10), zone),
        )
    }

    @Test
    fun sourceIgnoredAtNightWaitsUntilMorningThenStartsFresh() {
        val settings = settingsWith(
            day.copy(defaults = config(maxDurationMinutes = 60)),
            night.copy(apps = TypeSettings(ReminderMode.OFF)),
        )
        assertTrue(Schedule.isMonitored(settings, "pkg"))

        val arrived = at(27, 1)
        val reminder = Schedule.newReminder(settings, "pkg", arrived, "text", zone)
        assertTrue(reminder.waiting)
        assertEquals(at(27, 7), reminder.nextFireAt)

        // At 07:00 Day takes over: the 60-minute limit counts from 07:00, not from 01:00.
        val (next, fireWith) = Schedule.step(settings, "pkg", reminder, at(27, 7), zone)
        assertEquals(60, fireWith?.intervalSeconds)
        assertFalse(next!!.waiting)
        assertEquals(at(27, 7), next.startedAt)
        assertEquals(1, next.count)
    }

    @Test
    fun profileSettingsApplyAtFireTime() {
        val settings = settingsWith(day.copy(defaults = config(intervalSeconds = 60)), night)
        val reminder = ActiveReminder("pkg", startedAt = at(26, 23), nextFireAt = at(26, 23, 30))
        val (next, fireWith) = Schedule.step(settings, "pkg", reminder, at(26, 23, 30), zone)
        assertEquals(600, fireWith?.intervalSeconds)
        assertEquals(at(26, 23, 40), next!!.nextFireAt)
    }

    @Test
    fun eachTypeHasItsOwnChoice() {
        val custom = config(intervalSeconds = 15)
        val profile = Profile(
            id = "p", name = "P", defaults = config(intervalSeconds = 120),
            calls = TypeSettings(ReminderMode.CUSTOM, custom),
            messages = TypeSettings(ReminderMode.PHONE),
            apps = TypeSettings(ReminderMode.OFF, custom),
        )
        val settings = settingsWith(profile)
        val now = at(26, 12)
        assertEquals(custom, Schedule.configAt(settings, Sources.MISSED_CALLS, now, zone))
        val phone = Schedule.configAt(settings, Sources.MESSAGES, now, zone)!!
        assertEquals(120, phone.intervalSeconds)
        assertTrue(phone.respectSilentMode)
        assertNull(phone.soundUri)
        assertFalse(phone.loopSound)
        assertNull(Schedule.configAt(settings, "pkg", now, zone))
        assertFalse(Schedule.isMonitored(settings, "pkg"))
    }

    @Test
    fun switchedOffOrUnselectedAppIsNotReminded() {
        val settings = settingsWith(day)
        assertTrue(Schedule.isMonitored(settings, "pkg"))
        assertFalse(Schedule.isMonitored(settings, "other.app"))
        val off = settings.copy(apps = listOf(MonitoredApp("pkg", "Pkg", enabled = false)))
        assertFalse(Schedule.isMonitored(off, "pkg"))
    }

    @Test
    fun stepFiresAndSchedulesNext() {
        val now = at(26, 10)
        val reminder = ActiveReminder("pkg", startedAt = now - 60_000, nextFireAt = now)
        val (next, fireWith) = Schedule.step(single(config()), "pkg", reminder, now, zone)
        assertTrue(fireWith != null)
        assertEquals(1, next!!.count)
        assertEquals(now + 60_000, next.nextFireAt)
    }

    @Test
    fun stepWaitsUntilDue() {
        val now = at(26, 10)
        val reminder = ActiveReminder("pkg", startedAt = now, nextFireAt = now + 30_000)
        val (next, fireWith) = Schedule.step(single(config()), "pkg", reminder, now, zone)
        assertNull(fireWith)
        assertEquals(reminder, next)
    }

    @Test
    fun stepDropsAfterLastRepeat() {
        val now = at(26, 10)
        val reminder = ActiveReminder("pkg", startedAt = now, nextFireAt = now, count = 2)
        val (next, fireWith) = Schedule.step(single(config(maxRepeats = 3)), "pkg", reminder, now, zone)
        assertTrue(fireWith != null)
        assertNull(next)
    }

    @Test
    fun stepDropsWhenDurationElapsed() {
        val now = at(26, 10)
        val reminder = ActiveReminder("pkg", startedAt = now - 5 * 60_000, nextFireAt = now)
        val (next, fireWith) = Schedule.step(single(config(maxDurationMinutes = 5)), "pkg", reminder, now, zone)
        assertNull(fireWith)
        assertNull(next)
    }

    @Test
    fun stepRespectsSnooze() {
        val now = at(26, 10)
        val settings = single(config()).copy(snoozeUntil = now + 600_000)
        val reminder = ActiveReminder("pkg", startedAt = now, nextFireAt = now)
        val (next, fireWith) = Schedule.step(settings, "pkg", reminder, now, zone)
        assertNull(fireWith)
        assertEquals(now + 600_000, next!!.nextFireAt)
    }

    @Test
    fun stoppedReminderStaysSilent() {
        val now = at(26, 10)
        val reminder = ActiveReminder("pkg", startedAt = now, nextFireAt = now, stopped = true)
        val (next, fireWith) = Schedule.step(single(config()), "pkg", reminder, now, zone)
        assertNull(fireWith)
        assertEquals(reminder, next)
    }

    @Test
    fun notificationArrivingOnAQuietNetworkIsNeverReminded() {
        val settings = settingsWith(day.copy(defaults = ReminderConfig(quietWifi = listOf(WifiQuiet("Home", setOf("pkg"))))))
        val atHome = Schedule.newReminder(settings, "pkg", at(26, 10), null, zone, ssid = "Home")
        assertTrue(atHome.stopped)
        // Leaving home does not bring it back.
        assertEquals(atHome, Schedule.realign(settings, "pkg", atHome, at(26, 11), zone, ssid = null))
        // Other sources and other networks are reminded.
        assertFalse(Schedule.newReminder(settings, Sources.MESSAGES, at(26, 10), null, zone, ssid = "Home").stopped)
        assertFalse(Schedule.newReminder(settings, "pkg", at(26, 10), null, zone, ssid = "Office").stopped)
    }

    @Test
    fun reminderStartedElsewherePausesAtHomeAndContinuesAfterLeaving() {
        val settings = settingsWith(day.copy(defaults = ReminderConfig(quietWifi = listOf(WifiQuiet("Home")))))
        val away = Schedule.newReminder(settings, "pkg", at(26, 10), null, zone, ssid = null)
        assertFalse(away.stopped)

        val (paused, fireWith) = Schedule.step(settings, "pkg", away, away.nextFireAt, zone, ssid = "Home")
        assertNull(fireWith)
        assertTrue(paused!!.waiting)
        assertEquals(Schedule.NEVER, paused.nextFireAt)

        val resumed = Schedule.realign(settings, "pkg", paused, at(26, 12), zone, ssid = null)
        assertFalse(resumed.waiting)
        assertEquals(at(26, 12) + 30_000, resumed.nextFireAt)
        assertEquals(at(26, 12), resumed.startedAt)
    }

    @Test
    fun eachSettingsHasItsOwnQuietNetworks() {
        // Apps use the common settings, quiet at home; SMS use custom settings without a Wi-Fi exception.
        val profile = day.copy(
            defaults = ReminderConfig(quietWifi = listOf(WifiQuiet("Home"))),
            messages = TypeSettings(ReminderMode.CUSTOM, ReminderConfig(intervalSeconds = 60)),
        )
        val settings = settingsWith(profile, night)
        assertNull(Schedule.configAt(settings, "pkg", at(26, 12), zone, ssid = "Home"))
        assertEquals(60, Schedule.configAt(settings, Sources.MESSAGES, at(26, 12), zone, ssid = "Home")?.intervalSeconds)
        // Night's common settings have no exception, so at night the app is reminded at home.
        assertTrue(Schedule.configAt(settings, "pkg", at(26, 23, 30), zone, ssid = "Home") != null)
    }
}
