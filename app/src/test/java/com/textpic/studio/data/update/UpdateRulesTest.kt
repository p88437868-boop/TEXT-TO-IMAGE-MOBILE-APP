package com.textpic.studio.data.update

import com.google.common.truth.Truth.assertThat
import com.textpic.license.Base64Codec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Test

/** Manifest parsing, signature checking and the "is this newer" rules. */
class UpdateRulesTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val publicKeyBase64: String = Base64.getEncoder().encodeToString(keys.public.encoded)

    private fun manifest(
        versionCode: Int = 9,
        minVersionCode: Int = 0,
        chunkShas: List<String> = listOf("a".repeat(64), "b".repeat(64)),
        sizeBytes: Long = 700_000,
    ): String = """
        {"versionCode":$versionCode,"versionName":"1.1.4","minVersionCode":$minVersionCode,
        "sha256":"${"c".repeat(64)}","sizeBytes":$sizeBytes,"notes":"Fixes and the new keyboard",
        "publishedAt":1700000000000,"downloadPath":"/v1/download/$versionCode",
        "chunkCount":${chunkShas.size},"chunkSize":524288,
        "chunkShas":[${chunkShas.joinToString(",") { "\"$it\"" }}],"serverTime":1700000000000}
    """.trimIndent()

    private fun sign(payload: String): String {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(payload.toByteArray())
            sign()
        }
        return Base64Codec.encodeUrl(signature)
    }

    private fun payloadBase64(payload: String): String =
        Base64Codec.encodeUrl(payload.toByteArray())

    @Test
    fun `a properly signed manifest is accepted and parsed`() {
        val payload = manifest()
        val result = UpdateRules.interpret(
            payloadBase64(payload),
            sign(payload),
            publicKeyBase64,
            currentVersionCode = 8,
        )
        assertThat(result).isInstanceOf(UpdateCheck.Available::class.java)
        val info = (result as UpdateCheck.Available).info
        assertThat(info.versionCode).isEqualTo(9)
        assertThat(info.versionName).isEqualTo("1.1.4")
        assertThat(info.sizeBytes).isEqualTo(700_000)
        assertThat(info.chunkCount).isEqualTo(2)
        assertThat(info.chunkShas).hasSize(2)
        assertThat(result.mandatory).isFalse()
    }

    @Test
    fun `a tampered payload is rejected`() {
        val payload = manifest()
        val tampered = payload.replace("\"versionCode\":9", "\"versionCode\":99")
        val result = UpdateRules.interpret(
            payloadBase64(tampered),
            sign(payload),
            publicKeyBase64,
            currentVersionCode = 8,
        )
        assertThat(result).isInstanceOf(UpdateCheck.Failed::class.java)
    }

    @Test
    fun `an unsigned or garbage reply fails instead of throwing`() {
        assertThat(UpdateRules.interpret("not-base64!!", "nope", publicKeyBase64, 8))
            .isInstanceOf(UpdateCheck.Failed::class.java)
        assertThat(UpdateRules.interpret("", "", "", 8))
            .isInstanceOf(UpdateCheck.Failed::class.java)
    }

    @Test
    fun `the same or an older version means up to date`() {
        val payload = manifest(versionCode = 8)
        val result = UpdateRules.interpret(
            payloadBase64(payload),
            sign(payload),
            publicKeyBase64,
            currentVersionCode = 8,
        )
        assertThat(result).isEqualTo(UpdateCheck.UpToDate)
    }

    @Test
    fun `no release yet is up to date, not an error`() {
        val payload = """{"none":true,"serverTime":1700000000000}"""
        val result = UpdateRules.interpret(
            payloadBase64(payload),
            sign(payload),
            publicKeyBase64,
            currentVersionCode = 8,
        )
        assertThat(result).isEqualTo(UpdateCheck.UpToDate)
    }

    @Test
    fun `a missing chunk hash list is refused`() {
        val payload = manifest(chunkShas = emptyList())
        val result = UpdateRules.interpret(
            payloadBase64(payload),
            sign(payload),
            publicKeyBase64,
            currentVersionCode = 8,
        )
        assertThat(result).isInstanceOf(UpdateCheck.Failed::class.java)
    }

    @Test
    fun `mandatory updates only block phones below minVersionCode`() {
        val info = UpdateRules.parseManifest(
            com.textpic.license.Json.parse(manifest(versionCode = 10, minVersionCode = 10)),
        )!!
        assertThat(UpdateRules.isMandatory(currentVersionCode = 9, info = info)).isTrue()
        assertThat(UpdateRules.isMandatory(currentVersionCode = 10, info = info)).isFalse()
    }

    @Test
    fun `the check runs at most every six hours`() {
        val now = 1_700_000_000_000L
        assertThat(UpdateRules.shouldCheck(lastCheckAt = 0L, now = now)).isTrue()
        assertThat(UpdateRules.shouldCheck(now - 60_000, now)).isFalse()
        assertThat(UpdateRules.shouldCheck(now - UpdateRules.CHECK_INTERVAL_MS, now)).isTrue()
    }

    @Test
    fun `later hides the prompt for a day`() {
        val now = 1_700_000_000_000L
        val until = now + UpdateRules.SUPPRESS_MS
        assertThat(UpdateRules.isSuppressed(until, now)).isTrue()
        assertThat(UpdateRules.isSuppressed(until, until)).isFalse()
    }

    @Test
    fun `chunk paths and sizes read like a person expects`() {
        val info = UpdateRules.parseManifest(com.textpic.license.Json.parse(manifest()))!!
        assertThat(UpdateRules.chunkPath(info, 1)).isEqualTo("/v1/download/9/1")
        assertThat(UpdateRules.formatBytes(4_900_000)).isEqualTo("4.7 MB")
        assertThat(UpdateRules.formatBytes(900 * 1024)).isEqualTo("900 KB")
    }
}
