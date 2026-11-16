package com.textpic.studio.ui.analytics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.style.ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.component.shape.DashedShape
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import com.textpic.core.StylePresets
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.data.db.DailyCount
import com.textpic.studio.data.db.DailyStatusCount
import com.textpic.studio.data.db.MessageStatus
import com.textpic.studio.data.db.StyleCount
import com.textpic.studio.ui.components.DonutChart
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.LegendDot
import com.textpic.studio.ui.components.MetricCard
import com.textpic.studio.ui.components.ScreenHeading
import com.textpic.studio.ui.components.SectionLabel
import com.textpic.studio.ui.components.TextPicCard
import com.textpic.studio.ui.theme.BgCanvas
import com.textpic.studio.ui.theme.LocalTextPicColors
import com.textpic.studio.ui.theme.SurfaceCard
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AnalyticsRange(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "90 days"),
}

private data class DailyStat(
    val day: String,
    val label: String,
    val total: Int,
    val inserted: Int,
    val shared: Int,
    val copied: Int,
    val rendered: Int,
)

@Composable
fun AnalyticsScreen() {
    val colors = LocalTextPicColors.current
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val dao = container.database.messageDao()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var range by rememberSaveable { mutableStateOf(AnalyticsRange.WEEK) }
    var exporting by remember { mutableStateOf(false) }
    val since = remember(range) {
        System.currentTimeMillis() - range.days.toLong() * 24L * 60L * 60L * 1000L
    }

    val dailyCounts by dao.observeDailyCounts(since).collectAsStateWithLifecycle(emptyList())
    val statusCounts by dao.observeDailyStatusCounts(since).collectAsStateWithLifecycle(emptyList())
    val styleCounts by dao.observeStyleCountsSince(since).collectAsStateWithLifecycle(emptyList())
    val averageChars by dao.observeAverageCharsSince(since).collectAsStateWithLifecycle(null)

    val daily = remember(range, dailyCounts, statusCounts) {
        buildDailyStats(range, dailyCounts, statusCounts)
    }
    val total = daily.sumOf { it.total }
    val sent = daily.sumOf { it.inserted + it.shared }
    val activeDays = daily.count { it.total > 0 }
    val styleValues = remember(styleCounts) { buildStyleValues(styleCounts) }
    val topStyle = styleValues.firstOrNull()?.first ?: "None yet"

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeading(
                    title = "Analytics",
                    subtitle = "A private, offline view of what you have rendered on this device.",
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalyticsRange.entries.forEach { option ->
                        val selected = option == range
                        Surface(
                            onClick = { range = option },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) colors.accentSoft else colors.surfaceCard,
                            border = BorderStroke(
                                1.dp,
                                if (selected) colors.accent else colors.borderSubtle,
                            ),
                        ) {
                            Text(
                                text = option.label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (selected) colors.accent else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        label = "Renders",
                        value = total.toString().padStart(2, '0'),
                        chipText = range.label,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Image,
                    )
                    MetricCard(
                        label = "Sent",
                        value = sent.toString().padStart(2, '0'),
                        chipText = "inserted + shared",
                        modifier = Modifier.weight(1f),
                        chipColor = colors.info,
                        icon = Icons.Outlined.AutoGraph,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        label = "Avg length",
                        value = (averageChars?.roundToInt() ?: 0).toString(),
                        chipText = "characters",
                        modifier = Modifier.weight(1f),
                        chipColor = colors.success,
                    )
                    MetricCard(
                        label = "Active days",
                        value = activeDays.toString(),
                        chipText = "of ${range.days}",
                        modifier = Modifier.weight(1f),
                        chipColor = colors.warning,
                    )
                }
            }

            item {
                ChartCard(
                    title = "Renders per day",
                    subtitle = "Bar height is the number of images rendered each day.",
                ) {
                    if (total == 0) {
                        NoData()
                    } else {
                        DailyBarChart(daily, range)
                    }
                }
            }

            item {
                ChartCard(
                    title = "Inserted vs shared",
                    subtitle = "How rendered images left the app in this range.",
                ) {
                    if (total == 0) {
                        NoData()
                    } else {
                        DeliveryLineChart(daily)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            LegendDot(colors.accent, "Inserted")
                            LegendDot(colors.seriesTwo, "Shared")
                        }
                    }
                }
            }

            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Style usage",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Top presets in this range. Total renders: $total.",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (styleValues.isEmpty()) {
                            NoData()
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DonutChart(
                                    values = styleValues,
                                    modifier = Modifier.size(132.dp),
                                    centerTitle = "renders",
                                    centerValue = total.toString(),
                                    palette = listOf(
                                        colors.accent,
                                        colors.seriesTwo,
                                        colors.seriesThree,
                                        colors.borderStrong,
                                    ),
                                )
                                Spacer(Modifier.width(18.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    styleValues.forEachIndexed { index, pair ->
                                        val palette = listOf(
                                            colors.accent,
                                            colors.seriesTwo,
                                            colors.seriesThree,
                                            colors.borderStrong,
                                        )
                                        LegendDot(
                                            color = palette[index % palette.size],
                                            label = pair.first,
                                            value = pair.second.roundToInt().toString(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                TextPicCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoGraph,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Most used style",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                topStyle,
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "On device",
                            color = colors.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = "Export CSV",
                        onClick = {
                            if (!exporting) {
                                exporting = true
                                scope.launch {
                                    runCatching {
                                        val file = withContext(Dispatchers.IO) {
                                            writeCsv(context, range, daily)
                                        }
                                        withContext(Dispatchers.Main) {
                                            shareCsv(context, file)
                                        }
                                    }.onFailure {
                                        snackbar.showSnackbar(
                                            it.message ?: "Could not export the CSV.",
                                        )
                                    }
                                    exporting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Description,
                    )
                    GhostButton(
                        text = "Export PNG",
                        onClick = {
                            if (!exporting) {
                                exporting = true
                                scope.launch {
                                    runCatching {
                                        val bitmap = withContext(Dispatchers.Default) {
                                            buildExportBitmap(
                                                range = range,
                                                daily = daily,
                                                total = total,
                                                sent = sent,
                                                averageChars = averageChars?.roundToInt() ?: 0,
                                                activeDays = activeDays,
                                                styleValues = styleValues,
                                            )
                                        }
                                        val file = withContext(Dispatchers.IO) {
                                            container.imageStore.saveShare(bitmap)
                                        }
                                        withContext(Dispatchers.Main) {
                                            container.imageStore.launchShare(file)
                                        }
                                    }.onFailure {
                                        snackbar.showSnackbar(
                                            it.message ?: "Could not export the image.",
                                        )
                                    }
                                    exporting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Image,
                    )
                }
            }

            item {
                Text(
                    "Analytics are calculated from the renders stored on this device. No message text, keyboard input, or personal data is transmitted.",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalTextPicColors.current
    TextPicCard(Modifier.fillMaxWidth()) {
        Column {
            Text(
                title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = colors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun NoData() {
    val colors = LocalTextPicColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No renders in this range yet.",
            color = colors.textMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DailyBarChart(daily: List<DailyStat>, range: AnalyticsRange) {
    val colors = LocalTextPicColors.current
    val chartStyle = remember(colors) { vicoStyle(colors) }
    val labels = remember(daily) { daily.map { it.label } }
    val model = remember(daily) {
        entryModelOf(*daily.map { it.total as Number }.toTypedArray())
    }
    ProvideChartStyle(chartStyle) {
        val startAxis = rememberStartAxis(
            label = axisLabelComponent(color = colors.textMuted, textSize = 11.sp),
            guideline = dashedGuideline(colors.borderStrong.copy(alpha = 0.6f)),
            axis = null,
            tick = null,
        )
        val bottomAxis = rememberBottomAxis(
            label = axisLabelComponent(color = colors.textMuted, textSize = 11.sp),
            axis = null,
            tick = null,
            valueFormatter = remember(labels) { LabelAxisFormatter(labels) },
            itemPlacer = remember(range) { AxisItemPlacer.Horizontal.default(spacing = range.spacing) },
        )
        Chart(
            chart = columnChart(),
            model = model,
            modifier = Modifier,
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            isZoomEnabled = false,
        )
    }
}

@Composable
private fun DeliveryLineChart(daily: List<DailyStat>) {
    val colors = LocalTextPicColors.current
    val chartStyle = remember(colors) { vicoStyle(colors) }
    val labels = remember(daily) { daily.map { it.label } }
    val inserted = remember(daily) {
        daily.mapIndexed { index, stat -> entryOf(index.toFloat(), stat.inserted.toFloat()) }
    }
    val shared = remember(daily) {
        daily.mapIndexed { index, stat -> entryOf(index.toFloat(), stat.shared.toFloat()) }
    }
    val model = remember(inserted, shared) { entryModelOf(inserted, shared) }
    ProvideChartStyle(chartStyle) {
        val startAxis = rememberStartAxis(
            label = axisLabelComponent(color = colors.textMuted, textSize = 11.sp),
            guideline = dashedGuideline(colors.borderStrong.copy(alpha = 0.6f)),
            axis = null,
            tick = null,
        )
        val bottomAxis = rememberBottomAxis(
            label = axisLabelComponent(color = colors.textMuted, textSize = 11.sp),
            axis = null,
            tick = null,
            valueFormatter = remember(labels) { LabelAxisFormatter(labels) },
            itemPlacer = remember(daily) { AxisItemPlacer.Horizontal.default(spacing = labels.spacing) },
        )
        Chart(
            chart = lineChart(
                lines = listOf(
                    lineSpec(lineColor = colors.accent, lineThickness = 2.dp, lineBackgroundShader = null),
                    lineSpec(lineColor = colors.seriesTwo, lineThickness = 2.dp, lineBackgroundShader = null),
                ),
            ),
            model = model,
            modifier = Modifier,
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            isZoomEnabled = false,
        )
    }
}

private val AnalyticsRange.spacing: Int
    get() = when (this) {
        AnalyticsRange.WEEK -> 1
        AnalyticsRange.MONTH -> 5
        AnalyticsRange.QUARTER -> 15
    }

private val List<String>.spacing: Int
    get() = when {
        size <= 7 -> 1
        size <= 30 -> 5
        else -> 15
    }

private fun vicoStyle(colors: com.textpic.studio.ui.theme.TextPicColors): ChartStyle =
    ChartStyle.fromColors(
        axisLabelColor = colors.textMuted,
        axisGuidelineColor = colors.borderStrong,
        axisLineColor = colors.borderSubtle,
        entityColors = listOf(colors.accent, colors.seriesTwo, colors.seriesThree),
        elevationOverlayColor = colors.surfaceCard,
    )

private fun dashedGuideline(color: Color): LineComponent =
    LineComponent(
        color = color.toArgb(),
        thicknessDp = 1f,
        shape = DashedShape(),
    )

private class LabelAxisFormatter(
    private val labels: List<String>,
) : AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
    override fun formatValue(value: Float, chartValues: ChartValues): CharSequence =
        labels.getOrElse(value.roundToInt()) { "" }
}

private fun buildDailyStats(
    range: AnalyticsRange,
    daily: List<DailyCount>,
    statuses: List<DailyStatusCount>,
): List<DailyStat> {
    val totals = daily.associate { it.day to it.count }
    val byStatus = statuses.groupBy { it.day }
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val today = LocalDate.now()
    return (range.days - 1 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val key = date.toString()
        val status = byStatus[key].orEmpty()
        fun countFor(statusName: String): Int =
            status.firstOrNull { it.status == statusName }?.count ?: 0
        DailyStat(
            day = key,
            label = date.format(formatter),
            total = totals[key] ?: 0,
            inserted = countFor(MessageStatus.INSERTED),
            shared = countFor(MessageStatus.SHARED),
            copied = countFor(MessageStatus.COPIED),
            rendered = countFor(MessageStatus.RENDERED),
        )
    }
}

private fun buildStyleValues(counts: List<StyleCount>): List<Pair<String, Float>> {
    if (counts.isEmpty()) return emptyList()
    val values = counts.map { StylePresets.get(it.styleKey).displayName to it.count.toFloat() }
    if (values.size <= 4) return values
    val top = values.take(3)
    val other = values.drop(3).sumOf { it.second.toDouble() }.toFloat()
    return top + ("Other" to other)
}

private fun writeCsv(context: Context, range: AnalyticsRange, daily: List<DailyStat>): File {
    val directory = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(directory, "textpic-analytics-${System.currentTimeMillis()}.csv")
    val header = "date,total,inserted,shared,copied,rendered\n"
    val body = daily.joinToString(separator = "\n") {
        "${it.day},${it.total},${it.inserted},${it.shared},${it.copied},${it.rendered}"
    }
    file.writeText("TextPic analytics (${range.label})\n$header$body\n", Charsets.UTF_8)
    return file
}

private fun shareCsv(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "Export analytics").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
}

private fun buildExportBitmap(
    range: AnalyticsRange,
    daily: List<DailyStat>,
    total: Int,
    sent: Int,
    averageChars: Int,
    activeDays: Int,
    styleValues: List<Pair<String, Float>>,
): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val white = com.textpic.studio.ui.theme.TextPrimary.toArgb()
    val secondary = com.textpic.studio.ui.theme.TextSecondary.toArgb()
    val muted = com.textpic.studio.ui.theme.TextMuted.toArgb()
    val accent = com.textpic.studio.ui.theme.Accent.toArgb()
    val seriesTwo = com.textpic.studio.ui.theme.SeriesTwo.toArgb()
    val seriesThree = com.textpic.studio.ui.theme.SeriesThree.toArgb()

    canvas.drawColor(BgCanvas.toArgb())

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 54f
    paint.color = white
    canvas.drawText("TextPic analytics", 72f, 130f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 26f
    paint.color = secondary
    canvas.drawText("Last ${range.days} days · generated on device", 72f, 178f, paint)

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SurfaceCard.toArgb() }
    canvas.drawRoundRect(RectF(48f, 220f, 1032f, 400f), 28f, 28f, cardPaint)

    val metrics = listOf(
        "Renders" to total.toString(),
        "Sent" to sent.toString(),
        "Avg length" to averageChars.toString(),
        "Active days" to "$activeDays/${range.days}",
    )
    metrics.forEachIndexed { index, (label, value) ->
        val column = index % 2
        val row = index / 2
        val x = 96f + column * 468f
        val y = 280f + row * 66f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 36f
        paint.color = white
        canvas.drawText(value, x, y, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = muted
        canvas.drawText(label, x + 92f, y - 2f, paint)
    }

    canvas.drawRoundRect(RectF(48f, 438f, 1032f, 904f), 28f, 28f, cardPaint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 30f
    paint.color = white
    canvas.drawText("Renders per day", 84f, 500f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 20f
    paint.color = muted
    canvas.drawText("Daily totals, zero-filled for the selected range", 84f, 532f, paint)

    val chartLeft = 84f
    val chartRight = 996f
    val chartTop = 568f
    val chartBottom = 824f
    val max = daily.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    val slot = (chartRight - chartLeft) / daily.size.coerceAtLeast(1)
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    daily.forEachIndexed { index, stat ->
        val barWidth = (slot * 0.62f).coerceAtLeast(3f)
        val barHeight = if (stat.total == 0) 0f else (chartBottom - chartTop) * stat.total / max
        val left = chartLeft + index * slot + (slot - barWidth) / 2f
        if (barHeight > 0f) {
            canvas.drawRoundRect(
                RectF(left, chartBottom - barHeight, left + barWidth, chartBottom),
                6f,
                6f,
                barPaint,
            )
        }
    }
    paint.color = com.textpic.studio.ui.theme.BorderStrong.toArgb()
    paint.strokeWidth = 2f
    canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 18f
    paint.color = muted
    val labelSpacing = when {
        daily.size <= 7 -> 1
        daily.size <= 30 -> 5
        else -> 15
    }
    daily.forEachIndexed { index, stat ->
        if (index % labelSpacing == 0) {
            val x = chartLeft + index * slot + slot / 2f - 18f
            canvas.drawText(stat.label, x, chartBottom + 30f, paint)
        }
    }

    canvas.drawRoundRect(RectF(48f, 936f, 1032f, 1286f), 28f, 28f, cardPaint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 30f
    paint.color = white
    canvas.drawText("Style usage", 84f, 1000f, paint)

    val centerX = 270f
    val centerY = 1140f
    val radius = 96f
    val styleTotal = styleValues.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
    val palette = intArrayOf(accent, seriesTwo, seriesThree, muted)
    var startAngle = -90f
    val donutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 42f
        strokeCap = Paint.Cap.BUTT
    }
    styleValues.forEachIndexed { index, pair ->
        val sweep = pair.second / styleTotal * 360f
        donutPaint.color = palette[index % palette.size]
        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            startAngle,
            sweep,
            false,
            donutPaint,
        )
        startAngle += sweep
    }
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 44f
    paint.color = white
    val centerText = total.toString()
    canvas.drawText(centerText, centerX - paint.measureText(centerText) / 2f, centerY + 15f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 19f
    paint.color = muted
    canvas.drawText("renders", centerX - paint.measureText("renders") / 2f, centerY + 44f, paint)

    paint.textSize = 24f
    styleValues.forEachIndexed { index, pair ->
        val y = 1080f + index * 46f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[index % palette.size] }
        canvas.drawCircle(560f, y - 8f, 8f, dotPaint)
        paint.color = secondary
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(pair.first, 584f, y, paint)
        paint.color = white
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val value = pair.second.roundToInt().toString()
        canvas.drawText(value, 980f - paint.measureText(value), y, paint)
    }

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 20f
    paint.color = muted
    canvas.drawText(
        "No message text is included. TextPic never sends analytics off this device.",
        72f,
        1326f,
        paint,
    )
    return bitmap
}
