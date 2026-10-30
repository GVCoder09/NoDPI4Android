package com.gvcoder.nodpi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gvcoder.nodpi.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts the proxy after boot when autostart is enabled (the Android counterpart of desktop --install) */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (AppGraph.settings.current().autostart) {
                    ProxyController.start(context.applicationContext)
                }
            } catch (_: Exception) {
                // Starting a foreground service may be refused by the system; nothing to do here
            } finally {
                pending.finish()
            }
        }
    }
}
