package com.voltia.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(name = "notification_settings")

/**
 * Canal predeterminado para alertas nuevas. Se usa como valor por defecto al
 * crear una alerta rápida desde la pestaña Hoy (todavía no implementado);
 * cada alerta puede luego tener su propio [AlertEntity.channel].
 */
class NotificationPreferencesRepository(private val context: Context) {
    private object Keys {
        val DEFAULT_CHANNEL = stringPreferencesKey("default_channel")
    }

    /** [AlertChannel.SYSTEM_NOTIFICATION] por defecto la primera vez que se abre la app. */
    val defaultChannel: Flow<AlertChannel> = context.notificationDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_CHANNEL]?.let { stored ->
            runCatching { AlertChannel.valueOf(stored) }.getOrNull()
        } ?: AlertChannel.SYSTEM_NOTIFICATION
    }

    suspend fun setDefaultChannel(channel: AlertChannel) {
        context.notificationDataStore.edit { prefs -> prefs[Keys.DEFAULT_CHANNEL] = channel.name }
    }
}
