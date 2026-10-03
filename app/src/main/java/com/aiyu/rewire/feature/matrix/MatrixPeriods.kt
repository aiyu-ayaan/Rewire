package com.aiyu.rewire.feature.matrix

import androidx.compose.ui.platform.LocalConfiguration

import com.aiyu.rewire.ui.components.isLargeFont

import com.aiyu.rewire.ui.components.CappedFontScale

import com.aiyu.rewire.ui.components.BesideOrStacked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aiyu.rewire.ui.components.GoalChips
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.R
import com.aiyu.rewire.ui.components.AdaptiveColumns
import com.aiyu.rewire.ui.components.EmptyState
import com.aiyu.rewire.ui.components.SectionTitle
import com.aiyu.rewire.ui.components.formatClock
import com.aiyu.rewire.ui.components.formatMinutes
import java.util.Locale

@Composable
internal fun PeriodSwitch(selected: MatrixPeriod, onSelect: (MatrixPeriod) -> Unit) {
    val labels = mapOf(
        MatrixPeriod.DAILY to R.string.matrix_period_daily,
        MatrixPeriod.WEEKLY to R.string.matrix_period_weekly,
        MatrixPeriod.MONTHLY to R.string.matrix_period_monthly,
    )
    val large = isLargeFont()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        MatrixPeriod.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = p == selected, onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, MatrixPeriod.entries.size),
                icon = if (large) ({}) else ({ SegmentedButtonDefaults.Icon(p == selected) }),
            ) { Text(stringResource(labels.getValue(p))) }
        }
    }
}

private fun List<ChartPoint>.rows(fmt: (Float) -> String = { it.toInt().toString() }) = map { it.label to fmt(it.value) }

@Composable
private fun TimelineKind.label() = stringResource(
    when (this) {
        TimelineKind.FOCUS_START -> R.string.matrix_tl_focus_start
        TimelineKind.FOCUS_DONE -> R.string.matrix_tl_focus_done
        TimelineKind.WARNING -> R.string.matrix_tl_warning
        TimelineKind.BLOCKED -> R.string.matrix_tl_blocked
        TimelineKind.OVERRIDE -> R.string.matrix_tl_override
        TimelineKind.WENT_BACK -> R.string.matrix_tl_went_back
    },
)

@Composable
internal fun DailyView(ui: MatrixUi) {
    val d = ui.daily
    val scheme = MaterialTheme.colorScheme
    if (!d.hasData) {
        EmptyState(stringResource(R.string.matrix_empty_day_title), stringResource(R.string.matrix_empty_day_body))
        return
    }
    AdaptiveColumns(first = {
    SectionTitle(stringResource(R.string.matrix_today))
    TodayOverview(d.today)
    GoalChips(d.progress, Modifier.padding(vertical = 12.dp))
    ChartCard {
        Stat(stringResource(R.string.matrix_screen_time), formatMinutes(d.today.screenTimeMinutes))
    }
    }, second = {

    if (d.appUsage.isNotEmpty()) {
        SectionTitle(stringResource(R.string.matrix_protected_usage))
        val palette = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error, scheme.outline)
        val top = d.appUsage.take(4)
        val rest = d.appUsage.drop(4).sumOf { it.minutes }
        val slices = top.mapIndexed { i, a -> Slice(a.label, a.minutes, palette[i]) } +
            if (rest > 0) listOf(Slice(stringResource(R.string.matrix_other_apps), rest, palette[4])) else emptyList()
        ChartCard {
            ChartWithTable(d.appUsage.map { it.label to formatMinutes(it.minutes) }) {
                BesideOrStacked(leading = {
                    DonutChart(slices, Modifier.size(132.dp)) {
                        CappedFontScale { Text(formatMinutes(d.appUsage.sumOf { it.minutes }), style = MaterialTheme.typography.titleMedium) }
                    }
                }) { mod -> Legend(slices.map { it.copy(value = it.value) }, mod) }
            }
        }
    }

    if (d.timeline.isNotEmpty()) {
        SectionTitle(stringResource(R.string.matrix_timeline))
        ChartCard {
            val names = TimelineKind.entries.associateWith { it.label() }
            val rows = d.timeline.map { formatClock(it.minuteOfDay) to names.getValue(it.kind) }
            ChartWithTable(rows) {
                TimelineView(d.timeline, kindLabel = { names.getValue(it) })
            }
        }
    }
    })
}

