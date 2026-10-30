package com.gvcoder.nodpi

import android.app.Application
import com.gvcoder.nodpi.data.BlacklistRepository
import com.gvcoder.nodpi.data.SettingsRepository
import com.gvcoder.nodpi.service.ProxyNotifications

class NoDpiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        ProxyNotifications.createChannel(this)
    }
}

/** Application-wide singletons */
object AppGraph {
    lateinit var settings: SettingsRepository
        private set
    lateinit var blacklist: BlacklistRepository
        private set

    fun init(app: Application) {
        settings = SettingsRepository(app)
        blacklist = BlacklistRepository(app)
    }
}
