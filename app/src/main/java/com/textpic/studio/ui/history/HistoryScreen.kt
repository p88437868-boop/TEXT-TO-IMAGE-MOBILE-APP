package com.textpic.studio.ui.history

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.textpic.core.StylePresets
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.db.MessageEntity
import com.textpic.studio.data.db.MessageStatus
import com.textpic.studio.ui.components.CheckerboardBox
import com.textpic.studio.ui.components.EmptyState
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.HistoryThumbnail
import com.textpic.studio.ui.components.InfoPill
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.StatusChip
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.components.rememberGallerySaveAction
import com.textpic.studio.ui.theme.LocalTextPicColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenStudio: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val container = LocalAppContainer.current
    val colors = LocalTextPicColors.current
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val statusFilter by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveWithPermission = rememberGallerySaveAction {
        scope.launch {
            snackbarHostState.showSnackbar("Storage permission is needed to save on this Android version.")
        }
    }
    var searchText by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MessageEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeading(
                    title = "History",
                    subtitle = "Your rendered messages, kept on this device.",
                    trailing = {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("Clear all", color = colors.danger, fontSize = 12.sp)
                        }
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        viewModel.setQuery(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search message text", color = colors.textMuted) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textMuted)
                    },
                    singleLine = true,
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
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val filters = listOf(
                        "" to "All",
                        MessageStatus.RENDERED to "Rendered",
                        MessageStatus.COPIED to "Copied",
                        MessageStatus.SHARED to "Shared",
                        MessageStatus.INSERTED to "Inserted",
                    )
                    filters.forEach { (value, label) ->
                        FilterChip(
                            selected = statusFilter == value,
                            onClick = { viewModel.setStatus(value) },
                            label = { Text(label, fontSize = 12.sp) },
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

            if (messages.loadState.refresh is LoadState.Loading && messages.itemCount == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
            } else if (messages.itemCount == 0) {
                item {
                    EmptyState(
                        title = if (searchText.isBlank()) "Nothing rendered yet" else "No matches",
                        body = if (searchText.isBlank()) {
                            "Images you render appear here automatically."
                        } else {
                            "Try a different search or status filter."
                        },
                        actionText = if (searchText.isBlank()) "Create a message" else null,
                        onAction = if (searchText.isBlank()) onCreate else null,
                        icon = Icons.Outlined.History,
                    )
                }
            }

            items(messages.itemCount) { index ->
                val message = messages[index]
                if (message != null) {
                    HistoryRow(
                        message = message,
                        onClick = { selected = message },
                        onTogglePin = { viewModel.togglePin(message) },
                    )
                }
            }

            if (messages.loadState.append is LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp,
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

    selected?.let { message ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
            containerColor = colors.surfaceCard,
        ) {
            HistoryDetail(
                message = message,
                onEdit = {
                    val text = message.text
                    if (text != null) {
                        selected = null
                        onOpenStudio(text)
                    }
                },
                onCopy = {
                    viewModel.copy(message)
                    selected = null
                },
                onShare = {
                    viewModel.share(message)
                    selected = null
                },
                onSave = {
                    saveWithPermission { viewModel.save(message) }
                    selected = null
                },
                onPin = {
                    viewModel.togglePin(message)
                    selected = null
                },
                onDelete = { showDeleteDialog = true },
            )
        }
    }

    if (showDeleteDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = colors.surfaceCard,
            title = { Text("Delete this message?", color = colors.textPrimary) },
            text = { Text("The record and its image will be removed.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selected?.let { viewModel.delete(it) }
                        selected = null
                        showDeleteDialog = false
                    },
                ) { Text("Delete", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surfaceCard,
            title = { Text("Clear all history?", color = colors.textPrimary) },
            text = {
                Text(
                    "Every saved record and image will be deleted. This cannot be undone.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                ) { Text("Clear everything", color = colors.danger) }
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
private fun HistoryRow(
    message: MessageEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    TextPicCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HistoryThumbnail(
                path = message.imagePath,
                modifier = Modifier.size(width = 68.dp, height = 52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.text ?: "Text not saved",
                        color = if (message.text == null) colors.textMuted else colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (message.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = colors.warning,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(message.status)
                    Spacer(Modifier.width(6.dp))
                    InfoPill(StylePresets.get(message.styleKey).displayName)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(message.createdAt)),
                        color = colors.textMuted,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${message.chars} chars · ${message.widthPx}×${message.heightPx}",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (message.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (message.pinned) "Unpin" else "Pin",
                    tint = if (message.pinned) colors.warning else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
private fun HistoryDetail(
    message: MessageEntity,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    val store = LocalAppContainer.current.imageStore
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = message.imagePath) {
        value = withContext(Dispatchers.IO) { store.loadThumbnail(message.imagePath, 1600) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Message details",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.weight(1f))
            StatusChip(message.status)
        }
        CheckerboardBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Saved message image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = colors.textMuted)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceInput)
                .padding(12.dp),
        ) {
            Text(
                message.text ?: "Text not saved for this image.",
                color = if (message.text == null) colors.textMuted else colors.textPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(StylePresets.get(message.styleKey).displayName)
            InfoPill("${message.widthPx} × ${message.heightPx}")
            InfoPill("${message.chars} chars")
        }
        Text(
            SimpleDateFormat("MMMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(message.createdAt)),
            color = colors.textMuted,
            fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Copy", onCopy, Modifier.weight(1f), icon = Icons.Outlined.Image)
            GhostButton("Share", onShare, Modifier.weight(1f), icon = Icons.Outlined.Share)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Save", onSave, Modifier.weight(1f))
            GhostButton(
                if (message.pinned) "Unpin" else "Pin",
                onPin,
                Modifier.weight(1f),
                icon = Icons.Outlined.PushPin,
            )
        }
        if (message.text != null) {
            GhostButton(
                "Edit as new message",
                onEdit,
                Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Edit,
            )
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete message")
        }
    }
}
