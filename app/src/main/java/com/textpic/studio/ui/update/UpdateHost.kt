package com.textpic.studio.ui.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.update.UpdateInstaller
import com.textpic.studio.data.update.UpdateRules
import com.textpic.studio.ui.theme.LocalTextPicColors

/**
 * Shows "Update available" once per day (or a blocking notice when the release
 * is mandatory), downloads with progress, then hands over to Android's installer.
 */
@Composable
fun UpdateHost() {
    val container = LocalAppContainer.current
    val manager = container.updateManager
    val state by manager.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        manager.cleanup()
        manager.checkIfDue()
    }

    val info = state.info ?: return
    if (state.dismissed) return

    val colors = LocalTextPicColors.current
    AlertDialog(
        onDismissRequest = {
            if (!state.mandatory) manager.later()
        },
        title = {
            Text(
                if (state.mandatory) "Update required" else "Update available",
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "TextPic ${info.versionName} · ${UpdateRules.formatBytes(info.sizeBytes)}",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
                if (info.notes.isNotBlank()) {
                    Text(info.notes, color = colors.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (state.downloading) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Downloading… ${(state.progress * 100).toInt()}%",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                state.message?.let { message ->
                    Text(message, color = colors.danger, fontSize = 11.sp)
                }
                if (state.ready != null) {
                    Text(
                        "Downloaded and verified. Android will ask you to confirm the install.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                if (state.mandatory) {
                    Text(
                        "Rendering stays locked until TextPic is updated. The keyboard keeps typing normally.",
                        color = colors.danger,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.mandatory) {
                    TextButton(onClick = {
                        context.startActivity(UpdateInstaller.permissionIntent(context))
                    }) {
                        Text("Allow installs", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = {
                        if (state.ready != null) {
                            if (!UpdateInstaller.canInstall(context)) {
                                context.startActivity(UpdateInstaller.permissionIntent(context))
                            } else {
                                manager.installReady()
                            }
                        } else {
                            manager.download()
                        }
                    },
                    enabled = !state.downloading,
                ) {
                    Text(
                        when {
                            state.ready != null -> "Install"
                            state.downloading -> "Downloading…"
                            else -> "Update"
                        },
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        dismissButton = {
            if (!state.mandatory) {
                TextButton(onClick = { manager.later() }) {
                    Text("Later", color = colors.textMuted, fontSize = 12.sp)
                }
            }
        },
        containerColor = colors.surfaceCard,
    )
}

/** Kept for the settings row so the wording lives in one place. */
fun updatePermissionHint(context: Context): String =
    if (UpdateInstaller.canInstall(context)) {
        "Updates install after you confirm on Android's screen."
    } else {
        "Allow TextPic to install updates first (Settings → Install unknown apps)."
    }
