package com.textpic.studio.ime

import android.view.inputmethod.EditorInfo
import com.google.common.truth.Truth.assertThat
import com.textpic.studio.data.db.TemplateEntity
import org.junit.Test

/**
 * The Send button, step by step. A fake port stands in for the app's
 * `InputConnection`, so every branch can be checked without a phone.
 */
class SendFlowTest {

    private class FakePort(
        var text: String = "hello",
        var acceptsImage: Boolean = true,
        var actionHandled: Boolean = true,
    ) : SendSequence.Port {
        var commits = 0
        var clears = 0
        var actions = mutableListOf<Int>()
        var enters = 0
        var cannotInsert = 0
        var vibrations = 0
        var undoShown = 0L
        var undoHidden = 0

        override fun hasText(): Boolean = text.isNotBlank()

        override fun commitImage(): Boolean {
            commits++
            return acceptsImage
        }

        override fun clearText() {
            clears++
            text = ""
        }

        override fun performEditorAction(action: Int): Boolean {
            actions.add(action)
            return actionHandled
        }

        override fun pressEnter() {
            enters++
        }

        override fun notifyCannotInsert() {
            cannotInsert++
        }

        override fun confirmVibration() {
            vibrations++
        }

        override fun showUndo(millis: Long) {
            undoShown = millis
        }

        override fun hideUndo() {
            undoHidden++
        }
    }

    private fun plan(
        mode: AfterInsert = AfterInsert.APP_ACTION,
        action: Int = EditorInfo.IME_ACTION_SEND,
        confirm: Boolean = false,
    ) = SendSequence.Plan(afterInsert = mode, imeAction = action, confirm = confirm)

    @Test
    fun `empty text does nothing at all`() {
        val port = FakePort(text = "  ")
        val sequence = SendSequence(port)
        assertThat(sequence.start(plan())).isEqualTo(SendSequence.Result.NOTHING_TYPED)
        assertThat(port.commits).isEqualTo(0)
        assertThat(port.clears).isEqualTo(0)
        assertThat(port.actions).isEmpty()
        assertThat(port.enters).isEqualTo(0)
    }

    @Test
    fun `an app that refuses images is reported and nothing is cleared`() {
        val port = FakePort(acceptsImage = false)
        val sequence = SendSequence(port)
        assertThat(sequence.start(plan())).isEqualTo(SendSequence.Result.INSERT_FAILED)
        assertThat(port.cannotInsert).isEqualTo(1)
        assertThat(port.clears).isEqualTo(0)
        assertThat(port.actions).isEmpty()
        assertThat(port.enters).isEqualTo(0)
    }

