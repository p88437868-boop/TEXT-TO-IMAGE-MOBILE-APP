package com.textpic.license

import com.google.common.truth.Truth.assertThat
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Test

class SignedReplyTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256)
    }.generateKeyPair()

    private fun sign(payload: String): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keys.private)
        signature.update(payload.toByteArray())
        return Base64Codec.encodeUrl(signature.sign())
    }

    private fun payload(nonce: String, status: String = "ACTIVE", extra: String = ""): String =
        """{"status":"$status","name":"Amina Khan","expiresAt":1790000000000,""" +
            """"activatedAt":1780000000000,"serverTime":1780000000000,"nonce":"$nonce",""" +
            """"quota":{"mode":"daily","limit":50,"usedToday":13,"usedTotal":13,"remaining":37,""" +
            """"dayEndsAt":1790000000000},"reportedId":null$extra}"""

    private val publicKey: String = Base64Codec.encode(keys.public.encoded)

    @Test
    fun `a correctly signed reply is accepted`() {
        val nonce = "abc123"
        val body = payload(nonce)
        val result = SignedReplyVerifier.verify(
            payloadBase64Url = Base64Codec.encodeUrl(body.toByteArray()),
            signatureBase64Url = sign(body),
            publicKeyBase64 = publicKey,
            expectedNonce = nonce,
        )
        assertThat(result).isInstanceOf(ReplyVerification.Verified::class.java)
        val reply = (result as ReplyVerification.Verified).reply
        assertThat(reply.status).isEqualTo(ReplyStatus.ACTIVE)
        assertThat(reply.name).isEqualTo("Amina Khan")
        assertThat(reply.expiresAt).isEqualTo(1790000000000L)
        assertThat(reply.quota?.limit).isEqualTo(50)
        assertThat(reply.quota?.mode).isEqualTo(QuotaMode.DAILY)
        assertThat(reply.quota?.remaining).isEqualTo(37)
        assertThat(reply.state).isEqualTo(LicenseState.LICENSED)
    }

    @Test
    fun `an edited payload is rejected`() {
        val nonce = "abc123"
        val body = payload(nonce)
        val signature = sign(body)
        val tampered = body.replace("\"expiresAt\":1790000000000", "\"expiresAt\":2790000000000")
        val result = SignedReplyVerifier.verify(
            Base64Codec.encodeUrl(tampered.toByteArray()),
            signature,
            publicKey,
            nonce,
        )
        assertThat(result).isEqualTo(ReplyVerification.SignatureInvalid)
    }

    @Test
    fun `a reply signed with another key is rejected`() {
        val otherKeys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val body = payload("abc")
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(otherKeys.private)
            update(body.toByteArray())
            Base64Codec.encodeUrl(sign())
        }
        val result = SignedReplyVerifier.verify(
            Base64Codec.encodeUrl(body.toByteArray()),
            signature,
            publicKey,
            "abc",
        )
        assertThat(result).isEqualTo(ReplyVerification.SignatureInvalid)
    }

    @Test
    fun `a replayed reply with another nonce is rejected`() {
        val body = payload("old-nonce")
        val result = SignedReplyVerifier.verify(
            Base64Codec.encodeUrl(body.toByteArray()),
            sign(body),
            publicKey,
            "new-nonce",
        )
        assertThat(result).isEqualTo(ReplyVerification.NonceMismatch)
    }

    @Test
    fun `garbage is rejected without throwing`() {
        assertThat(SignedReplyVerifier.verify("", "", "", "x"))
            .isEqualTo(ReplyVerification.Malformed)
        assertThat(SignedReplyVerifier.verify("!!!", "!!!", "!!!", "x"))
            .isEqualTo(ReplyVerification.Malformed)
        assertThat(SignedReplyVerifier.verify("abc", "abc", publicKey, "abc"))
            .isInstanceOf(ReplyVerification.SignatureInvalid::class.java)
    }

    @Test
    fun `blocked and cancelled replies map to locked states`() {
        for ((wire, state) in listOf(
            "BLOCKED" to LicenseState.BLOCKED,
            "CANCELLED" to LicenseState.CANCELLED,
            "EXPIRED" to LicenseState.EXPIRED,
            "WRONG_DEVICE" to LicenseState.WRONG_DEVICE,
            "INVALID_KEY" to LicenseState.INVALID_KEY,
        )) {
            val body = payload("n", status = wire)
            val result = SignedReplyVerifier.verify(
                Base64Codec.encodeUrl(body.toByteArray()),
                sign(body),
                publicKey,
                "n",
            )
            val reply = (result as ReplyVerification.Verified).reply
            assertThat(reply.state).isEqualTo(state)
            assertThat(reply.state.locksOutput).isTrue()
        }
    }

    @Test
    fun `a lifetime reply has no end date`() {
        val body = """{"status":"ACTIVE","name":"A","expiresAt":null,"activatedAt":1,""" +
            """"serverTime":2,"nonce":"n","quota":{"mode":"unlimited","limit":null,""" +
            """"usedToday":0,"usedTotal":0,"remaining":null,"dayEndsAt":3},"reportedId":"r-1"}"""
        val result = SignedReplyVerifier.verify(
            Base64Codec.encodeUrl(body.toByteArray()),
            sign(body),
            publicKey,
            "n",
        )
        val reply = (result as ReplyVerification.Verified).reply
        assertThat(reply.isLifetime).isTrue()
        assertThat(reply.quota?.mode).isEqualTo(QuotaMode.UNLIMITED)
        assertThat(reply.reportedId).isEqualTo("r-1")
    }
}
