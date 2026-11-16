package com.textpic.studio.ime

import android.graphics.RectF

/** A key placed on screen: the visible key and the (larger) touch zone. */
data class PlacedKey(
    val key: KeyboardKey,
    val row: Int,
    val column: Int,
    val visual: RectF,
    val touch: RectF,
)

/**
 * Turns a [KeyboardPage] into rectangles.
 *
 * Widths follow Gboard: the first row of ten letters defines the key width, every
 * other key is a multiple of it, and the space bar takes whatever is left. Rows
 * without a space bar spread their spare pixels into the gaps, so the outer margins
 * are always exactly [KeyboardMetrics.margin].
 *
 * Touch zones tile the whole keyboard: neighbouring zones meet inside the gaps, so
 * there are no dead spots between keys (a Gboard habit people rely on).
 */
object KeyboardGeometry {
    fun layout(
        page: KeyboardPage,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        metrics: KeyboardMetrics = KeyboardMetrics.of(widthPx, heightPx, page.rows.size),
    ): List<PlacedKey> {
        if (page.rows.isEmpty() || widthPx <= 0f || heightPx <= 0f) return emptyList()
        val margin = metrics.margin
        val gapH = metrics.gapH
        val gapV = metrics.gapV
        val keyHeight = metrics.keyHeight
        val available = (widthPx - margin * 2f).coerceAtLeast(1f)
        val rowCount = page.rows.size
        // The numpad keeps its own column layout: its cells share the row evenly.
        val proportional = page.id == KeyboardPageId.NUMPAD
        val placed = ArrayList<PlacedKey>()

        page.rows.forEachIndexed { rowIndex, row ->
            val cells = row
            if (cells.isEmpty()) return@forEachIndexed
            val units = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.1f)
            val unitWidth = if (proportional) {
                (available - gapH * (cells.size - 1)) / units
            } else {
                metrics.keyWidth
            }
            val widths = FloatArray(cells.size) {
                val weight = cells[it].weight
                if (!proportional && cells[it].key == null) {
                    // A half-key pad: half a key plus half a gap, minus the gap that
                    // follows it, so a row of nine letters sits half a key in.
                    (weight * unitWidth - gapH * 0.5f).coerceAtLeast(0f)
                } else {
                    weight * unitWidth
                }
            }
            val spaceIndex = cells.indexOfFirst { it.key?.kind == KeyKind.SPACE }
            if (spaceIndex >= 0) {
                val other = widths.indices.filter { it != spaceIndex }.sumOf { widths[it].toDouble() }
                widths[spaceIndex] = (available - other - gapH * (cells.size - 1)).toFloat()
                    .coerceAtLeast(unitWidth)
            }
            // Rows without a space bar spread their spare pixels evenly into the gaps.
            val rowGap = if (proportional || spaceIndex >= 0 || cells.size < 2) {
                gapH
            } else {
                ((available - widths.sum()) / (cells.size - 1)).coerceIn(2f, gapH * 2f)
            }

            val visibleColumns = cells.indices.filter { cells[it].key != null && !cells[it].isSpacer }
            val firstColumn = visibleColumns.firstOrNull()
            val lastColumn = visibleColumns.lastOrNull()

            var cursor = margin
            cells.forEachIndexed { column, cell ->
                val cellWidth = widths[column]
                val key = cell.key
                if (cell.spanned || key == null || cell.isSpacer) {
                    cursor += cellWidth + rowGap
                    return@forEachIndexed
                }
                val top = rowIndex * (keyHeight + gapV)
                val span = key.spanRows.coerceAtLeast(1)
                val spanBottom = if (span > 1) {
                    (rowIndex + span - 1) * (keyHeight + gapV) + keyHeight
                } else {
                    top + keyHeight
                }
                val visual = RectF(cursor, top, cursor + cellWidth, spanBottom)
                val touch = RectF(
                    when {
                        // A spanned cell above (the numpad operator column) owns that edge.
                        column == firstColumn && !cells.first().spanned -> 0f
                        else -> visual.left - rowGap / 2f
                    },
                    when {
                        rowIndex == 0 -> 0f
                        else -> top - gapV / 2f
                    },
                    when {
                        column == lastColumn && !cells.last().spanned -> widthPx
                        else -> visual.right + rowGap / 2f
                    },
                    when {
                        rowIndex + span - 1 >= rowCount - 1 -> heightPx
                        else -> spanBottom + gapV / 2f
                    },
                )
                placed.add(
                    PlacedKey(
                        key = key,
                        row = rowIndex,
                        column = column,
                        visual = visual,
                        touch = touch,
                    ),
                )
                cursor += cellWidth + rowGap
            }
        }
        return placed
    }

    /** Key under a tap, or null for a gap outside every row. */
    fun keyAt(keys: List<PlacedKey>, x: Float, y: Float): PlacedKey? =
        keys.firstOrNull { it.touch.contains(x, y) }

    /**
     * Checks the layout for the things a person would notice as "broken":
     * overlapping touch zones and columns that do not line up.
     */
    fun problems(page: KeyboardPage, widthPx: Float, heightPx: Float, density: Float): List<String> {
        val problems = ArrayList<String>()
        if (!page.isConsistent()) problems.add("rows have different widths")
        val keys = layout(page, widthPx, heightPx, density)
        for (index in keys.indices) {
            for (other in index + 1 until keys.size) {
                val overlap = RectF()
                if (!RectF.intersects(keys[index].touch, keys[other].touch)) continue
                overlap.set(keys[index].touch)
                overlap.intersect(keys[other].touch)
                // Touching boundaries differ by a rounding error; only a real
                // overlap (more than half a pixel) is a bug.
                if (overlap.width() > 0.5f && overlap.height() > 0.5f) {
                    problems.add(
                        "keys ${keys[index].key.label} and ${keys[other].key.label} overlap",
                    )
                }
            }
        }
        return problems
    }
}
