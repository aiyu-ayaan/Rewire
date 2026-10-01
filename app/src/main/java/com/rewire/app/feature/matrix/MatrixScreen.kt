package com.rewire.app.feature.matrix

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rewire.app.AppContainer
import com.rewire.app.domain.analytics.DailyMetrics
import com.rewire.app.domain.analytics.GuardBreakdown
import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.analytics.MetricsCalculator
import com.rewire.app.domain.analytics.Punchlines
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.rewireViewModel
import com.rewire.app.ui.components.AppIcon
import com.rewire.app.ui.components.EmptyState
import com.rewire.app.ui.components.SectionTitle
import com.rewire.app.ui.components.formatClock
import com.rewire.app.ui.components.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class NamedBreakdown(val label: String, val packageName: String?, val stats: GuardBreakdown)

/** Everything Matrix draws, already aggregated. UI never touches raw events. */
data class MatrixUi(
    val week: List<DailyMetrics>,
    val month: List<DailyMetrics>,
    val habits: List<NamedBreakdown>,
    val apps: List<NamedBreakdown>,
    val peakHour: Int?,
    val focusLine: String?,
    val guardLine: String?,
) {
    val today get() = week.last()
    fun weekSum(f: (DailyMetrics) -> Int) = week.sumOf(f)
    val hasFocus get() = week.any { it.focusMinutes > 0 }
    val hasGuard get() = week.any { it.frictionMoments > 0 || it.appOpens > 0 }
}

class MatrixViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<MatrixUi> = combine(c.events.events, c.habits.habits, ::build)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(emptyList(), emptyList()))

    private fun build(events: List<HabitEvent>, habits: List<HabitProfile>): MatrixUi {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val since = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekRaw = MetricsCalculator.lastDays(events, today, 7, zone)
        val week = weekRaw.mapIndexed { idx, m ->
            if (idx == weekRaw.lastIndex) m.copy(screenTimeMinutes = c.usage.totalScreenTimeToday()) else m
        }
        val names = habits.associate { it.id to it.habit.name }
        val seed = today.toEpochDay()
        return MatrixUi(
            week = week,
            month = MetricsCalculator.lastDays(events, today, 28, zone),
            habits = MetricsCalculator.breakdown(events, since) { it.habitId }
                .map { NamedBreakdown(names[it.key] ?: "Removed habit", null, it) },
            apps = MetricsCalculator.breakdown(events, since) { it.packageName }
                .map { NamedBreakdown(c.installedApps.label(it.key), it.key, it) },
            peakHour = MetricsCalculator.peakHour(events, since, zone),
            focusLine = Punchlines.focus(week.sumOf { it.focusMinutes }, seed),
            guardLine = Punchlines.guard(week.sumOf { it.wentBackCount }, week.sumOf { it.overrideCount }, seed),
        )
    }
}

