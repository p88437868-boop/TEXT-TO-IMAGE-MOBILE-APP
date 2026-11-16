package com.textpic.studio.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.textpic.core.RenderMeta
import com.textpic.studio.data.db.MessageDao
import com.textpic.studio.data.db.MessageEntity
import com.textpic.studio.data.db.MessageStatus
import com.textpic.studio.data.prefs.RenderSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MessageRepository(
    private val dao: MessageDao,
    private val imageStore: ImageStore,
) {
    fun paged(query: String, status: String): Flow<PagingData<MessageEntity>> =
        Pager(
            config = PagingConfig(
                pageSize = 30,
                prefetchDistance = 20,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { dao.pagingSource(query, status) },
        ).flow

    suspend fun record(
        text: String?,
        chars: Int,
        settings: RenderSettings,
        meta: RenderMeta,
        imagePath: String?,
        status: String,
        createdAt: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        MessageEntity(
            createdAt = createdAt,
            text = text,
            styleKey = settings.styleKey,
            theme = settings.theme,
            font = settings.font,
            fontSize = settings.fontSize,
            maxWidth = settings.maxWidth,
            alignment = settings.alignment,
            fillWidth = settings.fillWidth,
            transparent = settings.transparent,
            scale = settings.scale,
            paddingX = settings.paddingX,
            paddingY = settings.paddingY,
            thumbnailFit = settings.thumbnailFit,
            widthPx = meta.widthPx,
            heightPx = meta.heightPx,
            chars = chars,
            status = status,
            imagePath = imagePath,
        ),
    )

    suspend fun markStatus(id: Long, status: String) {
        val message = dao.getById(id) ?: return
        dao.update(
            message.copy(
                status = status,
                insertedAt = if (status == MessageStatus.INSERTED) {
                    System.currentTimeMillis()
                } else {
                    message.insertedAt
                },
            ),
        )
    }

    suspend fun markInserted(id: Long) = markStatus(id, MessageStatus.INSERTED)

    suspend fun togglePin(message: MessageEntity) {
        dao.update(message.copy(pinned = !message.pinned))
    }

    suspend fun delete(message: MessageEntity) {
        dao.delete(message)
        imageStore.deleteFile(message.imagePath)
    }

    suspend fun deleteById(id: Long) {
        val message = dao.getById(id) ?: return
        dao.delete(message)
        imageStore.deleteFile(message.imagePath)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        imageStore.deleteAllImages()
    }

    suspend fun replaceImage(message: MessageEntity, file: File, width: Int, height: Int) {
        imageStore.deleteFile(message.imagePath)
        dao.update(message.copy(imagePath = file.absolutePath, widthPx = width, heightPx = height))
    }

    suspend fun purgeOlderThan(cutoff: Long) {
        val old = dao.getOlderThan(cutoff)
        dao.deleteOlderThan(cutoff)
        withContext(Dispatchers.IO) {
            old.forEach { imageStore.deleteFile(it.imagePath) }
        }
    }

    suspend fun allMessages(): List<MessageEntity> = dao.getAll()
}

