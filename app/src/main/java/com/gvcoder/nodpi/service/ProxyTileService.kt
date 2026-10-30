package com.gvcoder.nodpi.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gvcoder.nodpi.MainActivity
import com.gvcoder.nodpi.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick settings tile that toggles the proxy */
class ProxyTileService : TileService() {

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = scope.launch {
            ProxyController.status.collect(::render)
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (ProxyController.isActive) {
            ProxyController.stop(this)
            return
        }
        try {
            ProxyController.start(this)
        } catch (_: Exception) {
            // The system may refuse to start a foreground service from the tile: start it from the app
            openApp()
        }
    }

    private fun render(status: ProxyStatus) {
        val tile = qsTile ?: return
        tile.state = when (status) {
            is ProxyStatus.Running, ProxyStatus.Starting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                is ProxyStatus.Running -> status.config.address()
                ProxyStatus.Starting -> getString(R.string.status_starting)
                is ProxyStatus.Failed -> getString(R.string.status_error)
                ProxyStatus.Stopped -> getString(R.string.status_stopped)
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_START_PROXY, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
