package com.yywspace.anethack

import android.app.Application
import androidx.preference.PreferenceManager

class ANetHackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        LanguageManager.apply(
            prefs.getString(LanguageManager.PREF_KEY, LanguageManager.DEFAULT_MODE)
        )
        ThemeManager.apply(prefs.getString(ThemeManager.PREF_KEY, ThemeManager.DEFAULT_MODE))
    }
}
