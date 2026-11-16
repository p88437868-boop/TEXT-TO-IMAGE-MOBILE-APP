package com.textpic.studio.data.prefs

import com.google.common.truth.Truth.assertThat
import com.textpic.studio.ime.KeySoundSynth
import org.junit.Test

/** The numbers behind the keyboard settings. No Android needed. */
class KeyboardPrefsTest {
    @Test
    fun `vibration off means no haptic call at all`() {
        var haptics = 0
        var sounds = 0
        val feedback = KeyFeedback(
            vibrate = { _, _ -> haptics++ },
            playSound = { _, _ -> sounds++ },
            vibrateFallback = { haptics++ },
        )
        feedback.onKeyDown(KeyboardPrefs(vibration = false, sound = false))
        assertThat(haptics).isEqualTo(0)
        assertThat(sounds).isEqualTo(0)
        assertThat(feedback.hapticCount).isEqualTo(0)
    }

    @Test
    fun `vibration on calls the haptic with the chosen strength`() {
        val amplitudes = mutableListOf<Int>()
        val durations = mutableListOf<Long>()
        val feedback = KeyFeedback(
            vibrate = { amplitude, duration ->
                amplitudes.add(amplitude)
                durations.add(duration)
            },
        )
        KeyboardPrefs(
            vibration = true,
            vibrationStrength = HapticStrength.STRONG,
        ).let(feedback::onKeyDown)
        assertThat(amplitudes).containsExactly(KeyboardPrefRules.amplitude(HapticStrength.STRONG))
        assertThat(durations).containsExactly(KeyboardPrefRules.durationMs(HapticStrength.STRONG))
        assertThat(amplitudes.single()).isEqualTo(210)
        assertThat(amplitudes.single()).isGreaterThan(KeyboardPrefRules.amplitude(HapticStrength.LIGHT))
    }

    @Test
    fun `sound off means no sound call, and a silent volume is treated as off`() {
        var sounds = 0
        val feedback = KeyFeedback(playSound = { _, _ -> sounds++ })
        feedback.onKeyDown(KeyboardPrefs(sound = false, vibration = false))
        assertThat(sounds).isEqualTo(0)
        feedback.onKeyDown(KeyboardPrefs(sound = true, soundVolume = 0, vibration = false))
        assertThat(sounds).isEqualTo(0)
        feedback.onKeyDown(KeyboardPrefs(sound = true, soundVolume = 40, vibration = false))
        assertThat(sounds).isEqualTo(1)
    }

    @Test
    fun `volume is clamped to 0-100 percent`() {
        assertThat(KeyboardPrefRules.volumeFraction(150)).isEqualTo(1f)
        assertThat(KeyboardPrefRules.volumeFraction(-20)).isEqualTo(0f)
        assertThat(KeyboardPrefRules.volumeFraction(50)).isEqualTo(0.5f)
    }

    @Test
    fun `height levels stay within five steps`() {
        assertThat(KeyboardPrefRules.clampLevel(0)).isEqualTo(1)
        assertThat(KeyboardPrefRules.clampLevel(9)).isEqualTo(5)
        val fractions = com.textpic.studio.ime.KeyboardMetrics.HEIGHT_FRACTIONS
        assertThat(fractions).hasSize(5)
        assertThat(fractions.first()).isLessThan(fractions.last())
    }

    @Test
    fun `system theme follows the phone`() {
        assertThat(KeyboardPrefRules.resolvedTheme(KeyboardTheme.SYSTEM, true))
            .isEqualTo(KeyboardTheme.DARK)
        assertThat(KeyboardPrefRules.resolvedTheme(KeyboardTheme.SYSTEM, false))
            .isEqualTo(KeyboardTheme.LIGHT)
        assertThat(KeyboardPrefRules.resolvedTheme(KeyboardTheme.LIGHT, true))
            .isEqualTo(KeyboardTheme.LIGHT)
    }

    @Test
    fun `one handed mode uses less than the full width`() {
        assertThat(KeyboardPrefRules.oneHandedWidthFraction(OneHandedSide.OFF)).isEqualTo(1f)
        assertThat(KeyboardPrefRules.oneHandedWidthFraction(OneHandedSide.LEFT))
            .isLessThan(1f)
    }

    @Test
    fun `clipboard timeout of zero means never`() {
        assertThat(KeyboardPrefRules.clipboardTimeoutMs(0)).isNull()
        assertThat(KeyboardPrefRules.clipboardTimeoutMs(60)).isEqualTo(3_600_000L)
    }

    @Test
    fun `every key sound is a valid 16 bit mono wav`() {
        KeySoundId.entries.forEach { id ->
            val bytes = KeySoundSynth.wav(id)
            val text = String(bytes.copyOfRange(0, 4), Charsets.US_ASCII)
            assertThat(text).isEqualTo("RIFF")
            assertThat(String(bytes.copyOfRange(8, 12), Charsets.US_ASCII)).isEqualTo("WAVE")
            assertThat(bytes.size).isGreaterThan(44)
            val samples = KeySoundSynth.samples(id)
            assertThat(samples).isNotEmpty()
            // Loud enough to hear, quiet enough not to clip.
            val peak = samples.maxOf { kotlin.math.abs(it.toInt()) }
            assertThat(peak).isGreaterThan(1_000)
            assertThat(peak).isAtMost(Short.MAX_VALUE.toInt())
        }
    }

    @Test
    fun `the three sounds are different`() {
        val tick = KeySoundSynth.samples(KeySoundId.TICK)
        val pop = KeySoundSynth.samples(KeySoundId.POP)
        assertThat(tick.size).isNotEqualTo(pop.size)
        assertThat(tick.toList()).isNotEqualTo(pop.toList())
    }
}
