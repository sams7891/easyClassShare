package com.easyclassshare.app.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The app's languages. [tag] is empty for "same as the device". */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    LATVIAN("lv"),
    RUSSIAN("ru"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Settings of this device that are not about classes: language and light/dark look.
 * Stored on the device only.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME = "theme"

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private var loaded = false

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val prefs = prefs(context)
        _language.value = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, ""))
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
        loaded = true
    }

    fun init(context: Context) = load(context)

    fun setLanguage(context: Context, language: AppLanguage) {
        load(context)
        prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    fun setTheme(context: Context, mode: ThemeMode) {
        load(context)
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    /**
     * Returns [base] with the chosen app language, for attachBaseContext of the
     * activity, the application and the services. Unchanged when the device language is used.
     */
    fun wrap(base: Context): Context {
        load(base)
        val tag = _language.value.tag
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
