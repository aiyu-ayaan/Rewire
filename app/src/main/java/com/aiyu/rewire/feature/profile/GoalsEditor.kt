package com.aiyu.rewire.feature.profile

import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.R
import com.aiyu.rewire.data.GoalsRepository
import com.aiyu.rewire.domain.goals.GoalRules
import com.aiyu.rewire.domain.goals.Goals
import com.aiyu.rewire.ui.components.InnerScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repo: GoalsRepository) : ViewModel() {
    val goals: StateFlow<Goals> = repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Goals())
    fun save(goals: Goals) { viewModelScope.launch { repo.set(goals) } }
}

/** Profile row: shows the current goals, opens the editor page (or the detail pane on tablets) on tap. */
@Composable
fun GoalsRow(containerColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val vm = hiltViewModel<GoalsViewModel>()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val summary = listOfNotNull(
        goals.dailyFocusMinutes?.let { stringResource(R.string.goals_row_focus, it) },
        goals.maxOverridesPerDay?.let { stringResource(R.string.goals_row_overrides, it) },
    ).joinToString(stringResource(R.string.goals_row_join)).ifEmpty { stringResource(R.string.goals_row_none) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.goals_row_title)) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(Icons.Rounded.Flag, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** Daily goals editor as a full page; saving or clearing returns via [onBack]. */
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<GoalsViewModel>()
    val initial by vm.goals.collectAsStateWithLifecycle()
    // Seed the fields once the stored goals have loaded; the user's typing is never overwritten after that.
    var focus by rememberSaveable(initial) { mutableStateOf(initial.dailyFocusMinutes?.toString().orEmpty()) }
    var overrides by rememberSaveable(initial) { mutableStateOf(initial.maxOverridesPerDay?.toString().orEmpty()) }
    val parsed = GoalRules.parse(focus, overrides)
    val digits = KeyboardOptions(keyboardType = KeyboardType.Number)
    InnerScreen(title = stringResource(R.string.goals_row_title), subtitle = stringResource(R.string.goals_dialog_intro), onBack = onBack) {
        OutlinedTextField(
            focus, { focus = it.filter(Char::isDigit).take(3) }, singleLine = true, keyboardOptions = digits,
            label = { Text(stringResource(R.string.goals_field_focus, Goals.FOCUS_RANGE.first, Goals.FOCUS_RANGE.last)) },
            isError = GoalRules.parse(focus, "") == null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            overrides, { overrides = it.filter(Char::isDigit).take(2) }, singleLine = true, keyboardOptions = digits,
            label = { Text(stringResource(R.string.goals_field_overrides, Goals.OVERRIDE_RANGE.first, Goals.OVERRIDE_RANGE.last)) },
            isError = GoalRules.parse("", overrides) == null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(onClick = { parsed?.let { vm.save(it); onBack() } }, enabled = parsed != null, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.goals_save)) }
        TextButton(onClick = { vm.save(Goals()); onBack() }) { Text(stringResource(R.string.goals_clear)) }
    }
}
