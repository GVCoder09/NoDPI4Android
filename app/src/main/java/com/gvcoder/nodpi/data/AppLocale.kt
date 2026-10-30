package com.gvcoder.nodpi.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru"),
}

/**
 * Per-app language. Android 13+ keeps it in the system (it is also shown in the system app
 * language settings); older versions store it here and apply it through [wrap]
 */
object AppLocale {

    private const val PREFS = "locale"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) "" else locales[0].language
        } else {
            prefs(context).getString(KEY_LANGUAGE, "").orEmpty()
        }
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activity by itself
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        } else {
            prefs(activity).edit().putString(KEY_LANGUAGE, language.tag).apply()
            activity.recreate()
        }
    }

    /** Applies the chosen language to a context before Android 13 */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val tag = prefs(base).getString(KEY_LANGUAGE, "").orEmpty()
        if (tag.isEmpty()) {
            return base
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
