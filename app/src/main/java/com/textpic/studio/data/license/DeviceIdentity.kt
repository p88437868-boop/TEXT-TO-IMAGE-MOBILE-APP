package com.textpic.studio.data.license

import android.content.Context
import android.provider.Settings
import com.textpic.license.DeviceCode
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Derives this install's Device Code.
 *
 * `SHA-256(ANDROID_ID + salt)` truncated to 10 bytes. Only
 * `Settings.Secure.ANDROID_ID` is read: no IMEI, serial, MAC address or any other
 * identifier that needs a permission, and nothing is sent anywhere.
 *
 * The same signed app on the same phone keeps the same code across reinstalls; a
 * factory reset produces a new one, which needs a new key.
 */
class DeviceIdentity(
    private val context: Context,
    private val store: LicenseStore,
) {

    suspend fun codeText(): String = DeviceCode.format(codeBytes())

    suspend fun codeBytes(): ByteArray {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val seed = if (androidId.isNullOrBlank()) {
            // Extremely rare (some emulator images); fall back to a private
            // random id so the app still works and can still be activated.
            store.fallbackDeviceId { randomFallbackId() }
        } else {
            androidId
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((seed + SALT).toByteArray(Charsets.UTF_8))
        return digest.copyOf(10)
    }

    private fun randomFallbackId(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SALT = "textpic-studio-activation-v1"
    }
}
