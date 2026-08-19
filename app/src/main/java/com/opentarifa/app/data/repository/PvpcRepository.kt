package com.opentarifa.app.data.repository

import com.opentarifa.app.data.local.PriceHistoryDao
import com.opentarifa.app.data.local.PriceHistoryEntity
import com.opentarifa.app.data.model.HourlyPrice
import com.opentarifa.app.data.remote.ReeApiService
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PvpcRepository(
    private val api: ReeApiService,
    private val priceHistoryDao: PriceHistoryDao
) {
    private val zoneId = ZoneId.of("Europe/Madrid")
    private val requestDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /**
     * Mismo formato, con segundos. La API de REE cachea sus respuestas por URL exacta; para el
     * día de mañana, si la caché quedó fijada con una respuesta anterior a la publicación del
     * PVPC, la app se queda "atascada" viendo esa respuesta vieja aunque los datos ya existan
     * (confirmado manualmente: la misma consulta con este formato alternativo evita esa clave de
     * caché y devuelve datos frescos). Se usa solo como reintento puntual, ver [getPricesForDate].
     */
    private val requestDateFormatterWithSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** Precio PVPC de la serie principal, ver /docs/pvpc-api.md */
    private val pvpcSeriesId = "1001"

    /** Días de histórico a conservar; solo hacen falta 30 días para la futura pantalla de Evolución. */
    private val historyRetentionDays = 35L

    suspend fun getTodayPrices(): List<HourlyPrice> = getPricesForDate(LocalDate.now(zoneId))

    /**
     * Precio de la hora 23h-0h del día anterior a [date], leído del histórico ya guardado en
     * Room. Sirve para calcular la tendencia de la fila 00h-01h, que de otro modo no tendría con
     * qué compararse dentro del propio día. Null si no hay histórico guardado para ayer (p.ej.
     * primer día de uso de la app).
     */
    suspend fun getPreviousDayLastHourPrice(date: LocalDate): Double? =
        priceHistoryDao.getForDate(date.minusDays(1).toString())
            .firstOrNull { it.hourStart == 23 }
            ?.priceEurPerKwh

    /** Precios del día siguiente; REE los publica sobre las 20:30h, antes de eso devuelve lista vacía. */
    suspend fun getTomorrowPrices(): List<HourlyPrice> = getPricesForDate(LocalDate.now(zoneId).plusDays(1))

    suspend fun getPricesForDate(date: LocalDate): List<HourlyPrice> {
        var hourlyEntries = fetchHourlyEntries(date, requestDateFormatter)

        // Solo para fechas futuras ("mañana"): un único reintento con formato alternativo, para
        // no quedarse con una respuesta de caché de REE anterior a la publicación. Hoy/histórico
        // casi siempre tienen datos a la primera, así que no vale la pena duplicar tráfico ahí.
        if (hourlyEntries.isEmpty() && date.isAfter(LocalDate.now(zoneId))) {
            hourlyEntries = fetchHourlyEntries(date, requestDateFormatterWithSeconds)
        }

        if (hourlyEntries.isEmpty()) return emptyList()

        saveToHistory(date, hourlyEntries)

        return hourlyEntries.map { (startHour, priceEurPerKwh) ->
            val endHour = (startHour + 1) % 24
            HourlyPrice(
                hour = "%02d-%02dh".format(startHour, endHour),
                hourStart = startHour,
                priceEurPerKwh = priceEurPerKwh
            )
        }
    }

    private suspend fun fetchHourlyEntries(date: LocalDate, formatter: DateTimeFormatter): List<Pair<Int, Double>> {
        val startDate = date.atStartOfDay(zoneId).format(formatter)
        val endDate = date.atTime(LocalTime.of(23, 59)).atZone(zoneId).format(formatter)

        val response = api.getPreciosMercado(startDate = startDate, endDate = endDate)

        val pvpcSeries = response.included.firstOrNull { it.id == pvpcSeriesId }
            ?: return emptyList()

        return pvpcSeries.attributes.values.map { value ->
            val startHour = OffsetDateTime.parse(value.datetime).toLocalTime().hour
            startHour to value.value / 1000.0
        }
    }

    /**
     * Guarda el histórico del día y purga lo anterior a [historyRetentionDays]
     * días. La clave primaria (date, hourStart) de [PriceHistoryEntity] hace
     * que sincronizar el mismo día varias veces no duplique filas: se
     * sobreescribe con el precio más reciente si cambiara.
     */
    private suspend fun saveToHistory(date: LocalDate, entries: List<Pair<Int, Double>>) {
        val historyEntities = entries.map { (hourStart, priceEurPerKwh) ->
            PriceHistoryEntity(
                date = date.toString(),
                hourStart = hourStart,
                priceEurPerKwh = priceEurPerKwh
            )
        }
        priceHistoryDao.upsertAll(historyEntities)

        val cutoffDate = date.minusDays(historyRetentionDays).toString()
        priceHistoryDao.deleteOlderThan(cutoffDate)
    }
}