@Composable
internal fun WeeklyView(ui: MatrixUi, onShowAll: (apps: Boolean) -> Unit) {
    val w = ui.weekly
    val locale = LocalConfiguration.current.locales[0]
    val m = w.metrics
    val scheme = MaterialTheme.colorScheme
    if (!w.hasData) {
        EmptyState(stringResource(R.string.matrix_empty_week_title), stringResource(R.string.matrix_empty_week_body))
        return
    }
    AdaptiveColumns(first = {
    listOfNotNull(ui.focusLine, ui.guardLine).forEach { Punchline(it) }

    SectionTitle(stringResource(R.string.matrix_week_summary))
    ChartCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat(stringResource(R.string.matrix_focus_hours), "%.1f h".format(locale, m.focusHours))
            Stat(stringResource(R.string.matrix_distracted_hours), "%.1f h".format(locale, m.distractedHours))
            Stat(stringResource(R.string.matrix_habit_attempts), "${m.habitAttempts}")
            Stat(stringResource(R.string.matrix_successful_blocks), "${m.successfulBlocks}")
            Stat(stringResource(R.string.matrix_most_opened), w.mostOpenedLabel?.let { "$it (${m.mostOpenedApp?.opens})" } ?: "—")
            Stat(
                stringResource(R.string.matrix_problem_time),
                m.mostProblematicHour?.let { "${formatClock(it * 60)}–${formatClock((it + 1) % 24 * 60)}" } ?: "—",
            )
            Stat(stringResource(R.string.matrix_longest_focus), formatMinutes(m.longestFocusMinutes))
        }
    }

    SectionTitle(stringResource(R.string.matrix_focus_per_day))
    ChartCard {
        val desc = stringResource(R.string.matrix_desc_focus_week, w.focusBars.joinToString { "${it.label} ${formatMinutes(it.value.toInt())}" })
        ChartWithTable(w.focusBars.rows { formatMinutes(it.toInt()) }) {
            BarChart(w.focusBars, scheme.primary, desc, Modifier.fillMaxWidth().height(140.dp))
            CategoryLabels(w.focusBars.map { it.label })
        }
    }

    if (m.distractedMinutes > 0) {
        SectionTitle(stringResource(R.string.matrix_screen_per_day))
        ChartCard {
            val desc = stringResource(R.string.matrix_desc_screen_week, w.screenBars.joinToString { "${it.label} ${formatMinutes(it.value.toInt())}" })
            ChartWithTable(w.screenBars.rows { formatMinutes(it.toInt()) }) {
                BarChart(w.screenBars, scheme.tertiary, desc, Modifier.fillMaxWidth().height(120.dp))
                CategoryLabels(w.screenBars.map { it.label })
            }
        }
    }

    }, second = {
    if (!w.guardStack.isEmpty) {
        SectionTitle(stringResource(R.string.matrix_guard_per_day))
        val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error)
        val data = w.guardStack
        ChartCard {
            val desc = stringResource(R.string.matrix_desc_guard_week, data.series.joinToString { s -> "${s.name} ${s.values.sum()}" })
            val rows = data.labels.indices.map { i -> data.labels[i] to data.series.joinToString { "${it.name} ${it.values[i]}" } }
            ChartWithTable(rows) {
                StackedBarChart(data, colors, desc, Modifier.fillMaxWidth().height(140.dp))
                CategoryLabels(data.labels)
                Spacer(Modifier.height(8.dp))
                Legend(data.series.mapIndexed { i, s -> Slice(s.name, s.values.sum(), colors[i]) })
            }
        }
        Spacer(Modifier.height(12.dp))
        GuardOutcomes(ui)
    }

    if (ui.habits.isNotEmpty()) {
        SectionTitle(stringResource(R.string.matrix_by_habit), trailing = { ShowAll(ui.habits.size) { onShowAll(false) } })
        BreakdownList(ui.habits.take(PREVIEW_ROWS), scheme.secondary)
    }
    if (ui.apps.isNotEmpty()) {
        SectionTitle(stringResource(R.string.matrix_top_apps), trailing = { ShowAll(ui.apps.size) { onShowAll(true) } })
        BreakdownList(ui.apps.take(PREVIEW_ROWS), scheme.tertiary)
    }
    })
}

