package com.aiyu.rewire.feature.matrix

import com.aiyu.rewire.ui.components.readableWidth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
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
import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.domain.analytics.GuardBreakdown
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.MetricsCalculator
import com.aiyu.rewire.domain.analytics.MonthlyMetrics
import com.aiyu.rewire.domain.analytics.PeriodMetrics
import com.aiyu.rewire.domain.analytics.WeeklyMetrics
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import com.aiyu.rewire.domain.analytics.Punchlines
import com.aiyu.rewire.data.GoalsRepository
import com.aiyu.rewire.domain.goals.DayProgress
import com.aiyu.rewire.domain.goals.Goals
import com.aiyu.rewire.domain.goals.Streaks
import com.aiyu.rewire.domain.habit.HabitProfile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.rewire.core.apps.InstalledAppsSource
import com.aiyu.rewire.core.guard.UsageTracker
import com.aiyu.rewire.data.EventRepository
import com.aiyu.rewire.data.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.aiyu.rewire.ui.components.AppIcon
import com.aiyu.rewire.ui.components.EmptyState
import com.aiyu.rewire.ui.components.SectionTitle
import com.aiyu.rewire.ui.components.formatClock
import com.aiyu.rewire.ui.components.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class MatrixPeriod { DAILY, WEEKLY, MONTHLY }

data class NamedBreakdown(val label: String?, val packageName: String?, val stats: GuardBreakdown)

data class AppMinutes(val label: String, val minutes: Int)

/** Chart-ready Daily view. */
data class DailyUi(val today: DailyMetrics, val timeline: List<TimelineItem>, val appUsage: List<AppMinutes>, val progress: DayProgress = DayProgress.EMPTY) {
    val hasData get() = today.focusMinutes > 0 || today.frictionMoments > 0 || today.appOpens > 0 || timeline.isNotEmpty()
}

/** Chart-ready Weekly view (calendar week). */
data class WeeklyUi(
    val metrics: WeeklyMetrics,
    val focusBars: List<ChartPoint>,
    val screenBars: List<ChartPoint>,
    val guardStack: StackedBarData,
    val mostOpenedLabel: String?,
) {
    val hasData get() = metrics.focusMinutes > 0 || metrics.habitAttempts > 0 || metrics.distractedMinutes > 0 || metrics.mostOpenedApp != null
}

/** Chart-ready Monthly view. */
data class MonthlyUi(
    val metrics: MonthlyMetrics,
    val trendLines: List<LineSeries>,
    val overrideTrend: List<ChartPoint>,
    val heat: List<HeatCell>,
    /** Weekday column (0 = Monday) of the 1st, so the heatmap lines up with a calendar. */
    val firstColumn: Int,
    val scatter: List<ScatterPoint>,
    val radar: List<RadarAxis>,
) {
    val hasData get() = metrics.days.any { it.focusMinutes > 0 || it.frictionMoments > 0 || it.screenTimeMinutes > 0 }
}

/** Everything Matrix draws, already aggregated. UI never touches raw events. */
data class MatrixUi(
    val period: MatrixPeriod,
    val week: List<DailyMetrics>,
    val daily: DailyUi,
    val weekly: WeeklyUi,
    val monthly: MonthlyUi,
    val habits: List<NamedBreakdown>,
    val apps: List<NamedBreakdown>,
    val peakHour: Int?,
    val focusLine: String?,
    val guardLine: String?,
) {
    fun weekSum(f: (DailyMetrics) -> Int) = week.sumOf(f)
    val hasFocus get() = week.any { it.focusMinutes > 0 }
    val hasGuard get() = week.any { it.frictionMoments > 0 || it.appOpens > 0 }
}

