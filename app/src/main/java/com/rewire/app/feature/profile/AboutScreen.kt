package com.rewire.app.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import com.rewire.app.ui.components.UserAvatar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rewire.app.BuildConfig
import com.rewire.app.ui.components.SectionTitle

private const val GITHUB_URL = "https://github.com/aiyu-ayaan"
private const val INSTALL_GUIDE_URL = "https://github.com/aiyu-ayaan/Rewire#install"
private const val PORTFOLIO_URL = "https://aiyu.co.in"

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenAcknowledgements: () -> Unit) {
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
                    supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.ACCESSIBILITY) "Full" else "Lite"} build") },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    colors = itemColors(),
                )
                LinkRow(
                    Icons.Rounded.Info,
                    if (BuildConfig.ACCESSIBILITY) "Full build" else "Lite build",
                    if (BuildConfig.ACCESSIBILITY) "Instant detection through Accessibility. If Play Protect blocks the install or a payment app objects, Rewire Lite has the same features without Accessibility."
                    else "No Accessibility service, so Play Protect allows the install and payment apps keep working. Guard notices apps through Usage access within about a second.",
                ) { open(INSTALL_GUIDE_URL) }
                ListItem(
                    headlineContent = { Text("Privacy") },
                    supportingContent = { Text("All data stays on this device.") },
                    colors = itemColors(),
                )
            }

            SectionTitle("Developer")
            Group {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UserAvatar("aiyu-ayaan", shapeIndex = 0, size = 96.dp)
                    Text("aiyu-ayaan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("Built Rewire.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                LinkRow(Icons.Rounded.Code, "GitHub", GITHUB_URL) { open(GITHUB_URL) }
                LinkRow(Icons.Rounded.Language, "Portfolio", "aiyu.co.in") { open(PORTFOLIO_URL) }
            }

            SectionTitle("Open source")
            Group {
                LinkRow(Icons.Rounded.Description, "Acknowledgements", "Libraries Rewire is built on", onClick = onOpenAcknowledgements, external = false)
            }
        }
    }
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, external: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
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
