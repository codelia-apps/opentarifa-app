package com.opentarifa.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Infraestructura de notificaciones de OpenTarifa: por ahora solo el canal. La
 * lógica real de disparo de alertas (horas fijas, más barata/cara del día)
 * llega en una fase posterior.
 */
object OpenTarifaNotificationChannels {

    const val PRICE_ALERTS_CHANNEL_ID = "price_alerts"

    /**
     * Crea el canal si no existe todavía; no hace nada si ya existía
     * (createNotificationChannel es idempotente) ni en versiones anteriores
     * a Android 8, que no tienen canales.
     */
    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            PRICE_ALERTS_CHANNEL_ID,
            "Avisos de precio de luz",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos cuando el precio de la luz llega a la hora o nivel que has configurado."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
