package com.textpic.studio.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.textpic.core.Alignments
import com.textpic.core.StyleKeys
import com.textpic.core.ThumbnailFits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("textpic_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val styleKey = stringPreferencesKey("style_key")
        val theme = stringPreferencesKey("theme")
        val font = stringPreferencesKey("font")
        val fontSize = intPreferencesKey("font_size")
        val maxWidth = intPreferencesKey("max_width")
        val alignment = stringPreferencesKey("alignment")
        val transparent = booleanPreferencesKey("transparent")
        val fillWidth = booleanPreferencesKey("fill_width")
        val scale = floatPreferencesKey("scale")
        val paddingX = intPreferencesKey("padding_x")
        val paddingY = intPreferencesKey("padding_y")
        val thumbnailFit = stringPreferencesKey("thumbnail_fit")
        val keyboardHeight = stringPreferencesKey("keyboard_height")
        val haptics = booleanPreferencesKey("haptics")
        val saveMessageText = booleanPreferencesKey("save_message_text")
        val retentionDays = intPreferencesKey("retention_days")
        val workspaceName = stringPreferencesKey("workspace_name")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val lastImeActiveAt = longPreferencesKey("last_ime_active_at")
        val kbTheme = stringPreferencesKey("kb_theme")
        val kbHeight = intPreferencesKey("kb_height_level")
        val kbOneHanded = stringPreferencesKey("kb_one_handed")
        val kbVibration = booleanPreferencesKey("kb_vibration")
        val kbVibrationStrength = stringPreferencesKey("kb_vibration_strength")
        val kbSound = booleanPreferencesKey("kb_sound")
        val kbSoundId = stringPreferencesKey("kb_sound_id")
        val kbSoundVolume = intPreferencesKey("kb_sound_volume")
        val kbPopup = booleanPreferencesKey("kb_popup_preview")
        val kbNumberRow = booleanPreferencesKey("kb_number_row")
        val kbLongPress = intPreferencesKey("kb_long_press_ms")
        val kbDoubleSpace = booleanPreferencesKey("kb_double_space")
        val kbAutoCap = booleanPreferencesKey("kb_auto_capitalize")
        val kbAfterInsert = stringPreferencesKey("kb_after_insert")
        val kbSendDelay = intPreferencesKey("kb_send_delay_ms")
        val kbConfirmSend = booleanPreferencesKey("kb_confirm_send")
        val kbClipboard = booleanPreferencesKey("kb_clipboard")
        val kbClipTimeout = intPreferencesKey("kb_clipboard_timeout")
    }

    private val safePreferences: Flow<Preferences> = context.settingsDataStore.data
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyPreferences())
        }

    val settings: Flow<AppSettings> = safePreferences
        .map { preferences -> preferences.toAppSettings() }

    val lastImeActiveAt: Flow<Long> = safePreferences
        .map { preferences -> preferences[Keys.lastImeActiveAt] ?: 0L }

    suspend fun current(): AppSettings = settings.first()

    suspend fun updateRender(transform: (RenderSettings) -> RenderSettings) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences.toAppSettings().render
            val next = transform(current)
            preferences[Keys.styleKey] = next.styleKey
            preferences[Keys.theme] = next.theme
            preferences[Keys.font] = next.font
            preferences[Keys.fontSize] = next.fontSize.coerceIn(10, 48)
            preferences[Keys.maxWidth] = next.maxWidth.coerceAtLeast(80)
            preferences[Keys.alignment] = next.alignment
            preferences[Keys.transparent] = next.transparent
            preferences[Keys.fillWidth] = next.fillWidth
            preferences[Keys.scale] = next.scale.coerceIn(1f, 4f)
            preferences[Keys.paddingX] = next.paddingX.coerceAtLeast(0)
            preferences[Keys.paddingY] = next.paddingY.coerceAtLeast(0)
            preferences[Keys.thumbnailFit] = next.thumbnailFit
        }
    }

    suspend fun resetToGolden() {
        updateRender { RenderSettings.GOLDEN }
    }

    suspend fun updateApp(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            val next = transform(preferences.toAppSettings())
            preferences[Keys.keyboardHeight] = next.keyboardHeight
            preferences[Keys.haptics] = next.haptics
            preferences[Keys.saveMessageText] = next.saveMessageText
            preferences[Keys.retentionDays] = next.retentionDays
            preferences[Keys.workspaceName] = next.workspaceName
            preferences[Keys.onboardingComplete] = next.onboardingComplete
        }
    }

    /** The keyboard's own settings; written from the keyboard's gear panel too. */
    suspend fun updateKeyboard(transform: (KeyboardPrefs) -> KeyboardPrefs) {
        context.settingsDataStore.edit { preferences ->
            val next = transform(preferences.toAppSettings().keyboard)
            preferences[Keys.kbTheme] = next.theme.name
            preferences[Keys.kbHeight] = next.heightLevel
            preferences[Keys.kbOneHanded] = next.oneHanded.name
            preferences[Keys.kbVibration] = next.vibration
            preferences[Keys.kbVibrationStrength] = next.vibrationStrength.name
            preferences[Keys.kbSound] = next.sound
            preferences[Keys.kbSoundId] = next.soundId.name
            preferences[Keys.kbSoundVolume] = next.soundVolume
            preferences[Keys.kbPopup] = next.popupPreview
            preferences[Keys.kbNumberRow] = next.numberRow
            preferences[Keys.kbLongPress] = next.longPressMs
            preferences[Keys.kbDoubleSpace] = next.doubleSpacePeriod
            preferences[Keys.kbAutoCap] = next.autoCapitalize
            preferences[Keys.kbAfterInsert] = next.afterInsert.name
            preferences[Keys.kbSendDelay] = next.sendDelayMs
            preferences[Keys.kbConfirmSend] = next.confirmBeforeSend
            preferences[Keys.kbClipboard] = next.clipboardEnabled
            preferences[Keys.kbClipTimeout] = next.clipboardTimeoutMinutes
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.settingsDataStore.edit { it[Keys.onboardingComplete] = complete }
    }

    suspend fun markImeActive(at: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit { it[Keys.lastImeActiveAt] = at }
    }

    suspend fun exportJson(): String {
        val app = current()
        val render = app.render
        return JSONObject()
            .put("style_key", render.styleKey)
            .put("theme", render.theme)
            .put("font", render.font)
            .put("font_size", render.fontSize)
            .put("max_width", render.maxWidth)
            .put("alignment", render.alignment)
            .put("transparent", render.transparent)
            .put("fill_width", render.fillWidth)
            .put("scale", render.scale.toDouble())
            .put("padding_x", render.paddingX)
            .put("padding_y", render.paddingY)
            .put("thumbnail_fit", render.thumbnailFit)
            .put("keyboard_height", app.keyboardHeight)
            .put("haptics", app.haptics)
            .put("save_message_text", app.saveMessageText)
            .put("retention_days", app.retentionDays)
            .put("workspace_name", app.workspaceName)
            .toString(2)
    }

    suspend fun importJson(json: String): Result<Unit> = runCatching {
        val objectValue = JSONObject(json)
        val render = RenderSettings(
            styleKey = objectValue.optString("style_key", StyleKeys.RECEIVED_DARK),
            theme = if (objectValue.optString("theme") == "dark") "dark" else "light",
            font = objectValue.optString("font", "Verdana"),
            fontSize = objectValue.optInt("font_size", 10).coerceIn(10, 48),
            maxWidth = objectValue.optInt("max_width", 120).coerceAtLeast(80),
            alignment = if (objectValue.optString("alignment") == Alignments.RIGHT) {
                Alignments.RIGHT
            } else {
                Alignments.LEFT
            },
            transparent = objectValue.optBoolean("transparent", true),
            fillWidth = objectValue.optBoolean("fill_width", true),
            scale = objectValue.optDouble("scale", 3.0).toFloat().coerceIn(1f, 4f),
            paddingX = objectValue.optInt("padding_x", 8).coerceAtLeast(0),
            paddingY = objectValue.optInt("padding_y", 5).coerceAtLeast(0),
            thumbnailFit = objectValue.optString("thumbnail_fit", ThumbnailFits.OFF)
                .takeIf { it in setOf(ThumbnailFits.OFF, ThumbnailFits.WIDE, ThumbnailFits.SQUARE) }
                ?: ThumbnailFits.OFF,
        )
        val app = AppSettings(
            render = render,
            keyboardHeight = objectValue.optString("keyboard_height", "normal")
                .takeIf { it in setOf("small", "normal", "large") } ?: "normal",
            haptics = objectValue.optBoolean("haptics", true),
            saveMessageText = objectValue.optBoolean("save_message_text", true),
            retentionDays = objectValue.optInt("retention_days", 365)
                .takeIf { it in setOf(0, 30, 90, 365) } ?: 365,
            workspaceName = objectValue.optString("workspace_name", "My workspace").take(60),
        )
        updateRender { render }
        updateApp { app.copy(onboardingComplete = it.onboardingComplete) }
    }.recoverCatching { error ->
        if (error is JSONException) throw IllegalArgumentException("That is not a TextPic settings file.")
        throw error
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val style = this[Keys.styleKey] ?: StyleKeys.RECEIVED_DARK
        val render = RenderSettings(
            styleKey = if (com.textpic.core.StylePresets.get(style).key == style) style else StyleKeys.RECEIVED_DARK,
            theme = if (this[Keys.theme] == "dark") "dark" else "light",
            font = this[Keys.font] ?: "Verdana",
            fontSize = (this[Keys.fontSize] ?: 10).coerceIn(10, 48),
            maxWidth = (this[Keys.maxWidth] ?: 120).coerceAtLeast(80),
            alignment = if (this[Keys.alignment] == Alignments.RIGHT) Alignments.RIGHT else Alignments.LEFT,
            transparent = this[Keys.transparent] ?: true,
            fillWidth = this[Keys.fillWidth] ?: true,
            scale = (this[Keys.scale] ?: 3f).coerceIn(1f, 4f),
            paddingX = (this[Keys.paddingX] ?: 8).coerceAtLeast(0),
            paddingY = (this[Keys.paddingY] ?: 5).coerceAtLeast(0),
            thumbnailFit = this[Keys.thumbnailFit] ?: ThumbnailFits.OFF,
        )
        return AppSettings(
            render = render,
            keyboardHeight = this[Keys.keyboardHeight] ?: "normal",
            haptics = this[Keys.haptics] ?: true,
            saveMessageText = this[Keys.saveMessageText] ?: true,
            retentionDays = this[Keys.retentionDays] ?: 365,
            workspaceName = this[Keys.workspaceName] ?: "My workspace",
            onboardingComplete = this[Keys.onboardingComplete] ?: false,
            keyboard = KeyboardPrefs(
                theme = KeyboardTheme.fromWire(this[Keys.kbTheme]),
                heightLevel = KeyboardPrefRules.clampLevel(
                    this[Keys.kbHeight] ?: 3,
                ),
                oneHanded = OneHandedSide.fromWire(this[Keys.kbOneHanded]),
                vibration = this[Keys.kbVibration] ?: true,
                vibrationStrength = HapticStrength.fromWire(this[Keys.kbVibrationStrength]),
                sound = this[Keys.kbSound] ?: false,
                soundId = KeySoundId.fromWire(this[Keys.kbSoundId]),
                soundVolume = (this[Keys.kbSoundVolume] ?: 60).coerceIn(0, 100),
                popupPreview = this[Keys.kbPopup] ?: true,
                numberRow = this[Keys.kbNumberRow] ?: false,
                longPressMs = this[Keys.kbLongPress] ?: 300,
                doubleSpacePeriod = this[Keys.kbDoubleSpace] ?: true,
                autoCapitalize = this[Keys.kbAutoCap] ?: true,
                clipboardEnabled = this[Keys.kbClipboard] ?: true,
                clipboardTimeoutMinutes = this[Keys.kbClipTimeout] ?: 60,
                afterInsert = com.textpic.studio.ime.AfterInsert.fromWire(
                    this[Keys.kbAfterInsert],
                ),
                sendDelayMs = KeyboardPrefRules.sendDelayMs(this[Keys.kbSendDelay] ?: 600),
                confirmBeforeSend = this[Keys.kbConfirmSend] ?: false,
            ),
        )
    }
}