@HiltViewModel
class MatrixViewModel @Inject constructor(
    events: EventRepository,
    habits: HabitRepository,
    goals: GoalsRepository,
    private val usage: UsageTracker,
    private val installedApps: InstalledAppsSource,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val period = MutableStateFlow(MatrixPeriod.DAILY)
    fun selectPeriod(p: MatrixPeriod) { period.value = p }

    val ui: StateFlow<MatrixUi> = combine(events.events, habits.habits, period, goals.goals, ::build)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(emptyList(), emptyList(), MatrixPeriod.DAILY, Goals()))

    private fun s(id: Int) = context.getString(id)

    private fun build(events: List<HabitEvent>, habits: List<HabitProfile>, period: MatrixPeriod, goals: Goals): MatrixUi {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val since = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val screenToday = usage.totalScreenTimeToday()
        val weekRaw = MetricsCalculator.lastDays(events, today, 7, zone)
        val week = weekRaw.mapIndexed { idx, m -> if (idx == weekRaw.lastIndex) m.copy(screenTimeMinutes = screenToday) else m }
        val names = habits.associate { it.id to it.habit.name }
        val seed = today.toEpochDay()
        val screen = mapOf(today to screenToday)
        val dayLabel = { d: LocalDate -> dayName(d, TextStyle.SHORT) }
        val dateLabel = { d: LocalDate -> d.dayOfMonth.toString() }
        val protectedPkgs = habits.flatMap { h -> h.apps.map { it.packageName } }.toSet()

        val w = PeriodMetrics.weekly(events, PeriodMetrics.weekStart(today), zone, screen)
        val ym = YearMonth.from(today)
        val m = PeriodMetrics.monthly(events, ym, today, zone, screen, goals)
        val opened = w.mostOpenedApp
        return MatrixUi(
            period = period,
            week = week,
            daily = DailyUi(
                today = week.last(),
                timeline = ChartMapper.timeline(events, today, zone),
                progress = Streaks.progress(events, today, zone, goals),
                appUsage = usage.allAppsMinutesToday().filterKeys { it in protectedPkgs }
                    .map { AppMinutes(installedApps.label(it.key), it.value) }.sortedByDescending { it.minutes },
            ),
            weekly = WeeklyUi(
                metrics = w,
                focusBars = ChartMapper.trend(w.days, dayLabel) { it.focusMinutes },
                screenBars = ChartMapper.trend(w.days, dayLabel) { it.screenTimeMinutes },
                guardStack = ChartMapper.stackedGuard(
                    w.days, dayLabel,
                    listOf(s(R.string.matrix_went_back), s(R.string.matrix_continued), s(R.string.matrix_blocked), s(R.string.matrix_overrides)),
                ),
                mostOpenedLabel = opened?.let { installedApps.label(it.packageName) },
            ),
            monthly = MonthlyUi(
                metrics = m,
                trendLines = listOf(
                    LineSeries(s(R.string.matrix_focus), m.focusTrend.map { ChartPoint(dateLabel(it.date), it.value.toFloat()) }),
                    LineSeries(s(R.string.matrix_screen_time), m.screenTimeTrend.map { ChartPoint(dateLabel(it.date), it.value.toFloat()) }),
                ),
                overrideTrend = m.overrideTrend.map { ChartPoint(dateLabel(it.date), it.value.toFloat()) },
                heat = ChartMapper.heat(m.days, dateLabel) { it.focusMinutes },
                firstColumn = ym.atDay(1).dayOfWeek.value - 1,
                scatter = ChartMapper.scatter(m.days, dateLabel),
                radar = ChartMapper.radar(
                    m,
                    listOf(
                        s(R.string.matrix_radar_consistency), s(R.string.matrix_radar_goals), s(R.string.matrix_radar_reduction),
                        s(R.string.matrix_radar_discipline), s(R.string.matrix_radar_focus_days),
                    ),
                ),
            ),
            habits = MetricsCalculator.breakdown(events, since) { it.habitId }
                .map { NamedBreakdown(names[it.key], null, it) },
            apps = MetricsCalculator.breakdown(events, since) { it.packageName }
                .map { NamedBreakdown(installedApps.label(it.key), it.key, it) },
            peakHour = MetricsCalculator.peakHour(events, since, zone),
            focusLine = Punchlines.focus(week.sumOf { it.focusMinutes }, seed),
            guardLine = Punchlines.guard(week.sumOf { it.wentBackCount }, week.sumOf { it.overrideCount }, seed),
        )
    }
}

