package com.voltia.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.voltia.app.data.local.AlertChannel
import com.voltia.app.ui.pvpc.PriceCategory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Programa/cancela el disparo de alertas Tipo A con [AlarmManager]. Se usa
 * AlarmManager (no WorkManager) porque el aviso debe llegar a la hora exacta
 * configurada, no "aproximadamente en esa ventana" — setExactAndAllowWhileIdle
 * dispara incluso en Doze.
 */
object AlarmScheduler {

    private val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

    const val EXTRA_ALERT_ID = "alert_id"
    const val EXTRA_HOUR = "hour"
    const val EXTRA_PRICE = "price"
    const val EXTRA_CATEGORY = "category"
    const val EXTRA_CHANNEL = "channel"

    /**
     * Desde Android 12 (S) programar una alarma exacta requiere este permiso
     * especial, que no tiene diálogo propio — solo se concede desde Ajustes
     * del sistema (ver [requestExactAlarmPermission]).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    internal fun schedule(
        context: Context,
        alertId: Long,
        date: LocalDate,
        hour: Int,
        priceEurPerKwh: Double,
        category: PriceCategory,
        channel: AlertChannel
    ) {
        val triggerAtMillis = ZonedDateTime.of(date, LocalTime.of(hour, 0), MadridZone)
            .toInstant()
            .toEpochMilli()
        val pendingIntent = pendingIntentFor(context, alertId, hour, priceEurPerKwh, category, channel)
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun cancel(context: Context, alertId: Long) {
        val intent = Intent(context, AlarmFireReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntentFor(
        context: Context,
        alertId: Long,
        hour: Int,
        price: Double,
        category: PriceCategory,
        channel: AlertChannel
    ): PendingIntent {
        val intent = Intent(context, AlarmFireReceiver::class.java).apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_PRICE, price)
            putExtra(EXTRA_CATEGORY, category.name)
            putExtra(EXTRA_CHANNEL, channel.name)
        }
        return PendingIntent.getBroadcast(
            context,
            alertId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
