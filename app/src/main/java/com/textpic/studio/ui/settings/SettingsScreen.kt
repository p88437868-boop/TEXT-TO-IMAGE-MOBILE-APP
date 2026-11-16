package com.textpic.studio.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.textpic.studio.BuildConfig
import com.textpic.studio.LocalAppContainer
import com.textpic.license.LicenseRules
import com.textpic.license.LicenseState
import com.textpic.license.QuotaRules
import com.textpic.studio.data.license.LicenseSnapshot
import com.textpic.studio.data.RetentionScheduler
import com.textpic.studio.data.prefs.AppSettings
import com.textpic.studio.data.prefs.HapticStrength
import com.textpic.studio.data.prefs.KeySoundId
import com.textpic.studio.data.prefs.KeyboardPrefRules
import com.textpic.studio.data.prefs.KeyboardPrefs
import com.textpic.studio.data.prefs.KeyboardTheme
import com.textpic.studio.ime.ImeSettingsActivity
import com.textpic.studio.ime.KeySoundPlayer
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.InfoPill
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.SectionLabel
import com.textpic.studio.ui.components.SettingsRow
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onFinishOnboarding: () -> Unit,
    onOpenActivation: () -> Unit = {},
) {
    val colors = LocalTextPicColors.current
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    val license by remember { container.licenseService.state }
        .collectAsStateWithLifecycle(LicenseSnapshot())
    val snackbarHostState = remember { SnackbarHostState() }
    var showWorkspaceDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var fontVersion by remember { mutableIntStateOf(0) }
    val customFonts = remember(fontVersion, settings.render.font) { container.fontManager.customFonts() }
    val fontOptions = remember(fontVersion, settings.render.font) { container.fontManager.fontOptions() }

    fun emit(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = container.settingsRepository.exportJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(json.toByteArray())
                        } ?: error("Could not open the selected file.")
                    }
                }.onSuccess {
                    emit("Settings exported.")
                }.onFailure {
                    emit(it.message ?: "Could not export settings.")
                }
            }
        }
    }

    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not read the selected file.")
                    }
                    container.settingsRepository.importJson(json).getOrThrow()
                }
                result.onSuccess {
                    fontVersion++
                    emit("Settings imported.")
                }.onFailure {
                    emit(it.message ?: "That is not a TextPic settings file.")
                }
            }
        }
    }

    val addFont = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val key = withContext(Dispatchers.IO) { container.fontManager.addFont(uri) }
                if (key == null) {
                    emit("Choose a valid .ttf or .otf font file.")
                } else {
                    container.settingsRepository.updateRender { it.copy(font = key) }
                    fontVersion++
                    emit("Font added and selected.")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeading(
                    title = "Settings",
                    subtitle = "Workspace, defaults, keyboard, and local data.",
                )
            }

            item { SectionLabel("Workspace") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    SettingsRow(
                        title = "Workspace name",
                        subtitle = "Shown in the top bar and settings export.",
                    ) {
                        TextButton(onClick = { showWorkspaceDialog = true }) {
                            Text(settings.workspaceName, color = colors.accent, fontSize = 12.sp)
                        }
                    }
                }
            }

            item { SectionLabel("Appearance") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Dark theme (fixed)", color = colors.textPrimary, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            InfoPill("Forced")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The app stays dark in every system setting. Only the image's chat theme changes between light and dark.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            item { SectionLabel("Render defaults") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "${settings.render.style.displayName} · ${settings.render.fontSize} px · ${settings.render.maxWidth} px max · ${settings.render.scale}× scale",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Active render font: ${container.fontManager.displayName(settings.render.font)}",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        GhostButton(
                            text = "Reset to golden defaults",
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.resetToGolden()
                                    emit("Golden defaults restored.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.RestartAlt,
                        )
                    }
                }
            }

            item { SectionLabel("Fonts") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Choose a render font",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            fontOptions.forEach { option ->
                                FilterChip(
                                    selected = settings.render.font == option.key,
                                    onClick = {
                                        scope.launch {
                                            container.settingsRepository.updateRender {
                                                it.copy(font = option.key)
                                            }
                                        }
                                    },
                                    label = { Text(option.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.surfaceInput,
                                        labelColor = colors.textSecondary,
                                        selectedContainerColor = colors.accentSoft,
                                        selectedLabelColor = colors.textPrimary,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Renders with: ${container.fontManager.displayName(settings.render.font)}",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                text = "Add font file",
                                onClick = { addFont.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Add,
                            )
                            GhostButton(
                                text = "Verdana help",
                                onClick = {
                                    emit("Verdana is not bundled. Add your own Verdana .ttf here; until then DejaVu Sans is used.")
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (customFonts.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No custom fonts loaded. Only .ttf and .otf files are accepted.",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            customFonts.forEach { font ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.TextFields,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        font.label,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (settings.render.font == font.key) {
                                        InfoPill("Active")
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val removed = container.fontManager.deleteFont(font.key)
                                                if (settings.render.font == font.key) {
                                                    container.settingsRepository.updateRender {
                                                        it.copy(font = "Verdana")
                                                    }
                                                }
                                                fontVersion++
                                                emit(
                                                    if (removed) "Custom font removed." else "Could not remove that font.",
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Remove ${font.label}",
                                            tint = colors.danger,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Keyboard") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        val keyboard = settings.keyboard
                        val soundPreview = remember { KeySoundPlayer(context) }
                        DisposableEffect(Unit) {
                            onDispose { soundPreview.release() }
                        }
                        fun updateKeyboard(transform: (KeyboardPrefs) -> KeyboardPrefs) {
                            scope.launch {
                                container.settingsRepository.updateKeyboard(transform)
                            }
                        }

                        Text("Theme", color = colors.textPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                KeyboardTheme.DARK to "Dark",
                                KeyboardTheme.LIGHT to "Light",
                                KeyboardTheme.SYSTEM to "System",
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = keyboard.theme == value,
                                    onClick = { updateKeyboard { it.copy(theme = value) } },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.surfaceInput,
                                        labelColor = colors.textSecondary,
                                        selectedContainerColor = colors.accentSoft,
                                        selectedLabelColor = colors.textPrimary,
                                    ),
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Keyboard height", color = colors.textPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (KeyboardPrefRules.MIN_HEIGHT_LEVEL..KeyboardPrefRules.MAX_HEIGHT_LEVEL)
                                .forEach { level ->
                                    FilterChip(
                                        selected = keyboard.heightLevel == level,
                                        onClick = { updateKeyboard { it.copy(heightLevel = level) } },
                                        label = { Text(level.toString(), fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceInput,
                                            labelColor = colors.textSecondary,
                                            selectedContainerColor = colors.accentSoft,
                                            selectedLabelColor = colors.textPrimary,
                                        ),
                                    )
                                }
                        }

                        Spacer(Modifier.height(8.dp))
                        SettingsRow(
                            title = "Vibration",
                            subtitle = "Vibrate on key presses. Off means no vibration at all.",
                        ) {
                            Switch(
                                checked = keyboard.vibration,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(vibration = checked) }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        if (keyboard.vibration) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    HapticStrength.LIGHT to "Light",
                                    HapticStrength.MEDIUM to "Medium",
                                    HapticStrength.STRONG to "Strong",
                                ).forEach { (value, label) ->
                                    FilterChip(
                                        selected = keyboard.vibrationStrength == value,
                                        onClick = {
                                            updateKeyboard { it.copy(vibrationStrength = value) }
                                        },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceInput,
                                            labelColor = colors.textSecondary,
                                            selectedContainerColor = colors.accentSoft,
                                            selectedLabelColor = colors.textPrimary,
                                        ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        SettingsRow(
                            title = "Key sound",
                            subtitle = "A quiet tick on every key, independent of the phone's touch sounds.",
                        ) {
                            Switch(
                                checked = keyboard.sound,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(sound = checked) }
                                    if (checked) soundPreview.preview(keyboard.soundId)
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        if (keyboard.sound) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KeySoundId.entries.forEach { value ->
                                    FilterChip(
                                        selected = keyboard.soundId == value,
                                        onClick = {
                                            updateKeyboard { it.copy(soundId = value) }
                                            soundPreview.preview(value)
                                        },
                                        label = { Text(value.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceInput,
                                            labelColor = colors.textSecondary,
                                            selectedContainerColor = colors.accentSoft,
                                            selectedLabelColor = colors.textPrimary,
                                        ),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Volume ${keyboard.soundVolume}%",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                            )
                            Slider(
                                value = keyboard.soundVolume.toFloat(),
                                onValueChange = { value ->
                                    updateKeyboard { it.copy(soundVolume = value.toInt()) }
                                },
                                valueRange = 0f..100f,
                                steps = 9,
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Send button", color = colors.textPrimary, fontSize = 13.sp)
                        Text(
                            "After the picture is inserted, TextPic can press the app's own send button for you.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("After inserting", color = colors.textPrimary, fontSize = 12.sp)
                        com.textpic.studio.ime.AfterInsert.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { updateKeyboard { it.copy(afterInsert = option) } }
                                    .padding(vertical = 4.dp),
                            ) {
                                RadioButton(
                                    selected = keyboard.afterInsert == option,
                                    onClick = { updateKeyboard { it.copy(afterInsert = option) } },
                                )
                                Column {
                                    Text(option.label, color = colors.textPrimary, fontSize = 12.sp)
                                    Text(option.help, color = colors.textMuted, fontSize = 10.sp)
                                }
                            }
                        }
                        if (keyboard.afterInsert != com.textpic.studio.ime.AfterInsert.NOTHING) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Send delay: ${keyboard.sendDelayMs} ms",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                            )
                            Slider(
                                value = keyboard.sendDelayMs.toFloat(),
                                onValueChange = { value ->
                                    updateKeyboard { it.copy(sendDelayMs = value.toInt()) }
                                },
                                valueRange = 200f..1500f,
                                steps = 12,
                            )
                            SettingsRow(
                                title = "Confirm before sending",
                                subtitle = "Show \"Sending… Undo\" for two seconds first.",
                            ) {
                                Switch(
                                    checked = keyboard.confirmBeforeSend,
                                    onCheckedChange = { checked ->
                                        updateKeyboard { it.copy(confirmBeforeSend = checked) }
                                    },
                                    colors = textPicSwitchColors(),
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        SettingsRow(
                            title = "Key preview popups",
                            subtitle = "Show the pressed key above your finger.",
                        ) {
                            Switch(
                                checked = keyboard.popupPreview,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(popupPreview = checked) }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        SettingsRow(
                            title = "Number row",
                            subtitle = "Show 1-0 above the letters.",
                        ) {
                            Switch(
                                checked = keyboard.numberRow,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(numberRow = checked) }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        SettingsRow(
                            title = "Double-space period",
                            subtitle = "Two taps on space insert \". \".",
                        ) {
                            Switch(
                                checked = keyboard.doubleSpacePeriod,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(doubleSpacePeriod = checked) }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        SettingsRow(
                            title = "Auto-capitalise",
                            subtitle = "Start sentences with a capital letter.",
                        ) {
                            Switch(
                                checked = keyboard.autoCapitalize,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(autoCapitalize = checked) }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        SettingsRow(
                            title = "Clipboard history",
                            subtitle = "Remember copied text while TextPic is the keyboard. Never passwords.",
                        ) {
                            Switch(
                                checked = keyboard.clipboardEnabled,
                                onCheckedChange = { checked ->
                                    updateKeyboard { it.copy(clipboardEnabled = checked) }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        if (keyboard.clipboardEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                KeyboardPrefRules.CLIPBOARD_TIMEOUTS.forEach { (minutes, label) ->
                                    FilterChip(
                                        selected = keyboard.clipboardTimeoutMinutes == minutes,
                                        onClick = {
                                            updateKeyboard { it.copy(clipboardTimeoutMinutes = minutes) }
                                        },
                                        label = { Text(label, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceInput,
                                            labelColor = colors.textSecondary,
                                            selectedContainerColor = colors.accentSoft,
                                            selectedLabelColor = colors.textPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Long-press delay: ${keyboard.longPressMs} ms",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Slider(
                            value = keyboard.longPressMs.toFloat(),
                            onValueChange = { value ->
                                updateKeyboard { it.copy(longPressMs = value.toInt()) }
                            },
                            valueRange = 200f..600f,
                            steps = 7,
                        )
                        GhostButton(
                            text = "Reset keyboard settings",
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.updateKeyboard { KeyboardPrefs() }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Refresh,
                        )
                        Spacer(Modifier.height(8.dp))
                        GhostButton(
                            text = "Open keyboard setup",
                            onClick = { context.startActivity(ImeSettingsActivity.intent(context)) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Keyboard,
                        )
                    }
                }
            }

            item { SectionLabel("License") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        val active = license.licensed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = if (active) colors.success else colors.textMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        active -> "Activated"
                                        license.state == LicenseState.NEEDS_ONLINE -> "Needs an online check"
                                        license.state == LicenseState.BLOCKED -> "Blocked"
                                        license.state == LicenseState.CANCELLED -> "Cancelled"
                                        license.state == LicenseState.EXPIRED -> "Expired"
                                        else -> "Not activated"
                                    },
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    when {
                                        active && license.name.isNotBlank() -> license.name
                                        license.message != null -> license.message!!
                                        else -> "Render, Insert, Copy, Share and Save are locked."
                                    },
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                        if (active) {
                            Spacer(Modifier.height(6.dp))
                            val daysLeft = LicenseRules.daysLeft(
                                license.expiresAt,
                                System.currentTimeMillis(),
                            )
                            Text(
                                when {
                                    license.expiresAt == null -> "Lifetime license"
                                    daysLeft != null && daysLeft <= 1 ->
                                        "Valid until " + SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                            .format(Date(license.expiresAt!!)) + " (last day)"
                                    else ->
                                        "Valid until " + SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                            .format(Date(license.expiresAt!!)) +
                                            (daysLeft?.let { " ($it days left)" } ?: "")
                                },
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                            QuotaRules.summary(
                                license.quota,
                                license.pendingUsage,
                                System.currentTimeMillis(),
                            )?.let { summary ->
                                Spacer(Modifier.height(2.dp))
                                Text(summary, color = colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Device Code: ${license.deviceCode.ifBlank { "—" }}",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                text = if (active) "License details" else "Activate",
                                onClick = onOpenActivation,
                                icon = Icons.Outlined.Key,
                            )
                            if (active) {
                                GhostButton(
                                    text = "Check now",
                                    onClick = {
                                        scope.launch {
                                            container.licenseService.refresh(online = true)
                                            emit("License checked.")
                                        }
                                    },
                                    icon = Icons.Outlined.Refresh,
                                )
                                GhostButton(
                                    text = "Remove license",
                                    onClick = {
                                        scope.launch {
                                            container.licenseService.remove()
                                            emit("License removed from this phone.")
                                        }
                                    },
                                    icon = Icons.Outlined.Delete,
                                )
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Storage & privacy") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            title = "Save original message text",
                            subtitle = "Off keeps only the image and character count.",
                        ) {
                            Switch(
                                checked = settings.saveMessageText,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        container.settingsRepository.updateApp {
                                            it.copy(saveMessageText = checked)
                                        }
                                    }
                                },
                                colors = textPicSwitchColors(),
                            )
                        }
                        Text(
                            "Turning this off affects new renders only. Existing saved text stays until you clear history.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Delete old history", color = colors.textPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0 to "Never", 30 to "30 days", 90 to "90 days", 365 to "365 days").forEach {
                                (value, label) ->
                                FilterChip(
                                    selected = settings.retentionDays == value,
                                    onClick = {
                                        scope.launch {
                                            container.settingsRepository.updateApp {
                                                it.copy(retentionDays = value)
                                            }
                                            RetentionScheduler.schedule(context)
                                            if (value > 0) {
                                                val cutoff = System.currentTimeMillis() -
                                                    TimeUnit.DAYS.toMillis(value.toLong())
                                                container.messageRepository.purgeOlderThan(cutoff)
                                            }
                                            emit(if (value == 0) "Automatic deletion is off." else "Old history purged.")
                                        }
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.surfaceInput,
                                        labelColor = colors.textSecondary,
                                        selectedContainerColor = colors.accentSoft,
                                        selectedLabelColor = colors.textPrimary,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Deletion removes the record and its PNG. Templates and settings are not affected.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            item { SectionLabel("Backup") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Settings JSON contains render preferences, keyboard height, haptics, retention, workspace name, and save-text choice. It never contains rendered images or message text.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                text = "Export",
                                onClick = { exportSettings.launch("textpic-settings.json") },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.FileUpload,
                            )
                            GhostButton(
                                text = "Import",
                                onClick = { importSettings.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.FileDownload,
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Onboarding") }
            item {
                GhostButton(
                    text = "Replay onboarding",
                    onClick = {
                        scope.launch {
                            container.settingsRepository.setOnboardingComplete(false)
                            onFinishOnboarding()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { SectionLabel("Danger zone") }
            item {
                GhostButton(
                    text = "Delete ALL history",
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Delete,
                )
            }

            item { SectionLabel("Updates") }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        val update by container.updateManager.state.collectAsState()
                        SettingsRow(
                            title = "TextPic Studio ${BuildConfig.VERSION_NAME}",
                            subtitle = when {
                                update.checking -> "Checking for updates…"
                                update.info != null ->
                                    "Version ${update.info!!.versionName} is available."
                                else -> update.message ?: "You are on the latest version."
                            },
                        ) {
                            GhostButton(
                                text = "Check for updates",
                                onClick = { container.updateManager.check(force = true) },
                                icon = Icons.Outlined.Refresh,
                            )
                        }
                        if (update.info != null) {
                            Spacer(Modifier.height(6.dp))
                            PrimaryButton(
                                text = "Install ${update.info!!.versionName}",
                                onClick = {
                                    if (update.ready != null) {
                                        container.updateManager.installReady()
                                    } else {
                                        container.updateManager.download()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Only the license check and the update check use the internet. " +
                                "The update check sends nothing but the app version. " +
                                "Android always shows its own install confirmation.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showWorkspaceDialog) {
        WorkspaceDialog(
            initial = settings.workspaceName,
            onDismiss = { showWorkspaceDialog = false },
            onSave = { name ->
                scope.launch {
                    container.settingsRepository.updateApp {
                        it.copy(workspaceName = name.trim().take(60).ifBlank { "My workspace" })
                    }
                    showWorkspaceDialog = false
                    emit("Workspace renamed.")
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surfaceCard,
            title = { Text("Delete all history?", color = colors.textPrimary) },
            text = {
                Text(
                    "Every history record and rendered PNG will be deleted. Templates, custom fonts, and settings stay.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            container.messageRepository.deleteAll()
                            showClearDialog = false
                            emit("All history deleted.")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
                ) { Text("Delete everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun WorkspaceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = LocalTextPicColors.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = { Text("Workspace name", color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.borderFocus,
                    unfocusedBorderColor = colors.borderSubtle,
                    cursorColor = colors.accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("Save", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun textPicSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = LocalTextPicColors.current.textOnAccent,
    checkedTrackColor = LocalTextPicColors.current.accent,
    uncheckedThumbColor = LocalTextPicColors.current.textMuted,
    uncheckedTrackColor = LocalTextPicColors.current.surfaceSelected,
)
