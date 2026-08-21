package com.opentarifa.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.opentarifa.app.calendar.CalendarEventWriter
import com.opentarifa.app.data.local.AlertChannel
import com.opentarifa.app.data.local.AlertType
import com.opentarifa.app.data.model.HourlyPrice
import com.opentarifa.app.data.repository.AlertRepository
import com.opentarifa.app.ui.pvpc.PriceCategory
import com.opentarifa.app.ui.pvpc.priceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

private const val LOG_TAG = "RecurringAlertScheduler"

/**
 * Cálculo diario de las alertas Tipo B (recurrentes: "más barata/cara del
 * día"): para cada alerta activa cuyo día de la semana de hoy esté marcado,
 * localiza la hora más barata/cara de [prices] y (re)programa su disparo con
 * el mismo [AlarmScheduler]/[AlarmFireReceiver] que Tipo A. Al usar el mismo
 * id de alerta como request code, volver a llamar esto otro día simplemente
 * actualiza la alarma existente a la nueva hora — no hace falta cancelarla
 * antes.
 *
 * Se llama tanto desde el worker diario de las 8:00 (con la app cerrada) como,
 * si hoy está entre sus días activos, justo al crear/reactivar una alerta
 * recurrente (ver [com.opentarifa.app.data.repository.AlertRepository]) — si
 * no, una alerta creada después de las 8:00 no dispararía nada hasta el
 * siguiente ciclo del worker.
 */
suspend fun scheduleTodaysRecurringAlerts(
    context: Context,
    alertRepository: AlertRepository,
    date: LocalDate,
    prices: List<HourlyPrice>
) {
    if (prices.isEmpty()) return
    // Sin diálogo propio (Android 12+): si falta, se concede desde la campana de Hoy o
    // desde Ajustes del sistema. No lo pedimos aquí porque este cálculo corre solo,
    // sin que el usuario haya tocado nada.
    if (!AlarmScheduler.canScheduleExactAlarms(context)) return

    val cheapest = prices.minByOrNull { it.priceEurPerKwh } ?: return
    val priciest = prices.maxByOrNull { it.priceEurPerKwh } ?: return
    val priceValues = prices.map { it.priceEurPerKwh }
    val minPrice = priceValues.min()
    val maxPrice = priceValues.max()
    val today = date.dayOfWeek

    val alerts = alertRepository.getActiveRecurringAlerts()
    for (alert in alerts) {
        val type = runCatching { AlertType.valueOf(alert.type) }.getOrNull()
        if (type != AlertType.CHEAPEST_TODAY && type != AlertType.PRICIEST_TODAY) continue
        if (!isActiveOn(alert.activeDays, today)) continue

        val channel = runCatching { AlertChannel.valueOf(alert.channel) }.getOrDefault(AlertChannel.SYSTEM_NOTIFICATION)
        val target = if (type == AlertType.CHEAPEST_TODAY) cheapest else priciest
        val category: PriceCategory = priceCategory(target.priceEurPerKwh, minPrice, maxPrice)

        val wantsCalendar = channel == AlertChannel.CALENDAR_EVENT || channel == AlertChannel.BOTH
        val hasCalendarPermission = hasCalendarPermission(context)

        // Si el canal pide calendario pero falta el permiso (nunca concedido porque el usuario
        // nunca creó una alerta Tipo A con canal calendario, o lo revocó después), no hay diálogo
        // posible desde un worker en background: se hace fallback a notificación de sistema para
        // no dejar la alerta completamente muda, y se deja constancia en el log.
        val effectiveChannel = if (wantsCalendar && !hasCalendarPermission) {
            Log.w(LOG_TAG, "Alerta ${alert.id}: sin permiso de calendario, fallback a notificación de sistema")
            AlertChannel.SYSTEM_NOTIFICATION
        } else {
            channel
        }

        AlarmScheduler.schedule(context, alert.id, date, target.hourStart, target.priceEurPerKwh, category, effectiveChannel)

        if (wantsCalendar && hasCalendarPermission) {
            if (alert.lastCalendarEventDate == date.toString()) {
                Log.d(LOG_TAG, "Alerta ${alert.id}: evento de calendario de $date ya creado, no se duplica")
            } else {
                val created = withContext(Dispatchers.IO) {
                    CalendarEventWriter.createFixedHourEvent(context, date, target.hourStart, target.hour, target.priceEurPerKwh, category)
                }
                if (created) {
                    alertRepository.markCalendarEventCreated(alert, date)
                    Log.d(LOG_TAG, "Alerta ${alert.id}: evento de calendario creado para $date")
                } else {
                    Log.w(LOG_TAG, "Alerta ${alert.id}: no se pudo crear el evento de calendario para $date (sin calendario disponible en el dispositivo)")
                }
            }
        }
    }
}

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** null/vacío = todos los días (ver [com.opentarifa.app.data.local.AlertEntity.activeDays]). */
private fun isActiveOn(activeDays: String?, day: DayOfWeek): Boolean {
    if (activeDays.isNullOrBlank()) return true
    return activeDays.split(",").any { runCatching { DayOfWeek.valueOf(it) }.getOrNull() == day }
}
