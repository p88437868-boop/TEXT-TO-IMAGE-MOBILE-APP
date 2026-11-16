package com.textpic.studio.ui.templates

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.db.TemplateEntity
import com.textpic.studio.ui.components.EmptyState
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.SectionLabel
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors
import kotlinx.coroutines.launch

@Composable
fun TemplatesScreen(onUseTemplate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val colors = LocalTextPicColors.current
    val scope = rememberCoroutineScope()
    val templates by container.templateRepository.templates.collectAsStateWithLifecycle(emptyList())
    var editorTarget by remember { mutableStateOf<TemplateEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TemplateEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(
                title = "Templates",
                subtitle = "Reusable messages with {placeholders}.",
                trailing = {
                    TextButton(
                        onClick = {
                            editorTarget = null
                            showEditor = true
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent)
                        Spacer(Modifier.width(4.dp))
                        Text("New", color = colors.accent, fontSize = 12.sp)
                    }
                },
            )
        }

        item {
            TextPicCard {
                Column {
                    SectionLabel("Placeholder cheat sheet")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "{name} · {product} · {price} · {company}",
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Placeholders stay editable text in the image. Nothing is auto-filled with customer data.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        if (templates.isEmpty()) {
            item {
                EmptyState(
                    title = "No templates yet",
                    body = "Save a message from Compose, or create one here.",
                    actionText = "Create a template",
                    onAction = {
                        editorTarget = null
                        showEditor = true
                    },
                    icon = Icons.Outlined.GridView,
                )
            }
        }

        items(templates, key = { it.id }) { template ->
            TemplateCard(
                template = template,
                onUse = { onUseTemplate(template.body) },
                onEdit = {
                    editorTarget = template
                    showEditor = true
                },
                onDelete = { deleteTarget = template },
            )
        }
    }

    if (showEditor) {
        TemplateEditorDialog(
            template = editorTarget,
            onDismiss = { showEditor = false },
            onSave = { title, body ->
                scope.launch {
                    val target = editorTarget
                    if (target == null) {
                        container.templateRepository.add(title, body)
                    } else {
                        container.templateRepository.update(target.copy(title = title, body = body))
                    }
                    showEditor = false
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = colors.surfaceCard,
            title = { Text("Delete template?", color = colors.textPrimary) },
            text = { Text("“${target.title}” will be removed from this device.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.templateRepository.delete(target) }
                        deleteTarget = null
                    },
                ) { Text("Delete", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun TemplateCard(
    template: TemplateEntity,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    TextPicCard(Modifier.fillMaxWidth()) {
        Column {
            Text(
                template.title,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                template.body,
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Use",
                    onClick = onUse,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Send,
                )
                GhostButton(
                    text = "Edit",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Edit,
                )
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete template")
                }
            }
        }
    }
}

@Composable
private fun TemplateEditorDialog(
    template: TemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val colors = LocalTextPicColors.current
    var title by remember(template) { mutableStateOf(template?.title ?: "") }
    var body by remember(template) { mutableStateOf(template?.body ?: "") }
    val valid = title.isNotBlank() && body.isNotBlank() && body.length <= 2000

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = {
            Text(if (template == null) "New template" else "Edit template", color = colors.textPrimary)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= 2000) body = it },
                    label = { Text("Message") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = dialogFieldColors(),
                )
                Spacer(Modifier.height(4.dp))
                Text("${body.length} / 2000", color = colors.textMuted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), body.trim()) }, enabled = valid) {
                Text("Save", color = if (valid) colors.accent else colors.textDisabled)
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
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalTextPicColors.current.textPrimary,
    unfocusedTextColor = LocalTextPicColors.current.textPrimary,
    focusedBorderColor = LocalTextPicColors.current.borderFocus,
    unfocusedBorderColor = LocalTextPicColors.current.borderSubtle,
    cursorColor = LocalTextPicColors.current.accent,
)
