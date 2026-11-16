package com.textpic.studio.ime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Clipboard history: pinning, expiry, the 20-clip cap and the exclusions. */
class ClipboardRulesTest {
    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun clips(vararg texts: String): List<ClipEntry> =
        texts.mapIndexed { index, text ->
            ClipEntry(id = index + 1L, text = text, createdAt = now + index, pinned = false)
        }

    @Test
    fun `a new clip goes to the top with a fresh id`() {
        val existing = clips("one", "two")
        val next = ClipboardRules.add(existing, "three", now + 10, 60, ClipboardRules.nextId(existing))
        assertThat(next.first().text).isEqualTo("three")
        assertThat(next.first().id).isEqualTo(3L)
        assertThat(next).hasSize(3)
    }

    @Test
    fun `copying the same text twice moves it up instead of duplicating`() {
        val existing = clips("one", "two", "three")
        val next = ClipboardRules.add(existing, "one", now + 10, 60, 99L)
        assertThat(next).hasSize(3)
        assertThat(next.first().text).isEqualTo("one")
        assertThat(next.count { it.text == "one" }).isEqualTo(1)
    }

    @Test
    fun `at most twenty clips are kept`() {
        var store = emptyList<ClipEntry>()
        repeat(26) { index ->
            store = ClipboardRules.add(store, "clip $index", now + index, 60, index + 1L)
        }
        assertThat(store).hasSize(ClipboardRules.MAX_CLIPS)
        assertThat(store.first().text).isEqualTo("clip 25")
        assertThat(store.none { it.text == "clip 0" }).isTrue()
    }

    @Test
    fun `unpinned clips expire after the timeout but pinned ones stay`() {
        val existing = listOf(
            ClipEntry(1, "old", createdAt = now - 2 * 60 * minute),
            ClipEntry(2, "pinned old", createdAt = now - 5 * 60 * minute, pinned = true),
            ClipEntry(3, "fresh", createdAt = now - minute),
        )
        val next = ClipboardRules.purge(existing, now, 60)
        assertThat(next.map { it.text }).containsExactly("pinned old", "fresh")
    }

    @Test
    fun `a timeout of zero means clips never expire`() {
        val existing = listOf(ClipEntry(1, "old", createdAt = now - 10_000 * minute))
        assertThat(ClipboardRules.purge(existing, now, 0)).hasSize(1)
    }

    @Test
    fun `pinning and unpinning keeps the order stable`() {
        val existing = clips("one", "two")
        val pinned = ClipboardRules.togglePin(existing, id = 2, now = now, timeoutMinutes = 60)
        assertThat(pinned.first().text).isEqualTo("two")
        assertThat(pinned.first().pinned).isTrue()
        val unpinned = ClipboardRules.togglePin(pinned, id = 2, now = now, timeoutMinutes = 60)
        assertThat(unpinned.first().pinned).isFalse()
    }

    @Test
    fun `clearing removes unpinned clips and keeps pinned ones`() {
        val existing = listOf(
            ClipEntry(1, "pinned", createdAt = now, pinned = true),
            ClipEntry(2, "loose", createdAt = now),
        )
        assertThat(ClipboardRules.clearUnpinned(existing).map { it.text }).containsExactly("pinned")
        assertThat(ClipboardRules.clearAll(existing)).isEmpty()
    }

    @Test
    fun `passwords, sensitive clips, blanks and disabled history are never stored`() {
        assertThat(ClipboardRules.accepts(true, FieldKind.TEXT, false, "hello")).isTrue()
        assertThat(ClipboardRules.accepts(true, FieldKind.PASSWORD, false, "hunter2")).isFalse()
        assertThat(ClipboardRules.accepts(true, FieldKind.TEXT, true, "card 4111")).isFalse()
        assertThat(ClipboardRules.accepts(true, FieldKind.TEXT, false, "   ")).isFalse()
        assertThat(ClipboardRules.accepts(true, FieldKind.TEXT, false, null)).isFalse()
        assertThat(ClipboardRules.accepts(false, FieldKind.TEXT, false, "hello")).isFalse()
        assertThat(
            ClipboardRules.accepts(true, FieldKind.TEXT, false, "x".repeat(20_001)),
        ).isFalse()
    }
}
