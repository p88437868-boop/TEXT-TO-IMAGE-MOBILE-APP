package com.textpic.studio

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * TextPic Studio talks to the internet for one reason only: checking the
 * activation key with the owner's server. These tests keep it that way.
 */
class NetworkPolicyTest {
    private val allowedPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.REQUEST_INSTALL_PACKAGES",
    )

    private val permissionPattern = Regex("""android:name="(android\.permission\.[A-Z_]+)"""")

    @Test
    fun `the app manifest requests only the two license permissions`() {
        val root = projectRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        assertWithMessage("app manifest missing").that(manifest.isFile).isTrue()

        val requested = permissionPattern.findAll(manifest.readText())
            .map { it.groupValues[1] }
            .filter { it.startsWith("android.permission.") }
            .toSet()

        assertWithMessage("Only the license and update permissions may be added by our own manifest")
            .that(requested - setOf("android.permission.WRITE_EXTERNAL_STORAGE"))
            .containsExactlyElementsIn(allowedPermissions)
    }

    @Test
    fun `no manifest enables cleartext traffic`() {
        val root = projectRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertWithMessage("cleartext http must stay off")
            .that(manifest.contains("android:usesCleartextTraffic=\"true\""))
            .isFalse()
    }

    @Test
    fun `only LicenseApi and UpdateApi touch the network`() {
        val root = projectRoot()
        val sources = File(root, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertWithMessage("no sources found").that(sources).isNotEmpty()

        val networkMarkers = listOf(
            "java.net.",
            "HttpURLConnection",
            "okhttp3",
            "java.net.Socket",
            "URLConnection",
        )
        val offenders = sources.filter { file ->
            val text = file.readText()
            networkMarkers.any { text.contains(it) }
        }.filter { it.name != "LicenseApi.kt" && it.name != "UpdateApi.kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }

        assertWithMessage("Only LicenseApi.kt and UpdateApi.kt may use the network")
            .that(offenders)
            .isEmpty()
    }

    @Test
    fun `the license request body can only carry the whitelisted fields`() {
        val body = com.textpic.license.LicenseApiContract.buildRequestBody(
            key = "ABCD-EFGH-JKLM-NPQR",
            deviceCode = "TP-5RKD-8WQZ-3MNB-6YTF",
            deviceModel = "Pixel",
            androidVersion = "14",
            appVersion = "1.1.2",
            nonce = "n",
            usageCount = 0,
            reportId = "r-1",
        )
        val parsed = com.textpic.license.Json.parse(body)!!.obj()!!
        assertWithMessage("request must not grow fields")
            .that(parsed.fields.keys)
            .containsExactlyElementsIn(com.textpic.license.LicenseApiContract.REQUEST_FIELDS)
    }

    private fun projectRoot(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("Could not locate the project root from ${System.getProperty("user.dir")}")
    }
}
