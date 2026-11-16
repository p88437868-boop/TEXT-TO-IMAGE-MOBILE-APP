package com.textpic.core

import android.content.res.Resources
import android.graphics.Typeface

data class ResolvedFont(
    val typeface: Typeface,
    val displayName: String,
)

interface FontProvider {
    fun resolve(fontKey: String): ResolvedFont
}

data class FontOption(
    val key: String,
    val label: String,
    val bundledLabel: String,
)

object FontCatalog {
    val standard = listOf(
        FontOption("Verdana", "Verdana", "Verdana-like (DejaVu Sans)"),
        FontOption("Segoe UI", "Segoe UI", "Segoe UI-like (Selawik)"),
        FontOption("Arial", "Arial", "Arial-like (Arimo)"),
        FontOption("Calibri", "Calibri", "Calibri-like (Carlito)"),
        FontOption("Tahoma", "Tahoma", "Tahoma-like (Noto Sans)"),
        FontOption("Georgia", "Georgia", "Georgia-like (Gelasio)"),
    )

    fun labelFor(fontKey: String): String =
        standard.firstOrNull { it.key == fontKey }?.bundledLabel
            ?: fontKey.removePrefix("custom:")

    fun isCustom(fontKey: String): Boolean = fontKey.startsWith("custom:")
}

class BundledFontProvider(private val resources: Resources) : FontProvider {
    override fun resolve(fontKey: String): ResolvedFont {
        val option = when (fontKey) {
            "Segoe UI" -> R.font.selawik to "Segoe UI-like (Selawik)"
            "Arial" -> R.font.arimo to "Arial-like (Arimo)"
            "Calibri" -> R.font.carlito to "Calibri-like (Carlito)"
            "Tahoma" -> R.font.noto_sans to "Tahoma-like (Noto Sans)"
            "Georgia" -> R.font.gelasio to "Georgia-like (Gelasio)"
            else -> R.font.dejavu_sans to "Verdana-like (DejaVu Sans)"
        }
        val typeface = Typeface.create(resources.getFont(option.first), Typeface.NORMAL)
        return ResolvedFont(typeface, option.second)
    }
}
