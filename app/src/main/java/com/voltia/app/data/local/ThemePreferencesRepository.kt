package com.voltia.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode {
    LIGHT,
    DARK,
    /** Sigue el tema del sistema, incluso si cambia mientras la app está abierta. */
    SYSTEM
}

class ThemePreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /** [ThemeMode.SYSTEM] por defecto la primera vez que se abre la app. */
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }
}
