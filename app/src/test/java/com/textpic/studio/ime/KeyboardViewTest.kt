package com.textpic.studio.ime

import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.textpic.studio.TextPicApplication
import com.textpic.studio.data.prefs.KeyboardPrefRules
import com.textpic.studio.data.prefs.KeyboardPrefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real canvas keyboard with synthetic touches, so the JSON pages,
 * the geometry and the touch routing are all exercised together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TextPicApplication::class)
class KeyboardViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class Recording : KeyboardView.Listener {
        val texts = mutableListOf<String>()
        var backspaces = 0
        var spaces = 0
        var enters = 0
        var actions = mutableListOf<Int>()
        var renders = 0
        var inserts = 0
        var sends = 0
        var globes = 0
        override fun onText(text: String) { texts.add(text) }
        override fun onBackspace() { backspaces++ }
        override fun onBackspaceDragSelect() = Unit
        override fun onSpace() { spaces++ }
        override fun onEnter() { enters++ }
        override fun onEditorAction(action: Int) { actions.add(action) }
        override fun onGlobe() { globes++ }
        override fun onCursorMove(direction: Int) = Unit
        override fun onSelectAll() = Unit
        override fun onCut() = Unit
        override fun onCopy() = Unit
        override fun onPaste() = Unit
        override fun onRender() { renders++ }
        override fun onSend() { sends++ }
        override fun onRenderAndInsert() = Unit
        override fun onInsert() { inserts++ }
        override fun onDiscard() = Unit
        override fun onOpenAppSettings() = Unit
        override fun onTemplate(template: com.textpic.studio.data.db.TemplateEntity) = Unit
        override fun clipText(): String? = null
        override fun clipSensitive(): Boolean = false
    }

    private fun view(prefs: KeyboardPrefs = KeyboardPrefs()): Pair<KeyboardView, Recording> {
        val view = KeyboardView(context)
        val listener = Recording()
        view.listener = listener
        view.applyPrefs(prefs)
        view.setEditor(FieldKind.TEXT, EditorInfo.IME_ACTION_NONE, 0)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view to listener
    }

    private fun density(): Float = context.resources.displayMetrics.density

    private fun pageKeys(page: KeyboardPageId, widthPx: Float = 1080f): List<PlacedKey> {
        val frame = KeyboardFrame.of(widthPx, 2400f, 3, false)
        val bodyHeight = frame.bodyHeightPx
        val toolbar = frame.toolbarHeightPx + frame.paddingPx
        val metrics = KeyboardMetrics.of(widthPx, bodyHeight, 4)
        val keys = KeyboardGeometry.layout(
            KeyboardSpecs.load(context, page),
            widthPx,
            bodyHeight,
            density(),
            metrics,
        )
        keys.forEach { key ->
            key.visual.offset(0f, toolbar)
            key.touch.offset(0f, toolbar)
        }
        return keys
    }

    private fun tap(view: KeyboardView, key: PlacedKey) {
        val x = key.visual.centerX()
        val y = key.visual.centerY()
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun key(page: KeyboardPageId, label: String): PlacedKey =
        pageKeys(page).first { it.key.label == label }

    private fun kind(page: KeyboardPageId, kind: KeyKind): PlacedKey =
        pageKeys(page).first { it.key.kind == kind }

    @Test
    fun `the view is sized to the Gboard proportions`() {
        val (view, _) = view()
        assertThat(view.measuredWidth).isEqualTo(1080)
        // Level 3 is 0.71 of the width on a portrait screen.
        assertThat(view.measuredHeight.toFloat()).isWithin(1080f * 0.02f).of(1080f * 0.71f)
    }

    @Test
    fun `tapping Send in the toolbar reaches the listener`() {
        val (view, listener) = view()
        val slot = view.width / 5f
        val y = 20f * density()
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, slot * 2.5f, y, 0)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, slot * 2.5f, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        assertThat(listener.sends).isEqualTo(1)
    }

    @Test
    fun `tapping a letter commits it`() {
        val (view, listener) = view()
        tap(view, key(KeyboardPageId.LETTERS, "q"))
        assertThat(listener.texts).containsExactly("q")
    }

    @Test
    fun `shift turns the next letter into a capital`() {
        val (view, listener) = view()
        tap(view, kind(KeyboardPageId.LETTERS, KeyKind.SHIFT))
        tap(view, key(KeyboardPageId.LETTERS, "q"))
        assertThat(listener.texts).containsExactly("Q")
    }

    @Test
    fun `the question mark key opens the symbols page`() {
        val (view, _) = view()
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.LETTERS)
        tap(view, key(KeyboardPageId.LETTERS, "?123"))
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.SYMBOLS1)
    }

    @Test
    fun `symbols page two is reached through the backslash key and returns`() {
        val (view, _) = view()
        tap(view, key(KeyboardPageId.LETTERS, "?123"))
        tap(view, key(KeyboardPageId.SYMBOLS1, "=<"))
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.SYMBOLS2)
        tap(view, key(KeyboardPageId.SYMBOLS2, "?123"))
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.SYMBOLS1)
    }

    @Test
    fun `the numpad page is reached from the symbols page and returns to letters`() {
        val (view, _) = view()
        tap(view, key(KeyboardPageId.LETTERS, "?123"))
        tap(view, key(KeyboardPageId.SYMBOLS1, "1234"))
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.NUMPAD)
        tap(view, key(KeyboardPageId.NUMPAD, "ABC"))
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.LETTERS)
    }

    @Test
    fun `a number field starts on the numpad`() {
        val view = KeyboardView(context)
        view.listener = Recording()
        view.applyPrefs(KeyboardPrefs())
        view.setEditor(FieldKind.NUMBER, EditorInfo.IME_ACTION_NONE, 0)
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.NUMPAD)
    }

    @Test
    fun `space and backspace reach the listener once per tap`() {
        val (view, listener) = view()
        tap(view, kind(KeyboardPageId.LETTERS, KeyKind.SPACE))
        assertThat(listener.spaces).isEqualTo(1)
        tap(view, kind(KeyboardPageId.LETTERS, KeyKind.BACKSPACE))
        assertThat(listener.backspaces).isEqualTo(1)
    }

    @Test
    fun `a taller preference makes the keyboard taller`() {
        val (small, _) = view(KeyboardPrefs(heightLevel = 1))
        val (tall, _) = view(KeyboardPrefs(heightLevel = 5))
        assertThat(tall.measuredHeight).isGreaterThan(small.measuredHeight)
    }

    @Test
    fun `password fields get a plain keyboard and no personalisation`() {
        val view = KeyboardView(context)
        view.listener = Recording()
        view.applyPrefs(KeyboardPrefs())
        view.setEditor(
            FieldKind.PASSWORD,
            EditorInfo.IME_ACTION_DONE,
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
        assertThat(view.currentPage()).isEqualTo(KeyboardPageId.LETTERS)
    }
}
