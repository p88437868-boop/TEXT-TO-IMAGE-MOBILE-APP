package com.textpic.studio.ime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Gboard proportions, measured on a 720 px wide phone: 60 px keys, 83 px tall,
 * 21 px between rows and 104 px from one row to the next.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardMetricsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val phoneWidth = 720f
    private val phoneHeight = 1600f

    private fun frame(width: Float = phoneWidth, height: Float = phoneHeight, level: Int = 3, landscape: Boolean = false) =
        KeyboardFrame.of(width, height, level, landscape)

    private fun metrics(width: Float = phoneWidth): KeyboardMetrics {
        val f = frame(width)
        return KeyboardMetrics.of(width, f.bodyHeightPx, 4, false)
    }

    private fun keys(page: KeyboardPageId, width: Float = phoneWidth): List<PlacedKey> {
        val f = frame(width)
        val model = KeyboardSpecs.load(context, page)
        return KeyboardGeometry.layout(
            model,
            width,
            f.bodyHeightPx,
            context.resources.displayMetrics.density,
            KeyboardMetrics.of(width, f.bodyHeightPx, model.rows.size),
        )
    }

    @Test
    fun `a letter key is 60 px wide at 720 px`() {
        assertThat(KeyboardMetrics.letterKeyWidth(phoneWidth)).isWithin(1f).of(60f)
        assertThat(metrics().keyWidth).isWithin(1f).of(60f)
    }

    @Test
    fun `a key is 83 px tall and rows are 104 px apart`() {
        assertThat(metrics().keyHeight).isWithin(1f).of(83f)
        assertThat(metrics().rowPitch).isWithin(2f).of(104f)
        assertThat(metrics().gapV).isWithin(2f).of(21f)
    }

    @Test
    fun `the whole keyboard is 0_71 of the width`() {
        val f = frame()
        assertThat(f.totalHeightPx).isWithin(phoneWidth * 0.71f * 0.02f).of(phoneWidth * 0.71f)
        assertThat(f.toolbarHeightPx).isWithin(4f).of(phoneWidth * 0.13f)
        // Toolbar, padding and four rows add up to the total.
        val used = f.toolbarHeightPx + f.paddingPx * 2f + f.bodyHeightPx
        assertThat(used).isWithin(1f).of(f.totalHeightPx)
    }

    @Test
    fun `all five height levels scale around the normal height`() {
        val heights = (1..5).map { frame(level = it).totalHeightPx }
        assertThat(heights).isInOrder()
        assertThat(heights[2]).isWithin(phoneWidth * 0.71f * 0.02f).of(phoneWidth * 0.71f)
        val fractions = heights.map { it / phoneWidth }
        assertThat(fractions[0]).isWithin(0.01f).of(0.58f)
        assertThat(fractions[3]).isWithin(0.01f).of(0.78f)
        assertThat(fractions[4]).isWithin(0.01f).of(0.85f)
    }

    @Test
    fun `the side margin is 0_014 and the gap 0_0153 of the width`() {
        assertThat(metrics().margin).isWithin(0.5f).of(10.08f)
        assertThat(metrics().gapH).isWithin(0.5f).of(11.02f)
    }

    @Test
    fun `row two starts half a key in from the left`() {
        val a = keys(KeyboardPageId.LETTERS).first { it.key.label == "a" }
        assertThat(a.visual.left).isWithin(1f).of(phoneWidth * 0.064f)
        // …and ends the mirror distance from the right edge.
        val l = keys(KeyboardPageId.LETTERS).first { it.key.label == "l" }
        assertThat(l.visual.right).isWithin(1f).of(phoneWidth - phoneWidth * 0.064f)
    }

    @Test
    fun `shift and backspace are 95 px, the letters between them 60 px`() {
        val all = keys(KeyboardPageId.LETTERS)
        val shift = all.first { it.key.kind == KeyKind.SHIFT }
        val back = all.first { it.key.kind == KeyKind.BACKSPACE }
        val z = all.first { it.key.label == "z" }
        assertThat(shift.visual.width()).isWithin(2f).of(phoneWidth * 0.132f)
        assertThat(back.visual.width()).isWithin(2f).of(phoneWidth * 0.132f)
        assertThat(z.visual.width()).isWithin(1f).of(60f)
        // Both pills start and end on the margins.
        assertThat(shift.visual.left).isWithin(0.5f).of(phoneWidth * 0.014f)
        assertThat(back.visual.right).isWithin(0.5f).of(phoneWidth - phoneWidth * 0.014f)
    }

    @Test
    fun `the space bar takes the rest of the bottom row`() {
        val all = keys(KeyboardPageId.LETTERS)
        val space = all.first { it.key.kind == KeyKind.SPACE }
        assertThat(space.visual.width()).isWithin(6f).of(274f)
        val row = all.filter { it.row == 3 }.sortedBy { it.visual.left }
        assertThat(row.first().visual.left).isWithin(0.5f).of(phoneWidth * 0.014f)
        assertThat(row.last().visual.right).isWithin(0.5f).of(phoneWidth - phoneWidth * 0.014f)
    }

    @Test
    fun `every page keeps the same height and the same margins`() {
        val heights = KeyboardPageId.entries.map { page ->
            val laid = keys(page)
            laid.maxOf { it.visual.bottom } - laid.minOf { it.visual.top }
        }
        for (height in heights) {
            assertThat(height).isWithin(1f).of(heights.first())
        }
        for (page in KeyboardPageId.entries) {
            val laid = keys(page)
            assertThat(laid.minOf { it.visual.left }).isWithin(0.5f).of(phoneWidth * 0.014f)
            assertThat(laid.maxOf { it.visual.right })
                .isWithin(0.5f).of(phoneWidth - phoneWidth * 0.014f)
        }
    }

    @Test
    fun `no key overlaps and every gap still hits a key`() {
        for (page in KeyboardPageId.entries) {
            val model = KeyboardSpecs.load(context, page)
            val f = frame()
            assertThat(KeyboardGeometry.problems(model, phoneWidth, f.bodyHeightPx, 2f)).isEmpty()
            val laid = keys(page)
            // The middle of every horizontal gap belongs to one of its neighbours.
            val row = laid.filter { it.row == 0 }.sortedBy { it.visual.left }
            for (index in 1 until row.size) {
                val gap = row[index].visual.left - row[index - 1].visual.right
                val mid = row[index - 1].visual.right + gap * 0.25f
                assertThat(KeyboardGeometry.keyAt(laid, mid, row[index].visual.centerY())).isNotNull()
            }
        }
    }

    @Test
    fun `landscape rows are shorter and stay inside the screen`() {
        val wide = 1600f
        val f = frame(wide, 720f, 3, landscape = true)
        val m = KeyboardMetrics.of(wide, f.bodyHeightPx, 4, true)
        assertThat(f.totalHeightPx).isWithin(2f).of(720f * 0.55f)
        assertThat(f.totalHeightPx).isLessThan(720f)
        // Shorter than the portrait key height would be at this width.
        assertThat(m.keyHeight).isLessThan(wide * 0.115f)
        assertThat(m.keyHeight).isAtLeast(40f)
        val model = KeyboardSpecs.load(context, KeyboardPageId.LETTERS)
        val laid = KeyboardGeometry.layout(model, wide, f.bodyHeightPx, 2f, m)
        assertThat(laid.maxOf { it.visual.bottom }).isAtMost(f.bodyHeightPx)
        assertThat(laid.maxOf { it.visual.right }).isAtMost(wide)
    }

    @Test
    fun `the navigation bar inset is added below the keys`() {
        // Three-button navigation on a 720 x 1600 phone.
        assertThat(NavigationInsetRules.bottomInset(96, 0)).isEqualTo(96)
        // Gesture navigation reports only the small gesture inset.
        assertThat(NavigationInsetRules.bottomInset(0, 32)).isEqualTo(32)
        // Some devices report both; the taller one wins.
        assertThat(NavigationInsetRules.bottomInset(96, 32)).isEqualTo(96)
        // No inset at all, and the keyboard grows by exactly the inset.
        assertThat(NavigationInsetRules.bottomInset(0, 0)).isEqualTo(0)
        assertThat(NavigationInsetRules.keyboardHeight(511, 96)).isEqualTo(607)
        // Landscape gesture bar.
        assertThat(NavigationInsetRules.bottomInset(0, 24)).isEqualTo(24)
    }

    @Test
    fun `the keys sit above the navigation bar inside the view`() {
        val view = KeyboardView(context)
        view.applyPrefs(com.textpic.studio.data.prefs.KeyboardPrefs())
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        val withoutInset = view.measuredHeight
        view.setNavigationInset(96)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        assertThat(view.measuredHeight).isEqualTo(withoutInset + 96)
    }
}