    @Test
    fun `the app's own Send action is pressed after the delay`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        assertThat(sequence.start(plan(action = EditorInfo.IME_ACTION_SEND)))
            .isEqualTo(SendSequence.Result.WAITING_TO_SEND)
        assertThat(port.commits).isEqualTo(1)
        // The text is cleared only after the image was accepted.
        assertThat(port.clears).isEqualTo(1)
        assertThat(port.actions).isEmpty()
        sequence.onSendDelayElapsed()
        assertThat(port.actions).containsExactly(EditorInfo.IME_ACTION_SEND)
        assertThat(port.enters).isEqualTo(0)
        assertThat(port.vibrations).isEqualTo(1)
        assertThat(sequence.stage).isEqualTo(SendSequence.Stage.DONE)
    }

    @Test
    fun `Send is only pressed once even if the timer fires twice`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        sequence.start(plan())
        sequence.onSendDelayElapsed()
        sequence.onSendDelayElapsed()
        assertThat(port.actions).hasSize(1)
        assertThat(port.vibrations).isEqualTo(1)
    }

    @Test
    fun `Go and Done also count as send actions`() {
        for (action in listOf(EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE)) {
            val port = FakePort()
            val sequence = SendSequence(port)
            sequence.start(plan(action = action))
            sequence.onSendDelayElapsed()
            assertThat(port.actions).containsExactly(action)
        }
    }

    @Test
    fun `a field without a send action falls back to Enter`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        sequence.start(plan(action = EditorInfo.IME_ACTION_NONE))
        sequence.onSendDelayElapsed()
        assertThat(port.actions).isEmpty()
        assertThat(port.enters).isEqualTo(1)
    }

    @Test
    fun `an unhandled app action falls back to Enter`() {
        val port = FakePort(actionHandled = false)
        val sequence = SendSequence(port)
        sequence.start(plan(action = EditorInfo.IME_ACTION_SEND))
        sequence.onSendDelayElapsed()
        assertThat(port.actions).containsExactly(EditorInfo.IME_ACTION_SEND)
        assertThat(port.enters).isEqualTo(1)
    }

    @Test
    fun `Press Enter only never uses the app action`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        sequence.start(plan(mode = AfterInsert.ENTER, action = EditorInfo.IME_ACTION_SEND))
        sequence.onSendDelayElapsed()
        assertThat(port.actions).isEmpty()
        assertThat(port.enters).isEqualTo(1)
    }

    @Test
    fun `Do nothing inserts and clears but never sends`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        sequence.start(plan(mode = AfterInsert.NOTHING))
        sequence.onSendDelayElapsed()
        assertThat(port.commits).isEqualTo(1)
        assertThat(port.clears).isEqualTo(1)
        assertThat(port.actions).isEmpty()
        assertThat(port.enters).isEqualTo(0)
        assertThat(port.vibrations).isEqualTo(0)
    }

    @Test
    fun `confirming shows Undo for two seconds before sending`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        assertThat(sequence.start(plan(confirm = true)))
            .isEqualTo(SendSequence.Result.WAITING_TO_CONFIRM)
        assertThat(port.undoShown).isEqualTo(2_000L)
        // The send delay alone must not send while the chip is up.
        sequence.onSendDelayElapsed()
        assertThat(port.actions).isEmpty()
        sequence.onConfirmElapsed()
        assertThat(port.actions).hasSize(1)
        assertThat(port.undoHidden).isEqualTo(1)
    }

    @Test
    fun `Undo cancels the send and keeps the inserted image`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        sequence.start(plan(confirm = true))
        sequence.undo()
        sequence.onConfirmElapsed()
        assertThat(port.actions).isEmpty()
        assertThat(port.enters).isEqualTo(0)
        assertThat(port.commits).isEqualTo(1)
        assertThat(port.undoHidden).isEqualTo(1)
        assertThat(sequence.stage).isEqualTo(SendSequence.Stage.DONE)
    }

    @Test
    fun `confirming with Do nothing skips the chip`() {
        val port = FakePort()
        val sequence = SendSequence(port)
        assertThat(sequence.start(plan(mode = AfterInsert.NOTHING, confirm = true)))
            .isEqualTo(SendSequence.Result.WAITING_TO_SEND)
        assertThat(port.undoShown).isEqualTo(0L)
    }

    @Test
    fun `the send delay stays between 200 and 1500 ms`() {
        assertThat(SendRules.delayMs(0)).isEqualTo(200)
        assertThat(SendRules.delayMs(600)).isEqualTo(600)
        assertThat(SendRules.delayMs(9_000)).isEqualTo(1_500)
    }

    @Test
    fun `the trigger follows the setting and the field's action`() {
        assertThat(SendRules.triggerFor(AfterInsert.APP_ACTION, EditorInfo.IME_ACTION_SEND))
            .isEqualTo(SendTrigger.EDITOR_ACTION)
        assertThat(SendRules.triggerFor(AfterInsert.APP_ACTION, EditorInfo.IME_ACTION_SEARCH))
            .isEqualTo(SendTrigger.ENTER)
        assertThat(SendRules.triggerFor(AfterInsert.APP_ACTION, EditorInfo.IME_ACTION_NEXT))
            .isEqualTo(SendTrigger.ENTER)
        assertThat(SendRules.triggerFor(AfterInsert.ENTER, EditorInfo.IME_ACTION_SEND))
            .isEqualTo(SendTrigger.ENTER)
        assertThat(SendRules.triggerFor(AfterInsert.NOTHING, EditorInfo.IME_ACTION_SEND))
            .isEqualTo(SendTrigger.NOTHING)
        assertThat(SendRules.actionFor(AfterInsert.APP_ACTION, EditorInfo.IME_ACTION_SEND))
            .isEqualTo(EditorInfo.IME_ACTION_SEND)
        assertThat(SendRules.actionFor(AfterInsert.ENTER, EditorInfo.IME_ACTION_SEND)).isNull()
    }

    @Test
    fun `template placeholders are found once, in order`() {
        assertThat(TemplateRules.placeholders("Hi {name}, {product} is {price}."))
            .containsExactly("name", "product", "price").inOrder()
        assertThat(TemplateRules.placeholders("{name} and {name} again"))
            .containsExactly("name")
        assertThat(TemplateRules.placeholders("no placeholders here")).isEmpty()
    }

    @Test
    fun `filling a template keeps the holes that were skipped`() {
        val body = "Hi {name}, {product} costs {price}."
        assertThat(TemplateRules.fill(body, mapOf("name" to "Sam", "price" to "500")))
            .isEqualTo("Hi Sam, {product} costs 500.")
        assertThat(TemplateRules.fill(body, emptyMap())).isEqualTo(body)
    }

    @Test
    fun `only a template with placeholders asks for values`() {
        val plain = TemplateEntity(1, "Hello", "Good morning", 0)
        val holes = TemplateEntity(2, "Offer", "Hi {name}", 0)
        assertThat(TemplateRules.needsValues(plain)).isFalse()
        assertThat(TemplateRules.needsValues(holes)).isTrue()
    }

    @Test
    fun `a skin tone goes before the variation selector`() {
        val thumb = "\uD83D\uDC4D"
        val victory = "\u270C\uFE0F"
        assertThat(EmojiData.withSkinTone(thumb, EmojiData.SKIN_TONES[0]))
            .isEqualTo("\uD83D\uDC4D\uD83C\uDFFB")
        assertThat(EmojiData.withSkinTone(victory, EmojiData.SKIN_TONES[1]))
            .isEqualTo("\u270C\uD83C\uDFFC\uFE0F")
        assertThat(EmojiData.SKIN_TONES).hasSize(5)
        assertThat(EmojiData.supportsTone(thumb)).isTrue()
        assertThat(EmojiData.supportsTone("\uD83D\uDE00")).isFalse()
    }

    @Test
    fun `the setting survives a round trip through its wire name`() {
        for (mode in AfterInsert.entries) {
            assertThat(AfterInsert.fromWire(mode.name)).isEqualTo(mode)
        }
        assertThat(AfterInsert.fromWire(null)).isEqualTo(AfterInsert.APP_ACTION)
        assertThat(AfterInsert.fromWire("nonsense")).isEqualTo(AfterInsert.APP_ACTION)
    }
}
