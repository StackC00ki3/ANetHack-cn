package com.yywspace.anethack

import android.app.Application
import androidx.preference.PreferenceManager

class ANetHackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeManager.apply(prefs.getString(ThemeManager.PREF_KEY, ThemeManager.DEFAULT_MODE))
    }
}
