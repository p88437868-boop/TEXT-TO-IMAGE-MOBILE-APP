package com.textpic.studio.ime

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.textpic.studio.TextPicApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The keyboard must keep typing for free, but Render / Insert / template
 * insertion have to be refused in the service itself (not only by hiding a
 * button) while the install is unlicensed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TextPicApplication::class)
class TextPicImeLicenseGateTest {
    @Test
    fun `render key is refused while unlicensed`() {
        val service = Robolectric.buildService(TextPicIME::class.java).create().get()
        service.onCreateInputView()
        val handled = service.onKeyDown(KeyEvent.KEYCODE_F6, null)
        assertThat(handled).isTrue()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Activate TextPic to use this.")
        service.onDestroy()
    }

    @Test
    fun `insert key is refused while unlicensed`() {
        val service = Robolectric.buildService(TextPicIME::class.java).create().get()
        service.onCreateInputView()
        service.onKeyDown(KeyEvent.KEYCODE_F9, null)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Activate TextPic to use this.")
        service.onDestroy()
    }
}
