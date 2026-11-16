package com.textpic.studio.ui.history

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.textpic.studio.AppContainer
import com.textpic.studio.data.db.MessageEntity
import com.textpic.studio.data.db.MessageStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val status = MutableStateFlow("")

    val selectedStatus: StateFlow<String> = status.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages = combine(query.debounce(250), status) { text, filter ->
        text to filter
    }
        .flatMapLatest { (text, filter) ->
            container.messageRepository.paged(text, filter).cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PagingData.empty())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStatus(value: String) {
        status.value = value
    }

    fun copy(message: MessageEntity) = launchAction {
        val path = message.imagePath ?: error("This image is no longer available.")
        check(File(path).isFile) { "This image is no longer available." }
        withContext(Dispatchers.IO) { container.imageStore.copyImageToClipboard(File(path)) }
        container.messageRepository.markStatus(message.id, MessageStatus.COPIED)
        _events.tryEmit("Image copied to the clipboard.")
    }

    fun share(message: MessageEntity) = launchAction {
        val path = message.imagePath ?: error("This image is no longer available.")
        val file = File(path)
        check(file.isFile) { "This image is no longer available." }
        container.messageRepository.markStatus(message.id, MessageStatus.SHARED)
        withContext(Dispatchers.Main) { container.imageStore.launchShare(file) }
    }

    fun save(message: MessageEntity) = launchAction {
        val path = message.imagePath ?: error("This image is no longer available.")
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path) ?: error("This image is no longer available.")
        }
        withContext(Dispatchers.IO) { container.imageStore.saveToGallery(bitmap) }
        _events.tryEmit("Saved to Pictures/TextPic.")
    }

    fun togglePin(message: MessageEntity) = launchAction {
        container.messageRepository.togglePin(message)
    }

    fun delete(message: MessageEntity) = launchAction {
        container.messageRepository.delete(message)
        _events.tryEmit("Message deleted.")
    }

    fun clearAll() = launchAction {
        container.messageRepository.deleteAll()
        _events.tryEmit("History cleared.")
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure {
                _events.tryEmit(it.message ?: "Something went wrong.")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}
