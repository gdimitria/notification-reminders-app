package com.gdimitria.notificationreminders.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternsTest {

    @Test
    fun everyPatternFillsTheRequestedDuration() {
        for (type in VibrationType.entries.filter { it != VibrationType.NONE }) {
            for (duration in listOf(1000L, 2500L, 5000L, 60_000L)) {
                val (timings, amplitudes) = VibrationPatterns.build(type, 200, duration)
                assertEquals("$type $duration", duration, timings.sum())
                assertEquals(timings.size, amplitudes.size)
                assertTrue(amplitudes.all { it in 0..255 })
                assertTrue("$type vibrates", amplitudes.any { it > 0 })
            }
        }
    }

    @Test
    fun noneIsEmpty() {
        val (timings, amplitudes) = VibrationPatterns.build(VibrationType.NONE, 255, 5000)
        assertEquals(0, timings.size)
        assertEquals(0, amplitudes.size)
    }

    @Test
    fun intensityScalesAmplitude() {
        val (_, amplitudes) = VibrationPatterns.build(VibrationType.CONTINUOUS, 100, 2000)
        assertTrue(amplitudes.all { it == 100 })
    }
}

class FlashPatternsTest {

    @Test
    fun everyPatternFillsTheRequestedDurationAndBlinks() {
        for (pattern in FlashPattern.entries) {
            for (duration in listOf(1000L, 2500L, 5000L, 60_000L)) {
                val segments = FlashPatterns.build(pattern, duration)
                assertEquals("$pattern $duration", duration, segments.sumOf { it.first })
                assertTrue("$pattern blinks", segments.any { it.second })
            }
        }
    }
}
