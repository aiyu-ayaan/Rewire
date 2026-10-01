package com.rewire.app.feature.matrix

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rewire.app.AppContainer
import com.rewire.app.domain.analytics.DailyMetrics
import com.rewire.app.domain.analytics.MetricsCalculator
import com.rewire.app.rewireViewModel
import com.rewire.app.ui.components.EmptyState
import com.rewire.app.ui.components.SectionTitle
import com.rewire.app.ui.components.formatMinutes
import com.rewire.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class MatrixViewModel(c: AppContainer) : ViewModel() {
    /** Last 7 days, oldest first. Chart-ready: UI never touches raw events. */
    val week: StateFlow<List<DailyMetrics>> = c.events.events
        .map { MetricsCalculator.lastDays(it, LocalDate.now(), 7, ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricsCalculator.lastDays(emptyList(), LocalDate.now(), 7, ZoneId.systemDefault()))
}

@Composable
fun MatrixScreen() {
    val vm = rewireViewModel { MatrixViewModel(it) }
    val week by vm.week.collectAsStateWithLifecycle()
    val today = week.last()
    val hasData = week.any { it.focusMinutes > 0 || it.warningCount > 0 || it.blockedAttempts > 0 || it.sessionsCompleted > 0 }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Matrix", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        Text("What you did, measured. Stored only on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("Focus", formatMinutes(today.focusMinutes), Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            MetricTile("Break", formatMinutes(today.breakMinutes), Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("Sessions", "${today.sessionsCompleted}", Modifier.weight(1f))
            MetricTile("Warnings", "${today.warningCount}", Modifier.weight(1f))
            MetricTile("Overrides", "${today.overrideCount}", Modifier.weight(1f))
        }

        SectionTitle("Focus · last 7 days")
        if (!hasData) {
            EmptyState("No data yet", "Finish a focus block and it shows up here. Guard activity joins once protection is live.")
        } else {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                FocusBarChart(week, Modifier.fillMaxWidth().height(200.dp).padding(16.dp))
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    week.forEach {
                        Text(
                            it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (it.date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            Text(
                "Week total ${formatMinutes(week.sumOf { it.focusMinutes })} · best day ${formatMinutes(week.maxOf { it.focusMinutes })}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        Text(
            "More charts (trends, heatmap, habit breakdown) arrive with usage analytics.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun MetricTile(
    label: String, value: String, modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** Bars grow in with a spring (static under reduced motion). Summary exposed to screen readers. */
@Composable
private fun FocusBarChart(days: List<DailyMetrics>, modifier: Modifier) {
    val max = (days.maxOf { it.focusMinutes }).coerceAtLeast(1)
    val reduced = rememberReducedMotion()
    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(days) { if (!reduced) grow.animateTo(1f, spec) }
    val bar = MaterialTheme.colorScheme.primary
    val todayBar = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val summary = days.joinToString { "${it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.focusMinutes} minutes" }

    Canvas(modifier.semantics { contentDescription = "Focus minutes per day: $summary" }) {
        val slot = size.width / days.size
        val w = slot * 0.56f
        val radius = CornerRadius(w / 2, w / 2)
        days.forEachIndexed { i, d ->
            val x = i * slot + (slot - w) / 2
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), radius)
            val h = size.height * d.focusMinutes / max * grow.value
            if (h > 0) drawRoundRect(if (i == days.lastIndex) todayBar else bar, Offset(x, size.height - h), Size(w, h), radius)
        }
    }
}
