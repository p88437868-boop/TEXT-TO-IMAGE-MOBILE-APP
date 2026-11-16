package com.textpic.core

import android.graphics.Color

data class StylePreset(
    val key: String,
    val displayName: String,
    val fill: Int,
    val text: Int,
    val isBubble: Boolean,
)

object StylePresets {
    val sentBlue = StylePreset(StyleKeys.SENT_BLUE, "Sent blue", 0xFF0084FF.toInt(), 0xFFFFFFFF.toInt(), true)
    val sentViolet = StylePreset(StyleKeys.SENT_VIOLET, "Sent violet", 0xFF8E5CF7.toInt(), 0xFFFFFFFF.toInt(), true)
    val sentDark = StylePreset(StyleKeys.SENT_DARK, "Sent dark", 0xFF0866FF.toInt(), 0xFFFFFFFF.toInt(), true)
    val receivedLight = StylePreset(StyleKeys.RECEIVED_LIGHT, "Received light", 0xFFE4E6EB.toInt(), 0xFF050505.toInt(), true)
    val receivedDark = StylePreset(StyleKeys.RECEIVED_DARK, "Received dark", 0xFF303030.toInt(), 0xFFE4E6EB.toInt(), true)
    val plainLight = StylePreset(StyleKeys.PLAIN_LIGHT, "Plain light", Color.TRANSPARENT, 0xFF1C1E21.toInt(), false)
    val plainDark = StylePreset(StyleKeys.PLAIN_DARK, "Plain dark", Color.TRANSPARENT, 0xFFE4E6EB.toInt(), false)

    val all = listOf(sentBlue, sentViolet, sentDark, receivedLight, receivedDark, plainLight, plainDark)

    private val byKey = all.associateBy { it.key }

    fun get(key: String): StylePreset = byKey[key] ?: receivedDark

    fun isBubble(key: String): Boolean = get(key).isBubble

    fun themeBackground(theme: String): Int =
        if (theme == "dark") 0xFF0B0B10.toInt() else 0xFFFFFFFF.toInt()
}
