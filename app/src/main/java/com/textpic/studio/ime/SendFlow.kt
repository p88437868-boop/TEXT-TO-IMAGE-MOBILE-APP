package com.textpic.studio.ime

import android.view.inputmethod.EditorInfo

/** What happens after the image is inserted into the chat field. */
enum class AfterInsert(val label: String, val help: String) {
    APP_ACTION(
        "Press the app's Send action",
        "Uses the app's own Send/Go/Done action, otherwise the Enter key.",
    ),
    ENTER("Press Enter only", "Always sends the Enter key."),
    NOTHING("Do nothing", "Only inserts the image; you tap the app's send button."),
    ;

    companion object {
        fun fromWire(value: String?): AfterInsert =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: APP_ACTION
    }
}

/** How the send is triggered, decided from the setting and the field's action. */
enum class SendTrigger { EDITOR_ACTION, ENTER, NOTHING }

/** The pure rules behind the Send button. */
object SendRules {
    const val MIN_DELAY_MS = 200
    const val MAX_DELAY_MS = 1_500
    const val DEFAULT_DELAY_MS = 600
    const val CONFIRM_MS = 2_000L

    fun delayMs(value: Int): Int = value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)

    /** An app only "sends" on Enter when its action is Send, Go or Done. */
    fun triggerFor(mode: AfterInsert, imeAction: Int): SendTrigger = when (mode) {
        AfterInsert.ENTER -> SendTrigger.ENTER
        AfterInsert.NOTHING -> SendTrigger.NOTHING
        AfterInsert.APP_ACTION -> when (imeAction) {
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_DONE,
            -> SendTrigger.EDITOR_ACTION
            else -> SendTrigger.ENTER
        }
    }

    /** The editor action to perform, or null when Enter is used instead. */
    fun actionFor(mode: AfterInsert, imeAction: Int): Int? =
        if (triggerFor(mode, imeAction) == SendTrigger.EDITOR_ACTION) imeAction else null
}

/**
 * The Send button, step by step: insert, clear the typed text, wait, then press
 * send. Pure and driven from outside (the IME owns the clock and the input
 * connection), so every branch can be tested without a phone.
 */
class SendSequence(private val port: Port) {

    interface Port {
        fun hasText(): Boolean

        /** `commitContent` — true only when the app accepted the image. */
        fun commitImage(): Boolean

        fun clearText()
        fun performEditorAction(action: Int): Boolean
        fun pressEnter()
        fun notifyCannotInsert()
        fun confirmVibration()
        fun showUndo(millis: Long)
        fun hideUndo()
    }

    enum class Stage { IDLE, WAITING_CONFIRM, WAITING_SEND, DONE }

    enum class Result {
        /** Nothing was typed. */
        NOTHING_TYPED,

        /** The app refused the image; the caller falls back to the clipboard. */
        INSERT_FAILED,

        /** The image is in the field; send happens after the delay. */
        WAITING_TO_SEND,

        /** Waiting for the Undo chip to expire before sending. */
        WAITING_TO_CONFIRM,
    }

    var stage: Stage = Stage.IDLE
        private set

    private var plan = Plan()
    private var lastTrigger: SendTrigger = SendTrigger.NOTHING

    data class Plan(
        val afterInsert: AfterInsert = AfterInsert.APP_ACTION,
        val imeAction: Int = EditorInfo.IME_ACTION_NONE,
        val confirm: Boolean = false,
    )

    fun start(plan: Plan): Result {
        this.plan = plan
        if (!port.hasText()) {
            stage = Stage.DONE
            return Result.NOTHING_TYPED
        }
        if (!port.commitImage()) {
            port.notifyCannotInsert()
            stage = Stage.DONE
            return Result.INSERT_FAILED
        }
        port.clearText()
        lastTrigger = SendRules.triggerFor(plan.afterInsert, plan.imeAction)
        return if (plan.confirm && lastTrigger != SendTrigger.NOTHING) {
            stage = Stage.WAITING_CONFIRM
            port.showUndo(SendRules.CONFIRM_MS)
            Result.WAITING_TO_CONFIRM
        } else {
            stage = Stage.WAITING_SEND
            Result.WAITING_TO_SEND
        }
    }

    /** The send delay has passed. */
    fun onSendDelayElapsed(): Boolean {
        if (stage != Stage.WAITING_SEND) return false
        return fire()
    }

    /** The two-second Undo chip expired. */
    fun onConfirmElapsed(): Boolean {
        if (stage != Stage.WAITING_CONFIRM) return false
        port.hideUndo()
        return fire()
    }

    /** The customer tapped Undo: the image stays inserted, nothing is sent. */
    fun undo() {
        if (stage != Stage.WAITING_CONFIRM) return
        port.hideUndo()
        stage = Stage.DONE
    }

    fun reset() {
        stage = Stage.IDLE
    }

    private fun fire(): Boolean {
        stage = Stage.DONE
        when (lastTrigger) {
            SendTrigger.NOTHING -> Unit
            SendTrigger.EDITOR_ACTION -> {
                val action = SendRules.actionFor(plan.afterInsert, plan.imeAction)
                val handled = action != null && port.performEditorAction(action)
                if (!handled) port.pressEnter()
            }
            SendTrigger.ENTER -> port.pressEnter()
        }
        if (lastTrigger != SendTrigger.NOTHING) port.confirmVibration()
        return true
    }
}
