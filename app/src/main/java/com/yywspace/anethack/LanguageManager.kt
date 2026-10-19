package com.yywspace.anethack

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager

/**
 * App-wide language selection.
 *
 * "system" (the default) clears the application locales so Android follows the
 * device language. Any other value pins every activity to that language.
 */
object LanguageManager {
    const val PREF_KEY = "language"
    const val MODE_SYSTEM = "system"
    const val MODE_ZH_CN = "zh-CN"
    const val MODE_EN = "en"
    const val DEFAULT_MODE = MODE_SYSTEM

    fun apply(mode: String?) {
        AppCompatDelegate.setApplicationLocales(
            when (mode) {
                MODE_ZH_CN -> LocaleListCompat.forLanguageTags(MODE_ZH_CN)
                MODE_EN -> LocaleListCompat.forLanguageTags(MODE_EN)
                else -> LocaleListCompat.getEmptyLocaleList()
            }
        )
    }

    fun applyFromPreferences(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        apply(prefs.getString(PREF_KEY, DEFAULT_MODE))
    }
}
