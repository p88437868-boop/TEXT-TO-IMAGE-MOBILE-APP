package com.textpic.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BubbleRendererTest {
    private lateinit var renderer: BubbleRenderer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        renderer = BubbleRenderer(BundledFontProvider(context.resources))
    }

    private fun golden(text: String, scale: Float = 3f) = RenderRequest(
        text = text,
        styleKey = StyleKeys.RECEIVED_DARK,
        theme = "light",
        fontKey = "Verdana",
        fontSize = 10,
        maxWidth = 120,
        alignment = Alignments.LEFT,
        transparent = true,
        fillWidth = true,
        scale = scale,
        paddingX = 8,
        paddingY = 5,
        thumbnailFit = ThumbnailFits.OFF,
    )

    private fun alphaArray(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    @Test
    fun goldenProfile_isExactly460x69_opaque_and_usesExpectedMetrics() {
        val result = renderer.render(golden("Hi"))
        val bitmap = result.bitmap
        val meta = result.meta

        assertThat(bitmap.width).isEqualTo(460)
        assertThat(bitmap.height).isEqualTo(69)
        assertThat(meta.widthPx).isEqualTo(460)
        assertThat(meta.heightPx).isEqualTo(69)
        assertThat(meta.finalFontPx).isEqualTo(30)
        assertThat(meta.finalLineHeightPx).isEqualTo(39)
        assertThat(meta.padLeftPx).isEqualTo(24)
        assertThat(meta.padTopPx).isEqualTo(15)
        assertThat(meta.fillColor).isEqualTo(0xFF303030.toInt())
        assertThat(meta.textColor).isEqualTo(0xFFE4E6EB.toInt())
        assertThat(meta.isOpaque).isTrue()
        assertThat(meta.fontUsedName).isEqualTo("Verdana-like (DejaVu Sans)")

        val pixels = alphaArray(bitmap)
        assertThat(pixels.all { Color.alpha(it) == 255 }).isTrue()
        assertThat(bitmap.getPixel(bitmap.width - 1, bitmap.height / 2))
            .isEqualTo(0xFF303030.toInt())
        assertThat(pixels.count { it == 0xFFE4E6EB.toInt() }).isGreaterThan(0)
    }

    @Test
    fun scaleTest_producesExpectedHeights() {
        val expected = mapOf(
            1.0f to 23,
            1.5f to 35,
            2.0f to 46,
            3.0f to 69,
        )
        expected.forEach { (scale, height) ->
            val result = renderer.render(golden("Hi", scale))
            assertThat(result.bitmap.width).isEqualTo(460)
            assertThat(result.bitmap.height).isEqualTo(height)
        }
    }

    @Test
    fun longText_wrapsAtMaxWidth_stays460AndGrowsByFixedLineHeight() {
        val text = "This is a long message that must wrap at the maximum text width and keep every pixel opaque without clipping any of the text."
        val result = renderer.render(golden(text))
        assertThat(result.bitmap.width).isEqualTo(460)
        assertThat(result.bitmap.height).isGreaterThan(69)
        assertThat(alphaArray(result.bitmap).all { Color.alpha(it) == 255 }).isTrue()
        assertThat(result.meta.finalFontPx).isEqualTo(30)
        assertThat(result.meta.finalLineHeightPx).isEqualTo(39)
        assertThat((result.bitmap.height - 30) % 39).isEqualTo(0)
    }

    @Test
    fun longText_rejects2001Characters() {
        try {
            renderer.render(golden("a".repeat(2001)))
            throw AssertionError("Expected RenderException")
        } catch (expected: RenderException) {
            assertThat(expected).hasMessageThat().contains("2000")
        }
    }

    @Test
    fun alignmentControlsTextSideWithNoTransparency() {
        val left = renderer.render(golden("Hi").copy(alignment = Alignments.LEFT))
        val right = renderer.render(golden("Hi").copy(alignment = Alignments.RIGHT))
        val textColor = 0xFFE4E6EB.toInt()

        fun textBounds(bitmap: Bitmap): IntRange {
            val pixels = IntArray(bitmap.width) { x ->
                var found = -1
                for (y in 0 until bitmap.height) {
                    if (bitmap.getPixel(x, y) == textColor) {
                        found = x
                        break
                    }
                }
                found
            }
            return pixels.indexOfFirst { it >= 0 }..pixels.indexOfLast { it >= 0 }
        }

        val leftBounds = textBounds(left.bitmap)
        val rightBounds = textBounds(right.bitmap)
        assertThat(leftBounds.first).isLessThan(left.bitmap.width / 2)
        assertThat(rightBounds.last).isGreaterThan(right.bitmap.width / 2)
        assertThat(left.meta.isOpaque).isTrue()
        assertThat(right.meta.isOpaque).isTrue()
        assertThat(alphaArray(left.bitmap).all { Color.alpha(it) == 255 }).isTrue()
        assertThat(alphaArray(right.bitmap).all { Color.alpha(it) == 255 }).isTrue()
    }

    @Test
    fun compactMode_keepsTransparentLegacyPadding_andIsNotGoldenDefault() {
        val compact = renderer.render(
            golden("Hi").copy(transparent = true, fillWidth = false),
        )
        assertThat(compact.bitmap.width).isEqualTo(460)
        assertThat(compact.meta.isOpaque).isFalse()
        assertThat(compact.bitmap.getPixel(compact.bitmap.width - 1, compact.bitmap.height / 2))
            .isEqualTo(Color.TRANSPARENT)
        assertThat(alphaArray(compact.bitmap).any { Color.alpha(it) == 0 }).isTrue()

        val goldenDefault = renderer.render(golden("Hi"))
        assertThat(goldenDefault.meta.isOpaque).isTrue()
    }

    @Test
    fun validation_rejectsEmptyWhitespaceAndSmallMaxWidth_andClampsSizes() {
        listOf("", "   ", "\n\t").forEach { invalid ->
            try {
                renderer.render(golden(invalid))
                throw AssertionError("Expected rejection for $invalid")
            } catch (expected: RenderException) {
                assertThat(expected).hasMessageThat().contains("empty")
            }
        }

        try {
            renderer.render(golden("Hi").copy(maxWidth = 79))
            throw AssertionError("Expected max width rejection")
        } catch (expected: RenderException) {
            assertThat(expected).hasMessageThat().contains("80")
        }

        val clampedSmall = renderer.render(golden("Hi").copy(fontSize = 2, scale = 0.5f))
        assertThat(clampedSmall.meta.finalFontPx).isEqualTo(10)
        val clampedLarge = renderer.render(golden("Hi").copy(fontSize = 99, scale = 9f))
        assertThat(clampedLarge.meta.finalFontPx).isEqualTo(192)
    }

    @Test
    fun thumbnailFit_usesWideAndSquareRatios() {
        val text = "A longer thumbnail message that wraps enough to create a wide base image for ratio checks."
        val off = renderer.render(golden(text))
        val wide = renderer.render(golden(text).copy(thumbnailFit = ThumbnailFits.WIDE))
        val square = renderer.render(golden(text).copy(thumbnailFit = ThumbnailFits.SQUARE))

        assertThat(wide.bitmap.width).isEqualTo(square.bitmap.width)
        assertThat(wide.bitmap.width).isNotEqualTo(460)
        assertThat(wide.bitmap.height)
            .isEqualTo(max(off.bitmap.height, (wide.bitmap.width / 1.91f).roundToInt()))
        assertThat(square.bitmap.height)
            .isEqualTo(max(off.bitmap.height, square.bitmap.width))
        assertThat(alphaArray(wide.bitmap).all { Color.alpha(it) == 255 }).isTrue()
        assertThat(alphaArray(square.bitmap).all { Color.alpha(it) == 255 }).isTrue()
    }
}
