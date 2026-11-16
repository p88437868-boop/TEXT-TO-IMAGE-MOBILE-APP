package com.textpic.studio.ime

import com.google.common.truth.Truth.assertThat
import com.textpic.studio.TextPicApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TextPicApplication::class)
class TextPicImeServiceTest {
    @Test
    fun `input view is created`() {
        val service = Robolectric.buildService(TextPicIME::class.java).create().get()
        val view = service.onCreateInputView()
        assertThat(view).isInstanceOf(KeyboardView::class.java)
        service.onDestroy()
    }
}
