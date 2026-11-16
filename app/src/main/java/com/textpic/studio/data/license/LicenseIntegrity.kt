package com.textpic.studio.data.license

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.textpic.studio.BuildConfig
import java.security.MessageDigest

/**
 * Light hardening: if the APK is not signed by the expected certificate the
 * install is treated as unlicensed. The expected digest is injected at build
 * time from the release keystore, so debug builds skip the check.
 *
 * Every failure path returns `true` (check passes) on purpose: a quirk of a
 * phone's PackageManager must never lock out a paying customer.
 */
object LicenseIntegrity {
    fun signatureMatches(context: Context): Boolean {
        val expected = BuildConfig.LICENSE_CERT_SHA256
        if (expected.isBlank()) return true
        return runCatching {
            val digest = signingCertificateSha256(context) ?: return true
            digest.equals(expected, ignoreCase = true)
        }.getOrDefault(true)
    }

    private fun signingCertificateSha256(context: Context): String? {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
