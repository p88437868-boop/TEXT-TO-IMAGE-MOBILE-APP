package com.textpic.license

import java.security.SecureRandom

/**
 * The only fields the app is allowed to send to the license server.
 *
 * A unit test parses the generated body and fails if anything else appears, so
 * typed text, clipboard contents, history, images or contacts can never leak into
 * a license request.
 */
object LicenseApiContract {
    const val PATH = "/v1/verify"

    val REQUEST_FIELDS: Set<String> = setOf(
        "key",
        "deviceCode",
        "deviceModel",
        "androidVersion",
        "appVersion",
        "nonce",
        "usageCount",
        "reportId",
    )

    fun buildRequestBody(
        key: String,
        deviceCode: String,
        deviceModel: String,
        androidVersion: String,
        appVersion: String,
        nonce: String,
        usageCount: Int,
        reportId: String?,
    ): String = buildString {
        append('{')
        appendField("key", key)
        append(',')
        appendField("deviceCode", deviceCode)
        append(',')
        appendField("deviceModel", deviceModel)
        append(',')
        appendField("androidVersion", androidVersion)
        append(',')
        appendField("appVersion", appVersion)
        append(',')
        appendField("nonce", nonce)
        append(',')
        append("\"usageCount\":").append(usageCount.coerceIn(0, 10_000))
        if (reportId != null) {
            append(',')
            appendField("reportId", reportId)
        }
        append('}')
    }

    fun newNonce(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun newReportId(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(12)
        random.nextBytes(bytes)
        return "r-" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        append('"').append(name).append("\":\"").append(escape(value)).append('"')
    }
}
