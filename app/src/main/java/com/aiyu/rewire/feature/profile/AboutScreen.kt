package com.aiyu.rewire.feature.profile

import com.aiyu.rewire.ui.components.readableWidth

import android.content.Intent
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
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
import com.aiyu.rewire.ui.components.UserAvatar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.ui.components.SectionTitle

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
                title = { Text(stringResource(R.string.about_title)) },
                subtitle = { Text(stringResource(R.string.about_tagline)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.warning_back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle(stringResource(R.string.about_app))
            Group {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, stringResource(if (BuildConfig.ACCESSIBILITY) R.string.about_edition_full else R.string.about_edition_lite))) },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    colors = itemColors(),
                )
                LinkRow(
                    Icons.Rounded.Info,
                    stringResource(if (BuildConfig.ACCESSIBILITY) R.string.about_build_full else R.string.about_build_lite),
                    stringResource(if (BuildConfig.ACCESSIBILITY) R.string.about_build_full_desc else R.string.about_build_lite_desc),
                ) { open(INSTALL_GUIDE_URL) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_privacy)) },
                    supportingContent = { Text(stringResource(R.string.about_privacy_desc)) },
                    colors = itemColors(),
                )
            }

            SectionTitle(stringResource(R.string.about_developer))
            Group {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UserAvatar("aiyu-ayaan", shapeIndex = 0, size = 96.dp)
                    Text("aiyu-ayaan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.about_built), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                LinkRow(Icons.Rounded.Code, "GitHub", GITHUB_URL) { open(GITHUB_URL) }
                LinkRow(Icons.Rounded.Language, stringResource(R.string.about_portfolio), "aiyu.co.in") { open(PORTFOLIO_URL) }
            }

            SectionTitle(stringResource(R.string.about_open_source))
            Group {
                LinkRow(Icons.Rounded.Description, stringResource(R.string.about_acknowledgements), stringResource(R.string.about_acknowledgements_desc), onClick = onOpenAcknowledgements, external = false)
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
