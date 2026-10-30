package com.gvcoder.nodpi.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.BuildConfig
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.ui.components.ItemGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val REPOSITORY_URL = "https://github.com/GVCoder09/NoDPI4Android"
private const val RELEASES_URL = "$REPOSITORY_URL/releases/latest"
private const val DESKTOP_URL = "https://github.com/GVCoder09/NoDPI"
private const val SITE_URL = "https://gvcoder09.github.io/nodpi_site/"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/GVCoder09/NoDPI4Android/releases/latest"

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String) : UpdateState
    data object Failed : UpdateState
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(120.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_full_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_slogan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            UpdateButton(update) {
                when (val state = update) {
                    is UpdateState.Available -> openUrl(context, RELEASES_URL)
                    UpdateState.Checking -> Unit
                    else -> scope.launch {
                        update = UpdateState.Checking
                        update = checkForUpdate()
                    }
                }
            }

            Text(
                stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            ItemGroup(
                title = stringResource(R.string.about_links),
                items = listOf<@Composable () -> Unit>(
                    { LinkItem(Icons.Rounded.Code, stringResource(R.string.about_source), REPOSITORY_URL) },
                    { LinkItem(Icons.Rounded.Computer, stringResource(R.string.about_desktop), DESKTOP_URL) },
                    { LinkItem(Icons.Rounded.Language, stringResource(R.string.about_site), SITE_URL) },
                ),
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Gavel, null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun UpdateButton(state: UpdateState, onClick: () -> Unit) {
    val text = when (state) {
        UpdateState.Idle -> stringResource(R.string.update_check)
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_latest)
        is UpdateState.Available -> stringResource(R.string.update_available, state.version)
        UpdateState.Failed -> stringResource(R.string.update_failed)
    }
    val content: @Composable () -> Unit = {
        if (state == UpdateState.Checking) {
            LoadingIndicator(Modifier.size(24.dp))
        } else {
            Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
    if (state is UpdateState.Available) {
        Button(onClick = onClick) { content() }
    } else {
        FilledTonalButton(onClick = onClick, enabled = state != UpdateState.Checking) { content() }
    }
}

@Composable
private fun LinkItem(icon: ImageVector, title: String, url: String) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(url.removePrefix("https://").trimEnd('/')) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { openUrl(context, url) },
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private suspend fun checkForUpdate(): UpdateState = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val latest = JSONObject(body).getString("tag_name").trimStart('v', 'V')
            if (isNewer(latest, BuildConfig.VERSION_NAME)) UpdateState.Available(latest) else UpdateState.UpToDate
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(UpdateState.Failed)
}

private fun isNewer(candidate: String, current: String): Boolean {
    fun parts(version: String) = version.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
