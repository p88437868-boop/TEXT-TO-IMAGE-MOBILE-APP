package com.textpic.studio.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("createdAt"),
        Index("status"),
        Index("pinned"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val text: String?,
    val styleKey: String,
    val theme: String,
    val font: String,
    val fontSize: Int,
    val maxWidth: Int,
    val alignment: String,
    val fillWidth: Boolean,
    val transparent: Boolean,
    val scale: Float,
    val paddingX: Int,
    val paddingY: Int,
    val thumbnailFit: String,
    val widthPx: Int,
    val heightPx: Int,
    val chars: Int,
    val status: String,
    val insertedAt: Long? = null,
    val pinned: Boolean = false,
    val imagePath: String? = null,
)

object MessageStatus {
    const val RENDERED = "RENDERED"
    const val INSERTED = "INSERTED"
    const val COPIED = "COPIED"
    const val SHARED = "SHARED"
}
