package com.rewire.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.RewireApp
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.domain.warning.Warning
import com.rewire.app.feature.guard.LevelSelector
import com.rewire.app.ui.components.LevelBadge
import com.rewire.app.ui.components.style

@Composable
fun WarningLibraryScreen(onBack: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as RewireApp).container.warnings
    val warnings by repo.warnings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<WarningLevel?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shown = warnings.filter { filter == null || it.level == filter }.sortedByDescending { it.favorite }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Warning library") },
                subtitle = { Text("Words that help you pause. A random enabled one is shown each time.") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Custom warning") })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") }) }
                    items(WarningLevel.entries) { l ->
                        FilterChip(selected = filter == l, onClick = { filter = if (filter == l) null else l }, label = { Text(l.style().label) }, leadingIcon = { Icon(l.style().icon, null, Modifier.size(FilterChipDefaults.IconSize)) })
                    }
                }
            }
            items(shown, key = { it.id }) { w ->
                WarningCard(w, onChange = repo::update, onDelete = { repo.delete(w.id) }, modifier = Modifier.animateItem())
            }
        }
    }

    if (adding) {
        CustomWarningDialog(onDismiss = { adding = false }, onSave = { level, title, msg, why -> repo.addCustom(level, title, msg, why); adding = false })
    }
}

@Composable
private fun WarningCard(w: Warning, onChange: (Warning) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (w.enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(w.level)
                Spacer(Modifier.padding(4.dp))
                Text(w.category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconToggleButton(checked = w.favorite, onCheckedChange = { onChange(w.copy(favorite = it)) }) {
                    Icon(if (w.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = if (w.favorite) "Unfavorite" else "Favorite")
                }
                if (w.custom) IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete warning") }
                Switch(w.enabled, { onChange(w.copy(enabled = it)) }, modifier = Modifier.semantics { contentDescription = "Enabled" })
            }
            Text(w.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
            Text(w.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            Text("“${w.motivationalMessage}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
        }
    }
}

@Composable
private fun CustomWarningDialog(onDismiss: () -> Unit, onSave: (WarningLevel, String, String, String) -> Unit) {
    var level by rememberSaveable { mutableStateOf(WarningLevel.MAJOR) }
    var title by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var why by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom warning") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelSelector(level, { level = it })
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it.take(140) }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(why, { why = it.take(80) }, label = { Text("Your reason") }, supportingText = { Text("Shown as the quote. Keep it kind.") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(0.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(level, title, message, why) }, enabled = title.isNotBlank() && message.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
