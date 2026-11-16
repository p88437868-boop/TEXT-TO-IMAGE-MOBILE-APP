package com.textpic.studio.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.textpic.studio.data.prefs.AppSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextPicKeyboardViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `keyboard measures inside the ime frame layout`() {
        // InputMethodService hosts the input view in a FrameLayout, which casts child
        // params to MarginLayoutParams. Plain LayoutParams here crash the keyboard.
        val parent = FrameLayout(context)
        val view = TextPicKeyboardView(context)
        parent.addView(view)
        // FrameLayout.measureChildWithMargins casts these; a plain LayoutParams kills the IME.
        assertThat(view.layoutParams).isInstanceOf(ViewGroup.MarginLayoutParams::class.java)
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 1080, 2400)
        assertThat(view.measuredHeight).isGreaterThan(0)
    }

    @Test
    fun `keyboard builds and resets`() {
        val view = TextPicKeyboardView(context)
        view.applySettings(AppSettings())
        view.resetForNewInput()
        view.setBusy(true)
        view.setBusy(false)
        assertThat(view.childCount).isGreaterThan(0)
    }
}
