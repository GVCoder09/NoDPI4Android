package com.gvcoder.nodpi.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.gvcoder.nodpi.AppGraph
import com.gvcoder.nodpi.R
import com.gvcoder.nodpi.core.AllDomainsBlacklist
import com.gvcoder.nodpi.core.AutoBlacklist
import com.gvcoder.nodpi.core.Blacklist
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.DomainBlacklist
import com.gvcoder.nodpi.core.ProxyConfig
import com.gvcoder.nodpi.core.ProxyServer
import com.gvcoder.nodpi.core.ProxyStats
import com.gvcoder.nodpi.core.StatsSnapshot
import com.gvcoder.nodpi.data.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.BindException

/** Foreground service that owns the running [ProxyServer] */
class ProxyService : Service() {

    // All proxy start/stop operations run one at a time on this dispatcher
    private val control = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + control)

    private var server: ProxyServer? = null
    private var statsJob: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { shutdown(null) }
                return START_NOT_STICKY
            }

            ACTION_RESTART -> {
                if (!promote()) return START_NOT_STICKY
                scope.launch {
                    stopProxy()
                    startProxy()
                }
            }

            // ACTION_START or a restart of the sticky service by the system (null intent)
            else -> {
                if (!promote()) return START_NOT_STICKY
                scope.launch {
                    if (server == null) startProxy()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        scope.cancel()
        if (ProxyController.status.value !is ProxyStatus.Failed) {
            ProxyController.setStatus(ProxyStatus.Stopped)
        }
        super.onDestroy()
    }

    /** Puts the service into the foreground; false if the system refused */
    private fun promote(): Boolean {
        val running = ProxyController.status.value as? ProxyStatus.Running
        val notification = ProxyNotifications.build(this, running?.config?.address(), null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, ProxyNotifications.NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            ProxyController.setStatus(ProxyStatus.Failed(e.message ?: e.javaClass.simpleName))
            stopSelf()
            false
        }
    }

    private suspend fun startProxy() {
        ProxyController.setStatus(ProxyStatus.Starting)

        val config = AppGraph.settings.current().toProxyConfig()
        val blacklistRepository = AppGraph.blacklist
        val revision = blacklistRepository.revision.value
        val blacklist: Blacklist = when (config.blacklistMode) {
            BlacklistMode.STANDARD -> DomainBlacklist(blacklistRepository.domains.value, config.domainMatching)
            BlacklistMode.ALL -> AllDomainsBlacklist
            BlacklistMode.AUTO -> AutoBlacklist(
                known = blacklistRepository.domains.value,
                matching = config.domainMatching,
                onDetected = blacklistRepository::addDetected,
            )
        }

        val stats = ProxyStats()
        val proxy = ProxyServer(config, blacklist, stats, ProxyController.log)
        try {
            proxy.start()
        } catch (e: Exception) {
            val message = if (e is BindException) {
                getString(R.string.error_address_in_use, config.address())
            } else {
                getString(R.string.error_start_failed, e.message ?: e.javaClass.simpleName)
            }
            shutdown(ProxyStatus.Failed(message))
            return
        }

        server = proxy
        ProxyController.setStats(StatsSnapshot())
        ProxyController.setStatus(ProxyStatus.Running(config, revision))

        val address = config.address()
        ProxyNotifications.update(this, ProxyNotifications.build(this, address, null))
        statsJob = scope.launch(Dispatchers.Default) {
            var ticks = 0
            while (isActive) {
                delay(1_000)
                val snapshot = stats.tick()
                ProxyController.setStats(snapshot)
                if (++ticks % NOTIFICATION_UPDATE_TICKS == 0) {
                    ProxyNotifications.update(this@ProxyService, ProxyNotifications.build(this@ProxyService, address, snapshot))
                }
            }
        }
    }

    private fun stopProxy() {
        statsJob?.cancel()
        statsJob = null
        server?.stop()
        server = null
    }

    private suspend fun shutdown(status: ProxyStatus.Failed?) {
        stopProxy()
        ProxyController.setStatus(status ?: ProxyStatus.Stopped)
        withContext(Dispatchers.Main) {
            ServiceCompat.stopForeground(this@ProxyService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START = "com.gvcoder.nodpi.action.START"
        const val ACTION_STOP = "com.gvcoder.nodpi.action.STOP"
        const val ACTION_RESTART = "com.gvcoder.nodpi.action.RESTART"

        private const val NOTIFICATION_UPDATE_TICKS = 3

        fun intent(context: Context, action: String): Intent =
            Intent(context, ProxyService::class.java).setAction(action)
    }
}

fun ProxyConfig.address(): String = if (host.contains(':')) "[$host]:$port" else "$host:$port"
