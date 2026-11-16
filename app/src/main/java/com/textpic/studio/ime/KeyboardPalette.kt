package com.textpic.studio.ime

import com.textpic.studio.data.prefs.KeyboardTheme

/** Every colour the keyboard paints, in one place, for both themes. */
data class KeyboardPalette(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val special: Int,
    val specialPressed: Int,
    val pill: Int,
    val pillPressed: Int,
    val text: Int,
    val hint: Int,
    val icon: Int,
    val circle: Int,
    val accent: Int,
    val accentText: Int,
    val panel: Int,
    val card: Int,
    val divider: Int,
    val shadow: Int,
)

object KeyboardPalettes {
    private val dark = KeyboardPalette(
        background = 0xFF1C1D22.toInt(),
        key = 0xFF3A3B40.toInt(),
        keyPressed = 0xFF4C4E55.toInt(),
        special = 0xFF434A63.toInt(),
        specialPressed = 0xFF535B78.toInt(),
        pill = 0xFF434A63.toInt(),
        pillPressed = 0xFF535B78.toInt(),
        text = 0xFFE8EAED.toInt(),
        hint = 0xFFC4C7CC.toInt(),
        icon = 0xFFE8EAED.toInt(),
        circle = 0xFF35363B.toInt(),
        accent = 0xFF9B6DDB.toInt(),
        accentText = 0xFFFFFFFF.toInt(),
        panel = 0xFF232429.toInt(),
        card = 0xFF2E3036.toInt(),
        divider = 0xFF3A3B40.toInt(),
        shadow = 0x66000000,
    )

    private val light = KeyboardPalette(
        background = 0xFFE9EDF2.toInt(),
        key = 0xFFFFFFFF.toInt(),
        keyPressed = 0xFFDCE3EE.toInt(),
        special = 0xFFC9D2E3.toInt(),
        specialPressed = 0xFFB7C2D7.toInt(),
        pill = 0xFFC9D2E3.toInt(),
        pillPressed = 0xFFB7C2D7.toInt(),
        text = 0xFF1F1F1F.toInt(),
        hint = 0xFF5F6368.toInt(),
        icon = 0xFF1F1F1F.toInt(),
        circle = 0xFFDCE2EC.toInt(),
        accent = 0xFF9B6DDB.toInt(),
        accentText = 0xFFFFFFFF.toInt(),
        panel = 0xFFF7F9FC.toInt(),
        card = 0xFFFFFFFF.toInt(),
        divider = 0xFFD6DCE6.toInt(),
        shadow = 0x33000000,
    )

    fun of(theme: KeyboardTheme, systemDark: Boolean): KeyboardPalette {
        val resolved = com.textpic.studio.data.prefs.KeyboardPrefRules.resolvedTheme(theme, systemDark)
        return if (resolved == KeyboardTheme.LIGHT) light else dark
    }
}
