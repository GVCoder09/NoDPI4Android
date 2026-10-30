package com.gvcoder.nodpi.ui.blacklist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.ui.components.InfoBanner
import com.gvcoder.nodpi.ui.components.groupItemShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ConfirmAction { RESET, CLEAR }

@Composable
fun BlacklistScreen(settings: AppSettings, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = AppGraph.blacklist
    val domains by repository.domains.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<ConfirmAction?>(null) }

    val shown = remember(domains, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) domains else domains.filter { it.contains(q) }
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    repository.add(text)
                }.getOrNull()
            }
            message(
                if (added == null) context.getString(R.string.blacklist_import_failed)
                else context.resources.getQuantityString(R.plurals.blacklist_added, added, added),
            )
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(repository.export().toByteArray()) }
                }.isSuccess
            }
            message(context.getString(if (ok) R.string.blacklist_exported else R.string.blacklist_export_failed))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_blacklist)) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blacklist_import)) },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("text/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blacklist_export)) },
                                leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("blacklist.txt")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blacklist_reset)) },
                                leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                                onClick = {
                                    menuOpen = false
                                    confirm = ConfirmAction.RESET
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blacklist_clear)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                onClick = {
                                    menuOpen = false
                                    confirm = ConfirmAction.CLEAR
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.blacklist_add)) },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    when (settings.blacklistMode) {
                        BlacklistMode.ALL -> InfoBanner(Icons.Rounded.Public, stringResource(R.string.blacklist_mode_all_hint))
                        BlacklistMode.AUTO -> InfoBanner(Icons.Rounded.AutoAwesome, stringResource(R.string.blacklist_mode_auto_hint))
                        BlacklistMode.STANDARD -> Unit
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.blacklist_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, stringResource(R.string.action_clear)) }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        pluralStringResource(R.plurals.blacklist_count, domains.size, domains.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (domains.isEmpty()) R.string.blacklist_empty else R.string.blacklist_nothing_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            itemsIndexed(shown, key = { _, domain -> domain }) { index, domain ->
                Surface(
                    shape = groupItemShape(index, shown.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.animateItem(),
                ) {
                    ListItem(
                        headlineContent = { Text(domain) },
                        leadingContent = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            IconButton(onClick = {
                                val position = domains.indexOf(domain)
                                repository.remove(domain)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = context.getString(R.string.blacklist_removed, domain),
                                        actionLabel = context.getString(R.string.action_undo),
                                        withDismissAction = true,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        repository.insert(domain, position)
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddDomainsDialog(
            onDismiss = { showAdd = false },
            onAdd = { text ->
                showAdd = false
                val added = repository.add(text)
                message(
                    if (added == 0) context.getString(R.string.blacklist_nothing_added)
                    else context.resources.getQuantityString(R.plurals.blacklist_added, added, added),
                )
            },
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(stringResource(if (action == ConfirmAction.RESET) R.string.blacklist_reset_title else R.string.blacklist_clear_title))
            },
            text = {
                Text(stringResource(if (action == ConfirmAction.RESET) R.string.blacklist_reset_text else R.string.blacklist_clear_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == ConfirmAction.RESET) repository.resetToDefault() else repository.clear()
                    confirm = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AddDomainsDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Add, null) },
        title = { Text(stringResource(R.string.blacklist_add_title)) },
        text = {
            Column {
                Text(stringResource(R.string.blacklist_add_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("example.com") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
