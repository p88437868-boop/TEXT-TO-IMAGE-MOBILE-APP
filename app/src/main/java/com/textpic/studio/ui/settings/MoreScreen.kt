package com.textpic.studio.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textpic.studio.BuildConfig
import com.textpic.studio.R
import com.textpic.studio.ime.ImeSettingsActivity
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.SectionLabel
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors

@Composable
fun MoreScreen(
    onAnalytics: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onCreate: () -> Unit,
    onActivation: () -> Unit,
    licensed: Boolean,
) {
    val colors = LocalTextPicColors.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(
                title = "More",
                subtitle = "Settings, insights, and help.",
            )
        }

        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TextPic Studio",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} · offline by design",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "New chat image",
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Add,
                )
            }
        }

        item { SectionLabel("Explore") }
        item {
            MoreRow(
                title = if (licensed) "Activation & license" else "Activate TextPic",
                subtitle = if (licensed) {
                    "This phone is activated. View licence details."
                } else {
                    "Enter your Activation Key to unlock Render, Insert and export."
                },
                icon = Icons.Outlined.VerifiedUser,
                onClick = onActivation,
            )
        }
        item {
            MoreRow(
                title = "Usage analytics",
                subtitle = "Private render and insertion trends.",
                icon = Icons.Outlined.AutoGraph,
                onClick = onAnalytics,
            )
        }
        item {
            MoreRow(
                title = "Settings",
                subtitle = "Workspace, render defaults, keyboard, fonts, and storage.",
                icon = Icons.Outlined.Settings,
                onClick = onSettings,
            )
        }
        item {
            MoreRow(
                title = "Keyboard setup",
                subtitle = "Enable, select, and tune the TextPic keyboard.",
                icon = Icons.Outlined.Keyboard,
                onClick = { context.startActivity(ImeSettingsActivity.intent(context)) },
            )
        }
        item {
            MoreRow(
                title = "Help & privacy",
                subtitle = "Shortcuts, clipboard behavior, and local data.",
                icon = Icons.Outlined.HelpOutline,
                onClick = onHelp,
            )
        }

        item { SectionLabel("Privacy") }
        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "TextPic has no network permission. Images, settings, and optional message text stay in this app's local storage. The clipboard receives an image only, never text.",
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
private fun MoreRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    TextPicCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.textMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
