package com.gvcoder.nodpi.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gvcoder.nodpi.core.BlacklistMode
import com.gvcoder.nodpi.core.DomainMatching
import com.gvcoder.nodpi.core.ProxyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val host: String = ProxyConfig.DEFAULT_HOST,
    val port: Int = ProxyConfig.DEFAULT_PORT,
    val blacklistMode: BlacklistMode = BlacklistMode.STANDARD,
    val domainMatching: DomainMatching = DomainMatching.STRICT,
    val disorder: Boolean = true,
    val authEnabled: Boolean = false,
    val authUsername: String = "",
    val authPassword: String = "",
    val autostart: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    fun toProxyConfig(): ProxyConfig {
        val auth = authEnabled && authUsername.isNotEmpty()
        return ProxyConfig(
            host = host.ifBlank { ProxyConfig.DEFAULT_HOST },
            port = port,
            blacklistMode = blacklistMode,
            domainMatching = domainMatching,
            disorder = disorder,
            username = if (auth) authUsername else null,
            password = if (auth) authPassword else null,
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[HOST] = updated.host
            prefs[PORT] = updated.port
            prefs[BLACKLIST_MODE] = updated.blacklistMode.name
            prefs[DOMAIN_MATCHING] = updated.domainMatching.name
            prefs[DISORDER] = updated.disorder
            prefs[AUTH_ENABLED] = updated.authEnabled
            prefs[AUTH_USERNAME] = updated.authUsername
            prefs[AUTH_PASSWORD] = updated.authPassword
            prefs[AUTOSTART] = updated.autostart
            prefs[THEME_MODE] = updated.themeMode.name
            prefs[DYNAMIC_COLOR] = updated.dynamicColor
            prefs[DISCLAIMER_ACCEPTED] = updated.disclaimerAccepted
            prefs[ONBOARDING_DONE] = updated.onboardingDone
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            host = this[HOST] ?: defaults.host,
            port = this[PORT] ?: defaults.port,
            blacklistMode = enumValue(this[BLACKLIST_MODE], defaults.blacklistMode),
            domainMatching = enumValue(this[DOMAIN_MATCHING], defaults.domainMatching),
            disorder = this[DISORDER] ?: defaults.disorder,
            authEnabled = this[AUTH_ENABLED] ?: defaults.authEnabled,
            authUsername = this[AUTH_USERNAME] ?: defaults.authUsername,
            authPassword = this[AUTH_PASSWORD] ?: defaults.authPassword,
            autostart = this[AUTOSTART] ?: defaults.autostart,
            themeMode = enumValue(this[THEME_MODE], defaults.themeMode),
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            disclaimerAccepted = this[DISCLAIMER_ACCEPTED] ?: defaults.disclaimerAccepted,
            onboardingDone = this[ONBOARDING_DONE] ?: defaults.onboardingDone,
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val BLACKLIST_MODE = stringPreferencesKey("blacklist_mode")
        val DOMAIN_MATCHING = stringPreferencesKey("domain_matching")
        val DISORDER = booleanPreferencesKey("disorder")
        val AUTH_ENABLED = booleanPreferencesKey("auth_enabled")
        val AUTH_USERNAME = stringPreferencesKey("auth_username")
        val AUTH_PASSWORD = stringPreferencesKey("auth_password")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
