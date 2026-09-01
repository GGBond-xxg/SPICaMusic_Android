package me.spica27.spicamusic

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Persists and applies the language selected from the in-app settings screen. */
object AppLocaleController {
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SIMPLIFIED_CHINESE = "zh-CN"
    const val LANGUAGE_TRADITIONAL_CHINESE = "zh-TW"

    private const val PREFERENCES_NAME = "app_locale"
    private const val KEY_LANGUAGE = "language"

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val language = savedLanguage(context)
        if (language == LANGUAGE_SYSTEM) return context

        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    fun currentLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tags =
                context
                    .getSystemService(LocaleManager::class.java)
                    .applicationLocales
                    .toLanguageTags()
            if (tags.isBlank()) return LANGUAGE_SYSTEM
            return normalize(tags.substringBefore(','))
        }
        return savedLanguage(context)
    }

    fun setLanguage(
        context: Context,
        value: String,
    ) {
        val language = normalize(value)
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            // The first commit after clearing app data has to create and fsync the XML file.
            // Doing that synchronously on the UI thread can drop a frame exactly on the first
            // language change. apply() updates memory immediately and persists off the main thread.
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == LANGUAGE_SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(language)
                }
        } else {
            applyLegacyLanguageInPlace(context, language)
        }
    }

    /**
     * Android 12 and earlier do not expose [LocaleManager]. Updating the Activity resources and
     * dispatching the configuration change keeps the existing Compose navigation stack alive,
     * matching the in-place behavior used on Android 13+ via MainActivity's configChanges entry.
     */
    @Suppress("DEPRECATION")
    private fun applyLegacyLanguageInPlace(
        context: Context,
        language: String,
    ) {
        val activity = context.findActivity() ?: return
        val locales =
            if (language == LANGUAGE_SYSTEM) {
                Resources.getSystem().configuration.locales
            } else {
                LocaleList(Locale.forLanguageTag(language))
            }
        val configuration =
            Configuration(activity.resources.configuration).apply {
                setLocales(locales)
            }
        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        activity.application.resources.updateConfiguration(
            configuration,
            activity.application.resources.displayMetrics,
        )
        locales.get(0)?.let(Locale::setDefault)
        activity.onConfigurationChanged(configuration)
    }

    private fun savedLanguage(context: Context): String =
        normalize(
            context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANGUAGE_SYSTEM),
        )

    private fun normalize(value: String?): String =
        when (value?.trim()?.lowercase(Locale.ROOT)) {
            "en", "en-us", "en-gb" -> LANGUAGE_ENGLISH
            "zh-cn", "zh-hans", "zh-hans-cn" -> LANGUAGE_SIMPLIFIED_CHINESE
            "zh-tw", "zh-hant", "zh-hant-tw", "zh-hk", "zh-hant-hk" ->
                LANGUAGE_TRADITIONAL_CHINESE
            else -> LANGUAGE_SYSTEM
        }

    private tailrec fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
}
