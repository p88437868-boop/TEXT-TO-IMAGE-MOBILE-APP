package com.textpic.studio.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.textpic.core.MAX_TEXT_LENGTH
import com.textpic.studio.AppContainer
import com.textpic.studio.TextPicApplication
import com.textpic.studio.data.db.MessageStatus
import com.textpic.studio.data.db.TemplateEntity
import com.textpic.studio.data.prefs.KeyboardPrefRules
import com.textpic.studio.data.prefs.KeyboardPrefs
import com.textpic.studio.domain.LicenseGate
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextPicIME : InputMethodService(), KeyboardView.Listener {
    private lateinit var container: AppContainer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var keyboardView: KeyboardView? = null
    private var pending: PendingRender? = null
    private var renderJob: Job? = null
    private var rendering = false
    private var prefs: KeyboardPrefs = KeyboardPrefs()
    private var lastSpaceAt = 0L
    private val clipboardStore by lazy { ClipboardStore(this) }

    private companion object {
        const val LICENSE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
        const val CLIP_POLL_MS = 900L
    }

    private var lastClipPoll = 0L
    private val sendHandler = Handler(Looper.getMainLooper())
    private var sendSequence: SendSequence? = null

    private data class PendingRender(
        val id: Long,
        val file: File,
    )

    override fun onCreate() {
        super.onCreate()
        container = (application as TextPicApplication).container
    }

    override fun onCreateInputView(): android.view.View {
        val view = KeyboardView(this).apply {
            listener = this@TextPicIME
            onAppearanceChanged = { applyNavigationBarColors() }
            prefsChanged = { updated ->
                prefs = updated
                serviceScope.launch {
                    runCatching {
                        container.settingsRepository.updateKeyboard { updated }
                    }
                }
            }
            clipsCleared = {
                serviceScope.launch {
                    runCatching {
                        clipboardStore.update(System.currentTimeMillis(), prefs.clipboardTimeoutMinutes) {
                            ClipboardRules.clearUnpinned(it)
                        }
                        refreshClips()
                    }
                }
            }
        }
        keyboardView = view
        // The navigation bar is behind the last key row: add its height as padding
        // and paint it in the keyboard colour, like Gboard does.
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            view.setNavigationInset(navigationInsetPx(insets))
            insets
        }
        refreshUi()
        view.post { ViewCompat.requestApplyInsets(view) }
        return view
    }

    private fun navigationInsetPx(insets: WindowInsetsCompat): Int =
        NavigationInsetRules.bottomInset(
            navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
            systemGestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom,
        )

    /** Some launchers report no insets; the resource is the last resort. */
    private fun fallbackNavigationInset(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun applyNavigationBarColors() {
        val window = window?.window ?: return
        val view = keyboardView ?: return
        val background = view.backgroundArgb()
        runCatching {
            // Draw behind the navigation bar so the inset area is ours to paint.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(background))
            @Suppress("DEPRECATION")
            window.navigationBarColor = background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val resolved = com.textpic.studio.data.prefs.KeyboardPrefRules.resolvedTheme(
                prefs.theme,
                isSystemDark(),
            )
            WindowInsetsControllerCompat(window, view).isAppearanceLightNavigationBars =
                resolved == com.textpic.studio.data.prefs.KeyboardTheme.LIGHT
        }
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboardView?.let { view ->
            ViewCompat.requestApplyInsets(view)
            applyNavigationBarColors()
            view.requestLayout()
        }
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (!isInputViewShown) return
        // Push the app above the keyboard, so no input field hides behind it.
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        discardPending()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyEditorState()
        cancelSend()
        applyNavigationBarColors()
        keyboardView?.let { view ->
            view.post {
                if (view.isAttachedToWindow && view.navigationInsetPx() == 0) {
                    view.setNavigationInset(fallbackNavigationInset())
                }
            }
        }
        container.appScope.launch {
            runCatching { container.settingsRepository.markImeActive() }
        }
        container.appScope.launch {
            runCatching {
                // At most one check every six hours while typing.
                if (container.licenseService.shouldCheckNow(LICENSE_CHECK_INTERVAL_MS)) {
                    container.licenseService.refresh(online = true)
                }
            }
        }
        refreshUi()
    }

    override fun onUpdateSelection(
        beforeStart: Int,
        beforeEnd: Int,
        afterStart: Int,
        afterEnd: Int,
        candStart: Int,
        candEnd: Int,
    ) {
        super.onUpdateSelection(beforeStart, beforeEnd, afterStart, afterEnd, candStart, candEnd)
        applyEditorState()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        cancelSend()
        discardPending()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_F6 -> {
                renderAndShow(autoInsert = false)
                true
            }
            KeyEvent.KEYCODE_F9 -> {
                renderAndShow(autoInsert = true)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        if (pending != null) {
            discardPending()
        }
        renderJob?.cancel()
        serviceScope.cancel()
        keyboardView = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ typing

    override fun onText(text: String) {
        discardPending()
        currentInputConnection?.commitText(text, 1)
        // Copying something out of another app puts it on the clipboard; remember it
        // the next time a key is pressed instead of polling continuously.
        maybeCaptureClipboard()
    }

    override fun onBackspace() {
        discardPending()
        val connection = currentInputConnection ?: return
        val selection = connection.getSelectedText(0)?.toString().orEmpty()
        if (selection.isNotEmpty()) {
            connection.commitText("", 1)
            return
        }
        connection.deleteSurroundingTextInCodePoints(1, 0)
    }

    override fun onBackspaceDragSelect() {
        // Shift + ← extends the selection; releasing backspace deletes it.
        val connection = currentInputConnection ?: return
        connection.sendKeyEvent(
            KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                0,
                KeyEvent.META_SHIFT_ON,
            ),
        )
        connection.sendKeyEvent(
            KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_LEFT,
                0,
                KeyEvent.META_SHIFT_ON,
            ),
        )
    }

    override fun onSpace() {
        discardPending()
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val now = SystemClock.uptimeMillis()
        val previous = before.lastOrNull()
        if (prefs.doubleSpacePeriod &&
            KeyboardRules.doubleSpacePeriod(previous, now, lastSpaceAt)
        ) {
            // Replace the space we typed a moment ago with ". ".
            connection.deleteSurroundingTextInCodePoints(1, 0)
            connection.commitText(". ", 1)
            lastSpaceAt = 0L
            return
        }
        connection.commitText(" ", 1)
        lastSpaceAt = if (previous == ' ') now else 0L
    }

    override fun onEnter() {
        discardPending()
        sendKeyChar('\n')
    }

    override fun onEditorAction(action: Int) {
        discardPending()
        val handled = runCatching { sendDefaultEditorAction(true) }.getOrDefault(false)
        if (!handled) onEnter()
    }

    override fun onGlobe() {
        runCatching { switchToNextInputMethod(false) }
    }

    override fun onCursorMove(direction: Int) {
        val connection = currentInputConnection ?: return
        val keyCode = when {
            direction >= 10_000 -> KeyEvent.KEYCODE_MOVE_END
            direction <= -10_000 -> KeyEvent.KEYCODE_MOVE_HOME
            direction > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> KeyEvent.KEYCODE_DPAD_LEFT
        }
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun onSelectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    override fun onCut() {
        currentInputConnection?.performContextMenuAction(android.R.id.cut)
    }

    override fun onCopy() {
        currentInputConnection?.performContextMenuAction(android.R.id.copy)
    }

    override fun onPaste() {
        currentInputConnection?.performContextMenuAction(android.R.id.paste)
    }

    // ------------------------------------------------------------- TextPic output

    override fun onRender() {
        renderAndShow(autoInsert = false)
    }

    override fun onInsert() {
        container.licenseGate.denialMessage()?.let { message ->
            toast(message)
            return
        }
        val current = pending
        if (current == null) {
            renderAndShow(autoInsert = true)
            return
        }
        insertPending(current)
    }

    override fun onRenderAndInsert() {
        renderAndShow(autoInsert = true)
    }

    // ---------------------------------------------------------------------- send

    /**
     * One tap: render, insert, then press send once the app has attached the image.
     * The gates are the same as rendering: licence, mandatory update, password field.
     */
    override fun onSend() {
        if (rendering) return
        val existing = pending
        if (existing != null && keyboardView?.hasPreview() == true) {
            sendPending(existing)
            return
        }
        renderAndShow(autoInsert = true, sendAfterInsert = true)
    }

    private fun cancelSend() {
        sendHandler.removeCallbacksAndMessages(null)
        sendSequence?.reset()
        sendSequence = null
        keyboardView?.hideUndo()
    }

    private fun sendPending(current: PendingRender) {
        val connection = currentInputConnection
        if (connection == null) {
            toast("This field cannot receive an image.")
            return
        }
        val uri = runCatching { container.imageStore.uriFor(current.file) }.getOrElse {
            toast("This image is no longer available.")
            return
        }
        if (fieldText().isBlank()) {
            toast("Type a message first.")
            return
        }
        cancelSend()
        val view = keyboardView
        val sequence = SendSequence(
            object : SendSequence.Port {
                override fun hasText(): Boolean = fieldText().isNotBlank()

                override fun commitImage(): Boolean {
                    val inserted = insertImage(connection, uri)
                    if (inserted) {
                        container.appScope.launch {
                            runCatching { container.messageRepository.markInserted(current.id) }
                        }
                        pending = null
                    }
                    return inserted
                }

                override fun clearText() = deleteFieldText(connection)

                override fun performEditorAction(action: Int): Boolean =
                    runCatching { connection.performEditorAction(action) }.getOrDefault(false)

                override fun pressEnter() {
                    sendKeyChar('\n')
                }

                override fun notifyCannotInsert() {
                    fallBackToClipboard(current)
                }

                override fun confirmVibration() {
                    view?.confirmVibration()
                }

                override fun showUndo(millis: Long) {
                    view?.showInserted()
                    view?.showUndo { sendSequence?.undo() }
                }

                override fun hideUndo() {
                    view?.hideUndo()
                }
            },
        )
        sendSequence = sequence
        val plan = SendSequence.Plan(
            afterInsert = prefs.afterInsert,
            imeAction = currentInputEditorInfo?.actionId ?: EditorInfo.IME_ACTION_NONE,
            confirm = prefs.confirmBeforeSend,
        )
        view?.setBusy(false)
        when (sequence.start(plan)) {
            SendSequence.Result.NOTHING_TYPED -> toast("Type a message first.")
            SendSequence.Result.INSERT_FAILED -> Unit
            SendSequence.Result.WAITING_TO_SEND -> {
                sendHandler.postDelayed(
                    { sequence.onSendDelayElapsed() },
                    KeyboardPrefRules.sendDelayMs(prefs.sendDelayMs).toLong(),
                )
                if (prefs.afterInsert == AfterInsert.NOTHING) {
                    toast("Image inserted. Tap the app's send button.")
                }
            }
            SendSequence.Result.WAITING_TO_CONFIRM -> {
                sendHandler.postDelayed(
                    { sequence.onConfirmElapsed() },
                    SendRules.CONFIRM_MS,
                )
            }
        }
    }

    /** The app refuses keyboard images: copy the picture instead, as before. */
    private fun fallBackToClipboard(current: PendingRender) {
        val copied = runCatching {
            container.imageStore.copyImageToClipboard(current.file)
        }.isSuccess
        if (copied) {
            container.appScope.launch {
                runCatching {
                    container.messageRepository.markStatus(current.id, MessageStatus.COPIED)
                }
            }
            pending = null
            keyboardView?.clearPreview()
            toast("This app does not take images from a keyboard — image copied, paste it in.")
        } else {
            toast("This app cannot insert the image.")
        }
    }

    override fun onDiscard() {
        discardPending()
        keyboardView?.clearPreview()
    }

    override fun onOpenAppSettings() {
        runCatching { startActivity(ImeSettingsActivity.intent(this)) }
    }

    override fun onTemplate(template: TemplateEntity) {
        container.licenseGate.denialMessage()?.let { message ->
            toast(message)
            return
        }
        discardPending()
        currentInputConnection?.commitText(template.body, 1)
    }

    override fun clipText(): String? = readClip()

    override fun clipSensitive(): Boolean = isClipSensitive()

    // ----------------------------------------------------------------- clipboard

    private fun clipboard(): ClipboardManager? =
        getSystemService(ClipboardManager::class.java)

    private fun readClip(): String? {
        val clip = clipboard()?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun isClipSensitive(): Boolean {
        val description: ClipDescription = clipboard()?.primaryClipDescription ?: return false
        val extras = description.extras
        return extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
    }

    private fun maybeCaptureClipboard() {
        val now = SystemClock.uptimeMillis()
        if (now - lastClipPoll < CLIP_POLL_MS) return
        lastClipPoll = now
        val editor = currentInputEditorInfo ?: return
        val field = FieldKinds.of(editor.inputType)
        if (!prefs.clipboardEnabled) return
        if (!KeyboardRules.allowsHistory(field, editor.inputType)) return
        val sensitive = isClipSensitive()
        val text = readClip()
        if (!ClipboardRules.accepts(true, field, sensitive, text)) return
        val body = text!!.trim()
        val existing = clipboardStore.load()
        if (existing.any { it.text == body }) return
        val next = ClipboardRules.add(
            existing,
            body,
            System.currentTimeMillis(),
            prefs.clipboardTimeoutMinutes,
            ClipboardRules.nextId(existing),
        )
        runCatching { clipboardStore.save(next) }
        keyboardView?.refreshClips(next)
    }

    private fun refreshClips() {
        serviceScope.launch {
            val now = System.currentTimeMillis()
            val list = runCatching {
                clipboardStore.update(now, prefs.clipboardTimeoutMinutes) { it }
            }.getOrDefault(emptyList())
            keyboardView?.refreshClips(list)
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun applyEditorState() {
        val editor = currentInputEditorInfo ?: return
        val field = FieldKinds.of(editor.inputType)
        val caps = runCatching {
            currentInputConnection?.getCursorCapsMode(editor.inputType) ?: 0
        }.getOrDefault(0)
        keyboardView?.setEditor(field, editor.actionId, caps)
    }

    private fun refreshUi() {
        serviceScope.launch {
            runCatching {
                val settings = container.settingsRepository.current()
                prefs = settings.keyboard
                container.templateRepository.ensureSeeded()
                val templates = container.templateRepository.all()
                keyboardView?.applyPrefs(settings.keyboard)
                keyboardView?.setTemplates(templates)
                val remaining = runCatching { container.usageGate.remainingLabel() }.getOrNull()
                val low = runCatching { container.usageGate.isLow() }.getOrDefault(false)
                keyboardView?.setRemaining(remaining, low)
                refreshClips()
            }
        }
    }

    private fun renderAndShow(autoInsert: Boolean, sendAfterInsert: Boolean = false) {
        if (rendering) return
        val editor = currentInputEditorInfo
        if (editor != null && FieldKinds.of(editor.inputType) == FieldKind.PASSWORD) {
            toast("Render is not available in a password field.")
            return
        }
        if (container.updateManager.state.value.mandatory) {
            toast("Update TextPic before rendering. Typing still works.")
            return
        }
        container.licenseGate.denialMessage()?.let { message ->
            toast(message)
            return
        }
        val text = fieldText().trim()
        if (text.isEmpty()) {
            toast("Type a message first.")
            return
        }
        if (text.length > MAX_TEXT_LENGTH) {
            toast("Message is limited to $MAX_TEXT_LENGTH characters.")
            return
        }
        discardPending()
        rendering = true
        keyboardView?.setBusy(true)
        renderJob = serviceScope.launch {
            try {
                val settings = container.settingsRepository.current()
                val result = withContext(Dispatchers.Default) {
                    container.renderer.render(settings.render.toRequest(text))
                }
                val file = withContext(Dispatchers.IO) {
                    container.imageStore.savePermanent(result.bitmap)
                }
                val id = try {
                    withContext(Dispatchers.IO) {
                        container.messageRepository.record(
                            text = if (settings.saveMessageText) text else null,
                            chars = text.length,
                            settings = settings.render,
                            meta = result.meta,
                            imagePath = file.absolutePath,
                            status = MessageStatus.RENDERED,
                        )
                    }
                } catch (error: Throwable) {
                    runCatching { file.delete() }
                    throw error
                }
                pending = PendingRender(id, file)
                keyboardView?.showPreview(
                    result.bitmap,
                    "${result.meta.widthPx} × ${result.meta.heightPx} px · ${result.meta.finalFontPx} px text",
                )
                runCatching { container.licenseService.noteRender() }
                if (autoInsert) {
                    val finished = pending!!
                    if (sendAfterInsert) sendPending(finished) else insertPending(finished)
                }
            } catch (error: Throwable) {
                toast(error.message ?: "Could not render that message.")
            } finally {
                rendering = false
                keyboardView?.setBusy(false)
            }
        }
    }

    private fun insertPending(current: PendingRender) {
        val connection = currentInputConnection
        if (connection == null) {
            toast("This field cannot receive an image.")
            return
        }
        val uri = runCatching { container.imageStore.uriFor(current.file) }.getOrElse {
            toast("This image is no longer available.")
            return
        }
        val inserted = insertImage(connection, uri)
        if (inserted) {
            container.appScope.launch {
                runCatching { container.messageRepository.markInserted(current.id) }
            }
            deleteFieldText(connection)
            pending = null
            keyboardView?.showInserted()
            toast("Image inserted.")
            return
        }

        val copied = runCatching {
            container.imageStore.copyImageToClipboard(current.file)
        }.isSuccess
        if (copied) {
            container.appScope.launch {
                runCatching {
                    container.messageRepository.markStatus(current.id, MessageStatus.COPIED)
                }
            }
            pending = null
            keyboardView?.clearPreview()
            toast("Image copied to clipboard — paste it into this field.")
        } else {
            toast("This app cannot insert the image. Try copying it instead.")
        }
    }

    private fun insertImage(connection: InputConnection, uri: Uri): Boolean {
        val editorInfo = currentInputEditorInfo ?: return false
        val mimeTypes = editorInfo.contentMimeTypes
        if (!mimeTypes.isNullOrEmpty() &&
            mimeTypes.none { it.startsWith("image/") || it == "*/*" }
        ) {
            return false
        }
        editorInfo.packageName?.let { packageName ->
            runCatching {
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        val info = InputContentInfo(
            uri,
            ClipDescription("TextPic image", arrayOf("image/png")),
        )
        return runCatching {
            connection.commitContent(info, 0, Bundle())
        }.getOrDefault(false)
    }

    private fun fieldText(): String {
        val connection = currentInputConnection ?: return ""
        val limit = MAX_TEXT_LENGTH + 1
        val before = connection.getTextBeforeCursor(limit, 0)?.toString().orEmpty()
        val after = connection.getTextAfterCursor(limit, 0)?.toString().orEmpty()
        return before + after
    }

    private fun deleteFieldText(connection: InputConnection) {
        runCatching {
            val selection = connection.getSelectedText(0)?.toString().orEmpty()
            if (selection.isNotEmpty()) {
                connection.commitText("", 1)
            }
            val before = connection.getTextBeforeCursor(MAX_TEXT_LENGTH, 0)?.length ?: 0
            val after = connection.getTextAfterCursor(MAX_TEXT_LENGTH, 0)?.length ?: 0
            if (before > 0 || after > 0) {
                connection.deleteSurroundingText(before, after)
            }
        }
    }

    private fun discardPending() {
        val current = pending ?: return
        pending = null
        keyboardView?.clearPreview()
        container.appScope.launch {
            runCatching { container.messageRepository.deleteById(current.id) }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
