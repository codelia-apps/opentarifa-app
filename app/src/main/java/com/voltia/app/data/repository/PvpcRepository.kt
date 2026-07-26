package com.voltia.app.data.repository

import com.voltia.app.data.local.PriceHistoryDao
import com.voltia.app.data.local.PriceHistoryEntity
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.data.remote.ReeApiService
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

    /** Precio PVPC de la serie principal, ver /docs/pvpc-api.md */
    private val pvpcSeriesId = "1001"

    /** Días de histórico a conservar; solo hacen falta 30 días para la futura pantalla de Evolución. */
    private val historyRetentionDays = 35L

    suspend fun getTodayPrices(): List<HourlyPrice> {
        val today = LocalDate.now(zoneId)
        val startDate = today.atStartOfDay(zoneId).format(requestDateFormatter)
        val endDate = today.atTime(LocalTime.of(23, 59)).atZone(zoneId).format(requestDateFormatter)

        val response = api.getPreciosMercado(startDate = startDate, endDate = endDate)

        val pvpcSeries = response.included.firstOrNull { it.id == pvpcSeriesId }
            ?: return emptyList()

        val hourlyEntries = pvpcSeries.attributes.values.map { value ->
            val startHour = OffsetDateTime.parse(value.datetime).toLocalTime().hour
            startHour to value.value / 1000.0
        }

        saveToHistory(today, hourlyEntries)

        return hourlyEntries.map { (startHour, priceEurPerKwh) ->
            val endHour = (startHour + 1) % 24
            HourlyPrice(
                hour = "${startHour}h - ${endHour}h",
                priceEurPerKwh = priceEurPerKwh
            )
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
