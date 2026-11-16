package com.textpic.studio.data.prefs

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.textpic.core.BundledFontProvider
import com.textpic.core.FontCatalog
import com.textpic.core.FontOption
import com.textpic.core.FontProvider
import com.textpic.core.ResolvedFont
import java.io.File

class FontManager(private val context: Context) : FontProvider {
    private val bundled = BundledFontProvider(context.resources)
    private val fontDir = File(context.filesDir, "fonts").apply { mkdirs() }

    override fun resolve(fontKey: String): ResolvedFont {
        if (fontKey == "Verdana") {
            val userVerdana = customFonts().firstOrNull {
                it.label.contains("verdana", ignoreCase = true)
            }
            if (userVerdana != null) {
                loadFile(userVerdana.key.removePrefix("custom:"))?.let {
                    return ResolvedFont(it, userVerdana.label)
                }
            }
        }
        if (FontCatalog.isCustom(fontKey)) {
            val fileName = fontKey.removePrefix("custom:")
            loadFile(fileName)?.let {
                return ResolvedFont(it, fileName.removeSuffix(".ttf").removeSuffix(".otf"))
            }
        }
        return bundled.resolve(fontKey)
    }

    fun fontOptions(): List<FontOption> =
        FontCatalog.standard + customFonts()

    fun customFonts(): List<FontOption> = fontDir.listFiles()
        ?.filter { it.isFile && (it.extension.equals("ttf", ignoreCase = true) || it.extension.equals("otf", ignoreCase = true)) }
        ?.sortedBy { it.name.lowercase() }
        ?.map {
            FontOption(
                key = "custom:${it.name}",
                label = it.name.removeSuffix(".ttf").removeSuffix(".otf"),
                bundledLabel = it.name,
            )
        }
        ?: emptyList()

    fun displayName(fontKey: String): String {
        if (FontCatalog.isCustom(fontKey)) {
            val name = fontKey.removePrefix("custom:")
            return if (loadFile(name) != null) name.removeSuffix(".ttf").removeSuffix(".otf") else "Missing font"
        }
        if (fontKey == "Verdana") {
            val userVerdana = customFonts().firstOrNull {
                it.label.contains("verdana", ignoreCase = true)
            }
            if (userVerdana != null) return "${userVerdana.label} (user font)"
        }
        return FontCatalog.labelFor(fontKey)
    }

    fun relativeFilePath(fontKey: String): String? {
        if (FontCatalog.isCustom(fontKey)) {
            return fontKey.removePrefix("custom:")
        }
        if (fontKey == "Verdana") {
            return customFonts().firstOrNull { it.label.contains("verdana", ignoreCase = true) }
                ?.key?.removePrefix("custom:")
        }
        return null
    }

    fun addFont(uri: Uri): String? {
        val resolver = context.contentResolver
        var displayName = "Custom font.ttf"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) displayName = cursor.getString(index) ?: displayName
            }
        }
        val extension = displayName.substringAfterLast('.', "ttf").lowercase()
        if (extension != "ttf" && extension != "otf") return null
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(64)
        val target = File(fontDir, if (safeName.lowercase().endsWith(".$extension")) safeName else "$safeName.$extension")
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            "custom:${target.name}"
        }.getOrNull()
    }

    fun deleteFont(fontKey: String): Boolean {
        val path = relativeFilePath(fontKey) ?: return false
        return File(fontDir, path).delete()
    }

    private fun loadFile(fileName: String): Typeface? {
        val file = File(fontDir, File(fileName).name)
        if (!file.isFile) return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }
}
