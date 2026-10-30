package com.gvcoder.nodpi.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.StatsSnapshot
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.service.ProxyController
import com.gvcoder.nodpi.service.ProxyStatus
import com.gvcoder.nodpi.service.address
import com.gvcoder.nodpi.ui.components.InfoBanner
import com.gvcoder.nodpi.ui.components.MorphShape
import com.gvcoder.nodpi.ui.components.hasPendingChanges
import com.gvcoder.nodpi.ui.formatSize
import com.gvcoder.nodpi.ui.formatSpeed
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun HomeScreen(
    settings: AppSettings,
    status: ProxyStatus,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val stats by ProxyController.stats.collectAsStateWithLifecycle()
    val revision by AppGraph.blacklist.revision.collectAsStateWithLifecycle()
    val domains by AppGraph.blacklist.domains.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold) },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            PowerButton(
                status = status,
                onClick = { if (ProxyController.isActive) ProxyController.stop(context) else onStart() },
                modifier = Modifier.padding(top = 16.dp),
            )

            StatusText(status, settings, domains.size)

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

            val runningConfig = (status as? ProxyStatus.Running)?.config ?: settings.toProxyConfig()
            AddressCard(runningConfig.address(), runningConfig.host, runningConfig.port)

            StatisticsSection(stats)

            HowToCard()

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PowerButton(status: ProxyStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = status is ProxyStatus.Running || status is ProxyStatus.Starting
    val colors = MaterialTheme.colorScheme

    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "morph",
    )
    // The circle is symmetric, so rotation is only needed (and only runs) while the shape is a cookie
    val rotation = if (progress > 0f) {
        val transition = rememberInfiniteTransition(label = "rotation")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
        angle
    } else {
        0f
    }

    val container by animateColorAsState(
        when (status) {
            is ProxyStatus.Running -> colors.primary
            ProxyStatus.Starting -> colors.tertiaryContainer
            is ProxyStatus.Failed -> colors.errorContainer
            ProxyStatus.Stopped -> colors.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "container",
    )
    val content by animateColorAsState(
        when (status) {
            is ProxyStatus.Running -> colors.onPrimary
            ProxyStatus.Starting -> colors.onTertiaryContainer
            is ProxyStatus.Failed -> colors.onErrorContainer
            ProxyStatus.Stopped -> colors.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "content",
    )

    Surface(
        onClick = onClick,
        shape = MorphShape(morph, progress, rotation),
        color = container,
        contentColor = content,
        shadowElevation = if (active) 6.dp else 0.dp,
        modifier = modifier.size(208.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (status == ProxyStatus.Starting) {
                LoadingIndicator(color = content, modifier = Modifier.size(96.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = stringResource(if (active) R.string.action_stop else R.string.action_start),
                    modifier = Modifier.size(80.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusText(status: ProxyStatus, settings: AppSettings, domainsCount: Int) {
    val title = when (status) {
        is ProxyStatus.Running -> stringResource(R.string.status_running)
        ProxyStatus.Starting -> stringResource(R.string.status_starting)
        is ProxyStatus.Failed -> stringResource(R.string.status_error)
        ProxyStatus.Stopped -> stringResource(R.string.status_stopped)
    }
    val subtitle = when (status) {
        is ProxyStatus.Running -> when (status.config.blacklistMode) {
            BlacklistMode.STANDARD -> stringResource(R.string.status_running_standard, domainsCount)
            BlacklistMode.ALL -> stringResource(R.string.status_running_all)
            BlacklistMode.AUTO -> stringResource(R.string.status_running_auto, domainsCount)
        }
        ProxyStatus.Starting -> ""
        is ProxyStatus.Failed -> status.message
        ProxyStatus.Stopped -> stringResource(
            if (settings.disorder) R.string.status_stopped_hint_disorder else R.string.status_stopped_hint,
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (status is ProxyStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun AddressCard(address: String, host: String, port: Int) {
    val context = LocalContext.current
    val listensEverywhere = host == "0.0.0.0" || host == "::"
    val lanAddresses = remember(host, port) { if (listensEverywhere) localIpv4Addresses().map { "$it:$port" } else emptyList() }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.proxy_address),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(address, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                if (lanAddresses.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.proxy_lan_addresses, lanAddresses.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { copyToClipboard(context, if (listensEverywhere) "127.0.0.1:$port" else address) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy))
            }
        }
    }
}

@Composable
private fun StatisticsSection(stats: StatsSnapshot) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.statistics),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.stat_total), stats.total, colors.primaryContainer, colors.onPrimaryContainer, Modifier.weight(1f))
            StatTile(stringResource(R.string.stat_unblocked), stats.unblocked, colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.stat_direct), stats.direct, colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f))
            StatTile(stringResource(R.string.stat_errors), stats.errors, colors.errorContainer, colors.onErrorContainer, Modifier.weight(1f))
        }
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 16.dp)) {
                TrafficColumn(
                    icon = { Icon(Icons.Rounded.ArrowDownward, null, tint = colors.primary) },
                    title = stringResource(R.string.traffic_download),
                    total = stats.trafficIn,
                    speed = stats.speedIn,
                    average = stats.averageSpeedIn,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(Modifier.height(88.dp).align(Alignment.CenterVertically))
                TrafficColumn(
                    icon = { Icon(Icons.Rounded.ArrowUpward, null, tint = colors.tertiary) },
                    title = stringResource(R.string.traffic_upload),
                    total = stats.trafficOut,
                    speed = stats.speedOut,
                    average = stats.averageSpeedOut,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Long, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = container, contentColor = content, modifier = modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TrafficColumn(
    icon: @Composable () -> Unit,
    title: String,
    total: Long,
    speed: Double,
    average: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(formatSize(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(formatSpeed(speed), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.traffic_average, formatSpeed(average)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HowToCard() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.howto_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.howto_body), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                    }) {
                        Icon(Icons.Rounded.Wifi, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.howto_open_wifi))
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("NoDPI proxy", text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
}.getOrDefault(emptyList())
