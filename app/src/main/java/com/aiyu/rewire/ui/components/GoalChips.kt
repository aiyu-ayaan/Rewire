package com.aiyu.rewire.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.R
import com.aiyu.rewire.domain.goals.DayProgress

/** Streak plus today's goal progress. Wording stays encouraging: a zero streak invites, never scolds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalChips(p: DayProgress, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val s = p.streak
        Chip(
            if (s.current > 0) stringResource(R.string.streak_chip_days, s.current) else stringResource(R.string.streak_chip_start),
            highlight = s.current > 0,
        )
        if (s.best > s.current && s.best > 1) Chip(stringResource(R.string.streak_best, s.best))
        val st = p.status
        st.focusTarget?.let { Chip(stringResource(R.string.goals_chip_focus, st.focusMinutes, it), highlight = st.focusMet) }
        st.overridesMax?.let { Chip(stringResource(R.string.goals_chip_overrides, st.overrides, it), highlight = st.overridesOk) }
        if (!p.hasGoals) Chip(stringResource(R.string.goals_set_prompt))
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
