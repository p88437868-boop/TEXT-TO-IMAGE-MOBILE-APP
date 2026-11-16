package com.textpic.license

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Status word the server puts in a signed reply. */
enum class ReplyStatus {
    ACTIVE,
    EXPIRED,
    BLOCKED,
    CANCELLED,
    WRONG_DEVICE,
    INVALID_KEY,
    ;

    companion object {
        fun fromWire(value: String?): ReplyStatus? = when (value) {
            "ACTIVE" -> ACTIVE
            "EXPIRED" -> EXPIRED
            "BLOCKED" -> BLOCKED
            "CANCELLED" -> CANCELLED
            "WRONG_DEVICE" -> WRONG_DEVICE
            "INVALID_KEY" -> INVALID_KEY
            else -> null
        }
    }
}

/** A license reply after its signature and nonce were checked. */
data class ServerReply(
    val status: ReplyStatus,
    val name: String?,
    val expiresAt: Long?,
    val activatedAt: Long?,
    val serverTime: Long,
    val nonce: String,
    val quota: QuotaInfo?,
    val reportedId: String?,
) {
    val state: LicenseState
        get() = when (status) {
            ReplyStatus.ACTIVE -> LicenseState.LICENSED
            ReplyStatus.EXPIRED -> LicenseState.EXPIRED
            ReplyStatus.BLOCKED -> LicenseState.BLOCKED
            ReplyStatus.CANCELLED -> LicenseState.CANCELLED
            ReplyStatus.WRONG_DEVICE -> LicenseState.WRONG_DEVICE
            ReplyStatus.INVALID_KEY -> LicenseState.INVALID_KEY
        }

    val isLifetime: Boolean get() = expiresAt == null
}

sealed interface ReplyVerification {
    data class Verified(val reply: ServerReply) : ReplyVerification
    data object SignatureInvalid : ReplyVerification
    data object Malformed : ReplyVerification
    data object NonceMismatch : ReplyVerification
}

/**
 * Checks a `{payload, sig}` reply from the license server.
 *
 * The signature is ECDSA P-256 / SHA-256 in DER form over the decoded payload
 * bytes, made with the private key that only the server holds. Because the
 * payload must also echo the nonce we sent, an old reply cannot be replayed and a
 * fake server cannot unlock the app: it cannot produce a valid signature.
 */
object SignedReplyVerifier {
    fun verify(
        payloadBase64Url: String,
        signatureBase64Url: String,
        publicKeyBase64: String,
        expectedNonce: String,
    ): ReplyVerification {
        val payloadBytes = Base64Codec.decodeUrl(payloadBase64Url)
            ?: return ReplyVerification.Malformed
        val signatureBytes = Base64Codec.decodeUrl(signatureBase64Url)
            ?: return ReplyVerification.Malformed
        val keyBytes = Base64Codec.decode(publicKeyBase64)
            ?: return ReplyVerification.Malformed

        val signatureValid = runCatching {
            val key: PublicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(payloadBytes)
            verifier.verify(signatureBytes)
        }.getOrDefault(false)
        if (!signatureValid) return ReplyVerification.SignatureInvalid

        val reply = parseReply(payloadBytes) ?: return ReplyVerification.Malformed
        if (reply.nonce != expectedNonce) return ReplyVerification.NonceMismatch
        return ReplyVerification.Verified(reply)
    }

    fun parseReply(payloadBytes: ByteArray): ServerReply? {
        val root = Json.parse(String(payloadBytes, Charsets.UTF_8)) ?: return null
        val status = ReplyStatus.fromWire(root["status"]?.str()) ?: return null
        val nonce = root["nonce"]?.str() ?: return null
        val serverTime = root["serverTime"]?.long() ?: return null
        return ServerReply(
            status = status,
            name = root["name"]?.str(),
            expiresAt = root["expiresAt"]?.long(),
            activatedAt = root["activatedAt"]?.long(),
            serverTime = serverTime,
            nonce = nonce,
            quota = parseQuota(root["quota"]),
            reportedId = root["reportedId"]?.str(),
        )
    }

    private fun parseQuota(value: JsonValue?): QuotaInfo? {
        val quota = value?.obj() ?: return null
        return QuotaInfo(
            mode = QuotaMode.fromWire(quota["mode"]?.str()),
            limit = quota["limit"]?.int(),
            usedToday = quota["usedToday"]?.int() ?: 0,
            usedTotal = quota["usedTotal"]?.int() ?: 0,
            remaining = quota["remaining"]?.int(),
            dayEndsAt = quota["dayEndsAt"]?.long(),
        )
    }
}
