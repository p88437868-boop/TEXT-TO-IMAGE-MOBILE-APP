package com.textpic.studio.ui.settings

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textpic.studio.ui.components.KeycapHint
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors

@Composable
fun HelpScreen() {
    val colors = LocalTextPicColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(
                title = "Help & privacy",
                subtitle = "Everything you need to send a clean chat image.",
            )
        }

        item {
            HelpCard(
                icon = Icons.Outlined.Keyboard,
                title = "Use the keyboard",
                paragraphs = listOf(
                    "Enable TextPic in Settings, then choose it from the keyboard picker.",
                    "Type in any chat field. The preview strip appears above the keys. Tap Render to turn the text into an image and insert it into the chat.",
                    "The R key with a spark badge renders. Shift is a normal modifier; double-tap for caps lock.",
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeycapHint("R", "Render image")
                    KeycapHint("⌫", "Delete selection or character")
                    KeycapHint("123", "Numbers and symbols")
                }
            }
        }

        item {
            HelpCard(
                icon = Icons.Outlined.ContentCopy,
                title = "Copy, share, insert",
                paragraphs = listOf(
                    "Copy sends an image-only clip to the clipboard. No text is placed on the clipboard.",
                    "Share opens the Android share sheet with the PNG.",
                    "Insert from the keyboard adds the rendered image to the active chat field. The app also records the event locally.",
                ),
            )
        }

        item {
            HelpCard(
                icon = Icons.Outlined.Image,
                title = "How rendering works",
                paragraphs = listOf(
                    "Bubble styles render at exactly 460 px wide. Plain styles shrink-wrap the text.",
                    "The output is always an opaque PNG. Rounded corners in the app preview are only a visual aid.",
                    "Messages are limited to 2,000 characters. Oversized images are rejected with a clear message.",
                    "If a selected custom font cannot be loaded, TextPic falls back to the bundled DejaVu Sans and shows the actual font name.",
                ),
            )
        }

        item {
            HelpCard(
                icon = Icons.Outlined.Tune,
                title = "Fill width and transparency",
                paragraphs = listOf(
                    "Fill width pads bubble images to 460 px using the bubble's own background, avoiding white bars in dark chats.",
                    "While Fill width is on, the Transparent switch shows off but remembers your preference. Turning Fill width off restores it.",
                    "Plain styles show transparency directly and hide Fill width.",
                ),
            )
        }

        item {
            HelpCard(
                icon = Icons.Outlined.Lock,
                title = "Privacy",
                paragraphs = listOf(
                    "TextPic has no INTERNET permission and no analytics or crash-reporting SDK.",
                    "Renders, settings, templates, custom fonts, and optional message text live only in this app's local storage.",
                    "Turn off Save original text in Settings to keep only the image. Retention can delete old records automatically.",
                    "The keyboard keeps drafts in memory; they are not shown in history until you choose Render or Insert.",
                ),
            )
        }

        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Bundled fonts",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Inter, DejaVu Sans, Selawik, Arimo, Carlito, Gelasio, and Noto Sans. Licenses are included under licenses/fonts in the source distribution.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Verdana is not redistributed. TextPic uses DejaVu Sans as a metric-compatible stand-in, and uses your own Verdana file if you add one in Settings.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        item {
            Text(
                "TextPic Studio 1.0.0 · built offline",
                color = colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun HelpCard(
    icon: ImageVector,
    title: String,
    paragraphs: List<String>,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = LocalTextPicColors.current
    TextPicCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                paragraphs.forEach { paragraph ->
                    Text(
                        paragraph,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            if (extra != null) {
                Spacer(Modifier.height(10.dp))
                extra()
            }
        }
    }
}
