package com.gvcoder.nodpi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gvcoder.nodpi.data.AppLocale
import com.gvcoder.nodpi.data.AppSettings
import com.gvcoder.nodpi.data.ThemeMode
import com.gvcoder.nodpi.ui.NoDpiApp
import com.gvcoder.nodpi.ui.theme.NoDpiTheme
import com.gvcoder.nodpi.ui.theme.isDark

class MainActivity : ComponentActivity() {

    /** Set when the app is opened by the quick settings tile to start the proxy */
    private val startRequested = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            consumeStartRequest(intent)
        }

        setContent {
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val darkTheme = (settings?.themeMode ?: ThemeMode.SYSTEM).isDark()

            // System bar icons follow the app theme, which may differ from the system one
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose { }
            }

            NoDpiTheme(darkTheme = darkTheme, dynamicColor = settings?.dynamicColor ?: false) {
                NoDpiApp(
                    settings = settings,
                    startRequested = startRequested.value,
                    onStartRequestHandled = { startRequested.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeStartRequest(intent)
    }

    private fun consumeStartRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_PROXY, false) == true) {
            intent.removeExtra(EXTRA_START_PROXY)
            startRequested.value = true
        }
    }

    companion object {
        const val EXTRA_START_PROXY = "start_proxy"

        // Same scrims as the androidx defaults for 3-button navigation
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