@Composable
fun MatrixScreen(onShowAll: (apps: Boolean) -> Unit) {
    val vm = rewireViewModel { MatrixViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Matrix", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        Text("Focus and Guard, measured together. Stored only on this device.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

        if (!ui.hasFocus && !ui.hasGuard) {
            EmptyState("No data yet", "Finish a focus block or let Guard catch an app. Your week shows up here.")
            return@Column
        }

        listOfNotNull(ui.focusLine, ui.guardLine).forEach { Punchline(it) }

        SectionTitle("Today")
        TodayOverview(ui.today)

        SectionTitle("Focus · last 7 days")
        if (!ui.hasFocus) Caption("No focus sessions this week. Start one from the Focus tab and it lands here.")
        else ChartCard {
            AreaLineChart(
                ui.week.map { it.focusMinutes }, scheme.primary,
                "Focus minutes per day: " + ui.week.joinToString { "${dayName(it.date, TextStyle.SHORT)} ${it.focusMinutes}" },
                Modifier.fillMaxWidth().height(160.dp),
            )
            DayLabels(ui.week)
            Caption("Total ${formatMinutes(ui.weekSum { it.focusMinutes })} · best day ${formatMinutes(ui.week.maxOf { it.focusMinutes })} · ${ui.weekSum { it.sessionsCompleted }} sessions")
        }

        SectionTitle("Guard · last 7 days")
        if (!ui.hasGuard) {
            Caption("Guard hasn't stepped in this week. Either you're in control, or no habit is active yet.")
        } else {
            GuardOutcomes(ui)
            Spacer(Modifier.height(12.dp))
            ChartCard {
                Text("Friction moments per day", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                AreaLineChart(
                    ui.week.map { it.frictionMoments }, scheme.tertiary,
                    "Guard warnings and blocks per day: " + ui.week.joinToString { "${dayName(it.date, TextStyle.SHORT)} ${it.frictionMoments}" },
                    Modifier.fillMaxWidth().height(120.dp),
                )
                DayLabels(ui.week)
                ui.peakHour?.let { Caption("Most guarded hour: ${formatClock(it * 60)}–${formatClock((it + 1) % 24 * 60)}") }
            }
        }

        if (ui.habits.isNotEmpty()) {
            SectionTitle("By habit", trailing = { ShowAll(ui.habits.size) { onShowAll(false) } })
            BreakdownList(ui.habits.take(PREVIEW_ROWS), scheme.secondary)
        }
        if (ui.apps.isNotEmpty()) {
            SectionTitle("Most opened protected apps", trailing = { ShowAll(ui.apps.size) { onShowAll(true) } })
            BreakdownList(ui.apps.take(PREVIEW_ROWS), scheme.tertiary)
        }

        SectionTitle("Focus · last 4 weeks")
        ChartCard {
            ActivityHeatmap(ui.month, Modifier.fillMaxWidth())
            Caption("Focused on ${ui.month.count { it.focusMinutes > 0 }} of ${ui.month.size} days. Darker = longer.")
        }
    }
}

private const val PREVIEW_ROWS = 4

@Composable
private fun ShowAll(count: Int, onClick: () -> Unit) {
    if (count > PREVIEW_ROWS) TextButton(onClick = onClick) { Text("Show all ($count)") }
}

/** Full habit or app list for the last 7 days, opened from "Show all". */
@Composable
fun MatrixBreakdownScreen(apps: Boolean, onBack: () -> Unit) {
    val vm = rewireViewModel { MatrixViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items = if (apps) ui.apps else ui.habits
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (apps) "Protected apps" else "Habits") },
                subtitle = { Text("Last 7 days · busiest first") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (items.isEmpty()) EmptyState("Nothing here yet", "Guard activity from the last 7 days shows up here.")
            else BreakdownList(items, if (apps) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
        }
    }
}

/** Big friendly line, in the spirit of "your tokens ≈ 18 copies of Monte Cristo". */
@Composable
private fun Punchline(text: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun TodayOverview(m: DailyMetrics) {
    val score = m.disciplineScore
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { score ?: 0f }, modifier = Modifier.size(104.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                    Text("control", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("Focus", formatMinutes(m.focusMinutes))
                Stat("Break", formatMinutes(m.breakMinutes))
                Stat("Warnings", "${m.warningCount}")
                Stat("Blocked", "${m.blockedAttempts}")
                Stat("Went back", "${m.wentBackCount}")
                Stat("Overrides", "${m.overrideCount}")
            }
        }
    }
}

@Composable
private fun GuardOutcomes(ui: MatrixUi) {
    val scheme = MaterialTheme.colorScheme
    val slices = listOf(
        Slice("Went back", ui.weekSum { it.wentBackCount }, scheme.primary),
        Slice("Continued (minor)", ui.weekSum { it.continuedCount }, scheme.secondary),
        Slice("Blocked", ui.weekSum { it.blockedAttempts }, scheme.inversePrimary),
        Slice("Overrides", ui.weekSum { it.overrideCount }, scheme.error),
        Slice("Notifications held", ui.weekSum { it.notificationsBlocked }, scheme.tertiary),
    )
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${slices.sumOf { it.value }}", style = MaterialTheme.typography.headlineSmall)
                    Text("guard events", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Legend(slices, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BreakdownList(items: List<NamedBreakdown>, color: Color) {
    val max = items.maxOf { it.stats.moments + it.stats.opens }.coerceAtLeast(1)
    ChartCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { item ->
                val s = item.stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.packageName?.let { AppIcon(it, 32.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(item.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${s.opens} opens · ${s.moments} caught", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        ShareMeter((s.moments + s.opens).toFloat() / max, color, Modifier.fillMaxWidth().height(8.dp))
                        if (s.wentBack + s.overrides > 0) {
                            Text(
                                "Went back ${s.wentBack} · overrode ${s.overrides}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun DayLabels(week: List<DailyMetrics>) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        week.forEachIndexed { i, d ->
            Text(
                dayName(d.date, TextStyle.NARROW),
                style = MaterialTheme.typography.labelMedium,
                color = if (i == week.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = when (i) { 0 -> TextAlign.Start; week.lastIndex -> TextAlign.End; else -> TextAlign.Center },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun dayName(d: LocalDate, style: TextStyle) = d.dayOfWeek.getDisplayName(style, Locale.getDefault())
