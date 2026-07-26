package com.voltia.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "settings")

class ThemePreferencesRepository(private val context: Context) {
    private object Keys {
        val DARK_MODE_ENABLED = booleanPreferencesKey("dark_mode_enabled")
    }

    /** null si el usuario no ha elegido explícitamente: se debe seguir el tema del sistema. */
    val darkModeEnabled: Flow<Boolean?> =
        context.themeDataStore.data.map { prefs -> prefs[Keys.DARK_MODE_ENABLED] }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs -> prefs[Keys.DARK_MODE_ENABLED] = enabled }
    }
}
