package com.textpic.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class BubbleRenderer(
    private val fontProvider: FontProvider,
) {
    fun render(request: RenderRequest): RenderResult {
        validate(request)

        val fontSize = request.fontSize.coerceIn(10, 48)
        val scale = request.scale.coerceIn(1f, 4f)
        val lineHeight = fontSize * LINE_HEIGHT_FACTOR
        val padX = request.paddingX.coerceAtLeast(0)
        val padY = request.paddingY.coerceAtLeast(0)
        val maxTextWidth = max(40, request.maxWidth - 2 * padX)
        val style = StylePresets.get(request.styleKey)
        val resolvedFont = fontProvider.resolve(request.fontKey)
        val themeBackground = StylePresets.themeBackground(request.theme)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = resolvedFont.typeface
            textSize = fontSize.toFloat()
            isLinearText = true
            isSubpixelText = true
            isAntiAlias = true
            color = style.text
        }

        val layout = StaticLayout.Builder.obtain(
            request.text,
            0,
            request.text.length,
            paint,
            maxTextWidth,
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()

        val lines = (0 until layout.lineCount).map { index ->
            var value = request.text.substring(layout.getLineStart(index), layout.getLineEnd(index))
            while (value.endsWith("\n") || value.endsWith("\r")) {
                value = value.dropLast(1)
            }
            value
        }
        val widestLine = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val textWidth = minOf(maxTextWidth, ceil(widestLine.toDouble()).toInt())
        val textHeight = ceil(lines.size * lineHeight.toDouble()).toInt()
        val contentWidth = textWidth + 2 * padX
        val contentHeight = textHeight + 2 * padY
        val baseWidth = ceil(contentWidth * scale.toDouble()).toInt()
        val baseHeight = ceil(contentHeight * scale.toDouble()).toInt()

        var supersample = SUPERSAMPLE
        var hiWidth = ceil(contentWidth * scale * supersample.toDouble()).toInt()
        var hiHeight = ceil(contentHeight * scale * supersample.toDouble()).toInt()
        while (supersample > 1 && hiWidth.toLong() * hiHeight > MAX_HI_RES_PIXELS) {
            supersample -= 1
            hiWidth = ceil(contentWidth * scale * supersample.toDouble()).toInt()
            hiHeight = ceil(contentHeight * scale * supersample.toDouble()).toInt()
        }
        val renderScale = scale * supersample

        val background = when {
            style.isBubble -> style.fill
            request.transparent -> Color.TRANSPARENT
            else -> themeBackground
        }
        var fillColor = background
        var isOpaque = Color.alpha(background) == 255

        val hiBitmap = Bitmap.createBitmap(hiWidth, hiHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(hiBitmap)
        if (Color.alpha(background) != 0) {
            canvas.drawColor(background, PorterDuff.Mode.SRC)
        }
        canvas.save()
        canvas.scale(renderScale, renderScale)

        val fontHeight = paint.descent() - paint.ascent()
        lines.forEachIndexed { index, line ->
            val lineWidth = paint.measureText(line)
            val x = when (request.alignment) {
                Alignments.RIGHT -> padX + textWidth - lineWidth
                else -> padX.toFloat()
            }
            val baseline = padY + index * lineHeight +
                (lineHeight - fontHeight) / 2f - paint.ascent()
            canvas.drawText(line, x, baseline, paint)
        }
        canvas.restore()

        var output = Bitmap.createScaledBitmap(hiBitmap, baseWidth, baseHeight, true)
        if (output !== hiBitmap) {
            hiBitmap.recycle()
        }

        val thumbnailFit = request.thumbnailFit
        if (style.isBubble && thumbnailFit == ThumbnailFits.OFF) {
            val targetWidth = maxOf(
                baseWidth,
                MESSENGER_PHOTO_WIDTH,
                (request.maxWidth * scale).roundToInt(),
            )
            if (targetWidth > baseWidth) {
                val fill = when {
                    request.fillWidth -> style.fill
                    request.transparent -> Color.TRANSPARENT
                    else -> themeBackground
                }
                val extended = Bitmap.createBitmap(targetWidth, baseHeight, Bitmap.Config.ARGB_8888)
                val extendedCanvas = Canvas(extended)
                if (Color.alpha(fill) != 0) {
                    extendedCanvas.drawColor(fill, PorterDuff.Mode.SRC)
                }
                val x = if (request.alignment == Alignments.RIGHT) {
                    targetWidth - baseWidth
                } else {
                    0
                }
                extendedCanvas.drawBitmap(output, x.toFloat(), 0f, null)
                output.recycle()
                output = extended
                fillColor = fill
                isOpaque = Color.alpha(fill) == 255
            }
        } else if (thumbnailFit != ThumbnailFits.OFF) {
            val ratio = if (thumbnailFit == ThumbnailFits.WIDE) 1.91f else 1f
            val targetHeight = max(baseHeight, (baseWidth / ratio).roundToInt())
            if (targetHeight > baseHeight) {
                val fill = when {
                    style.isBubble -> style.fill
                    request.transparent -> Color.TRANSPARENT
                    else -> themeBackground
                }
                val originalWidth = output.width
                val originalHeight = output.height
                val extended = Bitmap.createBitmap(originalWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val extendedCanvas = Canvas(extended)
                if (Color.alpha(fill) != 0) {
                    extendedCanvas.drawColor(fill, PorterDuff.Mode.SRC)
                }
                extendedCanvas.drawBitmap(
                    output,
                    0f,
                    ((targetHeight - originalHeight) / 2f),
                    null,
                )
                output.recycle()
                output = extended
                fillColor = fill
                isOpaque = Color.alpha(fill) == 255
            }
        }

        val meta = RenderMeta(
            widthPx = output.width,
            heightPx = output.height,
            finalFontPx = (fontSize * scale).roundToInt(),
            finalLineHeightPx = (lineHeight * scale).roundToInt(),
            padLeftPx = (padX * scale).roundToInt(),
            padTopPx = (padY * scale).roundToInt(),
            fillColor = fillColor,
            textColor = style.text,
            isOpaque = isOpaque,
            fontUsedName = resolvedFont.displayName,
        )
        return RenderResult(output, meta)
    }

    private fun validate(request: RenderRequest) {
        if (request.text.isBlank()) {
            throw RenderException("Message cannot be empty.")
        }
        if (request.text.length > MAX_TEXT_LENGTH) {
            throw RenderException("Message is limited to $MAX_TEXT_LENGTH characters.")
        }
        if (request.maxWidth < 80) {
            throw RenderException("Max width must be at least 80.")
        }
        if (request.alignment != Alignments.LEFT && request.alignment != Alignments.RIGHT) {
            throw RenderException("Alignment must be left or right.")
        }
        if (request.thumbnailFit !in setOf(
                ThumbnailFits.OFF,
                ThumbnailFits.WIDE,
                ThumbnailFits.SQUARE,
            )
        ) {
            throw RenderException("Unknown thumbnail fit.")
        }
    }
}
