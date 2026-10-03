package com.aiyu.rewire.feature.profile

import com.aiyu.rewire.ui.components.readableWidth

import android.content.Intent
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Name, project URL, license. Keep in sync with gradle/libs.versions.toml. */
private data class Library(val name: String, val url: String, val license: String)

private const val APACHE = "Apache License 2.0"

private val libraries = listOf(
    Library("Kotlin", "https://github.com/JetBrains/kotlin", APACHE),
    Library("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines", APACHE),
    Library("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization", APACHE),
    Library("Jetpack Compose", "https://developer.android.com/jetpack/compose", APACHE),
    Library("Material 3 for Compose", "https://developer.android.com/jetpack/androidx/releases/compose-material3", APACHE),
    Library("Material Icons Extended", "https://developer.android.com/jetpack/androidx/releases/compose-material", APACHE),
    Library("AndroidX Core KTX", "https://developer.android.com/jetpack/androidx/releases/core", APACHE),
    Library("AndroidX Core SplashScreen", "https://developer.android.com/jetpack/androidx/releases/core", APACHE),
    Library("AndroidX Lifecycle", "https://developer.android.com/jetpack/androidx/releases/lifecycle", APACHE),
    Library("AndroidX Activity Compose", "https://developer.android.com/jetpack/androidx/releases/activity", APACHE),
    Library("AndroidX Navigation Compose", "https://developer.android.com/jetpack/androidx/releases/navigation", APACHE),
    Library("AndroidX Room", "https://developer.android.com/jetpack/androidx/releases/room", APACHE),
    Library("AndroidX DataStore (one-time settings import)", "https://developer.android.com/jetpack/androidx/releases/datastore", APACHE),
    Library("AndroidX WorkManager", "https://developer.android.com/jetpack/androidx/releases/work", APACHE),
    Library("AndroidX Graphics Shapes", "https://developer.android.com/jetpack/androidx/releases/graphics", APACHE),
    Library("AndroidX Biometric", "https://developer.android.com/jetpack/androidx/releases/biometric", APACHE),
)

@Composable
fun AcknowledgementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.about_acknowledgements)) },
                subtitle = { Text(stringResource(R.string.about_acknowledgements_subtitle)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.warning_back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    libraries.forEach { lib ->
                        ListItem(
                            headlineContent = { Text(lib.name) },
                            supportingContent = { Text("${lib.license}\n${lib.url.removePrefix("https://")}") },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clickable { open(lib.url) },
                        )
                    }
                }
            }
        }
    }
}
