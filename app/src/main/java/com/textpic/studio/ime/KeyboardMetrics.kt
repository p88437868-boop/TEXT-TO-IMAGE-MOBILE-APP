package com.textpic.studio.ime

import kotlin.math.min

/**
 * Key sizes for one screen, as fractions of the screen width.
 *
 * The numbers come from measuring Gboard on a 720 px wide phone: a letter key is
 * 60 px, the key height 83 px, the row gap 21 px and the side margin 10 px. Every
 * value is a fraction of the width, so the same proportions appear on any phone.
 */
data class KeyboardMetrics(
    /** Distance from the screen edge to the first key. */
    val margin: Float,
    /** Gap between two keys in a row. */
    val gapH: Float,
    /** Gap between two rows. */
    val gapV: Float,
    /** Width of one normal letter key. */
    val keyWidth: Float,
    /** Height of one row of keys. */
    val keyHeight: Float,
    /** Corner radius of a normal key. */
    val radius: Float,
    /** True on a wide screen, where rows are shorter. */
    val landscape: Boolean,
) {
    val rowPitch: Float get() = keyHeight + gapV

    companion object {
        /** 0.014 W — about 10 px at 720 px. */
        const val MARGIN_FRACTION = 0.014f
        /** 0.0153 W — about 11 px at 720 px. */
        const val GAP_FRACTION = 0.0153f
        /** 0.115 W — about 83 px at 720 px. */
        const val KEY_HEIGHT_FRACTION = 0.115f
        /** 0.029 W — about 21 px at 720 px. */
        const val ROW_GAP_FRACTION = 0.029f
        /** 0.13 W — the toolbar above the keys. */
        const val TOOLBAR_FRACTION = 0.13f
        /** 0.0165 W — about 12 px at 720 px. */
        const val RADIUS_FRACTION = 0.0165f

        /** Gap between rows, as a share of the key height (0.029 / 0.115). */
        const val ROW_GAP_RATIO = 0.252f

        /** Total keyboard height (toolbar + keys + padding) per height level. */
        val HEIGHT_FRACTIONS = listOf(0.58f, 0.65f, 0.71f, 0.78f, 0.85f)

        /** Landscape: the keyboard takes this share of the screen height. */
        const val LANDSCAPE_TOTAL_FRACTION = 0.55f
        const val LANDSCAPE_TOOLBAR_FRACTION = 0.10f

        fun letterKeyWidth(widthPx: Float): Float =
            (widthPx - 2f * MARGIN_FRACTION * widthPx - 9f * GAP_FRACTION * widthPx) / 10f

        /** Total height of the keyboard (toolbar, keys and padding), in px. */
        fun totalHeightPx(
            widthPx: Float,
            screenHeightPx: Float,
            level: Int,
            landscape: Boolean,
        ): Float {
            val clamped = level.coerceIn(1, HEIGHT_FRACTIONS.size)
            return if (landscape) {
                screenHeightPx * LANDSCAPE_TOTAL_FRACTION
            } else {
                widthPx * HEIGHT_FRACTIONS[clamped - 1]
            }
        }

        fun toolbarHeightPx(widthPx: Float, screenHeightPx: Float, landscape: Boolean): Float =
            if (landscape) {
                screenHeightPx * LANDSCAPE_TOOLBAR_FRACTION
            } else {
                min(widthPx * TOOLBAR_FRACTION, 60f * 2.75f)
            }

        /** Padding above and below the keys, so the key block does not touch the edge. */
        fun paddingPx(widthPx: Float, landscape: Boolean): Float =
            if (landscape) 4f else widthPx * RADIUS_FRACTION

        /**
         * Metrics for a body area of [bodyHeightPx] holding [rowCount] rows.
         * The row height follows from the space given, so a fifth row (the number
         * row) makes every key shorter instead of making the keyboard taller.
         */
        fun of(widthPx: Float, bodyHeightPx: Float, rowCount: Int, landscape: Boolean = false): KeyboardMetrics {
            val rows = rowCount.coerceAtLeast(1)
            val keyHeight = bodyHeightPx / (rows + (rows - 1) * ROW_GAP_RATIO)
            val margin = MARGIN_FRACTION * widthPx
            return KeyboardMetrics(
                margin = margin,
                gapH = GAP_FRACTION * widthPx,
                gapV = keyHeight * ROW_GAP_RATIO,
                keyWidth = letterKeyWidth(widthPx),
                keyHeight = keyHeight,
                radius = RADIUS_FRACTION * widthPx,
                landscape = landscape,
            )
        }
    }
}

/**
 * How much room the phone's navigation buttons need below the keys.
 *
 * A three-button bar reports a tall `navigationBars()` inset, gesture navigation
 * reports a small `systemGestures()` inset; the keyboard must clear both.
 */
object NavigationInsetRules {
    /** Height to keep free below the last key row. */
    fun bottomInset(navigationBars: Int, systemGestures: Int): Int =
        maxOf(navigationBars.coerceAtLeast(0), systemGestures.coerceAtLeast(0))

    /** The keyboard grows by exactly the inset, so no key ends up under the buttons. */
    fun keyboardHeight(totalPx: Int, inset: Int): Int = totalPx + inset.coerceAtLeast(0)
}

/** Everything the keyboard needs to size itself on the current screen. */
data class KeyboardFrame(
    val widthPx: Float,
    val totalHeightPx: Float,
    val toolbarHeightPx: Float,
    val paddingPx: Float,
    val bodyHeightPx: Float,
    val landscape: Boolean,
) {
    companion object {
        /**
         * Portrait uses the screen width, landscape the screen height, so the keys
         * stay a sensible physical size after a rotation.
         */
        fun of(
            widthPx: Float,
            screenHeightPx: Float,
            level: Int,
            landscape: Boolean,
        ): KeyboardFrame {
            val total = KeyboardMetrics.totalHeightPx(widthPx, screenHeightPx, level, landscape)
            val toolbar = KeyboardMetrics.toolbarHeightPx(widthPx, screenHeightPx, landscape)
            val padding = KeyboardMetrics.paddingPx(widthPx, landscape)
            return KeyboardFrame(
                widthPx = widthPx,
                totalHeightPx = total,
                toolbarHeightPx = toolbar,
                paddingPx = padding,
                bodyHeightPx = (total - toolbar - padding * 2f).coerceAtLeast(1f),
                landscape = landscape,
            )
        }
    }
}
