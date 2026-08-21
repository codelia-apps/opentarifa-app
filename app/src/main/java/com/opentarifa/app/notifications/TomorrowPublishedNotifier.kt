package com.opentarifa.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.opentarifa.app.MainActivity
import com.opentarifa.app.R
import com.opentarifa.app.data.local.NotificationPreferencesRepository
import com.opentarifa.app.data.model.HourlyPrice
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val LOG_TAG = "TomorrowPublishedCheck"

private val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

/** Id fijo, fuera del rango 0-23 que usa AlarmFireReceiver para notificaciones de alertas por hora. */
private const val TOMORROW_PUBLISHED_NOTIFICATION_ID = 1000

/**
 * Aviso opcional (Ajustes → "Avisarme cuando se publiquen los precios de mañana"): si está
 * activado y todavía no se ha avisado hoy de esta publicación, dispara una notificación puntual
 * que abre la app en Mañana. [tomorrowPrices] ya viene comprobado como no vacío por el llamante
 * (ver [TomorrowPublishedCheckWorker], que también usa esa misma comprobación para reprogramar
 * las alertas recurrentes de Tipo B, independientemente de si este aviso está activado o no).
 */
suspend fun checkAndNotifyTomorrowPricesPublished(
    context: Context,
    notificationPreferencesRepository: NotificationPreferencesRepository,
    tomorrowPrices: List<HourlyPrice>,
    today: LocalDate
) {
    if (tomorrowPrices.isEmpty()) return

    if (!notificationPreferencesRepository.notifyTomorrowPublished.first()) {
        Log.d(LOG_TAG, "${LocalTime.now(MadridZone)} — aviso desactivado en Ajustes, no se notifica")
        return
    }
    if (notificationPreferencesRepository.lastTomorrowPublishedNotifiedDate() == today.toString()) {
        Log.d(LOG_TAG, "${LocalTime.now(MadridZone)} — ya se avisó hoy ($today), no se repite")
        return
    }

    showNotification(context)
    notificationPreferencesRepository.setLastTomorrowPublishedNotifiedDate(today.toString())
    Log.d(LOG_TAG, "${LocalTime.now(MadridZone)} — precios de mañana detectados, notificación disparada")
}

private fun showNotification(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val contentIntent = PendingIntent.getActivity(
        context,
        TOMORROW_PUBLISHED_NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TOMORROW, true)
        },
        PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, OpenTarifaNotificationChannels.PRICE_ALERTS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Precios de mañana disponibles")
        .setContentText("Los precios de mañana ya están disponibles")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

    NotificationManagerCompat.from(context).notify(TOMORROW_PUBLISHED_NOTIFICATION_ID, notification)
}
