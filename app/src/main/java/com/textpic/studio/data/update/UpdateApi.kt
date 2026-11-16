package com.textpic.studio.data.update

import com.textpic.license.Json
import com.textpic.studio.data.license.LicenseServerPublicKey
import java.io.BufferedInputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The second of the app's two network classes (the other is `LicenseApi`).
 *
 * The update check sends nothing but the installed version code, and every reply
 * must carry a valid signature from the owner's server or it is thrown away.
 */
class UpdateApi(
    private val baseUrl: String,
    private val publicKeyBase64: String = LicenseServerPublicKey.ENCODED,
) {
    private val root: String? = baseUrl.trim().trimEnd('/').takeIf { it.startsWith("https://") }

    val isConfigured: Boolean get() = root != null

    /** Blocking; call from a background dispatcher. */
    fun latest(currentVersionCode: Int): UpdateCheck {
        val host = root ?: return UpdateCheck.Failed("No update server is configured.")
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$host/v1/latest?vc=$currentVersionCode").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("accept", "application/json")
                useCaches = false
            }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                return UpdateCheck.Failed("The update server answered with code $status.")
            }
            val body = connection.inputStream.use { stream ->
                BufferedInputStream(stream).readNBytes(MAX_REPLY_BYTES)
            }
            val root = Json.parse(String(body, Charsets.UTF_8))
            val payload = root?.obj()?.fields?.get("payload")?.str()
                ?: return UpdateCheck.Failed("The update reply was empty.")
            val signature = root.obj()?.fields?.get("sig")?.str()
                ?: return UpdateCheck.Failed("The update reply was not signed.")
            UpdateRules.interpret(payload, signature, publicKeyBase64, currentVersionCode)
        } catch (error: SocketTimeoutException) {
            UpdateCheck.Failed("The update check timed out.")
        } catch (error: UnknownHostException) {
            UpdateCheck.Failed("No internet connection.")
        } catch (error: ConnectException) {
            UpdateCheck.Failed("The update server refused the connection.")
        } catch (error: SSLException) {
            UpdateCheck.Failed("The secure connection to the update server failed.")
        } catch (error: Exception) {
            UpdateCheck.Failed(error.message?.take(120) ?: "The update check failed.")
        } finally {
            connection?.disconnect()
        }
    }

    /** One chunk of the APK, or null when it cannot be fetched. */
    fun chunk(info: UpdateInfo, index: Int): ByteArray? {
        val host = root ?: return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(host + UpdateRules.chunkPath(info, index)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                requestMethod = "GET"
                useCaches = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { stream ->
                BufferedInputStream(stream).readNBytes(info.chunkSize + 1)
            }
            if (bytes.isEmpty() || bytes.size > info.chunkSize) null else bytes
        } catch (error: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val MAX_REPLY_BYTES = 128 * 1024
    }
}
