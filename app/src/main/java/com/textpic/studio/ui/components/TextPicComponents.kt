package com.textpic.studio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textpic.core.StylePreset
import com.textpic.core.StylePresets
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.db.MessageStatus
import com.textpic.studio.ui.theme.LocalTextPicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TextPicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalTextPicColors.current
    val shape = RoundedCornerShape(16.dp)
    val base = modifier
        .clip(shape)
        .background(colors.surfaceCard)
        .border(1.dp, colors.borderSubtle, shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = clickable.padding(16.dp)) {
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = LocalTextPicColors.current.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
    )
}

@Composable
fun ScreenHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.displaySmall,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    chipText: String,
    modifier: Modifier = Modifier,
    chipColor: Color = LocalTextPicColors.current.success,
    icon: ImageVector? = null,
) {
    val colors = LocalTextPicColors.current
    TextPicCard(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(text = label, color = colors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = chipColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = chipText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = chipColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val colors = LocalTextPicColors.current
    val color = when (status) {
        MessageStatus.INSERTED -> colors.success
        MessageStatus.COPIED -> colors.info
        MessageStatus.SHARED -> colors.warning
        else -> colors.textMuted
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalTextPicColors.current
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.textOnAccent,
            disabledContainerColor = colors.surfaceSelected,
            disabledContentColor = colors.textDisabled,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalTextPicColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = LocalTextPicColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = colors.accentSoft,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val colors = LocalTextPicColors.current
    val tint = color ?: colors.textSecondary
    Surface(
        modifier = modifier,
        color = colors.surfaceSelected,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun StyleSelector(
    selectedKey: String,
    onSelect: (StylePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StylePresets.all.forEach { preset ->
            val selected = preset.key == selectedKey
            val bubbleFill = if (preset.isBubble) Color(preset.fill) else colors.surfaceInput
            val bubbleText = Color(preset.text)
            Surface(
                onClick = { onSelect(preset) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) colors.surfaceSelected else colors.surfaceCard,
                border = BorderStroke(
                    1.dp,
                    if (selected) colors.accent else colors.borderSubtle,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 30.dp, height = 18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bubbleFill)
                            .border(1.dp, colors.borderStrong, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Aa", color = bubbleText, fontSize = 8.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = preset.displayName,
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
fun CheckerboardBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalTextPicColors.current
    Box(
        modifier = modifier.background(colors.surfaceInput),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val cell = 8.dp.toPx()
            var row = 0
            var y = 0f
            while (y < size.height) {
                var column = 0
                var x = 0f
                while (x < size.width) {
                    if ((row + column) % 2 == 0) {
                        drawRect(
                            color = colors.surfaceSelected.copy(alpha = 0.45f),
                            topLeft = Offset(x, y),
                            size = Size(cell, cell),
                        )
                    }
                    x += cell
                    column++
                }
                y += cell
                row++
            }
        }
        content()
    }
}

@Composable
fun OutputImage(
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
    contentDescription: String = "Rendered TextPic image",
) {
    CheckerboardBox(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun HistoryThumbnail(path: String?, modifier: Modifier = Modifier) {
    val colors = LocalTextPicColors.current
    val context = LocalContext.current
    val store = LocalAppContainer.current.imageStore
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { store.loadThumbnail(path, 160) }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceInput),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Message thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun DonutChart(
    values: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    centerTitle: String? = null,
    centerValue: String? = null,
    palette: List<Color>? = null,
) {
    val colors = LocalTextPicColors.current
    val arcColors = palette ?: listOf(colors.accent, colors.seriesTwo, colors.seriesThree)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(400)) }
    val total = values.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            var start = -90f
            values.forEachIndexed { index, pair ->
                val sweep = pair.second / total * 360f * progress.value
                drawArc(
                    color = arcColors[index % arcColors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerValue != null) {
                Text(
                    centerValue,
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (centerTitle != null) {
                Text(centerTitle, color = colors.textMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, value: String? = null, modifier: Modifier = Modifier) {
    val colors = LocalTextPicColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = colors.textSecondary, fontSize = 12.sp)
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Text(value, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun KeycapHint(keys: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalTextPicColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceInput)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = colors.keyFill,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, colors.borderStrong),
        ) {
            Text(
                text = keys,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = colors.textSecondary, fontSize = 12.sp)
    }
}
