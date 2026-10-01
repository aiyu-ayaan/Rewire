package com.rewire.app.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rewire.app.BuildConfig
import com.rewire.app.R
import com.rewire.app.ui.components.SectionTitle

private const val GITHUB_URL = "https://github.com/aiyu-ayaan"
private const val PORTFOLIO_URL = "https://aiyu.co.in"

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
    Library("AndroidX DataStore", "https://developer.android.com/jetpack/androidx/releases/datastore", APACHE),
    Library("AndroidX Graphics Shapes", "https://developer.android.com/jetpack/androidx/releases/graphics", APACHE),
    Library("AndroidX Biometric", "https://developer.android.com/jetpack/androidx/releases/biometric", APACHE),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("About") },
                subtitle = { Text("Break habits. Build control.") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle("App")
            Group {
                ListItem(
                    headlineContent = { Text("Rewire") },
                    supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    colors = itemColors(),
                )
                ListItem(
                    headlineContent = { Text("Privacy") },
                    supportingContent = { Text("All data stays on this device.") },
                    colors = itemColors(),
                )
            }

            SectionTitle("Developer")
            Group {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(
                        painterResource(R.drawable.developer_avatar), contentDescription = "Ayaan's GitHub profile picture",
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                    )
                    Text("aiyu-ayaan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("Built Rewire.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                LinkRow(Icons.Rounded.Code, "GitHub", GITHUB_URL) { open(GITHUB_URL) }
                LinkRow(Icons.Rounded.Language, "Portfolio", "aiyu.co.in") { open(PORTFOLIO_URL) }
            }

            SectionTitle("Acknowledgements")
            Text(
                "Rewire is built on these open-source libraries. Tap one to visit its page.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            Group {
                libraries.forEach { lib ->
                    ListItem(
                        headlineContent = { Text(lib.name) },
                        supportingContent = { Text("${lib.license}\n${lib.url.removePrefix("https://")}") },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                        colors = itemColors(),
                        modifier = Modifier.clickable { open(lib.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
        colors = itemColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column { content() }
    }
}

@Composable
private fun itemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
