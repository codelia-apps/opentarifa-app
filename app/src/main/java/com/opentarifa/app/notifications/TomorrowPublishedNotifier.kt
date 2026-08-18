package com.opentarifa.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.opentarifa.app.MainActivity
import com.opentarifa.app.data.local.NotificationPreferencesRepository
import com.opentarifa.app.data.repository.PvpcRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

/** Misma hora aproximada de publicación que usa la pestaña Mañana (ver TomorrowViewModel). */
private val PublicationTime: LocalTime = LocalTime.of(20, 30)

/** Id fijo, fuera del rango 0-23 que usa AlarmFireReceiver para notificaciones de alertas por hora. */
private const val TOMORROW_PUBLISHED_NOTIFICATION_ID = 1000

/**
 * Aviso opcional (Ajustes → "Avisarme cuando se publiquen los precios de mañana"): si está
 * activado, los precios de mañana ya están disponibles (misma comprobación que la pestaña Mañana:
 * ver [com.opentarifa.app.ui.pvpc.TomorrowViewModel]) y todavía no se ha avisado hoy de esta
 * publicación, dispara una notificación puntual que abre la app en Mañana.
 */
suspend fun checkAndNotifyTomorrowPricesPublished(
    context: Context,
    notificationPreferencesRepository: NotificationPreferencesRepository,
    pvpcRepository: PvpcRepository,
    today: LocalDate
) {
    if (!notificationPreferencesRepository.notifyTomorrowPublished.first()) return
    if (notificationPreferencesRepository.lastTomorrowPublishedNotifiedDate() == today.toString()) return
    if (LocalTime.now(MadridZone) < PublicationTime) return

    val tomorrowPrices = pvpcRepository.getTomorrowPrices()
    if (tomorrowPrices.isEmpty()) return

    showNotification(context)
    notificationPreferencesRepository.setLastTomorrowPublishedNotifiedDate(today.toString())
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
        .setSmallIcon(context.applicationInfo.icon)
        .setContentTitle("Precios de mañana disponibles")
        .setContentText("Los precios de mañana ya están disponibles")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

    NotificationManagerCompat.from(context).notify(TOMORROW_PUBLISHED_NOTIFICATION_ID, notification)
}
