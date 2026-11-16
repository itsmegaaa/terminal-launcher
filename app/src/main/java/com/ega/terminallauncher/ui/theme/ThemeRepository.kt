package com.ega.terminallauncher.ui.theme

import android.content.Context
import android.content.SharedPreferences

interface ThemeRepository {
    fun getTheme(): ThemePreset
    fun setTheme(preset: ThemePreset)
}

class InMemoryThemeRepository(initial: ThemePreset = ThemePreset.CLAUDE) : ThemeRepository {
    private var currentTheme: ThemePreset = initial

    override fun getTheme(): ThemePreset = currentTheme

    override fun setTheme(preset: ThemePreset) {
        currentTheme = preset
    }
}

class SharedPrefsThemeRepository(context: Context) : ThemeRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getTheme(): ThemePreset {
        val savedId = prefs.getString(KEY_THEME, ThemePreset.CLAUDE.id) ?: ThemePreset.CLAUDE.id
        return ThemePreset.fromId(savedId) ?: ThemePreset.CLAUDE
    }

    override fun setTheme(preset: ThemePreset) {
        prefs.edit().putString(KEY_THEME, preset.id).apply()
    }

    companion object {
        const val PREFS_NAME = "ash_launcher_theme"
        const val KEY_THEME = "selected_theme"
    }
}
