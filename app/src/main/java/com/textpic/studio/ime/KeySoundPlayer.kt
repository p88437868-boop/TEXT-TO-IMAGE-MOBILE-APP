package com.textpic.studio.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.textpic.studio.data.prefs.KeySoundId
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The three key sounds, synthesised here so there is nothing to download and
 * nothing to license. A "tick" is a very short high blip, a "soft click" is a
 * duller, quieter blip, and a "pop" is a low tone with a fast decay.
 *
 * The output is deterministic (the tiny noise floor uses a fixed seed), so a test
 * can check the file format without a sound card.
 */
object KeySoundSynth {
    const val SAMPLE_RATE = 22_050
    const val BITS_PER_SAMPLE = 16
    const val CHANNELS = 1

    fun samples(id: KeySoundId): ShortArray {
        val (durationMs, frequency, sweepTo, noise, gain) = when (id) {
            KeySoundId.TICK -> Blip(18, 1_760.0, 1_760.0, 0.05, 0.55)
            KeySoundId.SOFT_CLICK -> Blip(30, 880.0, 820.0, 0.22, 0.32)
            KeySoundId.POP -> Blip(56, 420.0, 190.0, 0.02, 0.62)
        }
        val count = SAMPLE_RATE * durationMs / 1_000
        val out = ShortArray(count)
        var rng = 0x5EED_1234
        for (index in 0 until count) {
            val progress = index.toDouble() / count
            val frequencyNow = frequency + (sweepTo - frequency) * progress
            val envelope = exp(-5.5 * progress)
            rng = rng * 1_103_515_245 + 12_345
            val noiseSample = ((rng ushr 16) and 0x7FFF) / 32_767.0 - 1.0
            val value = (sin(2 * PI * frequencyNow * index / SAMPLE_RATE) * (1.0 - noise) +
                noiseSample * noise) * envelope * gain
            out[index] = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        // Guarantee a soft start and end so the blip never clicks.
        val fade = (count / 40).coerceAtLeast(2)
        for (index in 0 until fade.coerceAtMost(count)) {
            val factor = index.toDouble() / fade
            out[index] = (out[index] * factor).toInt().toShort()
            val tail = count - 1 - index
            if (tail >= 0) out[tail] = (out[tail] * factor).toInt().toShort()
        }
        return out
    }

    /** A complete RIFF/WAVE file, ready for `SoundPool.load`. */
    fun wav(id: KeySoundId): ByteArray {
        val pcm = samples(id)
        val dataBytes = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8)
        buffer.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)
        pcm.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    fun fileName(id: KeySoundId): String = "key-${id.name.lowercase()}.wav"

    private data class Blip(
        val durationMs: Int,
        val frequency: Double,
        val sweepTo: Double,
        val noise: Double,
        val gain: Double,
    )
}

/**
 * Loads the synthesised sounds once and plays them on key-down.
 *
 * `SoundPool` keeps latency low and, unlike `AudioManager.playSoundEffect`, it
 * ignores the phone's system touch-sound switch, which the spec requires.
 */
class KeySoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mutableMapOf<KeySoundId, Int>()
    private var loaded = false
    private var volume = 0.6f

    fun prepare() {
        if (loaded) return
        loaded = true
        val dir = File(appContext.cacheDir, "key-sounds").apply { mkdirs() }
        KeySoundId.entries.forEach { id ->
            val file = File(dir, KeySoundSynth.fileName(id))
            if (!file.exists() || file.length() == 0L) {
                runCatching { file.writeBytes(KeySoundSynth.wav(id)) }
            }
            runCatching { pool.load(file.absolutePath, 1) }.getOrNull()?.let { ids[id] = it }
        }
    }

    fun setVolume(fraction: Float) {
        volume = fraction.coerceIn(0f, 1f)
    }

    fun play(id: KeySoundId, fraction: Float) {
        prepare()
        val soundId = ids[id] ?: return
        val level = fraction.coerceIn(0f, 1f) * volume.coerceAtLeast(0f)
        if (level <= 0f) return
        runCatching { pool.play(soundId, level, level, 1, 0, 1f) }
    }

    /** A single preview for the settings screen. */
    fun preview(id: KeySoundId, fraction: Float = 1f) {
        play(id, fraction)
    }

    fun release() {
        runCatching { pool.release() }
        ids.clear()
        loaded = false
    }
}
