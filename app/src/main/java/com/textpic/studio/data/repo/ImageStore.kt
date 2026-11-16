package com.textpic.studio.data.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ImageStore(private val context: Context) {
    private val imagesRoot = File(context.filesDir, "images")
    private val shareRoot = File(context.cacheDir, "share")
    private val imeRoot = File(context.cacheDir, "ime_images")

    fun savePermanent(bitmap: Bitmap): File {
        val stamp = SimpleDateFormat("yyyy/MM", Locale.US).format(Date())
        val directory = File(imagesRoot, stamp).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        writePng(bitmap, file)
        return file
    }

    fun saveShare(bitmap: Bitmap): File {
        shareRoot.mkdirs()
        val file = File(shareRoot, "${UUID.randomUUID()}.png")
        writePng(bitmap, file)
        return file
    }

    fun saveIme(bitmap: Bitmap): File {
        imeRoot.mkdirs()
        val file = File(imeRoot, "${UUID.randomUUID()}.png")
        writePng(bitmap, file)
        return file
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    fun copyImageToClipboard(file: File) {
        val uri = uriFor(file)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "TextPic image", uri))
    }

    fun copyImageUriToClipboard(uri: Uri) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "TextPic image", uri))
    }

    fun shareIntent(file: File): Intent {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "TextPic image", uri)
        }
        return Intent.createChooser(send, "Share image").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun launchShare(file: File) {
        val chooser = shareIntent(file)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun saveToGallery(bitmap: Bitmap): Uri {
        val fileName = "TextPic-${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/TextPic",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the gallery image.")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Could not write the gallery image."
                }
            } ?: error("Could not open the gallery image.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun deleteAllImages() {
        listOf(imagesRoot, shareRoot, imeRoot).forEach { root ->
            root.listFiles()?.forEach { file -> runCatching { file.deleteRecursively() } }
        }
    }

    fun loadThumbnail(path: String?, targetPx: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetPx || bounds.outHeight / sample > targetPx) {
            sample *= 2
        }
        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { android.graphics.BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not encode PNG."
            }
        }
    }
}
