package com.textpic.studio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TextPicApplication::class)
class MainActivityTest {
    @Test
    fun `main activity launches`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertThat(controller.get()).isNotNull()
        controller.pause().stop().destroy()
    }
}
