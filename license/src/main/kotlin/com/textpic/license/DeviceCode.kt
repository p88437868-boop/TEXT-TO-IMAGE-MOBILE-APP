package com.textpic.license

/**
 * This install's Device Code: `TP-XXXX-XXXX-XXXX-XXXX`.
 *
 * The 10 raw bytes are the first 10 bytes of `SHA-256(ANDROID_ID + salt)`, shown
 * as Base32 with an alphabet that leaves out `0`, `1`, `I` and `O` so it can be
 * read over the phone.
 */
object DeviceCode {
    const val PREFIX = "TP"
    const val BYTES = 10
    const val TEXT_LENGTH = 16
    const val GROUP_SIZE = 4

    /** Base32 text of the raw code bytes, without the prefix and dashes. */
    fun encode(codeBytes: ByteArray): String = Base32.encode(codeBytes)

    /** `TP-XXXX-XXXX-XXXX-XXXX`. */
    fun format(codeBytes: ByteArray): String = PREFIX + "-" + encode(codeBytes).chunked(GROUP_SIZE).joinToString("-")

    /**
     * Accepts `TP-XXXX-...`, lowercase, with or without dashes and spaces.
     * Returns the normalized 16 character code, or null when it is incomplete.
     */
    fun normalize(input: String): String? {
        val cleaned = input
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .removePrefix(PREFIX)
        if (cleaned.length != TEXT_LENGTH) return null
        if (!Base32.isWellFormed(cleaned)) return null
        return cleaned
    }

    fun toBytes(codeText: String): ByteArray? = normalize(codeText)?.let(Base32::decode)
}
