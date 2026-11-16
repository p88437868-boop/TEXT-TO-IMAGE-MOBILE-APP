package com.textpic.studio.ime

import android.content.Context
import org.json.JSONObject

internal enum class ImeKeyType {
    CHAR,
    SHIFT,
    BACKSPACE,
    SYMBOLS,
    LETTERS,
    EMOJI,
    SPACE,
    ENTER,
    RENDER,
    INSERT,
}

internal data class ImeKey(
    val label: String,
    val output: String,
    val type: ImeKeyType,
    val weight: Float,
)

internal data class ImeLayout(
    val name: String,
    val rows: List<List<ImeKey>>,
)

internal object ImeLayouts {
    fun load(context: Context, assetPath: String): ImeLayout {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        val name = json.optString("name", assetPath.substringAfterLast('/'))
        val rowsJson = json.getJSONArray("rows")
        val rows = buildList {
            for (rowIndex in 0 until rowsJson.length()) {
                val rowJson = rowsJson.getJSONArray(rowIndex)
                val row = buildList {
                    for (keyIndex in 0 until rowJson.length()) {
                        val keyJson = rowJson.getJSONObject(keyIndex)
                        val type = runCatching {
                            ImeKeyType.valueOf(keyJson.optString("type", "char").uppercase())
                        }.getOrDefault(ImeKeyType.CHAR)
                        add(
                            ImeKey(
                                label = keyJson.optString("label"),
                                output = keyJson.optString("output"),
                                type = type,
                                weight = keyJson.optDouble("weight", 1.0).toFloat().coerceAtLeast(0.5f),
                            ),
                        )
                    }
                }
                add(row)
            }
        }
        return ImeLayout(name, rows)
    }
}
