package com.textpic.studio.ui.studio

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.textpic.core.RenderMeta
import com.textpic.core.RenderResult
import com.textpic.core.RenderRequest
import com.textpic.studio.AppContainer
import com.textpic.studio.data.db.MessageStatus
import com.textpic.studio.data.prefs.AppSettings
import com.textpic.studio.data.prefs.RenderSettings
import com.textpic.studio.domain.LicenseGate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudioPreview(
    val bitmap: Bitmap? = null,
    val meta: RenderMeta? = null,
    val error: String? = null,
    val rendering: Boolean = false,
)

class StudioViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings(),
        )

    private val _preview = MutableStateFlow(StudioPreview())
    val preview: StateFlow<StudioPreview> = _preview.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var renderJob: Job? = null

    private val licensed: Boolean get() = container.licenseGate.isLicensed()

    /** Activation first, then the image allowance. Both come from the domain gate. */
    private fun requireOutput(): Boolean {
        if (container.updateManager.state.value.mandatory) {
            emit("Update TextPic before making new images. Typing and settings still work.")
            return false
        }
        val denial = container.licenseGate.denialMessage()
        if (denial == null) return true
        emit(denial)
        return false
    }

    /** Counts one new image and reports it to the server when due. */
    private suspend fun countRender() {
        runCatching { container.usageGate.recordRender() }
    }

    fun updateRender(transform: (RenderSettings) -> RenderSettings) {
        viewModelScope.launch {
            container.settingsRepository.updateRender(transform)
        }
    }

    fun resetGolden() {
        viewModelScope.launch { container.settingsRepository.resetToGolden() }
    }

    fun renderPreview(text: String) {
        val denial = container.licenseGate.denialMessage()
        if (denial != null) {
            // The live preview is free; only the message explains why output is off.
            _preview.value = StudioPreview(error = denial)
            return
        }
        if (text.isBlank()) {
            _preview.value = StudioPreview()
            return
        }
        if (text.length > 2000) {
            _preview.value = StudioPreview(error = "Message is limited to 2,000 characters.")
            return
        }
        val renderSettings = settings.value.render
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            delay(100)
            _preview.value = _preview.value.copy(rendering = true, error = null)
            val result = withContext(Dispatchers.Default) {
                runCatching { container.renderer.render(renderSettings.toRequest(text)) }
            }
            result.fold(
                onSuccess = { rendered ->
                    _preview.value = StudioPreview(bitmap = rendered.bitmap, meta = rendered.meta)
                },
                onFailure = { error ->
                    _preview.value = StudioPreview(error = error.message ?: "Could not render this message.")
                },
            )
        }
    }

    fun copyImage(text: String) {
        if (!requireOutput()) return
        if (text.isBlank()) {
            emit("Type a message first.")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val app = settings.value
                val rendered = renderNow(text, app.render)
                val permanent = withContext(Dispatchers.IO) { container.imageStore.savePermanent(rendered.bitmap) }
                val cache = withContext(Dispatchers.IO) { container.imageStore.saveShare(rendered.bitmap) }
                withContext(Dispatchers.IO) { container.imageStore.copyImageToClipboard(cache) }
                record(app, text, rendered, permanent, MessageStatus.COPIED)
                countRender()
                emit("Image copied — no text on the clipboard.")
            } catch (error: Throwable) {
                emit(error.message ?: "Could not copy the image.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun shareImage(text: String) {
        if (!requireOutput()) return
        if (text.isBlank()) {
            emit("Type a message first.")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val app = settings.value
                val rendered = renderNow(text, app.render)
                val permanent = withContext(Dispatchers.IO) { container.imageStore.savePermanent(rendered.bitmap) }
                val cache = withContext(Dispatchers.IO) { container.imageStore.saveShare(rendered.bitmap) }
                record(app, text, rendered, permanent, MessageStatus.SHARED)
                countRender()
                withContext(Dispatchers.Main) { container.imageStore.launchShare(cache) }
            } catch (error: Throwable) {
                emit(error.message ?: "Could not share the image.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveToGallery(text: String) {
        if (!requireOutput()) return
        if (text.isBlank()) {
            emit("Type a message first.")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val app = settings.value
                val rendered = renderNow(text, app.render)
                val permanent = withContext(Dispatchers.IO) { container.imageStore.savePermanent(rendered.bitmap) }
                withContext(Dispatchers.IO) { container.imageStore.saveToGallery(rendered.bitmap) }
                record(app, text, rendered, permanent, MessageStatus.RENDERED)
                countRender()
                emit("Saved to Pictures/TextPic.")
            } catch (error: Throwable) {
                emit(error.message ?: "Could not save the image.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveTemplate(title: String, body: String) {
        if (title.isBlank() || body.isBlank()) {
            emit("Add a title and message first.")
            return
        }
        viewModelScope.launch {
            container.templateRepository.add(title.trim(), body)
            emit("Template saved.")
        }
    }

    private suspend fun renderNow(text: String, renderSettings: RenderSettings): RenderResult {
        val request: RenderRequest = renderSettings.toRequest(text)
        return withContext(Dispatchers.Default) { container.renderer.render(request) }
    }

    private suspend fun record(
        app: AppSettings,
        text: String,
        rendered: RenderResult,
        file: File,
        status: String,
    ) {
        container.messageRepository.record(
            text = if (app.saveMessageText) text else null,
            chars = text.length,
            settings = app.render,
            meta = rendered.meta,
            imagePath = file.absolutePath,
            status = status,
        )
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StudioViewModel(container) }
        }
    }
}
