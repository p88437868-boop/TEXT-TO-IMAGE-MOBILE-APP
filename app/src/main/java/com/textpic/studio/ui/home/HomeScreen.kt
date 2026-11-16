package com.textpic.studio.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InsertPhoto
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.R
import com.textpic.studio.data.db.MessageEntity
import com.textpic.studio.ime.ImeSettingsActivity
import com.textpic.studio.ui.components.HistoryThumbnail
import com.textpic.studio.ui.components.InfoPill
import com.textpic.studio.ui.components.MetricCard
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.components.SectionLabel
import com.textpic.studio.ui.components.StatusChip
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.LocalTextPicColors
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onHistory: () -> Unit,
    onAnalytics: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val dao = container.database.messageDao()
    val recent by dao.observeRecent(4).collectAsStateWithLifecycle(emptyList())
    val total by dao.observeTotal().collectAsStateWithLifecycle(0)
    val weekSince = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    val sentThisWeek by dao.observeInsertedSharedSince(weekSince).collectAsStateWithLifecycle(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextPicCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Rendered messages, ready to send.",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp,
                                lineHeight = 25.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Type once. Render a clean chat image. Copy, share, or insert it without retyping.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Create a chat image",
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Add,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    label = "Renders",
                    value = total.toString().padStart(2, '0'),
                    chipText = "on device",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.InsertPhoto,
                )
                MetricCard(
                    label = "Sent this week",
                    value = sentThisWeek.toString().padStart(2, '0'),
                    chipText = "inserted + shared",
                    modifier = Modifier.weight(1f),
                    chipColor = colors.info,
                    icon = Icons.Outlined.Send,
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent renders", Modifier.weight(1f))
                TextButton(onClick = onHistory) {
                    Text("View all", color = colors.accent, fontSize = 12.sp)
                }
            }
        }

        item {
            if (recent.isEmpty()) {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = colors.textMuted)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("No renders yet", color = colors.textPrimary, fontSize = 13.sp)
                            Text(
                                "Your first image will appear here.",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.id }) { message ->
                        RecentCard(message = message, onClick = onHistory)
                    }
                }
            }
        }

        item {
            TextPicCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(ImeSettingsActivity.intent(context))
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Keyboard & render accuracy", color = colors.textPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Enable TextPic once, then type into any chat with an image-ready preview strip.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    Icon(
                        Icons.Outlined.FiberManualRecord,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(8.dp),
                    )
                }
            }
        }

        item {
            TextPicCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAnalytics,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoGraph, contentDescription = null, tint = colors.info)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Usage at a glance", color = colors.textPrimary, fontSize = 13.sp)
                        Text(
                            "Private, offline analytics from this device only.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Text("Open", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "No network permission. No analytics SDK. Everything stays on this device.",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun RecentCard(message: MessageEntity, onClick: () -> Unit) {
    val colors = LocalTextPicColors.current
    TextPicCard(
        modifier = Modifier.width(190.dp),
        onClick = onClick,
    ) {
        Column {
            HistoryThumbnail(
                path = message.imagePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message.text ?: "Image only",
                color = if (message.text == null) colors.textMuted else colors.textPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(message.status)
                Spacer(Modifier.weight(1f))
                InfoPill("${message.widthPx}px")
            }
        }
    }
}
