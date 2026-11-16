package com.textpic.license

import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import org.junit.Test

class DeviceCodeTest {
    private fun codeFor(androidId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((androidId + "textpic-studio-activation-v1").toByteArray())
        return DeviceCode.format(digest.copyOf(10))
    }

    @Test
    fun `the code is stable for the same phone and different for another`() {
        val first = codeFor("android-id-a")
        assertThat(codeFor("android-id-a")).isEqualTo(first)
        assertThat(codeFor("android-id-b")).isNotEqualTo(first)
        assertThat(first).matches("TP-[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}")
    }

    @Test
    fun `pasted codes are normalized`() {
        val code = codeFor("android-id-a")
        assertThat(DeviceCode.normalize(code.lowercase())).isEqualTo(code.removePrefix("TP-").replace("-", ""))
        assertThat(DeviceCode.normalize("  $code  ")).isNotNull()
        assertThat(DeviceCode.normalize(code.replace("-", " "))).isNotNull()
    }

    @Test
    fun `incomplete or impossible codes are refused`() {
        assertThat(DeviceCode.normalize("TP-1234")).isNull()
        assertThat(DeviceCode.normalize("TP-0000-0000-0000-0000")).isNull()
        assertThat(DeviceCode.normalize("")).isNull()
        assertThat(DeviceCode.toBytes("TP-5RKD-8WQZ-3MNB-6YTF")).hasLength(10)
    }

    @Test
    fun `json parsing handles the reply shape`() {
        val parsed = Json.parse(
            """{"a":"x","b":null,"c":12,"d":true,"e":[1,2],"f":{"g":"h"}}""",
        )!!
        assertThat(parsed["a"]?.str()).isEqualTo("x")
        assertThat(parsed["b"]).isEqualTo(JsonValue.Null)
        assertThat(parsed["c"]?.int()).isEqualTo(12)
        assertThat(parsed["d"]?.boolean()).isTrue()
        assertThat(parsed["f"]?.get("g")?.str()).isEqualTo("h")
        assertThat(Json.parse("not json")).isNull()
        assertThat(Json.parse("{\"a\":1} trailing")).isNull()
    }
}
