package com.gdimitria.notificationreminders.model

/**
 * Builds vibration waveforms as parallel arrays of segment durations (ms) and amplitudes (0–255),
 * suitable for VibrationEffect.createWaveform(timings, amplitudes, -1).
 */
object VibrationPatterns {

    /** One cycle of the pattern as (durationMs, amplitudeFraction) segments. */
    private fun cycle(type: VibrationType): List<Pair<Long, Float>> = when (type) {
        VibrationType.NONE -> emptyList()
        VibrationType.SHORT -> listOf(150L to 1f, 850L to 0f)
        VibrationType.LONG -> listOf(800L to 1f, 700L to 0f)
        VibrationType.DOUBLE -> listOf(150L to 1f, 120L to 0f, 150L to 1f, 800L to 0f)
        VibrationType.TRIPLE -> listOf(
            120L to 1f, 100L to 0f, 120L to 1f, 100L to 0f, 120L to 1f, 800L to 0f,
        )
        VibrationType.HEARTBEAT -> listOf(100L to 1f, 120L to 0f, 220L to 0.6f, 900L to 0f)
        VibrationType.SOS -> buildList {
            repeat(3) { add(150L to 1f); add(150L to 0f) }
            repeat(3) { add(450L to 1f); add(150L to 0f) }
            repeat(3) { add(150L to 1f); add(150L to 0f) }
            add(850L to 0f)
        }
        VibrationType.RAMP -> buildList {
            for (i in 1..10) add(80L to i / 10f)
            add(600L to 0f)
        }
        VibrationType.CONTINUOUS -> listOf(1000L to 1f)
    }

    fun build(type: VibrationType, intensity: Int, durationMs: Long): Pair<LongArray, IntArray> {
        val segments = cycle(type)
        if (segments.isEmpty() || durationMs <= 0) return LongArray(0) to IntArray(0)
        val peak = intensity.coerceIn(1, 255)
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        var total = 0L
        loop@ while (true) {
            for ((length, fraction) in segments) {
                val remaining = durationMs - total
                if (remaining <= 0) break@loop
                val segment = minOf(length, remaining)
                timings += segment
                amplitudes += if (fraction <= 0f) 0 else (peak * fraction).toInt().coerceIn(1, 255)
                total += segment
            }
        }
        return timings.toLongArray() to amplitudes.toIntArray()
    }
}

/** Builds camera-flash blink sequences as (durationMs, on) segments. */
object FlashPatterns {

    private fun cycle(pattern: FlashPattern): List<Pair<Long, Boolean>> = when (pattern) {
        FlashPattern.SHORT -> listOf(100L to true, 900L to false)
        FlashPattern.LONG -> listOf(600L to true, 600L to false)
        FlashPattern.DOUBLE -> listOf(100L to true, 150L to false, 100L to true, 800L to false)
        FlashPattern.STROBE -> listOf(60L to true, 90L to false)
        FlashPattern.SOS -> buildList {
            repeat(3) { add(150L to true); add(150L to false) }
            repeat(3) { add(450L to true); add(150L to false) }
            repeat(3) { add(150L to true); add(150L to false) }
            add(850L to false)
        }
    }

    fun build(pattern: FlashPattern, durationMs: Long): List<Pair<Long, Boolean>> {
        val segments = cycle(pattern)
        if (durationMs <= 0) return emptyList()
        val result = ArrayList<Pair<Long, Boolean>>()
        var total = 0L
        loop@ while (true) {
            for ((length, on) in segments) {
                val remaining = durationMs - total
                if (remaining <= 0) break@loop
                val segment = minOf(length, remaining)
                result += segment to on
                total += segment
            }
        }
        return result
    }
}
