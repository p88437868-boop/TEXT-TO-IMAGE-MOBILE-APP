package com.textpic.studio.ime

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** One remembered text clip. Text only, never an image. */
data class ClipEntry(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val pinned: Boolean = false,
)

/**
 * The clipboard rules from the spec: pinned first, newest first, at most 20
 * clips, unpinned clips older than the timeout disappear, and nothing sensitive
 * is ever written down.
 */
object ClipboardRules {
    const val MAX_CLIPS = 20
    const val MAX_TEXT_LENGTH = 20_000

    fun accepts(
        enabled: Boolean,
        field: FieldKind,
        sensitive: Boolean,
        text: String?,
    ): Boolean {
        if (!enabled) return false
        if (sensitive) return false
        if (field == FieldKind.PASSWORD) return false
        val trimmed = text?.trim().orEmpty()
        return trimmed.isNotEmpty() && trimmed.length <= MAX_TEXT_LENGTH
    }

    /** Newest clip goes to the top; a repeat of an existing text is moved up. */
    fun add(
        existing: List<ClipEntry>,
        text: String,
        now: Long,
        timeoutMinutes: Int,
        nextId: Long,
    ): List<ClipEntry> {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_TEXT_LENGTH) return existing
        val withoutDuplicate = existing.filterNot { it.text == clean }
        val fresh = ClipEntry(id = nextId, text = clean, createdAt = now, pinned = false)
        return order((listOf(fresh) + withoutDuplicate), now, timeoutMinutes)
    }

    fun purge(existing: List<ClipEntry>, now: Long, timeoutMinutes: Int): List<ClipEntry> {
        val timeout = KeyboardTimeout.of(timeoutMinutes)
        if (timeout == null) return existing
        return existing.filter { it.pinned || now - it.createdAt < timeout }
    }

    fun togglePin(existing: List<ClipEntry>, id: Long, now: Long, timeoutMinutes: Int): List<ClipEntry> =
        order(
            existing.map { if (it.id == id) it.copy(pinned = !it.pinned) else it },
            now,
            timeoutMinutes,
        )

    fun remove(existing: List<ClipEntry>, id: Long): List<ClipEntry> = existing.filterNot { it.id == id }

    fun clearUnpinned(existing: List<ClipEntry>): List<ClipEntry> = existing.filter { it.pinned }

    fun clearAll(existing: List<ClipEntry>): List<ClipEntry> = emptyList()

    /** Pinned first, then newest first, capped at [MAX_CLIPS]. */
    fun order(existing: List<ClipEntry>, now: Long, timeoutMinutes: Int): List<ClipEntry> =
        purge(existing, now, timeoutMinutes)
            .sortedWith(compareByDescending<ClipEntry> { it.pinned }.thenByDescending { it.createdAt })
            .take(MAX_CLIPS)

    fun nextId(existing: List<ClipEntry>): Long = (existing.maxOfOrNull { it.id } ?: 0L) + 1L
}

/** `null` means "never expire". */
internal object KeyboardTimeout {
    fun of(minutes: Int): Long? = if (minutes <= 0) null else minutes.toLong() * 60_000L
}

/**
 * The clipboard history, stored as one small JSON file in the app's private
 * directory. No database migration, no network, text only.
 */
class ClipboardStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "keyboard-clips.json")

    fun load(): List<ClipEntry> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val body = item.optString("text")
                if (body.isBlank()) return@mapNotNull null
                ClipEntry(
                    id = item.optLong("id"),
                    text = body,
                    createdAt = item.optLong("createdAt"),
                    pinned = item.optBoolean("pinned"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(entries: List<ClipEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("text", entry.text)
                    .put("createdAt", entry.createdAt)
                    .put("pinned", entry.pinned),
            )
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(array.toString())
        }
    }

    fun update(now: Long, timeoutMinutes: Int, change: (List<ClipEntry>) -> List<ClipEntry>): List<ClipEntry> {
        val next = ClipboardRules.order(change(load()), now, timeoutMinutes)
        save(next)
        return next
    }
}
