package com.textpic.license

/**
 * Base32 (RFC 4648, no padding) with an alphabet that omits characters people
 * misread when copying a key by hand: `0`, `1`, `I` and `O`.
 */
object Base32 {
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val decodeIndex = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(ALPHABET[(buffer ushr bits) and 0x1F])
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return out.toString()
    }

    /** Returns null when [text] contains a character outside the alphabet. */
    fun decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in text) {
            val code = char.code
            val value = if (code < decodeIndex.size) decodeIndex[code] else -1
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** True when every character of [text] belongs to the alphabet. */
    fun isWellFormed(text: String): Boolean =
        text.isNotEmpty() && text.all { code ->
            code.code < decodeIndex.size && decodeIndex[code.code] >= 0
        }
}
