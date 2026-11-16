package com.textpic.studio.ime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImeLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `qwerty layout has four rows and action keys`() {
        val layout = ImeLayouts.load(context, "layouts/en_qwerty.json")
        assertThat(layout.rows).hasSize(4)
        assertThat(layout.rows[0]).hasSize(10)
        assertThat(layout.rows[1].map { it.output }).containsExactly(
            "a", "s", "d", "f", "g", "h", "j", "k", "l",
        ).inOrder()
        assertThat(layout.rows[2].first().type).isEqualTo(ImeKeyType.SHIFT)
        assertThat(layout.rows[2].last().type).isEqualTo(ImeKeyType.BACKSPACE)
        assertThat(layout.rows[2].map { it.output }).containsAtLeast(
            "z", "x", "c", "v", "b", "n", "m",
        )
        assertThat(layout.rows[3].first().type).isEqualTo(ImeKeyType.SYMBOLS)
        assertThat(layout.rows[3].last().type).isEqualTo(ImeKeyType.RENDER)
        assertThat(layout.rows[3].map { it.type }).containsAtLeast(
            ImeKeyType.EMOJI,
            ImeKeyType.SPACE,
            ImeKeyType.RENDER,
            ImeKeyType.INSERT,
            ImeKeyType.ENTER,
        )
    }

    @Test
    fun `symbols and emoji layouts load`() {
        val symbols = ImeLayouts.load(context, "layouts/en_symbols.json")
        val emoji = ImeLayouts.load(context, "layouts/en_emoji.json")
        assertThat(symbols.rows).hasSize(4)
        assertThat(emoji.rows).hasSize(4)
        assertThat(symbols.rows.last().first().type).isEqualTo(ImeKeyType.LETTERS)
        assertThat(emoji.rows.first().first().output).isNotEmpty()
        assertThat(emoji.rows.first().first().output).isNotEqualTo("?")
    }
}
