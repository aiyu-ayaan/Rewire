package com.aiyu.rewire.feature.matrix

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.R
import com.aiyu.rewire.ui.components.formatClock
import com.aiyu.rewire.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun rememberReveal(key: Any): Float {
    val reduced = rememberReducedMotion()
    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(key) { if (!reduced) grow.animateTo(1f, spec) }
    return grow.value
}

/** Card body with a chart and a "Show as table" switch listing the same values, for screen readers and exact numbers. */
@Composable
fun ChartWithTable(rows: List<Pair<String, String>>, modifier: Modifier = Modifier, chart: @Composable () -> Unit) {
    var table by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        if (table) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        } else chart()
        TextButton(onClick = { table = !table }) {
            Text(stringResource(if (table) R.string.matrix_show_chart else R.string.matrix_show_table))
        }
    }
}

@Composable
fun CategoryLabels(labels: List<String>, modifier: Modifier = Modifier) {
    // Dense series (a month) would collide, so label roughly every fifth point.
    val every = if (labels.size > 8) 5 else 1
    Row(modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {}) {
        labels.forEachIndexed { i, l ->
            Text(
                if (i % every == 0) l else "", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun BarChart(points: List<ChartPoint>, color: Color, description: String, modifier: Modifier = Modifier) {
    val grow = rememberReveal(points)
    val max = points.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        val slot = size.width / points.size
        val w = slot * 0.6f
        points.forEachIndexed { i, p ->
            val h = (size.height - 4.dp.toPx()) * p.value / max * grow
            drawRoundRect(color, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 4))
        }
    }
}

private val seriesDashes = listOf(null, floatArrayOf(14f, 10f), floatArrayOf(3f, 9f))

/** Multi-series line chart. Series differ by dash pattern and marker, not only colour. */
@Composable
fun LineChart(series: List<LineSeries>, colors: List<Color>, description: String, modifier: Modifier = Modifier) {
    val grow = rememberReveal(series)
    val max = series.maxOfOrNull { s -> s.points.maxOfOrNull { it.value } ?: 0f }?.coerceAtLeast(1f) ?: 1f
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        for (g in 1..3) drawLine(grid, Offset(0f, size.height * g / 4), Offset(size.width, size.height * g / 4), 1.dp.toPx())
        series.forEachIndexed { si, s ->
            if (s.points.isEmpty()) return@forEachIndexed
            val color = colors[si % colors.size]
            val step = if (s.points.size == 1) 0f else size.width / (s.points.size - 1)
            val pts = s.points.mapIndexed { i, p -> Offset(i * step, size.height - (size.height - 8.dp.toPx()) * p.value / max * grow) }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            val dash = seriesDashes[si % seriesDashes.size]?.let { PathEffect.dashPathEffect(it) }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
            if (pts.size <= 14) pts.forEach { marker(si, it, color) }
        }
    }
}

private fun DrawScope.marker(index: Int, at: Offset, color: Color) {
    val r = 4.dp.toPx()
    when (index % 3) {
        0 -> drawCircle(color, r, at)
        1 -> drawRect(color, Offset(at.x - r, at.y - r), Size(2 * r, 2 * r))
        else -> drawPath(Path().apply { moveTo(at.x, at.y - r); lineTo(at.x + r, at.y + r); lineTo(at.x - r, at.y + r); close() }, color)
    }
}

@Composable
fun StackedBarChart(data: StackedBarData, colors: List<Color>, description: String, modifier: Modifier = Modifier) {
    val grow = rememberReveal(data)
    val max = data.totals.maxOrNull()?.coerceAtLeast(1) ?: 1
    val gap = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.semantics { contentDescription = description }) {
        if (data.labels.isEmpty()) return@Canvas
        val slot = size.width / data.labels.size
        val w = slot * 0.6f
        data.labels.indices.forEach { i ->
            var bottom = size.height
            data.series.forEachIndexed { si, s ->
                val v = s.values.getOrElse(i) { 0 }
                if (v == 0) return@forEachIndexed
                val h = size.height * v / max * grow
                drawRect(colors[si % colors.size], Offset(i * slot + (slot - w) / 2, bottom - h), Size(w, h))
                drawLine(gap, Offset(i * slot + (slot - w) / 2, bottom - h), Offset(i * slot + (slot + w) / 2, bottom - h), 2.dp.toPx())
                bottom -= h
            }
        }
    }
}

