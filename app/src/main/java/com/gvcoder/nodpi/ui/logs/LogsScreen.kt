package com.gvcoder.nodpi.ui.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.DomainBlacklist
import com.gvcoder.nodpi.core.DomainMatching
import com.gvcoder.nodpi.core.LogEntry
import com.gvcoder.nodpi.core.LogKind
import com.gvcoder.nodpi.service.ProxyController
import com.gvcoder.nodpi.ui.components.groupItemShape
import com.gvcoder.nodpi.ui.formatSize
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: Int, val kinds: Set<LogKind>) {
    ALL(R.string.logs_filter_all, LogKind.entries.toSet()),
    UNBLOCKED(R.string.logs_filter_unblocked, setOf(LogKind.UNBLOCKED)),
    DIRECT(R.string.logs_filter_direct, setOf(LogKind.DIRECT, LogKind.PLAIN)),
    ERRORS(R.string.logs_filter_errors, setOf(LogKind.ERROR)),
}

@Composable
fun LogsScreen(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by ProxyController.log.entries.collectAsStateWithLifecycle()
    val domains by AppGraph.blacklist.domains.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }

    val shown = remember(entries, filter) { entries.asReversed().filter { it.kind in filter.kinds } }
    val blacklist = remember(domains) { DomainBlacklist(domains, DomainMatching.STRICT) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_logs)) },
                actions = {
                    IconButton(enabled = entries.isNotEmpty(), onClick = {
                        val text = entries.joinToString("\n") { it.toLine(timeFormat) }
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, null))
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(enabled = entries.isNotEmpty(), onClick = { ProxyController.log.clear() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.action_clear))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                LogFilter.entries.forEach {
                    FilterChip(
                        selected = filter == it,
                        onClick = { filter = it },
                        label = { Text(stringResource(it.label)) },
                    )
                }
            }

            if (shown.isEmpty()) {
                EmptyLogs(Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(shown, key = { _, entry -> entry.id }) { index, entry ->
                        val canAdd = entry.kind == LogKind.DIRECT || entry.kind == LogKind.ERROR
                        LogItem(
                            entry = entry,
                            time = timeFormat.format(Date(entry.time)),
                            index = index,
                            count = shown.size,
                            onAddToBlacklist = if (canAdd && entry.host != "?" && !blacklist.isBlocked(entry.host)) {
                                {
                                    AppGraph.blacklist.add(entry.host)
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.logs_added_to_blacklist, entry.host)) }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(
    entry: LogEntry,
    time: String,
    index: Int,
    count: Int,
    onAddToBlacklist: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (icon, container, content) = when (entry.kind) {
        LogKind.UNBLOCKED -> Triple(Icons.Rounded.LockOpen, colors.tertiaryContainer, colors.onTertiaryContainer)
        LogKind.DIRECT -> Triple(Icons.Rounded.Lock, colors.secondaryContainer, colors.onSecondaryContainer)
        LogKind.PLAIN -> Triple(Icons.Rounded.Http, colors.secondaryContainer, colors.onSecondaryContainer)
        LogKind.ERROR -> Triple(Icons.Rounded.ErrorOutline, colors.errorContainer, colors.onErrorContainer)
    }

    Surface(shape = groupItemShape(index, count), color = colors.surfaceContainer, modifier = modifier) {
        ListItem(
            overlineContent = { Text("$time · ${entry.method}") },
            headlineContent = { Text(entry.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val traffic = "↓ ${formatSize(entry.bytesIn)}   ↑ ${formatSize(entry.bytesOut)}"
                Text(
                    entry.message ?: if (entry.cloudflare) "$traffic\n${stringResource(R.string.logs_cloudflare)}" else traffic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = { KindIcon(icon, container, content) },
            trailingContent = onAddToBlacklist?.let {
                {
                    IconButton(onClick = it) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.logs_add_to_blacklist))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
        )
    }
}

@Composable
private fun KindIcon(icon: ImageVector, container: Color, content: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmptyLogs(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(64.dp),
        )
        Text(
            stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private fun LogEntry.toLine(format: SimpleDateFormat): String {
    val time = format.format(Date(time))
    return if (kind == LogKind.ERROR) {
        "$time ERROR $method $host : $message"
    } else {
        "$time ${kind.name} $method $host $bytesIn $bytesOut"
    }
}
