package com.opentarifa.app.notifications

import android.content.Context
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

/**
 * Cálculo diario de las alertas Tipo B (recurrentes: "más barata/cara del
 * día"): para cada alerta activa cuyo día de la semana de hoy esté marcado,
 * localiza la hora más barata/cara de [prices] y (re)programa su disparo con
 * el mismo [AlarmScheduler]/[AlarmFireReceiver] que Tipo A. Al usar el mismo
 * id de alerta como request code, volver a llamar esto otro día simplemente
 * actualiza la alarma existente a la nueva hora — no hace falta cancelarla
 * antes.
 *
 * Se llama desde la pantalla de Hoy en cuanto hay precios del día
 * disponibles (mismo momento en que ya se calculan mínimo/máximo para
 * colorear las filas); no hay todavía ningún mecanismo que haga esto con la
 * app cerrada.
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

        AlarmScheduler.schedule(context, alert.id, date, target.hourStart, target.priceEurPerKwh, category, channel)

        if (channel == AlertChannel.CALENDAR_EVENT || channel == AlertChannel.BOTH) {
            withContext(Dispatchers.IO) {
                CalendarEventWriter.createFixedHourEvent(context, date, target.hourStart, target.hour, target.priceEurPerKwh, category)
            }
        }
    }
}

/** null/vacío = todos los días (ver [com.opentarifa.app.data.local.AlertEntity.activeDays]). */
private fun isActiveOn(activeDays: String?, day: DayOfWeek): Boolean {
    if (activeDays.isNullOrBlank()) return true
    return activeDays.split(",").any { runCatching { DayOfWeek.valueOf(it) }.getOrNull() == day }
}
