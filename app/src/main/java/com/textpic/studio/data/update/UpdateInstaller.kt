package com.textpic.studio.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs a verified APK.
 *
 * Android forbids silent self-updates outside the Play Store, so the customer
 * always confirms on Android's own install screen. The `PackageInstaller`
 * session is the tidy path; if a manufacturer blocks it we fall back to the
 * plain "open installer" intent, which is what Xiaomi/HyperOS usually needs.
 */
object UpdateInstaller {
    const val ACTION_INSTALL_RESULT = "com.textpic.studio.UPDATE_INSTALL_RESULT"

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

    /** The system screen that lets the customer allow installs from TextPic. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() == 0L) return false
        val viaSession = runCatching { installWithSession(context, apk) }.getOrDefault(false)
        if (viaSession) return true
        return openInstaller(context, apk)
    }

    private fun installWithSession(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("textpic.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(context, UpdateInstallReceiver::class.java)
                .setAction(ACTION_INSTALL_RESULT)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                flags,
            )
            session.commit(pending.intentSender)
        }
        return true
    }

    /** `ACTION_VIEW` through our FileProvider; the user's installer takes over. */
    fun openInstaller(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