/** Radar of 0..1 axes. Axes are numbered on the chart and listed below by [RadarLegend]. */
@Composable
fun RadarChart(axes: List<RadarAxis>, description: String, modifier: Modifier = Modifier) {
    val grow = rememberReveal(axes)
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurfaceVariant)
    Canvas(modifier.semantics { contentDescription = description }) {
        if (axes.size < 3) return@Canvas
        val c = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 18.dp.toPx()
        fun at(i: Int, f: Float): Offset {
            val a = -PI / 2 + 2 * PI * i / axes.size
            return Offset(c.x + radius * f * cos(a).toFloat(), c.y + radius * f * sin(a).toFloat())
        }
        for (ring in 1..4) {
            val p = Path().apply { axes.indices.forEach { i -> at(i, ring / 4f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }; close() }
            drawPath(p, scheme.outlineVariant, style = Stroke(1.dp.toPx()))
        }
        axes.indices.forEach { i ->
            drawLine(scheme.outlineVariant, c, at(i, 1f), 1.dp.toPx())
            val layout = measurer.measure("${i + 1}", labelStyle)
            val o = at(i, 1f) + (at(i, 1f) - c) / radius * 11.dp.toPx()
            drawText(layout, topLeft = Offset(o.x - layout.size.width / 2, o.y - layout.size.height / 2))
        }
        val shape = Path().apply { axes.forEachIndexed { i, a -> at(i, a.fraction * grow).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }; close() }
        drawPath(shape, scheme.primary.copy(alpha = 0.25f))
        drawPath(shape, scheme.primary, style = Stroke(3.dp.toPx()))
        axes.forEachIndexed { i, a -> drawCircle(scheme.primary, 4.dp.toPx(), at(i, a.fraction * grow)) }
    }
}

@Composable
fun RadarLegend(axes: List<RadarAxis>, modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        axes.forEachIndexed { i, a ->
            Row {
                Text("${i + 1}  ${a.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(a.display, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun ScatterChart(points: List<ScatterPoint>, color: Color, description: String, modifier: Modifier = Modifier) {
    val grow = rememberReveal(points)
    val axis = MaterialTheme.colorScheme.outline
    Canvas(modifier.semantics { contentDescription = description }) {
        val pad = 8.dp.toPx()
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        drawLine(axis, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
        val mx = points.maxOfOrNull { it.x }?.coerceAtLeast(1f) ?: 1f
        val my = points.maxOfOrNull { it.y }?.coerceAtLeast(1f) ?: 1f
        points.forEach { p ->
            val o = Offset(pad + (size.width - 2 * pad) * p.x / mx, size.height - pad - (size.height - 2 * pad) * p.y / my)
            drawCircle(color.copy(alpha = 0.3f), 9.dp.toPx() * grow, o)
            drawCircle(color, 5.dp.toPx() * grow, o, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Month calendar heatmap. Day numbers sit in the cells so intensity is never the only signal. [firstColumn] is 0 for Monday. */
@Composable
fun MonthHeatmap(cells: List<HeatCell>, firstColumn: Int, description: String, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    val rows = (List(firstColumn) { null } + cells).chunked(7)
    Column(modifier.semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { c ->
                    val bg = when {
                        c == null -> Color.Transparent
                        c.value == 0 -> empty
                        else -> lerp(full.copy(alpha = 0.3f), full, c.level)
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(bg), contentAlignment = Alignment.Center) {
                        if (c != null) Text(
                            "${c.dayOfMonth}", style = MaterialTheme.typography.labelSmall,
                            color = if (c.level > 0.6f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Vertical timeline: each moment has a time, a marker and a text label. */
@Composable
fun TimelineView(items: List<TimelineItem>, kindLabel: (TimelineKind) -> String, modifier: Modifier = Modifier) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    Column(modifier) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(item.minuteOfDay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                Canvas(Modifier.size(width = 14.dp, height = 36.dp).clearAndSetSemantics {}) {
                    drawLine(rail, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
                    drawCircle(dot, 5.dp.toPx(), center)
                }
                Text(kindLabel(item.kind), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
