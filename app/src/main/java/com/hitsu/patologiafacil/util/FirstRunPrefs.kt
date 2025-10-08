package com.hitsu.patologiafacil.util

import android.content.Context

object FirstRunPrefs {

    private const val PREF_NAME = "pf_first_run"
    private const val KEY_TUTORIAL_SEEN = "tutorial_seen"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isTutorialSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TUTORIAL_SEEN, false)

    fun setTutorialSeen(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_TUTORIAL_SEEN, seen).apply()
    }
}
