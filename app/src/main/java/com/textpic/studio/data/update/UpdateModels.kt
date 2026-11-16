package com.textpic.studio.data.update

import com.textpic.license.Base64Codec
import com.textpic.license.Json
import com.textpic.license.JsonValue
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** One published release, as described by the signed `/v1/latest` manifest. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minVersionCode: Int,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
    val publishedAt: Long,
    val chunkCount: Int,
    val chunkSize: Int,
    val chunkShas: List<String>,
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo, val mandatory: Boolean) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val detail: String) : UpdateCheck
}

/**
 * The update rules, kept pure so they can be tested without a phone: when to
 * check, when to stay quiet, how big a download may be, and how a manifest is
 * parsed and verified.
 */
object UpdateRules {
    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    const val SUPPRESS_MS = 24L * 60 * 60 * 1000
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    const val MAX_CHUNKS = 4096

    fun shouldCheck(lastCheckAt: Long, now: Long): Boolean =
        lastCheckAt <= 0L || now - lastCheckAt >= CHECK_INTERVAL_MS

    fun isSuppressed(suppressedUntil: Long, now: Long): Boolean = suppressedUntil > now

    fun isNewer(currentVersionCode: Int, info: UpdateInfo): Boolean =
        info.versionCode > currentVersionCode

    /** Below `minVersionCode` the app must be updated before it can render. */
    fun isMandatory(currentVersionCode: Int, info: UpdateInfo): Boolean =
        info.versionCode > currentVersionCode && info.minVersionCode > currentVersionCode

    fun chunkPath(info: UpdateInfo, index: Int): String =
        "/v1/download/${info.versionCode}/$index"

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "0 MB"
        bytes < 1024L * 1024L -> "${(bytes / 1024L).coerceAtLeast(1L)} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** Checks the ECDSA P-256 signature with the key compiled into the app. */
    fun verifySignature(payloadBase64Url: String, signatureBase64Url: String, publicKeyBase64: String): Boolean {
        val payload = Base64Codec.decodeUrl(payloadBase64Url) ?: return false
        val signature = Base64Codec.decodeUrl(signatureBase64Url) ?: return false
        val keyBytes = Base64Codec.decode(publicKeyBase64) ?: return false
        return runCatching {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(payload)
                verify(signature)
            }
        }.getOrDefault(false)
    }

    /** The manifest object from a verified payload, or null for `{none:true}`. */
    fun parseManifest(payload: JsonValue?): UpdateInfo? {
        val fields = payload?.obj()?.fields ?: return null
        if (fields["none"]?.boolean() == true) return null
        val versionCode = fields["versionCode"]?.int() ?: return null
        val sha256 = fields["sha256"]?.str()?.lowercase() ?: return null
        val sizeBytes = (fields["sizeBytes"] as? JsonValue.Num)?.value?.toLong() ?: return null
        val chunkCount = fields["chunkCount"]?.int() ?: return null
        val chunkSize = fields["chunkSize"]?.int() ?: 512 * 1024
        val chunkShas = (fields["chunkShas"] as? JsonValue.Arr)?.items
            ?.mapNotNull { it.str() }
            .orEmpty()
        if (sizeBytes <= 0 || sizeBytes > MAX_APK_BYTES) return null
        if (chunkCount <= 0 || chunkCount > MAX_CHUNKS) return null
        if (!sha256.matches(Regex("[0-9a-f]{64}"))) return null
        if (chunkShas.size != chunkCount) return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = fields["versionName"]?.str() ?: versionCode.toString(),
            minVersionCode = fields["minVersionCode"]?.int() ?: 0,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            notes = fields["notes"]?.str().orEmpty(),
            publishedAt = (fields["publishedAt"] as? JsonValue.Num)?.value?.toLong() ?: 0L,
            chunkCount = chunkCount,
            chunkSize = chunkSize,
            chunkShas = chunkShas,
        )
    }

    /** Whole reply → update check, the one function the UI needs. */
    fun interpret(
        payloadBase64Url: String,
        signatureBase64Url: String,
        publicKeyBase64: String,
        currentVersionCode: Int,
    ): UpdateCheck {
        if (!verifySignature(payloadBase64Url, signatureBase64Url, publicKeyBase64)) {
            return UpdateCheck.Failed("The update information could not be verified.")
        }
        val payload = Base64Codec.decodeUrl(payloadBase64Url)?.let {
            Json.parse(String(it, Charsets.UTF_8))
        } ?: return UpdateCheck.Failed("The update information was unreadable.")
        val info = parseManifest(payload)
            ?: return if (payload.obj()?.fields?.get("none")?.boolean() == true) {
                UpdateCheck.UpToDate
            } else {
                UpdateCheck.Failed("The update information was incomplete.")
            }
        return if (isNewer(currentVersionCode, info)) {
            UpdateCheck.Available(info, isMandatory(currentVersionCode, info))
        } else {
            UpdateCheck.UpToDate
        }
    }
}
