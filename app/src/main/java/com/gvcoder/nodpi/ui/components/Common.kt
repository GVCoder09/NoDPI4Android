package com.gvcoder.nodpi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.service.ProxyStatus

private val OuterCorner = 24.dp
private val InnerCorner = 6.dp

/** Expressive "connected" shape: the group is rounded on the outside, items are slightly rounded inside */
fun groupItemShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Titled group of items drawn as connected cards */
@Composable
fun ItemGroup(
    title: String?,
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                Surface(
                    shape = groupItemShape(index, items.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item()
                }
            }
        }
    }
}

/** Colored card with an icon and a short explanation */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(OuterCorner),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                action()
            }
        }
    }
}

/** True if the running proxy was started with settings or a blacklist different from the current ones */
fun hasPendingChanges(status: ProxyStatus, settings: AppSettings, blacklistRevision: Int): Boolean {
    if (status !is ProxyStatus.Running) {
        return false
    }
    val config = settings.toProxyConfig()
    if (status.config != config) {
        return true
    }
    return config.blacklistMode != BlacklistMode.ALL && status.blacklistRevision != blacklistRevision
}