@Composable
internal fun MonthlyView(mo: MonthlyUi) {
    val m = mo.metrics
    val scheme = MaterialTheme.colorScheme
    if (!mo.hasData) {
        EmptyState(stringResource(R.string.matrix_empty_month_title), stringResource(R.string.matrix_empty_month_body))
        return
    }

    AdaptiveColumns(first = {
    SectionTitle(stringResource(R.string.matrix_month_summary))
    ChartCard {
        ChartWithTable(mo.radar.map { it.label to it.display }) {
            BesideOrStacked(leading = {
                RadarChart(mo.radar, stringResource(R.string.matrix_desc_radar, mo.radar.joinToString { "${it.label} ${it.display}" }), Modifier.size(170.dp))
            }) { mod -> RadarLegend(mo.radar, mod) }
        }
        Caption(
            m.habitReduction?.let { stringResource(R.string.matrix_reduction_caption, (it * 100).toInt()) }
                ?: stringResource(R.string.matrix_reduction_none),
        )
    }

    SectionTitle(stringResource(R.string.matrix_trend_title))
    ChartCard {
        val colors = listOf(scheme.primary, scheme.tertiary)
        val lines = mo.trendLines
        val desc = stringResource(R.string.matrix_desc_trend, lines.joinToString { s -> "${s.name} ${formatMinutes(s.points.sumOf { it.value.toInt() })}" })
        val rows = lines[0].points.indices.map { i -> lines[0].points[i].label to lines.joinToString { "${it.name} ${formatMinutes(it.points[i].value.toInt())}" } }
        ChartWithTable(rows) {
            LineChart(lines, colors, desc, Modifier.fillMaxWidth().height(160.dp))
            CategoryLabels(lines[0].points.map { it.label })
            Spacer(Modifier.height(8.dp))
            Legend(lines.mapIndexed { i, s -> Slice(s.name, s.points.sumOf { it.value.toInt() }, colors[i]) })
        }
    }

    SectionTitle(stringResource(R.string.matrix_override_trend))
    ChartCard {
        val desc = stringResource(R.string.matrix_desc_override, mo.overrideTrend.sumOf { it.value.toInt() })
        ChartWithTable(mo.overrideTrend.rows()) {
            AreaLineChart(mo.overrideTrend.map { it.value.toInt() }, scheme.error, desc, Modifier.fillMaxWidth().height(110.dp))
            CategoryLabels(mo.overrideTrend.map { it.label })
        }
    }

    }, second = {
    SectionTitle(stringResource(R.string.matrix_calendar))
    ChartCard {
        val active = mo.heat.count { it.value > 0 }
        val desc = stringResource(R.string.matrix_desc_heatmap, active, mo.heat.size)
        ChartWithTable(mo.heat.map { it.label to formatMinutes(it.value) }) {
            MonthHeatmap(mo.heat, mo.firstColumn, desc, Modifier.fillMaxWidth())
        }
        Caption(stringResource(R.string.matrix_heatmap_caption, active, mo.heat.size))
    }

    if (mo.scatter.size >= 2) {
        SectionTitle(stringResource(R.string.matrix_scatter_title))
        ChartCard {
            val desc = stringResource(R.string.matrix_desc_scatter, mo.scatter.size)
            ChartWithTable(mo.scatter.map { it.label to stringResource(R.string.matrix_scatter_row, formatMinutes(it.x.toInt()), it.y.toInt()) }) {
                ScatterChart(mo.scatter, scheme.secondary, desc, Modifier.fillMaxWidth().height(160.dp))
            }
            Caption(stringResource(R.string.matrix_scatter_caption))
        }
    }

    SectionTitle(stringResource(R.string.matrix_goals))
    ChartCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat(stringResource(R.string.matrix_radar_consistency), "${(m.consistency * 100).toInt()}%")
            Stat(stringResource(R.string.matrix_radar_goals), "${(m.goalCompletion * 100).toInt()}%")
        }
    }
    })
}
