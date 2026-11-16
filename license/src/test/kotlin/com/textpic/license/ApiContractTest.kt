package com.textpic.license

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApiContractTest {
    @Test
    fun `the request body contains only whitelisted fields`() {
        val body = LicenseApiContract.buildRequestBody(
            key = "ABCD-EFGH-JKLM-NPQR",
            deviceCode = "TP-5RKD-8WQZ-3MNB-6YTF",
            deviceModel = "Pixel 7",
            androidVersion = "14",
            appVersion = "1.2.0",
            nonce = "abc",
            usageCount = 3,
            reportId = "r-1",
        )
        val parsed = Json.parse(body)?.obj()
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.fields.keys).containsExactlyElementsIn(LicenseApiContract.REQUEST_FIELDS)
        assertThat(parsed.fields.keys).hasSize(8)
    }

    @Test
    fun `nothing else can sneak into the body`() {
        // A message body that a careless caller might pass is simply not a
        // parameter of the request at all; escaping keeps it inside a value.
        val body = LicenseApiContract.buildRequestBody(
            key = "key",
            deviceCode = "TP-5RKD-8WQZ-3MNB-6YTF",
            deviceModel = "Pixel \"7\"",
            androidVersion = "14",
            appVersion = "1.2.0",
            nonce = "n",
            usageCount = 0,
            reportId = null,
        )
        val parsed = Json.parse(body)!!.obj()!!
        assertThat(parsed.fields.keys).containsExactly(
            "key",
            "deviceCode",
            "deviceModel",
            "androidVersion",
            "appVersion",
            "nonce",
            "usageCount",
        )
        assertThat(parsed["deviceModel"]?.str()).isEqualTo("Pixel \"7\"")
    }

    @Test
    fun `escaping keeps json valid`() {
        val body = LicenseApiContract.buildRequestBody(
            key = "a\"b\\c\nd",
            deviceCode = "TP-5RKD-8WQZ-3MNB-6YTF",
            deviceModel = "",
            androidVersion = "",
            appVersion = "",
            nonce = "n",
            usageCount = 0,
            reportId = null,
        )
        assertThat(Json.parse(body)!!["key"]?.str()).isEqualTo("a\"b\\c\nd")
    }

    @Test
    fun `usage counts are clamped and nonces are unique`() {
        val body = LicenseApiContract.buildRequestBody(
            key = "k",
            deviceCode = "d",
            deviceModel = "m",
            androidVersion = "a",
            appVersion = "v",
            nonce = "n",
            usageCount = 99_999,
            reportId = null,
        )
        assertThat(Json.parse(body)!!["usageCount"]?.int()).isEqualTo(10_000)

        val nonces = (1..100).map { LicenseApiContract.newNonce() }.toSet()
        assertThat(nonces).hasSize(100)
        assertThat(nonces.first().length).isEqualTo(32)
        val reportIds = (1..50).map { LicenseApiContract.newReportId() }.toSet()
        assertThat(reportIds).hasSize(50)
    }

    @Test
    fun `base64 round trips both alphabets`() {
        val bytes = ByteArray(64) { (it * 7).toByte() }
        assertThat(Base64Codec.decodeUrl(Base64Codec.encodeUrl(bytes))).isEqualTo(bytes)
        assertThat(Base64Codec.decode(Base64Codec.encode(bytes))).isEqualTo(bytes)
        assertThat(Base64Codec.decode("not base64!!")).isNull()
    }
}
