package com.textpic.studio.data.prefs

import com.textpic.core.Alignments
import com.textpic.core.RenderRequest
import com.textpic.core.StyleKeys
import com.textpic.core.StylePresets
import com.textpic.core.ThumbnailFits

data class RenderSettings(
    val styleKey: String = StyleKeys.RECEIVED_DARK,
    val theme: String = "light",
    val font: String = "Verdana",
    val fontSize: Int = 10,
    val maxWidth: Int = 120,
    val alignment: String = Alignments.LEFT,
    val transparent: Boolean = true,
    val fillWidth: Boolean = true,
    val scale: Float = 3f,
    val paddingX: Int = 8,
    val paddingY: Int = 5,
    val thumbnailFit: String = ThumbnailFits.OFF,
) {
    val style get() = StylePresets.get(styleKey)
    val isBubble get() = style.isBubble

    fun toRequest(text: String): RenderRequest = RenderRequest(
        text = text,
        styleKey = styleKey,
        theme = theme,
        fontKey = font,
        fontSize = fontSize,
        maxWidth = maxWidth,
        alignment = alignment,
        transparent = transparent,
        fillWidth = fillWidth,
        scale = scale,
        paddingX = paddingX,
        paddingY = paddingY,
        thumbnailFit = thumbnailFit,
    )

    companion object {
        val GOLDEN = RenderSettings()
    }
}

data class AppSettings(
    val render: RenderSettings = RenderSettings.GOLDEN,
    val keyboardHeight: String = "normal",
    val haptics: Boolean = true,
    val saveMessageText: Boolean = true,
    val retentionDays: Int = 365,
    val workspaceName: String = "My workspace",
    val onboardingComplete: Boolean = false,
    val keyboard: KeyboardPrefs = KeyboardPrefs(),
)
