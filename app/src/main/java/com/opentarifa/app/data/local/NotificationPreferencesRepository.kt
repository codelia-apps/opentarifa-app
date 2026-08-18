package com.opentarifa.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val NOTIFY_TOMORROW_PUBLISHED = booleanPreferencesKey("notify_tomorrow_published")
        val LAST_TOMORROW_PUBLISHED_NOTIFIED_DATE = stringPreferencesKey("last_tomorrow_published_notified_date")
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

    /** Aviso opcional de "precios de mañana publicados"; desactivado por defecto. */
    val notifyTomorrowPublished: Flow<Boolean> = context.notificationDataStore.data.map { prefs ->
        prefs[Keys.NOTIFY_TOMORROW_PUBLISHED] ?: false
    }

    suspend fun setNotifyTomorrowPublished(enabled: Boolean) {
        context.notificationDataStore.edit { prefs -> prefs[Keys.NOTIFY_TOMORROW_PUBLISHED] = enabled }
    }

    /** Fecha ISO-8601 (yyyy-MM-dd) del último día en que se avisó de la publicación; null si nunca. */
    suspend fun lastTomorrowPublishedNotifiedDate(): String? =
        context.notificationDataStore.data.first()[Keys.LAST_TOMORROW_PUBLISHED_NOTIFIED_DATE]

    suspend fun setLastTomorrowPublishedNotifiedDate(date: String) {
        context.notificationDataStore.edit { prefs -> prefs[Keys.LAST_TOMORROW_PUBLISHED_NOTIFIED_DATE] = date }
    }
}