@Composable
fun MatrixScreen(onShowAll: (apps: Boolean) -> Unit) {
    val vm = hiltViewModel<MatrixViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding().readableWidth(720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.matrix_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.matrix_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        PeriodSwitch(ui.period, vm::selectPeriod)
        when (ui.period) {
            MatrixPeriod.DAILY -> DailyView(ui)
            MatrixPeriod.WEEKLY -> WeeklyView(ui, onShowAll)
            MatrixPeriod.MONTHLY -> MonthlyView(ui.monthly)
        }
    }
}

internal const val PREVIEW_ROWS = 4

@Composable
internal fun ShowAll(count: Int, onClick: () -> Unit) {
    if (count > PREVIEW_ROWS) TextButton(onClick = onClick) { Text(stringResource(R.string.matrix_show_all, count)) }
}

/** Full habit or app list for the last 7 days, opened from "Show all". */
@Composable
fun MatrixBreakdownScreen(apps: Boolean, onBack: () -> Unit) {
    val vm = hiltViewModel<MatrixViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items = if (apps) ui.apps else ui.habits
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(if (apps) R.string.matrix_protected_apps else R.string.matrix_habits)) },
                subtitle = { Text(stringResource(R.string.matrix_breakdown_subtitle)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.warning_back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (items.isEmpty()) EmptyState(stringResource(R.string.matrix_breakdown_empty_title), stringResource(R.string.matrix_breakdown_empty_text))
            else BreakdownList(items, if (apps) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
        }
    }
}

/** Big friendly line, in the spirit of "your tokens ≈ 18 copies of Monte Cristo". */
@Composable
internal fun Punchline(text: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
internal fun TodayOverview(m: DailyMetrics) {
    val score = m.disciplineScore
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { score ?: 0f }, modifier = Modifier.size(104.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.matrix_control), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat(stringResource(R.string.matrix_stat_focus), formatMinutes(m.focusMinutes))
                Stat(stringResource(R.string.matrix_stat_break), formatMinutes(m.breakMinutes))
                Stat(stringResource(R.string.matrix_stat_warnings), "${m.warningCount}")
                Stat(stringResource(R.string.matrix_stat_blocked), "${m.blockedAttempts}")
                Stat(stringResource(R.string.matrix_stat_went_back), "${m.wentBackCount}")
                Stat(stringResource(R.string.matrix_stat_overrides), "${m.overrideCount}")
            }
        }
    }
}

@Composable
internal fun GuardOutcomes(ui: MatrixUi) {
    val scheme = MaterialTheme.colorScheme
    val slices = listOf(
        Slice(stringResource(R.string.matrix_stat_went_back), ui.weekSum { it.wentBackCount }, scheme.primary),
        Slice(stringResource(R.string.matrix_slice_continued), ui.weekSum { it.continuedCount }, scheme.secondary),
        Slice(stringResource(R.string.matrix_stat_blocked), ui.weekSum { it.blockedAttempts }, scheme.inversePrimary),
        Slice(stringResource(R.string.matrix_stat_overrides), ui.weekSum { it.overrideCount }, scheme.error),
        Slice(stringResource(R.string.matrix_slice_notifications_held), ui.weekSum { it.notificationsBlocked }, scheme.tertiary),
    )
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${slices.sumOf { it.value }}", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.matrix_guard_events), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Legend(slices, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun BreakdownList(items: List<NamedBreakdown>, color: Color) {
    val max = items.maxOf { it.stats.moments + it.stats.opens }.coerceAtLeast(1)
    ChartCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { item ->
                val s = item.stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.packageName?.let { AppIcon(it, 32.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(item.label ?: stringResource(R.string.matrix_removed_habit), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.matrix_breakdown_counts, s.opens, s.moments), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        ShareMeter((s.moments + s.opens).toFloat() / max, color, Modifier.fillMaxWidth().height(8.dp))
                        if (s.wentBack + s.overrides > 0) {
                            Text(
                                stringResource(R.string.matrix_breakdown_outcomes, s.wentBack, s.overrides),
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
internal fun ChartCard(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
internal fun DayLabels(week: List<DailyMetrics>) {
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
internal fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

@Composable
internal fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

internal fun dayName(d: LocalDate, style: TextStyle) = d.dayOfWeek.getDisplayName(style, Locale.getDefault())
