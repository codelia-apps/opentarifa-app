package com.opentarifa.app.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
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

    /** true si se pudo crear el evento; false si no hay calendario disponible o falta el permiso. */
    internal fun createFixedHourEvent(
        context: Context,
        date: LocalDate,
        hour: Int,
        hourLabel: String,
        priceEurPerKwh: Double,
        category: PriceCategory
    ): Boolean {
        val calendarId = findWritableCalendarId(context) ?: return false
        val start = ZonedDateTime.of(date, LocalTime.of(hour, 0), MadridZone)
        val end = start.plusMinutes(EVENT_DURATION_MINUTES)

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "OpenTarifa: Precio de luz $hourLabel")
            put(
                CalendarContract.Events.DESCRIPTION,
                "${formatPrice(priceEurPerKwh)} — ${categoryLabel(category)}. Creado por OpenTarifa."
            )
            put(CalendarContract.Events.DTSTART, start.toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, end.toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, MadridZone.id)
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
