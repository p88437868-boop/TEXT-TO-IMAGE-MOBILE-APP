package com.textpic.studio.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.textpic.license.LicenseMessages
import com.textpic.license.LicenseRules
import com.textpic.license.LicenseState
import com.textpic.license.QuotaRules
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.license.ActivationResult
import com.textpic.studio.data.license.LicenseSnapshot
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Activation: the customer sends the Device Code to the owner, receives an
 * Activation Key and pastes it here. The key is only stored after the owner's
 * server signs a reply for this exact phone.
 */
@Composable
fun ActivationScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val colors = LocalTextPicColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by remember { container.licenseService.state }
        .collectAsStateWithLifecycle(LicenseSnapshot())
    var keyText by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    var localIsError by remember { mutableStateOf(true) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedBorderColor = colors.borderFocus,
        unfocusedBorderColor = colors.borderSubtle,
        focusedContainerColor = colors.surfaceInput,
        unfocusedContainerColor = colors.surfaceInput,
        cursorColor = colors.accent,
        focusedLabelColor = colors.textMuted,
        unfocusedLabelColor = colors.textMuted,
        focusedPlaceholderColor = colors.textDisabled,
        unfocusedPlaceholderColor = colors.textDisabled,
    )

    val deviceCode = snapshot.deviceCode.ifBlank { "…" }
    val message = localMessage ?: snapshot.message

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(
                title = "Activation",
                subtitle = "One key per person. A key only unlocks the phone it was made for.",
            )
        }

        if (snapshot.licensed) {
            item { ActivatedCard(snapshot) }
            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Render, Insert, Copy, Share and Save are unlocked on this phone.",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Keep your Activation Key somewhere safe: you need it again after a " +
                                "factory reset or on a new phone (the owner can also move your license).",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = "Check now",
                        onClick = {
                            scope.launch {
                                localMessage = null
                                val updated = container.licenseService.refresh(online = true)
                                localIsError = updated.state != LicenseState.LICENSED
                                localMessage = if (localIsError) {
                                    updated.message ?: LicenseMessages.SERVER_UNREACHABLE
                                } else {
                                    "License checked. Everything is up to date."
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !snapshot.working,
                        icon = Icons.Outlined.Refresh,
                    )
                }
            }
            return@LazyColumn
        }

        if (message != null) {
            item { StatusBanner(message, error = localIsError || !snapshot.needsOnlineCheck) }
        }

        item { StepHeader(number = 1, title = "Send your Device Code") }
        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "This code identifies this phone. Send it to the owner on WhatsApp, by " +
                            "email or by SMS. It is not a password and it does not contain your messages.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceInput)
                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = deviceCode,
                            modifier = Modifier.weight(1f),
                            color = colors.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(letterSpacing = 1.4.sp),
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(deviceCode))
                                localIsError = false
                                localMessage = "Device Code copied."
                            },
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Device Code",
                                tint = colors.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        text = "Share Device Code",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "My TextPic Studio Device Code: $deviceCode",
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, "Send Device Code"),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Share,
                    )
                }
            }
        }

        item { StepHeader(number = 2, title = "Paste your Activation Key") }
        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "The owner sends back a key like ABCD-EFGH-JKLM-NPQR. Paste it below and " +
                            "tap Activate. The phone needs internet only for this check.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = {
                            keyText = it.uppercase()
                            localMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                        ),
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        text = "Paste from clipboard",
                        onClick = {
                            val pasted = clipboard.getText()?.text
                            if (pasted.isNullOrBlank()) {
                                localIsError = true
                                localMessage = "The clipboard is empty."
                            } else {
                                keyText = pasted.trim().uppercase()
                                localMessage = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.ContentPaste,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = if (snapshot.working) "Checking…" else "Activate",
                        onClick = {
                            scope.launch {
                                localMessage = null
                                localIsError = false
                                when (val result = container.licenseService.activate(keyText)) {
                                    is ActivationResult.Activated -> {
                                        keyText = ""
                                        localMessage = buildString {
                                            append("Activated")
                                            if (result.name.isNotBlank()) append(" for ${result.name}")
                                            append(".")
                                            result.expiresAt?.let { expires ->
                                                val days = LicenseRules.daysLeft(
                                                    expires,
                                                    System.currentTimeMillis(),
                                                )
                                                append(" Valid until ${formatDate(expires)}")
                                                if (days != null) append(" ($days days).")
                                            } ?: append(" This key never expires.")
                                        }
                                    }
                                    is ActivationResult.Rejected -> {
                                        localIsError = true
                                        localMessage = result.message
                                    }
                                    is ActivationResult.CannotReachServer -> {
                                        localIsError = true
                                        localMessage = if (result.detail.isBlank()) {
                                            LicenseMessages.OFFLINE_ACTIVATE
                                        } else {
                                            LicenseMessages.unreachable(result.detail)
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = keyText.isNotBlank() && !snapshot.working,
                        icon = Icons.Outlined.CheckCircle,
                    )
                }
            }
        }

        if (snapshot.needsOnlineCheck) {
            item {
                GhostButton(
                    text = "Check now",
                    onClick = {
                        scope.launch {
                            localMessage = null
                            val updated = container.licenseService.refresh(online = true)
                            localIsError = updated.state != LicenseState.LICENSED
                            localMessage = updated.message
                                ?: "License checked. Everything is up to date."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !snapshot.working,
                    icon = Icons.Outlined.Refresh,
                )
            }
        }

        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "How it works",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. You send the Device Code above.\n" +
                            "2. The owner creates a key for exactly this phone and sends it back.\n" +
                            "3. Paste the key, tap Activate, and the app unlocks.\n\n" +
                            "The key keeps working offline for a week at a time. Typing is always " +
                            "free; without a key only making images is locked. If the owner blocks " +
                            "or cancels the license, the app locks at the next check.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivatedCard(snapshot: LicenseSnapshot) {
    val colors = LocalTextPicColors.current
    val now = System.currentTimeMillis()
    TextPicCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Activated",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (snapshot.name.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(snapshot.name, color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val daysLeft = LicenseRules.daysLeft(snapshot.expiresAt, now)
            DetailRow(
                "Valid until",
                when {
                    snapshot.expiresAt == null -> "Lifetime"
                    daysLeft == null -> formatDate(snapshot.expiresAt)
                    daysLeft <= 1 -> "${formatDate(snapshot.expiresAt)} (last day)"
                    else -> "${formatDate(snapshot.expiresAt)} ($daysLeft days left)"
                },
            )
            QuotaRules.summary(snapshot.quota, snapshot.pendingUsage, now)?.let { summary ->
                DetailRow("Images", summary)
            }
            DetailRow("This phone", snapshot.deviceCode.ifBlank { "—" })
            snapshot.lastCheckedAt?.let { checked ->
                DetailRow("Last checked", "${formatDate(checked)} · ${formatTime(checked)}")
            }
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: String) {
    val colors = LocalTextPicColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$number",
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.18f))
                .padding(top = 4.dp),
            color = colors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusBanner(message: String, error: Boolean) {
    val colors = LocalTextPicColors.current
    val tint = if (error) colors.danger else colors.success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (error) Icons.Outlined.Refresh else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(message, color = tint, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(label, color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.width(104.dp))
        Text(value, color = colors.textPrimary, fontSize = 12.sp)
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
