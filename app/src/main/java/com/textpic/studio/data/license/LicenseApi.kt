package com.textpic.studio.data.license

import com.textpic.license.Json
import java.io.BufferedInputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The ONLY class in the app that may use the network.
 *
 * It posts the whitelisted license check body (see
 * [com.textpic.license.LicenseApiContract]) to the owner's server and returns the
 * signed reply untouched. Nothing is logged, and a key or device code can never
 * appear in an exception message that reaches a log.
 */
/** Small seam so tests (and only tests) can answer without a socket. */
interface LicenseTransport {
    val isConfigured: Boolean
    fun verify(body: String): LicenseApi.Result
}

class LicenseApi(baseUrl: String) : LicenseTransport {
    private val verifyUrl: String? = when {
        baseUrl.isBlank() -> null
        baseUrl.startsWith("https://") -> baseUrl.trimEnd('/') + com.textpic.license.LicenseApiContract.PATH
        else -> null
    }

    override val isConfigured: Boolean get() = verifyUrl != null

    sealed interface Result {
        data class Ok(val payload: String, val sig: String) : Result
        data class HttpError(val code: Int) : Result

        /**
         * The request never produced an HTTP status. [detail] says why, in words
         * a person can act on, so the UI never has to guess "no internet".
         */
        data class Unreachable(val detail: String = "") : Result
    }

    override fun verify(body: String): Result {
        val url = verifyUrl ?: return Result.Unreachable("this build has no server address")
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                // An explicit agent: some edges reject unknown/blank agents.
                setRequestProperty("User-Agent", "TextPicStudio/" + com.textpic.studio.BuildConfig.VERSION_NAME)
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != 200) {
                Result.HttpError(code)
            } else {
                val text = BufferedInputStream(connection.inputStream).use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
                val json = Json.parse(text)?.obj()
                val payload = json?.get("payload")?.str()
                val sig = json?.get("sig")?.str()
                if (payload.isNullOrBlank() || sig.isNullOrBlank()) {
                    Result.HttpError(code)
                } else {
                    Result.Ok(payload, sig)
                }
            }
        } catch (error: Exception) {
            // Timeouts, DNS failures, TLS problems and bugs all land here; the
            // reason travels to the screen instead of becoming "no internet".
            Result.Unreachable(describe(error))
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun describe(error: Exception): String = when (error) {
        is SocketTimeoutException -> "the server did not answer in time"
        is UnknownHostException -> "could not look up ${hostForMessage()}"
        is ConnectException -> "the connection was refused"
        is SSLException -> "the secure connection failed"
        is java.net.NoRouteToHostException -> "there is no route to the server"
        is android.os.NetworkOnMainThreadException -> "network call on the UI thread"
        else -> error.javaClass.simpleName
    }

    private fun hostForMessage(): String = runCatching { URL(verifyUrl ?: "").host }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "the license server"
}
