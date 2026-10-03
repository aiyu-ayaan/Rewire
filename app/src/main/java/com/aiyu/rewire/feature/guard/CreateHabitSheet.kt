package com.aiyu.rewire.feature.guard

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aiyu.rewire.core.apps.InstalledAppsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.aiyu.rewire.core.apps.InstalledApp
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.ui.components.AppIcon
import com.aiyu.rewire.ui.components.style

@Composable
fun CreateHabitSheet(onDismiss: () -> Unit, onCreate: (String, WarningLevel, List<String>) -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(WarningLevel.MAJOR) }
    val selected = remember { mutableStateListOf<String>() }

    val slideIn = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val slideOut = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(slideIn) { it / 3 * dir } + fadeIn())
                    .togetherWith(slideOutHorizontally(slideOut) { -it / 3 * dir } + fadeOut())
            },
            label = "step",
        ) { s ->
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding()) {
                Text(stringResource(R.string.guard_sheet_step, s + 1, 2), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (s == 0) {
                    Text(stringResource(R.string.guard_new_habit), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text(stringResource(R.string.guard_habit_name)) },
                        placeholder = { Text(stringResource(R.string.guard_habit_name_hint)) },
                        supportingText = { Text(stringResource(R.string.guard_habit_name_support)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.guard_friction_level), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    LevelSelector(level, onSelect = { level = it })
                    Text(
                        level.style().summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { step = 1 }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guard_choose_apps)) }
                } else {
                    Text(stringResource(R.string.guard_apps_for, name.trim()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    AppPicker(selected = selected, modifier = Modifier.heightIn(max = 420.dp))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.warning_back)) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onCreate(name, level, selected.toList()) }) {
                            Text(if (selected.isEmpty()) stringResource(R.string.guard_create_no_apps) else stringResource(R.string.guard_create_count, selected.size))
                        }
                    }
                }
            }
        }
    }
}

/** Expressive connected button group: Minor / Major / Max with icon + label. */
@Composable
fun LevelSelector(level: WarningLevel, onSelect: (WarningLevel) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        WarningLevel.entries.forEachIndexed { i, l ->
            val s = l.style()
            ToggleButton(
                checked = level == l,
                onCheckedChange = { onSelect(l) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    WarningLevel.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(checkedContainerColor = s.accent, checkedContentColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Icon(s.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.label)
            }
        }
    }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(val source: InstalledAppsSource) : ViewModel()

@Composable
fun AppPicker(selected: MutableList<String>, modifier: Modifier = Modifier) {
    val source = hiltViewModel<AppPickerViewModel>().source
    val apps by produceState<List<InstalledApp>?>(null) { value = source.launchableApps() }
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.guard_search_apps)) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            val filtered = remember(list, query) { list.filter { it.label.contains(query.trim(), ignoreCase = true) } }
            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = { AppIcon(app.packageName) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.toggleable(checked, role = Role.Checkbox) {
                            if (it) selected += app.packageName else selected -= app.packageName
                        },
                    )
                }
            }
        }
    }
}
