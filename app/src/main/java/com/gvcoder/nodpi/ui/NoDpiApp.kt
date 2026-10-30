package com.gvcoder.nodpi.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.service.ProxyController
import com.gvcoder.nodpi.service.ProxyStatus
import com.gvcoder.nodpi.ui.blacklist.BlacklistScreen
import com.gvcoder.nodpi.ui.home.HomeScreen
import com.gvcoder.nodpi.ui.logs.LogsScreen
import com.gvcoder.nodpi.ui.settings.AboutScreen
import com.gvcoder.nodpi.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Destination(@param:StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    BLACKLIST(R.string.nav_blacklist, Icons.Outlined.Shield, Icons.Rounded.Shield),
    LOGS(R.string.nav_logs, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun NoDpiApp(
    settings: AppSettings?,
    startRequested: Boolean,
    onStartRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val status by ProxyController.status.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    // The proxy works without the notification permission, so the result does not matter
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ProxyController.start(context)
    }
    val startProxy: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ProxyController.start(context)
        }
    }

    LaunchedEffect(startRequested, settings?.disclaimerAccepted) {
        if (startRequested && settings?.disclaimerAccepted == true) {
            if (!ProxyController.isActive) startProxy()
            onStartRequestHandled()
        }
    }

    LaunchedEffect(status) {
        (status as? ProxyStatus.Failed)?.let { snackbar.showSnackbar(it.message, withDismissAction = true) }
    }

    if (settings == null) {
        // Settings are still loading: keep the window background instead of flashing the disclaimer
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ShortNavigationBar {
                Destination.entries.forEach { item ->
                    val selected = item == destination
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = {
                            destination = item
                            if (item != Destination.SETTINGS) showAbout = false
                        },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            AnimatedContent(
                targetState = if (destination == Destination.SETTINGS && showAbout) null else destination,
                transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
                label = "destination",
            ) { target ->
                when (target) {
                    Destination.HOME -> HomeScreen(settings, status, onStart = startProxy)
                    Destination.BLACKLIST -> BlacklistScreen(settings, snackbar)
                    Destination.LOGS -> LogsScreen(snackbar)
                    Destination.SETTINGS -> SettingsScreen(settings, status, onOpenAbout = { showAbout = true })
                    null -> AboutScreen(onBack = { showAbout = false })
                }
            }
        }
    }

    if (!settings.disclaimerAccepted) {
        DisclaimerDialog(
            onAccept = { scope.launch { AppGraph.settings.update { it.copy(disclaimerAccepted = true) } } },
            onDecline = { (context as? Activity)?.finish() },
        )
    } else if (!settings.onboardingDone) {
        OnboardingDialog(
            batteryRestricted = remember { !isIgnoringBatteryOptimizations(context) },
            onDone = { battery, autostart ->
                scope.launch { AppGraph.settings.update { it.copy(onboardingDone = true, autostart = autostart) } }
                if (battery) requestIgnoreBatteryOptimizations(context)
            },
        )
    }
}

/** First launch: offers what the proxy needs to keep running in the background */
@Composable
private fun OnboardingDialog(batteryRestricted: Boolean, onDone: (battery: Boolean, autostart: Boolean) -> Unit) {
    var battery by rememberSaveable { mutableStateOf(batteryRestricted) }
    var autostart by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.RocketLaunch, contentDescription = null) },
        title = { Text(stringResource(R.string.onboarding_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.onboarding_text))
                if (batteryRestricted) {
                    CheckRow(
                        checked = battery,
                        onCheckedChange = { battery = it },
                        title = stringResource(R.string.onboarding_battery),
                        description = stringResource(R.string.onboarding_battery_desc),
                    )
                }
                CheckRow(
                    checked = autostart,
                    onCheckedChange = { autostart = it },
                    title = stringResource(R.string.settings_autostart),
                    description = stringResource(R.string.settings_autostart_desc),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(battery && batteryRestricted, autostart) }) {
                Text(stringResource(R.string.onboarding_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDone(false, false) }) { Text(stringResource(R.string.onboarding_skip)) }
        },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DisclaimerDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
        title = { Text(stringResource(R.string.disclaimer_title)) },
        text = {
            Text(
                stringResource(R.string.disclaimer_text),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.disclaimer_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.disclaimer_decline)) }
        },
    )
}
