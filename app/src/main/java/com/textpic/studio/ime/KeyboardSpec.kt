package com.textpic.studio.ime

import android.content.Context
import android.text.InputType
import com.textpic.license.Json
import com.textpic.license.JsonValue

/**
 * The keyboard pages, laid out like the Gboard screenshots.
 *
 * Everything here is pure: the JSON is parsed with the small reader in `:license`,
 * and geometry is plain arithmetic, so the layout, the touch targets and the
 * navigation can be unit tested without a phone.
 */

enum class KeyboardPageId {
    LETTERS,
    SYMBOLS1,
    SYMBOLS2,
    NUMPAD,
    ;

    companion object {
        fun fromWire(value: String?): KeyboardPageId? = when (value) {
            "letters" -> LETTERS
            "symbols1" -> SYMBOLS1
            "symbols2" -> SYMBOLS2
            "numpad" -> NUMPAD
            else -> null
        }
    }
}

enum class KeyKind {
    CHAR,
    SHIFT,
    BACKSPACE,
    SPACE,
    ACTION,
    MODE,
    GLOBE,
    COMMA,
    PERIOD,
    OPERATORS,
}

enum class KeyStyle {
    NORMAL,
    SPECIAL,
    PILL,
    ACCENT,
}

/** A key, or an empty slice of a row (spacing / the tall numpad column). */
data class KeyCell(
    val key: KeyboardKey? = null,
    val weight: Float = 1f,
    val isSpacer: Boolean = false,
    /** True for the continuation rows of a vertically merged key. */
    val spanned: Boolean = false,
)

data class KeyboardKey(
    val kind: KeyKind,
    val label: String,
    val output: String,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    /** Small grey character in the corner (digits on the letter row). */
    val hint: String? = null,
    /** Long-press characters, in order. */
    val alternates: String = "",
    /** Target page for [KeyKind.MODE]. */
    val target: KeyboardPageId? = null,
    /** Two short lines instead of one label (`12` over `34`). */
    val stackedLabel: Pair<String, String>? = null,
    val spanRows: Int = 1,
)

data class KeyboardPage(
    val id: KeyboardPageId,
    val rows: List<List<KeyCell>>,
) {
    fun rowUnits(row: List<KeyCell>): Float = row.sumOf { it.weight.toDouble() }.toFloat()

    /**
     * Row widths must roughly agree, otherwise the columns would not line up.
     * A row may be up to half a key wider (the shift row carries 95 px pills),
     * so the tolerance is in key units, not pixels.
     */
    fun isConsistent(tolerance: Float = 0.5f): Boolean {
        val first = rows.firstOrNull()?.let(::rowUnits) ?: return true
        return rows.all { kotlin.math.abs(rowUnits(it) - first) <= tolerance }
    }
}

object KeyboardSpecs {
    const val ASSET_DIR = "keyboard"

    val all: List<KeyboardPageId> = KeyboardPageId.entries

    fun load(context: Context, id: KeyboardPageId): KeyboardPage {
        val raw = context.assets.open("$ASSET_DIR/${assetName(id)}")
            .bufferedReader()
            .use { it.readText() }
        return parse(raw, id)
    }

    fun assetName(id: KeyboardPageId): String = when (id) {
        KeyboardPageId.LETTERS -> "letters.json"
        KeyboardPageId.SYMBOLS1 -> "symbols1.json"
        KeyboardPageId.SYMBOLS2 -> "symbols2.json"
        KeyboardPageId.NUMPAD -> "numpad.json"
    }

    /** Parses one page asset. Throws [IllegalArgumentException] on a malformed file. */
    fun parse(json: String, expected: KeyboardPageId): KeyboardPage {
        val root = Json.parse(json) ?: throw IllegalArgumentException("not JSON")
        val id = KeyboardPageId.fromWire(root["page"]?.str()) ?: expected
        val rowsJson = root["rows"] ?: throw IllegalArgumentException("no rows")
        val rows = rowsJson.let { value -> (value as? JsonValue.Arr)?.items ?: emptyList() }
            .map { rowValue ->
                val cells = (rowValue as? JsonValue.Arr)?.items ?: emptyList()
                cells.map(::parseCell)
            }
        return KeyboardPage(id, rows)
    }

