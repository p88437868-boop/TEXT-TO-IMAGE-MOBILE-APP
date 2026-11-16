package com.textpic.license

/**
 * Base64 and Base64url without padding, written by hand so the code runs on
 * every Android version without `android.util.Base64` or a Java 8 API level
 * requirement.
 */
object Base64Codec {
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val reverse = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
        table['-'.code] = 62
        table['_'.code] = 63
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var index = 0
        while (index + 2 < bytes.size) {
            val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
            out.append(ALPHABET[(chunk ushr 18) and 0x3F])
            out.append(ALPHABET[(chunk ushr 12) and 0x3F])
            out.append(ALPHABET[(chunk ushr 6) and 0x3F])
            out.append(ALPHABET[chunk and 0x3F])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val chunk = (bytes[index].toInt() and 0xFF) shl 16
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
            }
            2 -> {
                val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8)
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
                out.append(ALPHABET[(chunk ushr 6) and 0x3F])
            }
        }
        return out.toString()
    }

    /** Accepts standard and url-safe input, with or without padding. */
    fun decode(text: String): ByteArray? {
        val clean = text.trim().replace("=", "")
        if (clean.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (char in clean) {
            val code = char.code
            val value = if (code < reverse.size) reverse[code] else -1
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun encodeUrl(bytes: ByteArray): String =
        encode(bytes).replace('+', '-').replace('/', '_')

    fun decodeUrl(text: String): ByteArray? = decode(text)
}
