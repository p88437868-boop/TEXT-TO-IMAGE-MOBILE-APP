package com.textpic.studio.ime

import android.text.InputType
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The four pages, their geometry and the navigation between them, checked against
 * the real assets in `app/src/main/assets/keyboard`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSpecTest {
    private fun load(id: KeyboardPageId): KeyboardPage {
        val file = File("src/main/assets/keyboard/${KeyboardSpecs.assetName(id)}")
        assertThat(file.isFile).isTrue()
        return KeyboardSpecs.parse(file.readText(), id)
    }

    private fun keys(page: KeyboardPage) = page.rows.flatten().mapNotNull { it.key }

    private fun labels(page: KeyboardPage) = keys(page).map { it.label }

    @Test
    fun `every page parses and its rows line up`() {
        for (id in KeyboardPageId.entries) {
            val page = load(id)
            assertThat(page.id).isEqualTo(id)
            assertThat(page.rows).hasSize(4)
            assertThat(page.isConsistent()).isTrue()
        }
    }

    @Test
    fun `letters match the screenshot`() {
        val page = load(KeyboardPageId.LETTERS)
        val firstRow = page.rows[0].mapNotNull { it.key }
        assertThat(firstRow.map { it.label })
            .containsExactly("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").inOrder()
        assertThat(firstRow.map { it.hint })
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").inOrder()
        assertThat(page.rows[1].mapNotNull { it.key }.map { it.label })
            .containsExactly("a", "s", "d", "f", "g", "h", "j", "k", "l").inOrder()
        assertThat(page.rows[2].mapNotNull { it.key }.map { it.label })
            .containsExactly("", "z", "x", "c", "v", "b", "n", "m", "").inOrder()
        val lastRow = page.rows[3].mapNotNull { it.key }
        assertThat(lastRow.map { it.kind }).containsExactly(
            KeyKind.MODE,
            KeyKind.COMMA,
            KeyKind.GLOBE,
            KeyKind.SPACE,
            KeyKind.PERIOD,
            KeyKind.ACTION,
        ).inOrder()
        // ?123 is a pill, the space bar is labelled English, the shift sits on row 3.
        assertThat(lastRow.first().style).isEqualTo(KeyStyle.PILL)
        assertThat(lastRow[3].label).isEqualTo("English")
        assertThat(page.rows[2].mapNotNull { it.key }.first().kind).isEqualTo(KeyKind.SHIFT)
        assertThat(page.rows[2].mapNotNull { it.key }.last().kind).isEqualTo(KeyKind.BACKSPACE)
    }

    @Test
    fun `symbols page one matches the screenshot`() {
        val page = load(KeyboardPageId.SYMBOLS1)
        assertThat(page.rows[0].mapNotNull { it.key }.map { it.label })
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").inOrder()
        assertThat(page.rows[1].mapNotNull { it.key }.map { it.label })
            .containsExactly("@", "#", "£", "_", "&", "-", "+", "(", ")", "/").inOrder()
        assertThat(page.rows[2].mapNotNull { it.key }.map { it.label })
            .containsExactly("=<", "*", "\"", "'", ":", ";", "!", "?", "").inOrder()
        val lastRow = page.rows[3].mapNotNull { it.key }
        assertThat(lastRow.map { it.label })
            .containsExactly("ABC", ",", "1234", "English", ".", "").inOrder()
        assertThat(lastRow[2].stackedLabel).isEqualTo("12" to "34")
    }

    @Test
    fun `symbols page two matches the screenshot`() {
        val page = load(KeyboardPageId.SYMBOLS2)
        assertThat(page.rows[0].mapNotNull { it.key }.map { it.label })
            .containsExactly("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ").inOrder()
        assertThat(page.rows[1].mapNotNull { it.key }.map { it.label })
            .containsExactly("€", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\").inOrder()
        assertThat(page.rows[2].mapNotNull { it.key }.map { it.label })
            .containsExactly("?123", "%", "©", "®", "™", "✓", "[", "]", "").inOrder()
        assertThat(page.rows[3].mapNotNull { it.key }.map { it.label })
            .containsExactly("ABC", "<", "1234", "English", ">", "").inOrder()
    }

    @Test
    fun `numpad has the operator column, four rows and the stacked 1234`() {
        val page = load(KeyboardPageId.NUMPAD)
        val operators = page.rows[0].mapNotNull { it.key }.first()
        assertThat(operators.kind).isEqualTo(KeyKind.OPERATORS)
        assertThat(operators.spanRows).isEqualTo(3)
        assertThat(operators.label.split("\n")).containsExactly("+", "−", "*", "/").inOrder()
        // The first column of the next two rows is the continuation of that key.
        assertThat(page.rows[1].first().spanned).isTrue()
        assertThat(page.rows[2].first().spanned).isTrue()
        assertThat(page.rows[1].mapNotNull { it.key }.map { it.label })
            .containsExactly("4", "5", "6", "␣").inOrder()
        assertThat(page.rows[2].mapNotNull { it.key }.map { it.label })
            .containsExactly("7", "8", "9", "").inOrder()
        assertThat(page.rows[3].mapNotNull { it.key }.map { it.label })
            .containsExactly("ABC", ",", "!?#", "0", "=", ".", "").inOrder()
    }

    @Test
    fun `navigation follows the screenshots both ways`() {
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.LETTERS, "?123"))
            .isEqualTo(KeyboardPageId.SYMBOLS1)
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.SYMBOLS1, "=<"))
            .isEqualTo(KeyboardPageId.SYMBOLS2)
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.SYMBOLS2, "?123"))
            .isEqualTo(KeyboardPageId.SYMBOLS1)
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.SYMBOLS1, "1234"))
            .isEqualTo(KeyboardPageId.NUMPAD)
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.SYMBOLS2, "1234"))
            .isEqualTo(KeyboardPageId.NUMPAD)
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.NUMPAD, "!?#"))
            .isEqualTo(KeyboardPageId.SYMBOLS1)
        // Every page except the letters themselves has a way back to them.
        for (id in KeyboardPageId.entries - KeyboardPageId.LETTERS) {
            assertThat(KeyboardSpecs.nextPage(id, "ABC")).isEqualTo(KeyboardPageId.LETTERS)
        }
        assertThat(KeyboardSpecs.nextPage(KeyboardPageId.LETTERS, "ABC")).isNull()
    }

    @Test
    fun `the mode keys in the assets point where the navigation graph says`() {
        for (id in KeyboardPageId.entries) {
            val page = load(id)
            for (key in keys(page).filter { it.kind == KeyKind.MODE }) {
                assertThat(key.target).isNotNull()
                assertThat(KeyboardSpecs.nextPage(id, key.label)).isEqualTo(key.target)
            }
        }
    }

    @Test
    fun `geometry has no overlaps and no dead gaps`() {
        val density = 2.75f
        val width = 1080f
        for (id in KeyboardPageId.entries) {
            val page = load(id)
            val height = 52f * density * 4 + 6f * density * 3
            assertThat(KeyboardGeometry.problems(page, width, height, density)).isEmpty()
            val keys = KeyboardGeometry.layout(page, width, height, density)
            assertThat(keys).isNotEmpty()
            // Every row's touch zones together cover the full width.
            for (row in page.rows.indices) {
                // A merged key (the numpad operator column) covers several rows.
                val inRow = keys
                    .filter { row >= it.row && row < it.row + it.key.spanRows }
                    .sortedBy { it.touch.left }
                if (inRow.isEmpty()) continue
                assertThat(inRow.first().touch.left).isWithin(0.5f).of(0f)
                assertThat(inRow.last().touch.right).isWithin(0.5f).of(width)
                for (index in 1 until inRow.size) {
                    assertThat(inRow[index].touch.left)
                        .isWithin(0.5f).of(inRow[index - 1].touch.right)
                }
            }
        }
    }

    @Test
    fun `touching a gap still hits a key`() {
        val page = load(KeyboardPageId.LETTERS)
        val density = 2f
        val keys = KeyboardGeometry.layout(page, 1000f, 440f, density)
        // The 4dp gap between q and w is covered by one of the two touch zones.
        val q = keys.first { it.key.label == "q" }
        val midGap = (q.visual.right + keys.first { it.key.label == "w" }.visual.left) / 2f
        assertThat(KeyboardGeometry.keyAt(keys, midGap, q.visual.centerY())).isNotNull()
    }

    @Test
    fun `shift behaves like a real keyboard`() {
        var state = ShiftState.OFF
        state = ShiftLogic.afterTap(state)
        assertThat(state).isEqualTo(ShiftState.ONCE)
        assertThat(ShiftLogic.isUpper(state)).isTrue()
        state = ShiftLogic.afterChar(state)
        assertThat(state).isEqualTo(ShiftState.OFF)

        state = ShiftLogic.afterTap(ShiftLogic.afterTap(ShiftState.OFF))
        assertThat(state).isEqualTo(ShiftState.LOCKED)
        assertThat(ShiftLogic.afterChar(state)).isEqualTo(ShiftState.LOCKED)
        assertThat(ShiftLogic.afterTap(state)).isEqualTo(ShiftState.OFF)

        assertThat(ShiftLogic.fromCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, ShiftState.OFF))
            .isEqualTo(ShiftState.ONCE)
        assertThat(ShiftLogic.fromCapsMode(0, ShiftState.ONCE)).isEqualTo(ShiftState.OFF)
    }

    @Test
    fun `double space inserts a period but only quickly`() {
        assertThat(KeyboardRules.doubleSpacePeriod(' ', now = 10_000, lastSpaceAt = 9_800)).isTrue()
        assertThat(KeyboardRules.doubleSpacePeriod(' ', now = 10_000, lastSpaceAt = 9_000)).isFalse()
        assertThat(KeyboardRules.doubleSpacePeriod('a', now = 10_000, lastSpaceAt = 9_800)).isFalse()
        assertThat(KeyboardRules.doubleSpacePeriod(null, now = 10_000, lastSpaceAt = 0)).isFalse()
    }

    @Test
    fun `input types pick the right page`() {
        assertThat(FieldKinds.of(InputType.TYPE_CLASS_TEXT)).isEqualTo(FieldKind.TEXT)
        assertThat(FieldKinds.of(InputType.TYPE_CLASS_NUMBER)).isEqualTo(FieldKind.NUMBER)
        assertThat(FieldKinds.of(InputType.TYPE_CLASS_PHONE)).isEqualTo(FieldKind.PHONE)
        assertThat(FieldKinds.of(InputType.TYPE_CLASS_DATETIME)).isEqualTo(FieldKind.NUMBER)
        assertThat(
            FieldKinds.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
        ).isEqualTo(FieldKind.EMAIL)
        assertThat(
            FieldKinds.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
        ).isEqualTo(FieldKind.URL)
        assertThat(
            FieldKinds.of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
        ).isEqualTo(FieldKind.PASSWORD)

        assertThat(FieldKinds.startPage(FieldKind.NUMBER)).isEqualTo(KeyboardPageId.NUMPAD)
        assertThat(FieldKinds.startPage(FieldKind.PHONE)).isEqualTo(KeyboardPageId.NUMPAD)
        assertThat(FieldKinds.startPage(FieldKind.TEXT)).isEqualTo(KeyboardPageId.LETTERS)
    }

    @Test
    fun `password and no-suggestion fields learn nothing`() {
        assertThat(KeyboardRules.allowsHistory(FieldKind.PASSWORD, InputType.TYPE_CLASS_TEXT))
            .isFalse()
        assertThat(
            KeyboardRules.allowsHistory(
                FieldKind.TEXT,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            ),
        ).isFalse()
        assertThat(KeyboardRules.allowsHistory(FieldKind.TEXT, InputType.TYPE_CLASS_TEXT))
            .isTrue()
    }

    @Test
    fun `long press alternates come from the layout`() {
        val page = load(KeyboardPageId.LETTERS)
        val e = keys(page).first { it.label == "e" }
        // Row one carries the digit hint and nothing else.
        assertThat(KeyboardRules.alternates(e)).containsExactly("3")

        val a = keys(page).first { it.label == "a" }
        assertThat(KeyboardRules.alternates(a)).isEqualTo("àáâäæãåā".map { it.toString() })

        val q = keys(page).first { it.label == "q" }
        // The digit hint is offered first on the letter row.
        assertThat(KeyboardRules.alternates(q).first()).isEqualTo("1")

        val period = keys(page).first { it.kind == KeyKind.PERIOD }
        assertThat(KeyboardRules.alternates(period)).contains("?")
    }
}
