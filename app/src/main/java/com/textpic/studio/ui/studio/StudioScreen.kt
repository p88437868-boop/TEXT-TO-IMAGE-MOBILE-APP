package com.textpic.studio.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.textpic.core.Alignments
import com.textpic.core.FontCatalog
import com.textpic.core.StylePresets
import com.textpic.core.ThumbnailFits
import com.textpic.license.QuotaRules
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.license.LicenseSnapshot
import com.textpic.studio.data.prefs.RenderSettings
import com.textpic.studio.ui.components.InfoPill
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.rememberGallerySaveAction
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.SectionLabel
import com.textpic.studio.ui.components.StyleSelector
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors
import kotlinx.coroutines.launch

@Composable
fun StudioRoute(
    incomingText: String?,
    onIncomingTextConsumed: () -> Unit,
    onHistory: () -> Unit,
    onTemplates: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: StudioViewModel = viewModel(factory = StudioViewModel.factory(container))
    StudioScreen(
        viewModel = viewModel,
        incomingText = incomingText,
        onIncomingTextConsumed = onIncomingTextConsumed,
        onHistory = onHistory,
        onTemplates = onTemplates,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    incomingText: String?,
    onIncomingTextConsumed: () -> Unit,
    onHistory: () -> Unit,
    onTemplates: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val license by remember { container.licenseService.state }
        .collectAsStateWithLifecycle(LicenseSnapshot())
    val imagesLeft = QuotaRules.summary(
        license.quota,
        license.pendingUsage,
        System.currentTimeMillis(),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    var text by remember { mutableStateOf("") }
    var showFormatSheet by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var templateTitle by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val renderSettings = settings.render
    val saveWithPermission = rememberGallerySaveAction {
        scope.launch {
            snackbarHostState.showSnackbar("Storage permission is needed to save on this Android version.")
        }
    }

    LaunchedEffect(incomingText) {
        if (!incomingText.isNullOrBlank()) {
            text = incomingText.take(2000)
            onIncomingTextConsumed()
        }
    }

    LaunchedEffect(text, renderSettings) {
        viewModel.renderPreview(text)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (imagesLeft != null) {
                item {
                    Text(
                        imagesLeft,
                        color = if (QuotaRules.isLow(license.quota, license.pendingUsage, System.currentTimeMillis())) {
                            colors.warning
                        } else {
                            colors.textMuted
                        },
                        fontSize = 12.sp,
                    )
                }
            }

            item {
                ScreenHeading(
                    title = "Compose",
                    subtitle = "One renderer, every way you send.",
                    trailing = {
                        Row {
                            IconButton(onClick = onHistory) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = "History",
                                    tint = colors.textSecondary,
                                )
                            }
                            IconButton(onClick = onTemplates) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ViewQuilt,
                                    contentDescription = "Templates",
                                    tint = colors.textSecondary,
                                )
                            }
                        }
                    },
                )
            }

            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Message",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${text.length} / 2000",
                                color = if (text.length >= 1900) colors.warning else colors.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= 2000) text = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 112.dp),
                            placeholder = {
                                Text(
                                    "Type the message you want to turn into a chat image…",
                                    color = colors.textMuted,
                                )
                            },
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surfaceInput,
                                unfocusedContainerColor = colors.surfaceInput,
                                focusedBorderColor = colors.borderFocus,
                                unfocusedBorderColor = colors.borderSubtle,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accent,
                            ),
                        )
                    }
                }
            }

            item {
                StyleSelector(
                    selectedKey = renderSettings.styleKey,
                    onSelect = { preset ->
                        viewModel.updateRender { it.copy(styleKey = preset.key) }
                    },
                )
            }

            item {
                PreviewCard(
                    preview = preview,
                    theme = renderSettings.theme,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "Render & copy",
                        onClick = { viewModel.copyImage(text) },
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank() && !busy,
                        icon = Icons.Outlined.ContentCopy,
                    )
                    GhostButton(
                        text = "Format",
                        onClick = { showFormatSheet = true },
                        modifier = Modifier.width(120.dp),
                        icon = Icons.Outlined.Tune,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        text = "Share",
                        onClick = { viewModel.shareImage(text) },
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank() && !busy,
                        icon = Icons.AutoMirrored.Outlined.Send,
                    )
                    GhostButton(
                        text = "Save",
                        onClick = { saveWithPermission { viewModel.saveToGallery(text) } },
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank() && !busy,
                        icon = Icons.Outlined.Save,
                    )
                }
            }

            item {
                GhostButton(
                    text = "Save as template",
                    onClick = {
                        templateTitle = text.take(40).replace("\n", " ").ifBlank { "New template" }
                        showTemplateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = text.isNotBlank(),
                    icon = Icons.Outlined.Add,
                )
            }

            item {
                Text(
                    "The preview may round corners for realism. The saved, shared and inserted PNG is always the opaque image.",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showFormatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            sheetState = sheetState,
            containerColor = colors.surfaceCard,
        ) {
            FormatSheet(
                settings = renderSettings,
                actualFontName = preview.meta?.fontUsedName
                    ?: container.fontManager.displayName(renderSettings.font),
                fonts = container.fontManager.fontOptions().map { it.key to it.label },
                onUpdate = { transform -> viewModel.updateRender(transform) },
                onResetGolden = { viewModel.resetGolden() },
            )
        }
    }

    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            containerColor = colors.surfaceCard,
            title = { Text("Save as template", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = templateTitle,
                    onValueChange = { templateTitle = it.take(60) },
                    label = { Text("Template title") },
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
                TextButton(
                    onClick = {
                        viewModel.saveTemplate(templateTitle, text)
                        showTemplateDialog = false
                    },
                ) { Text("Save", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun PreviewCard(
    preview: StudioPreview,
    theme: String,
) {
    val colors = LocalTextPicColors.current
    val bitmap = preview.bitmap
    val meta = preview.meta
    TextPicCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Live preview",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.weight(1f))
                if (meta != null) {
                    InfoPill("${meta.widthPx} × ${meta.heightPx}", color = colors.accent)
                } else {
                    InfoPill("No image yet")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta != null) {
                    Text(
                        "Font: ${meta.fontUsedName}",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    InfoPill(if (meta.isOpaque) "Opaque PNG" else "Transparent PNG")
                } else {
                    Text(
                        "The exact output dimensions appear here.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val chatBackground = if (theme == "light") colors.seriesTwo else colors.bgCanvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(chatBackground)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preview.error != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = colors.warning,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                preview.error,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    bitmap != null -> {
                        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        var imageWidth = maxWidth
                        var imageHeight = imageWidth / ratio
                        if (imageHeight > 300.dp) {
                            imageHeight = 300.dp
                            imageWidth = imageHeight * ratio
                        }
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Live rendered preview ${bitmap.width} by ${bitmap.height}",
                            modifier = Modifier
                                .size(imageWidth, imageHeight)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    else -> Text(
                        if (preview.rendering) "Rendering…" else "Your message preview will appear here.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSheet(
    settings: RenderSettings,
    actualFontName: String,
    fonts: List<Pair<String, String>>,
    onUpdate: ((RenderSettings) -> RenderSettings) -> Unit,
    onResetGolden: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    val fillAvailable = settings.isBubble
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Format image",
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onResetGolden) {
                Text("Golden profile", color = colors.accent, fontSize = 12.sp)
            }
        }

        SectionLabel("Style")
        StyleSelector(
            selectedKey = settings.styleKey,
            onSelect = { preset -> onUpdate { it.copy(styleKey = preset.key) } },
        )

        SectionLabel("Chat theme")
        ChoiceRow(
            options = listOf("light" to "Light", "dark" to "Dark"),
            selected = settings.theme,
            onSelected = { value -> onUpdate { it.copy(theme = value) } },
        )

        SectionLabel("Font")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fonts.forEach { (key, label) ->
                FilterChip(
                    selected = settings.font == key,
                    onClick = { onUpdate { it.copy(font = key) } },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = filterChipColors(),
                )
            }
        }
        Text(
            "Actual font used: $actualFontName",
            color = colors.textMuted,
            fontSize = 11.sp,
        )

        SectionLabel("Size and layout")
        StepperRow(
            label = "Font size",
            value = "${settings.fontSize} px",
            onMinus = { onUpdate { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(10)) } },
            onPlus = { onUpdate { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(48)) } },
        )
        StepperRow(
            label = "Max width",
            value = "${settings.maxWidth} px",
            onMinus = { onUpdate { it.copy(maxWidth = (it.maxWidth - 10).coerceAtLeast(80)) } },
            onPlus = { onUpdate { it.copy(maxWidth = (it.maxWidth + 10).coerceAtMost(600)) } },
        )
        ChoiceRow(
            options = listOf(Alignments.LEFT to "Left", Alignments.RIGHT to "Right"),
            selected = settings.alignment,
            onSelected = { value -> onUpdate { it.copy(alignment = value) } },
        )
        ChoiceRow(
            options = listOf(
                "1.0" to "1.0×",
                "1.5" to "1.5×",
                "2.0" to "2.0×",
                "3.0" to "3.0×",
            ),
            selected = settings.scale.toString(),
            onSelected = { value -> onUpdate { it.copy(scale = value.toFloat()) } },
        )

        SectionLabel("Fill and transparency")
        if (fillAvailable) {
            SwitchRow(
                title = "Fill width (no white bars)",
                subtitle = "Extends the bubble to 460 px with its own fill color.",
                checked = settings.fillWidth,
                onCheckedChange = { checked -> onUpdate { it.copy(fillWidth = checked) } },
            )
        }
        SwitchRow(
            title = "Transparent PNG",
            subtitle = if (fillAvailable && settings.fillWidth) {
                "Off while Fill width is on. Your preference is remembered."
            } else {
                "Keeps the background transparent outside the text."
            },
            checked = if (fillAvailable && settings.fillWidth) false else settings.transparent,
            enabled = !(fillAvailable && settings.fillWidth),
            onCheckedChange = { checked -> onUpdate { it.copy(transparent = checked) } },
        )

        SectionLabel("Advanced")
        StepperRow(
            label = "Horizontal padding",
            value = "${settings.paddingX} px",
            onMinus = { onUpdate { it.copy(paddingX = (it.paddingX - 1).coerceAtLeast(0)) } },
            onPlus = { onUpdate { it.copy(paddingX = (it.paddingX + 1).coerceAtMost(48)) } },
        )
        StepperRow(
            label = "Vertical padding",
            value = "${settings.paddingY} px",
            onMinus = { onUpdate { it.copy(paddingY = (it.paddingY - 1).coerceAtLeast(0)) } },
            onPlus = { onUpdate { it.copy(paddingY = (it.paddingY + 1).coerceAtMost(48)) } },
        )
        Text("Thumbnail fit", color = colors.textSecondary, fontSize = 12.sp)
        ChoiceRow(
            options = listOf(
                ThumbnailFits.OFF to "Off",
                ThumbnailFits.WIDE to "Wide",
                ThumbnailFits.SQUARE to "Square",
            ),
            selected = settings.thumbnailFit,
            onSelected = { value -> onUpdate { it.copy(thumbnailFit = value) } },
        )
        Text(
            "Renderer fonts: ${FontCatalog.standard.map { it.bundledLabel }.joinToString(", ")}. Add a font file in Settings.",
            color = colors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label, fontSize = 12.sp) },
                colors = filterChipColors(),
            )
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = LocalTextPicColors.current.surfaceInput,
    labelColor = LocalTextPicColors.current.textSecondary,
    selectedContainerColor = LocalTextPicColors.current.accentSoft,
    selectedLabelColor = LocalTextPicColors.current.textPrimary,
)

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease $label", tint = colors.textSecondary)
        }
        Surface(
            color = colors.surfaceInput,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.borderSubtle),
        ) {
            Text(
                value,
                modifier = Modifier
                    .width(72.dp)
                    .padding(vertical = 8.dp),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        IconButton(onClick = onPlus) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase $label", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.textPrimary else colors.textDisabled, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = colors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.textOnAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceSelected,
                disabledUncheckedThumbColor = colors.textDisabled,
                disabledUncheckedTrackColor = colors.surfaceInput,
            ),
        )
    }
}
