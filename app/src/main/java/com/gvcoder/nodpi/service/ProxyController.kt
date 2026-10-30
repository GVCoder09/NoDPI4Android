package com.gvcoder.nodpi.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.gvcoder.nodpi.core.ProxyConfig
import com.gvcoder.nodpi.core.ProxyLog
import com.gvcoder.nodpi.core.StatsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProxyStatus {
    data object Stopped : ProxyStatus
    data object Starting : ProxyStatus

    /** [blacklistRevision] is the blacklist revision the proxy was started with */
    data class Running(val config: ProxyConfig, val blacklistRevision: Int) : ProxyStatus
    data class Failed(val message: String) : ProxyStatus
}

/** State of the proxy shared between the service, the UI and the tile */
object ProxyController {

    private val _status = MutableStateFlow<ProxyStatus>(ProxyStatus.Stopped)
    val status: StateFlow<ProxyStatus> = _status.asStateFlow()

    private val _stats = MutableStateFlow(StatsSnapshot())
    val stats: StateFlow<StatsSnapshot> = _stats.asStateFlow()

    val log = ProxyLog()

    val isActive: Boolean
        get() = status.value.let { it is ProxyStatus.Running || it is ProxyStatus.Starting }

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, ProxyService.intent(context, ProxyService.ACTION_START))
    }

    fun restart(context: Context) {
        ContextCompat.startForegroundService(context, ProxyService.intent(context, ProxyService.ACTION_RESTART))
    }

    fun stop(context: Context) {
        if (!isActive) {
            return
        }
        runCatching { context.startService(ProxyService.intent(context, ProxyService.ACTION_STOP)) }
    }

    fun toggle(context: Context) {
        if (isActive) stop(context) else start(context)
    }

    internal fun setStatus(status: ProxyStatus) {
        _status.value = status
    }

    internal fun setStats(stats: StatsSnapshot) {
        _stats.value = stats
    }
}
