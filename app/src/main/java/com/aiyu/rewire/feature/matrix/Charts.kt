package com.aiyu.rewire.feature.matrix

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.ui.theme.rememberReducedMotion

/** 0 → 1 reveal on first show / data change; static under reduced motion. */
@Composable
private fun rememberGrow(key: Any): Float {
    val reduced = rememberReducedMotion()
    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(key) { if (!reduced) grow.animateTo(1f, spec) }
    return grow.value
}

/** Smooth area chart; the last point (today) gets a dot. */
@Composable
fun AreaLineChart(values: List<Int>, color: Color, description: String, modifier: Modifier = Modifier) {
    val grow = rememberGrow(values)
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val step = if (values.size == 1) 0f else size.width / (values.size - 1)
        val top = 8.dp.toPx() // room for the today dot
        val pts = values.mapIndexed { i, v -> Offset(i * step, size.height - (size.height - top) * v / max * grow) }
        for (g in 1..3) {
            val y = size.height * g / 4
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val midX = (pts[i - 1].x + pts[i].x) / 2
                cubicTo(midX, pts[i - 1].y, midX, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 6.dp.toPx(), pts.last())
        drawCircle(Color.White.copy(alpha = 0.9f), 2.5.dp.toPx(), pts.last())
    }
}

data class Slice(val label: String, val value: Int, val color: Color)

/** Donut with rounded gaps between slices; [center] sits in the hole. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val grow = rememberGrow(slices.map { it.value })
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.semantics { contentDescription = slices.joinToString { "${it.label} ${it.value}" } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            val nonZero = slices.filter { it.value > 0 }
            val gap = if (nonZero.size > 1) 6f else 0f
            var start = -90f
            nonZero.forEach { s ->
                val sweep = 360f * s.value / total * grow
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        center()
    }
}

/** Calendar heatmap, one cell per day, weeks as rows, oldest first. Intensity = focus + guard activity. */
@Composable
fun ActivityHeatmap(days: List<DailyMetrics>, modifier: Modifier = Modifier) {
    val max = days.maxOfOrNull { it.focusMinutes }?.coerceAtLeast(1) ?: 1
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    val today = MaterialTheme.colorScheme.tertiary
    val active = days.count { it.focusMinutes > 0 }
    val heatmapDesc = stringResource(R.string.matrix_heatmap_desc, active, days.size)
    Column(modifier.semantics { contentDescription = heatmapDesc }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d ->
                    val t = d.focusMinutes.toFloat() / max
                    val c = when {
                        d == days.last() && d.focusMinutes == 0 -> today.copy(alpha = 0.35f)
                        d.focusMinutes == 0 -> empty
                        else -> lerp(full.copy(alpha = 0.3f), full, t)
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
        }
    }
}

/** Thin horizontal share meter for list rows. */
@Composable
fun ShareMeter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val grow = rememberGrow(fraction)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f) * grow, size.height), cornerRadius = r)
    }
}

@Composable
fun Legend(slices: List<Slice>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(s.color))
                Text(
                    "  ${s.label}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                Text("${s.value}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
