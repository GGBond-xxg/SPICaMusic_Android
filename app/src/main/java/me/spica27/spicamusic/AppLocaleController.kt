package me.spica27.spicamusic

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
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
            .commit()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == LANGUAGE_SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(language)
                }
        } else {
            context.findActivity()?.recreate()
        }
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