    private fun parseCell(value: JsonValue): KeyCell {
        if (value["char"] == null && value["kind"] == null) {
            val pad = value["pad"] ?: value["weight"]
            val weight = pad?.let { (it as? JsonValue.Num)?.value?.toFloat() } ?: 1f
            val spanned = value["spanned"]?.let { (it as? JsonValue.Bool)?.value } ?: false
            return KeyCell(weight = weight, isSpacer = !spanned, spanned = spanned)
        }
        val kind = when (val wire = value["kind"]?.str()) {
            null -> KeyKind.CHAR
            "shift" -> KeyKind.SHIFT
            "backspace" -> KeyKind.BACKSPACE
            "space" -> KeyKind.SPACE
            "action" -> KeyKind.ACTION
            "mode" -> KeyKind.MODE
            "globe" -> KeyKind.GLOBE
            "comma" -> KeyKind.COMMA
            "period" -> KeyKind.PERIOD
            "operators" -> KeyKind.OPERATORS
            else -> throw IllegalArgumentException("unknown key kind: $wire")
        }
        val char = value["char"]?.str()
        val label = value["label"]?.str() ?: char ?: ""
        val weight = value["weight"]?.let { (it as? JsonValue.Num)?.value?.toFloat() } ?: 1f
        val style = when (value["style"]?.str()) {
            "special" -> KeyStyle.SPECIAL
            "pill" -> KeyStyle.PILL
            "accent" -> KeyStyle.ACCENT
            else -> KeyStyle.NORMAL
        }
        val stacked = value["stacked"]?.let { stackedValue ->
            val parts = (stackedValue as? JsonValue.Arr)?.items?.mapNotNull { it.str() }.orEmpty()
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        return KeyCell(
            key = KeyboardKey(
                kind = kind,
                label = label,
                output = value["output"]?.str() ?: char ?: "",
                weight = weight,
                style = style,
                hint = value["hint"]?.str(),
                alternates = value["alt"]?.str().orEmpty(),
                target = KeyboardPageId.fromWire(value["page"]?.str()),
                stackedLabel = stacked,
                spanRows = value["span"]?.let { (it as? JsonValue.Num)?.value?.toInt() } ?: 1,
            ),
            weight = weight,
        )
    }

    /**
     * The `?123`, `=\<`, `1234`, `!?#`, `ABC` transitions, exactly as the
     * screenshots show them. A test checks the assets agree with this graph.
     */
    private val navigation: Map<Pair<KeyboardPageId, String>, KeyboardPageId> = mapOf(
        (KeyboardPageId.LETTERS to "?123") to KeyboardPageId.SYMBOLS1,
        (KeyboardPageId.SYMBOLS1 to "=<") to KeyboardPageId.SYMBOLS2,
        (KeyboardPageId.SYMBOLS1 to "ABC") to KeyboardPageId.LETTERS,
        (KeyboardPageId.SYMBOLS1 to "1234") to KeyboardPageId.NUMPAD,
        (KeyboardPageId.SYMBOLS2 to "?123") to KeyboardPageId.SYMBOLS1,
        (KeyboardPageId.SYMBOLS2 to "ABC") to KeyboardPageId.LETTERS,
        (KeyboardPageId.SYMBOLS2 to "1234") to KeyboardPageId.NUMPAD,
        (KeyboardPageId.NUMPAD to "ABC") to KeyboardPageId.LETTERS,
        (KeyboardPageId.NUMPAD to "!?#") to KeyboardPageId.SYMBOLS1,
    )

    fun nextPage(current: KeyboardPageId, label: String): KeyboardPageId? =
        navigation[current to label]

    fun navigationGraph(): Map<Pair<KeyboardPageId, String>, KeyboardPageId> = navigation
}

/** What kind of field the keyboard is attached to. */
enum class FieldKind {
    TEXT,
    NUMBER,
    PHONE,
    EMAIL,
    URL,
    PASSWORD,
    ;

    val usesNumpad: Boolean get() = this == NUMBER || this == PHONE
    val suppressesPersonalisation: Boolean get() = this == PASSWORD
}

object FieldKinds {
    fun of(inputType: Int): FieldKind {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_PHONE) return FieldKind.PHONE
        if (inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_DATETIME
        ) {
            return FieldKind.NUMBER
        }
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            return when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> FieldKind.PASSWORD
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                else -> FieldKind.TEXT
            }
        }
        return FieldKind.TEXT
    }

    /** Which page to open for a field. */
    fun startPage(kind: FieldKind): KeyboardPageId =
        if (kind.usesNumpad) KeyboardPageId.NUMPAD else KeyboardPageId.LETTERS
}

enum class ShiftState { OFF, ONCE, LOCKED }

object ShiftLogic {
    val isUpper: (ShiftState) -> Boolean = { it != ShiftState.OFF }

    /** Tap: off → once → locked → off. */
    fun afterTap(current: ShiftState): ShiftState = when (current) {
        ShiftState.OFF -> ShiftState.ONCE
        ShiftState.ONCE -> ShiftState.LOCKED
        ShiftState.LOCKED -> ShiftState.OFF
    }

    /** After a letter is typed: "once" is used up, "locked" stays. */
    fun afterChar(current: ShiftState): ShiftState =
        if (current == ShiftState.ONCE) ShiftState.OFF else current

    /** Sentence start, from `getCursorCapsMode`. */
    fun fromCapsMode(capsMode: Int, current: ShiftState): ShiftState = when {
        capsMode != 0 && current == ShiftState.OFF -> ShiftState.ONCE
        capsMode == 0 && current == ShiftState.ONCE -> ShiftState.OFF
        else -> current
    }
}

/**
 * Behaviour rules that must match the screenshots and the prompt. Pure, so the
 * keyboard and the tests agree.
 */
object KeyboardRules {
    /** Double space inserts ". " instead of a second space. */
    fun doubleSpacePeriod(previousChar: Char?, now: Long, lastSpaceAt: Long): Boolean =
        previousChar == ' ' && now - lastSpaceAt <= DOUBLE_SPACE_MS

    const val DOUBLE_SPACE_MS = 500L

    /** Nothing is learned or remembered in these fields. */
    fun allowsHistory(field: FieldKind, inputType: Int): Boolean =
        field != FieldKind.PASSWORD &&
            (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0

    /** "37 images left today" style counter sits in the preview strip. */
    fun shiftLabel(state: ShiftState): String = when (state) {
        ShiftState.OFF -> "⇧"
        ShiftState.ONCE -> "⇧"
        ShiftState.LOCKED -> "⇪"
    }

    /** The alternates to show on long-press, hint first for letters. */
    fun alternates(key: KeyboardKey): List<String> {
        if (key.kind != KeyKind.CHAR) return key.alternates.map { it.toString() }
        if (key.alternates.isEmpty()) return emptyList()
        val base = key.alternates.map { it.toString() }
        val withHint = key.hint?.let { hint -> listOf(hint) + base } ?: base
        return withHint.distinct()
    }
}
