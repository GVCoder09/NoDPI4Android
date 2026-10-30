package com.gvcoder.nodpi.ui.settings

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.DomainMatching
import com.gvcoder.nodpi.data.AppLanguage
import com.gvcoder.nodpi.data.AppLocale
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.data.ThemeMode
import com.gvcoder.nodpi.service.ProxyController
import com.gvcoder.nodpi.service.ProxyStatus
import com.gvcoder.nodpi.ui.areNotificationsAllowed
import com.gvcoder.nodpi.ui.components.InfoBanner
import com.gvcoder.nodpi.ui.components.ItemGroup
import com.gvcoder.nodpi.ui.components.hasPendingChanges
import com.gvcoder.nodpi.ui.isIgnoringBatteryOptimizations
import com.gvcoder.nodpi.ui.openNotificationSettings
import com.gvcoder.nodpi.ui.requestIgnoreBatteryOptimizations
import kotlinx.coroutines.launch

private const val ANY_HOST = "0.0.0.0"
private const val LOCAL_HOST = "127.0.0.1"

@Composable
fun SettingsScreen(settings: AppSettings, status: ProxyStatus, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by AppGraph.blacklist.revision.collectAsStateWithLifecycle()

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { AppGraph.settings.update(transform) }
    }

    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var notificationsAllowed by remember { mutableStateOf(areNotificationsAllowed(context)) }
    var language by remember { mutableStateOf(AppLocale.current(context)) }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = isIgnoringBatteryOptimizations(context)
        notificationsAllowed = areNotificationsAllowed(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AnimatedVisibility(hasPendingChanges(status, settings, revision)) {
                InfoBanner(
                    icon = Icons.Rounded.RestartAlt,
                    text = stringResource(R.string.pending_changes),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    action = {
                        TextButton(onClick = { ProxyController.restart(context) }) {
                            Text(stringResource(R.string.action_restart))
                        }
                    },
                )
            }

            ProxySection(settings, ::update)

            ItemGroup(
                title = stringResource(R.string.settings_unblocking),
                items = listOf<@Composable () -> Unit>(
                    {
                        ChoiceItem(
                            title = stringResource(R.string.settings_mode),
                            description = stringResource(
                                when (settings.blacklistMode) {
                                    BlacklistMode.STANDARD -> R.string.settings_mode_standard_desc
                                    BlacklistMode.ALL -> R.string.settings_mode_all_desc
                                    BlacklistMode.AUTO -> R.string.settings_mode_auto_desc
                                },
                            ),
                            options = listOf(
                                stringResource(R.string.settings_mode_standard),
                                stringResource(R.string.settings_mode_all),
                                stringResource(R.string.settings_mode_auto),
                            ),
                            selected = settings.blacklistMode.ordinal,
                            onSelect = { index -> update { it.copy(blacklistMode = BlacklistMode.entries[index]) } },
                        )
                    },
                    {
                        ChoiceItem(
                            title = stringResource(R.string.settings_matching),
                            description = stringResource(
                                if (settings.domainMatching == DomainMatching.STRICT) R.string.settings_matching_strict_desc
                                else R.string.settings_matching_loose_desc,
                            ),
                            options = listOf(
                                stringResource(R.string.settings_matching_strict),
                                stringResource(R.string.settings_matching_loose),
                            ),
                            selected = settings.domainMatching.ordinal,
                            onSelect = { index -> update { it.copy(domainMatching = DomainMatching.entries[index]) } },
                        )
                    },
                    {
                        SwitchItem(
                            icon = Icons.Rounded.Shuffle,
                            title = stringResource(R.string.settings_disorder),
                            description = stringResource(R.string.settings_disorder_desc),
                            checked = settings.disorder,
                            onCheckedChange = { value -> update { it.copy(disorder = value) } },
                        )
                    },
                ),
            )

            AuthSection(settings, ::update)

            ItemGroup(
                title = stringResource(R.string.settings_appearance),
                items = buildList<@Composable () -> Unit> {
                    add {
                        ChoiceItem(
                            title = stringResource(R.string.settings_theme),
                            description = null,
                            options = listOf(
                                stringResource(R.string.settings_theme_system),
                                stringResource(R.string.settings_theme_light),
                                stringResource(R.string.settings_theme_dark),
                            ),
                            selected = settings.themeMode.ordinal,
                            onSelect = { index -> update { it.copy(themeMode = ThemeMode.entries[index]) } },
                        )
                    }
                    add {
                        ChoiceItem(
                            title = stringResource(R.string.settings_language),
                            description = null,
                            options = listOf(
                                stringResource(R.string.settings_language_system),
                                stringResource(R.string.language_english),
                                stringResource(R.string.language_russian),
                            ),
                            selected = language.ordinal,
                            onSelect = { index ->
                                val chosen = AppLanguage.entries[index]
                                if (chosen != language) {
                                    language = chosen
                                    (context as? Activity)?.let { AppLocale.set(it, chosen) }
                                }
                            },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add {
                            SwitchItem(
                                icon = Icons.Rounded.Palette,
                                title = stringResource(R.string.settings_dynamic_color),
                                description = stringResource(R.string.settings_dynamic_color_desc),
                                checked = settings.dynamicColor,
                                onCheckedChange = { value -> update { it.copy(dynamicColor = value) } },
                            )
                        }
                    }
                },
            )

            ItemGroup(
                title = stringResource(R.string.settings_system),
                items = buildList<@Composable () -> Unit> {
                    add {
                        SwitchItem(
                            icon = Icons.Rounded.PowerSettingsNew,
                            title = stringResource(R.string.settings_autostart),
                            description = stringResource(R.string.settings_autostart_desc),
                            checked = settings.autostart,
                            onCheckedChange = { value -> update { it.copy(autostart = value) } },
                        )
                    }
                    add {
                        NavigationItem(
                            icon = if (batteryUnrestricted) Icons.Rounded.BatteryFull else Icons.Rounded.BatteryAlert,
                            title = stringResource(R.string.settings_battery),
                            description = stringResource(
                                if (batteryUnrestricted) R.string.settings_battery_ok else R.string.settings_battery_restricted,
                            ),
                            onClick = { requestIgnoreBatteryOptimizations(context) },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add {
                            NavigationItem(
                                icon = if (notificationsAllowed) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                                title = stringResource(R.string.settings_notifications),
                                description = stringResource(
                                    if (notificationsAllowed) R.string.settings_notifications_ok else R.string.settings_notifications_off,
                                ),
                                onClick = { openNotificationSettings(context) },
                            )
                        }
                    }
                },
            )

            ItemGroup(
                title = null,
                items = listOf<@Composable () -> Unit> {
                    NavigationItem(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.about_title),
                        description = stringResource(R.string.about_summary),
                        onClick = onOpenAbout,
                    )
                },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProxySection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    // Text fields keep their own state so that invalid input is not lost while typing
    var host by rememberSaveable { mutableStateOf(settings.host) }
    var port by rememberSaveable { mutableStateOf(settings.port.toString()) }
    val portValue = port.toIntOrNull()
    val portValid = portValue != null && portValue in 1..65535

    ItemGroup(
        title = stringResource(R.string.settings_proxy),
        items = listOf<@Composable () -> Unit>(
            {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { value ->
                            host = value.trim()
                            if (host.isNotEmpty()) update { it.copy(host = host) }
                        },
                        label = { Text(stringResource(R.string.settings_host)) },
                        isError = host.isEmpty(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { value ->
                            port = value.filter(Char::isDigit).take(5)
                            val parsed = port.toIntOrNull()
                            if (parsed != null && parsed in 1..65535) update { it.copy(port = parsed) }
                        },
                        label = { Text(stringResource(R.string.settings_port)) },
                        isError = !portValid,
                        supportingText = if (portValid) null else {
                            { Text(stringResource(R.string.settings_port_invalid)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            {
                SwitchItem(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.settings_lan),
                    description = stringResource(R.string.settings_lan_desc),
                    checked = host == ANY_HOST,
                    onCheckedChange = { enabled ->
                        host = if (enabled) ANY_HOST else LOCAL_HOST
                        update { it.copy(host = host) }
                    },
                )
            },
        ),
    )
}

@Composable
private fun AuthSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var username by rememberSaveable { mutableStateOf(settings.authUsername) }
    var password by rememberSaveable { mutableStateOf(settings.authPassword) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    ItemGroup(
        title = stringResource(R.string.settings_auth),
        items = buildList<@Composable () -> Unit> {
            add {
                SwitchItem(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.settings_auth_enabled),
                    description = stringResource(R.string.settings_auth_desc),
                    checked = settings.authEnabled,
                    onCheckedChange = { value -> update { it.copy(authEnabled = value) } },
                )
            }
            if (settings.authEnabled) {
                add {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { value ->
                                username = value
                                update { it.copy(authUsername = value) }
                            },
                            label = { Text(stringResource(R.string.settings_auth_username)) },
                            isError = username.isEmpty(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { value ->
                                password = value
                                update { it.copy(authPassword = value) }
                            },
                            label = { Text(stringResource(R.string.settings_auth_password)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun NavigationItem(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ChoiceItem(
    title: String,
    description: String?,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
