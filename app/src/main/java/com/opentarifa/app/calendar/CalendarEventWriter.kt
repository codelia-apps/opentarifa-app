package com.opentarifa.app.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.opentarifa.app.data.local.AlertType
import com.opentarifa.app.data.local.alertTypeTitle
import com.opentarifa.app.ui.pvpc.PriceCategory
import com.opentarifa.app.ui.pvpc.categoryLabel
import com.opentarifa.app.ui.pvpc.formatPrice
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Duración del evento creado para una alerta de hora fija. */
private const val EVENT_DURATION_MINUTES = 30L

/** Minutos antes del evento en los que se dispara el recordatorio. */
private const val REMINDER_MINUTES_BEFORE = 10

/**
 * Crea eventos en el calendario nativo del dispositivo para alertas de
 * precio. Requiere WRITE_CALENDAR/READ_CALENDAR ya concedidos: quien llama
 * es responsable de pedirlos antes (ver PriceScreen).
 */
object CalendarEventWriter {
    private val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

    /**
     * true si se pudo crear el evento; false si no hay calendario disponible o falta el permiso.
     * [alertType]/[name] solo se pasan para alertas Tipo B (CHEAPEST_TODAY/PRICIEST_TODAY): el
     * TITLE debe indicar el tipo. Tipo A (FIXED_HOUR) no pasa ninguno de los dos y conserva el
     * título genérico actual.
     */
    internal fun createFixedHourEvent(
        context: Context,
        date: LocalDate,
        hour: Int,
        hourLabel: String,
        priceEurPerKwh: Double,
        category: PriceCategory,
        alertType: AlertType? = null,
        name: String? = null
    ): Boolean {
        val calendarId = findWritableCalendarId(context) ?: return false
        val start = ZonedDateTime.of(date, LocalTime.of(hour, 0), MadridZone)
        val end = start.plusMinutes(EVENT_DURATION_MINUTES)

        val title = if (alertType == AlertType.CHEAPEST_TODAY || alertType == AlertType.PRICIEST_TODAY) {
            "OpenTarifa: ${alertTypeTitle(alertType.name, name)} ($hourLabel)"
        } else {
            "OpenTarifa: Precio de luz $hourLabel"
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(
                CalendarContract.Events.DESCRIPTION,
                "${formatPrice(priceEurPerKwh)} — ${categoryLabel(category)}. Creado por OpenTarifa."
            )
            put(CalendarContract.Events.DTSTART, start.toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, end.toInstant().toEpochMilli())
            // Obligatorio: sin esto algunos proveedores de calendario rechazan el insert o asumen
            // la zona horaria del dispositivo, desplazando el evento si difiere de Madrid.
            put(CalendarContract.Events.EVENT_TIMEZONE, MadridZone.id)
            // El evento es solo informativo (recordatorio de precio), no debe bloquear el hueco
            // como si fuera una reunión real.
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            put(CalendarContract.Events.EVENT_COLOR, categoryEventColor(category))
        }

        return try {
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return false
            val eventId = eventUri.lastPathSegment?.toLongOrNull() ?: return true
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES_BEFORE)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Mismo verde/naranja/rojo de categoría que el resto de la app (variante "light" de
     * tokens/colors.css: aquí no hay tema claro/oscuro que resolver). NEUTRAL reutiliza el gris
     * de MID ya que CalendarContract no admite un color "sin categoría".
     */
    private fun categoryEventColor(category: PriceCategory): Int = when (category) {
        PriceCategory.LOW -> 0xFF00631B.toInt()
        PriceCategory.MID, PriceCategory.NEUTRAL -> 0xFF933800.toInt()
        PriceCategory.HIGH -> 0xFFAC001E.toInt()
    }

    /** Primer calendario donde se pueden insertar eventos (nivel de acceso >= "contribuidor"). */
    private fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        } catch (e: SecurityException) {
            null
        }
    }
}
