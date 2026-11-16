package com.textpic.studio.data.prefs

/** How the keyboard looks. [SYSTEM] follows the phone's light/dark setting. */
enum class KeyboardTheme {
    DARK,
    LIGHT,
    SYSTEM,
    ;

    companion object {
        fun fromWire(value: String?): KeyboardTheme =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DARK
    }
}

/** Vibration strength, mapped to a `VibrationEffect` amplitude and duration. */
enum class HapticStrength {
    LIGHT,
    MEDIUM,
    STRONG,
    ;

    companion object {
        fun fromWire(value: String?): HapticStrength =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

/** The three key sounds; the waveforms are synthesised on the phone. */
enum class KeySoundId {
    TICK,
    SOFT_CLICK,
    POP,
    ;

    val label: String
        get() = when (this) {
            TICK -> "Tick"
            SOFT_CLICK -> "Soft click"
            POP -> "Pop"
        }

    companion object {
        fun fromWire(value: String?): KeySoundId =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TICK
    }
}

enum class OneHandedSide {
    OFF,
    LEFT,
    RIGHT,
    ;

    companion object {
        fun fromWire(value: String?): OneHandedSide =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OFF
    }
}

/**
 * Everything the keyboard itself needs to know about its looks and feedback.
 * Kept in one immutable record so a change can be applied to a live keyboard
 * without rebuilding it.
 */
data class KeyboardPrefs(
    val theme: KeyboardTheme = KeyboardTheme.DARK,
    val heightLevel: Int = 3,
    val oneHanded: OneHandedSide = OneHandedSide.OFF,
    val vibration: Boolean = true,
    val vibrationStrength: HapticStrength = HapticStrength.MEDIUM,
    val sound: Boolean = false,
    val soundId: KeySoundId = KeySoundId.TICK,
    val soundVolume: Int = 60,
    val popupPreview: Boolean = true,
    val numberRow: Boolean = false,
    val longPressMs: Int = 300,
    val doubleSpacePeriod: Boolean = true,
    val autoCapitalize: Boolean = true,
    val clipboardEnabled: Boolean = true,
    /** 0 means "never delete". */
    val clipboardTimeoutMinutes: Int = 60,
    /** What the Send button does once the image is in the chat field. */
    val afterInsert: com.textpic.studio.ime.AfterInsert = com.textpic.studio.ime.AfterInsert.APP_ACTION,
    /** How long the app gets to attach the image before Send is pressed. */
    val sendDelayMs: Int = 600,
    /** Show a two-second "Sending… Undo" chip first. */
    val confirmBeforeSend: Boolean = false,
)

/**
 * The numbers behind [KeyboardPrefs]. Pure, so the settings screen, the keyboard
 * and the tests can never disagree about what "strong" or "70%" means.
 */
object KeyboardPrefRules {
    const val MIN_HEIGHT_LEVEL = 1
    const val MAX_HEIGHT_LEVEL = 5

    val CLIPBOARD_TIMEOUTS = listOf(60 to "1 hour", 360 to "6 hours", 1_440 to "24 hours", 0 to "Never")

    fun clampLevel(level: Int): Int = level.coerceIn(MIN_HEIGHT_LEVEL, MAX_HEIGHT_LEVEL)

    /** `VibrationEffect` amplitude, 1..255. */
    fun amplitude(strength: HapticStrength): Int = when (strength) {
        HapticStrength.LIGHT -> 60
        HapticStrength.MEDIUM -> 120
        HapticStrength.STRONG -> 210
    }

    fun durationMs(strength: HapticStrength): Long = when (strength) {
        HapticStrength.LIGHT -> 8L
        HapticStrength.MEDIUM -> 12L
        HapticStrength.STRONG -> 18L
    }

    fun volumeFraction(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    /** A long-press shorter than this would fire while typing fast. */
    fun longPressMs(value: Int): Long = value.coerceIn(200, 600).toLong()

    /** The part of the screen the keyboard may use, as a fraction of its width. */
    fun oneHandedWidthFraction(side: OneHandedSide): Float =
        if (side == OneHandedSide.OFF) 1f else 0.82f

    /** Send delay in ms, inside the range the settings screen offers. */
    fun sendDelayMs(value: Int): Int = value.coerceIn(200, 1_500)

    /** 0 (never) becomes `null` so callers cannot confuse it with "now". */
    fun clipboardTimeoutMs(minutes: Int): Long? =
        if (minutes <= 0) null else minutes.toLong() * 60_000L

    /** Which theme is actually painted when the phone reports its own mode. */
    fun resolvedTheme(theme: KeyboardTheme, systemDark: Boolean): KeyboardTheme =
        if (theme == KeyboardTheme.SYSTEM) {
            if (systemDark) KeyboardTheme.DARK else KeyboardTheme.LIGHT
        } else {
            theme
        }
}

/**
 * Turns a preference snapshot into the side effects of pressing a key.
 *
 * The two callbacks are injectable so a test can prove that nothing at all
 * happens when vibration or sound is switched off.
 */
class KeyFeedback(
    private val vibrate: (amplitude: Int, durationMs: Long) -> Unit = { _, _ -> },
    private val playSound: (sound: KeySoundId, volume: Float) -> Unit = { _, _ -> },
    private val vibrateFallback: () -> Unit = {},
) {
    var hapticCount: Int = 0
        private set
    var soundCount: Int = 0
        private set

    /** Called once per key-down, before the character is committed. */
    fun onKeyDown(prefs: KeyboardPrefs, strong: Boolean = false) {
        if (prefs.vibration) {
            hapticCount++
            val strength = if (strong && prefs.vibrationStrength != HapticStrength.STRONG) {
                HapticStrength.STRONG
            } else {
                prefs.vibrationStrength
            }
            runCatching {
                vibrate(
                    KeyboardPrefRules.amplitude(strength),
                    KeyboardPrefRules.durationMs(strength),
                )
            }.onFailure { runCatching { vibrateFallback() } }
        }
        if (prefs.sound && prefs.soundVolume > 0) {
            soundCount++
            runCatching {
                playSound(prefs.soundId, KeyboardPrefRules.volumeFraction(prefs.soundVolume))
            }
        }
    }
}
