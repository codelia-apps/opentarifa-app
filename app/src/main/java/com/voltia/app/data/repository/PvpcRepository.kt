package com.voltia.app.data.repository

import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.data.remote.ReeApiService
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PvpcRepository(
    private val api: ReeApiService
) {
    private val zoneId = ZoneId.of("Europe/Madrid")
    private val requestDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /** Precio PVPC de la serie principal, ver /docs/pvpc-api.md */
    private val pvpcSeriesId = "1001"

    suspend fun getTodayPrices(): List<HourlyPrice> {
        val today = LocalDate.now(zoneId)
        val startDate = today.atStartOfDay(zoneId).format(requestDateFormatter)
        val endDate = today.atTime(LocalTime.of(23, 59)).atZone(zoneId).format(requestDateFormatter)

        val response = api.getPreciosMercado(startDate = startDate, endDate = endDate)

        val pvpcSeries = response.included.firstOrNull { it.id == pvpcSeriesId }
            ?: return emptyList()

        return pvpcSeries.attributes.values.map { value ->
            val hour = OffsetDateTime.parse(value.datetime).toLocalTime()
            HourlyPrice(
                hour = hour.format(DateTimeFormatter.ofPattern("HH:mm")),
                priceEurPerKwh = value.value / 1000.0
            )
        }
    }
}
