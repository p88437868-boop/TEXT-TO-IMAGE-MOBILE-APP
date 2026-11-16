package com.textpic.core

import android.graphics.Bitmap

const val MAX_TEXT_LENGTH = 2000
const val LINE_HEIGHT_FACTOR = 1.3f
const val BUBBLE_RADIUS = 18
const val MESSENGER_PHOTO_WIDTH = 460
const val SUPERSAMPLE = 3
const val MAX_HI_RES_PIXELS = 64_000_000L

data class RenderRequest(
    val text: String,
    val styleKey: String = StyleKeys.RECEIVED_DARK,
    val theme: String = "light",
    val fontKey: String = "Verdana",
    val fontSize: Int = 10,
    val maxWidth: Int = 120,
    val alignment: String = "left",
    val transparent: Boolean = true,
    val fillWidth: Boolean = true,
    val scale: Float = 3f,
    val paddingX: Int = 8,
    val paddingY: Int = 5,
    val thumbnailFit: String = "off",
)

data class RenderMeta(
    val widthPx: Int,
    val heightPx: Int,
    val finalFontPx: Int,
    val finalLineHeightPx: Int,
    val padLeftPx: Int,
    val padTopPx: Int,
    val fillColor: Int,
    val textColor: Int,
    val isOpaque: Boolean,
    val fontUsedName: String,
)

data class RenderResult(
    val bitmap: Bitmap,
    val meta: RenderMeta,
)

class RenderException(message: String) : IllegalArgumentException(message)

object StyleKeys {
    const val SENT_BLUE = "sent_blue"
    const val SENT_VIOLET = "sent_violet"
    const val SENT_DARK = "sent_dark"
    const val RECEIVED_LIGHT = "received_light"
    const val RECEIVED_DARK = "received_dark"
    const val PLAIN_LIGHT = "plain_light"
    const val PLAIN_DARK = "plain_dark"
}

object ThumbnailFits {
    const val OFF = "off"
    const val WIDE = "wide"
    const val SQUARE = "square"
}

object Alignments {
    const val LEFT = "left"
    const val RIGHT = "right"
}
