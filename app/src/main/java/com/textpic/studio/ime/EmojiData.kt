package com.textpic.studio.ime

/**
 * The emoji shown in the panel, grouped like Gboard's tabs.
 *
 * Only the characters are stored; the phone's own colour emoji font draws them,
 * so there is no asset to license.
 */
object EmojiData {
    data class Category(val label: String, val emoji: List<String>)

    /** Skin-tone modifiers, appended to the base emoji on long-press. */
    val SKIN_TONES = listOf("\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF")

    val categories: List<Category> = listOf(
        Category(
            "Smileys",
            listOf(
                "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06",
                "\uD83D\uDE05", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE0A", "\uD83D\uDE07",
                "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE1C", "\uD83E\uDD2A", "\uD83D\uDE10",
                "\uD83D\uDE11", "\uD83D\uDE14", "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE24",
                "\uD83D\uDE20", "\uD83E\uDD2C", "\uD83D\uDE31", "\uD83D\uDE28",
            ),
        ),
        Category(
            "People",
            listOf(
                "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4C", "\uD83D\uDC4F", "\uD83D\uDE4C",
                "\uD83D\uDC4B", "\uD83E\uDD1D", "\uD83D\uDE4F", "\uD83D\uDCAA", "\u270C\uFE0F",
                "\uD83E\uDD1E", "\uD83D\uDC46", "\uD83D\uDC47", "\uD83D\uDC48", "\uD83D\uDC49",
                "\uD83D\uDC50", "\uD83E\uDD19", "\uD83D\uDC9A",
            ),
        ),
        Category(
            "Animals",
            listOf(
                "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83E\uDD8A",
                "\uD83D\uDC3B", "\uD83D\uDC3C", "\uD83D\uDC28", "\uD83D\uDC2F", "\uD83E\uDD81",
                "\uD83D\uDC14", "\uD83D\uDC24", "\uD83E\uDD84", "\uD83D\uDC1D", "\uD83D\uDC33",
                "\uD83D\uDC1F", "\uD83E\uDD8B", "\uD83D\uDC0D",
            ),
        ),
        Category(
            "Food",
            listOf(
                "\uD83C\uDF4E", "\uD83C\uDF4C", "\uD83C\uDF47", "\uD83C\uDF53", "\uD83C\uDF6B",
                "\uD83C\uDF6A", "\uD83C\uDF70", "\uD83C\uDF82", "\u2615", "\uD83C\uDF7A",
                "\uD83C\uDF55", "\uD83C\uDF54", "\uD83C\uDF5F", "\uD83C\uDF5C", "\uD83C\uDF73",
                "\uD83E\uDD5A", "\uD83C\uDF7F", "\uD83C\uDF77",
            ),
        ),
        Category(
            "Travel",
            listOf(
                "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE8C", "\uD83D\uDE97", "\u2708\uFE0F",
                "\uD83D\uDE80", "\uD83D\uDE82", "\uD83D\uDEA2", "\u26F5", "\uD83C\uDFD6\uFE0F",
                "\uD83C\uDFD4\uFE0F", "\uD83C\uDFDD\uFE0F", "\uD83C\uDF0D", "\uD83C\uDF06", "\uD83C\uDFD9\uFE0F",
                "\uD83D\uDEA6", "\uD83C\uDFAF", "\uD83C\uDF9F\uFE0F",
            ),
        ),
        Category(
            "Activities",
            listOf(
                "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFC8", "\u26BE", "\uD83C\uDFBE",
                "\uD83C\uDFB1", "\uD83C\uDFAE", "\uD83C\uDFB2", "\uD83C\uDFA8", "\uD83C\uDFB5",
                "\uD83C\uDFB8", "\uD83C\uDFB9", "\uD83C\uDFAC", "\uD83C\uDFC6", "\uD83C\uDFC5",
                "\uD83E\uDD47", "\uD83C\uDF89", "\uD83C\uDF8A",
            ),
        ),
        Category(
            "Objects",
            listOf(
                "\uD83D\uDCF1", "\uD83D\uDCBB", "\u2328\uFE0F", "\uD83D\uDDA5\uFE0F", "\uD83D\uDD0B",
                "\uD83D\uDD0C", "\uD83D\uDCF7", "\uD83C\uDFA7", "\uD83C\uDF9B\uFE0F", "\u23F0",
                "\uD83D\uDCB0", "\uD83D\uDCB3", "\uD83D\uDD11", "\uD83D\uDD12", "\uD83D\uDCA1",
                "\uD83D\uDCDA", "\u270F\uFE0F", "\uD83D\uDCCC",
            ),
        ),
        Category(
            "Symbols",
            listOf(
                "\u2764\uFE0F", "\uD83D\uDC94", "\u2728", "\u2B50", "\uD83D\uDD25",
                "\uD83D\uDCA5", "\u2705", "\u274C", "\u26A0\uFE0F", "\u2757",
                "\u2753", "\uD83D\uDC4C", "\uD83D\uDD14", "\uD83C\uDFB6", "\u2795",
                "\u2796", "\u2714\uFE0F", "\u267B\uFE0F",
            ),
        ),
        Category(
            "Flags",
            listOf(
                "\uD83C\uDDFA\uD83C\uDDF8", "\uD83C\uDDEC\uD83C\uDDE7", "\uD83C\uDDE8\uD83C\uDDE6",
                "\uD83C\uDDE6\uD83C\uDDFA", "\uD83C\uDDE9\uD83C\uDDEA", "\uD83C\uDDEB\uD83C\uDDF7",
                "\uD83C\uDDEE\uD83C\uDDF3", "\uD83C\uDDEF\uD83C\uDDF5", "\uD83C\uDDE7\uD83C\uDDF7",
                "\uD83C\uDDE8\uD83C\uDDF3", "\uD83C\uDDFF\uD83C\uDDE6", "\uD83C\uDDF3\uD83C\uDDEC",
            ),
        ),
    )

    /** Recents are kept in memory for the life of the keyboard process. */
    private val recents = ArrayDeque<String>()

    fun recent(limit: Int = 24): List<String> = recents.take(limit)

    fun noteUsed(emoji: String) {
        recents.remove(emoji)
        recents.addFirst(emoji)
        while (recents.size > 48) recents.removeLast()
    }

    /** Emoji that can carry a skin tone (hands, faces, people). */
    private val tonedBases = setOf(
        "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4C", "\uD83D\uDC4F", "\uD83D\uDE4C",
        "\uD83D\uDCAA", "\u270C\uFE0F", "\uD83E\uDD1D", "\uD83D\uDC4B", "\uD83D\uDC4D",
        "\uD83E\uDD19", "\uD83D\uDC4A", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDC4C",
    )

    fun supportsTone(base: String): Boolean = base in tonedBases

    /**
     * Skin tones go before a trailing variation selector, otherwise the phone
     * draws the plain emoji and drops the tone.
     */
    fun withSkinTone(base: String, tone: String): String =
        if (base.endsWith("\uFE0F")) base.dropLast(1) + tone + "\uFE0F" else base + tone
}
