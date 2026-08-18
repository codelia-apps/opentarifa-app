package com.opentarifa.app.data.repository

import com.opentarifa.app.data.local.PriceHistoryDao
import com.opentarifa.app.data.local.PriceHistoryEntity
import com.opentarifa.app.data.remote.PvpcAttributesDto
import com.opentarifa.app.data.remote.PvpcIncludedDto
import com.opentarifa.app.data.remote.PvpcResponseDto
import com.opentarifa.app.data.remote.PvpcValueDto
import com.opentarifa.app.data.remote.ReeApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val MadridZone = ZoneId.of("Europe/Madrid")

/**
 * Cubre el reintento de PvpcRepository.getPricesForDate() ante la caché
 * obsoleta de REE para el día de mañana (ver commit del fix): la API de REE
 * cachea sus respuestas por URL exacta, y para una fecha futura puede quedar
 * fijada en una respuesta sin la serie PVPC (id=1001) aunque los datos ya
 * existan. Un formato de timestamp alternativo (con segundos) evita esa
 * clave de caché.
 */
class PvpcRepositoryTest {

    private val pvpcResponseWithData = PvpcResponseDto(
        included = listOf(
            PvpcIncludedDto(
                type = "Precio",
                id = "1001",
                attributes = PvpcAttributesDto(
                    title = "PVPC",
                    values = listOf(PvpcValueDto(value = 150.0, datetime = "2026-08-15T00:00:00.000+02:00"))
                )
            )
        )
    )

    private val pvpcResponseWithoutPvpcSeries = PvpcResponseDto(
        included = listOf(
            PvpcIncludedDto(
                type = "Precio",
                id = "600",
                attributes = PvpcAttributesDto(title = "Precio mercado spot", values = emptyList())
            )
        )
    )

    private class FakeReeApiService(
        private val responder: (startDate: String) -> PvpcResponseDto
    ) : ReeApiService {
        var callCount = 0
        val requestedStartDates = mutableListOf<String>()

        override suspend fun getPreciosMercado(startDate: String, endDate: String, timeTrunc: String): PvpcResponseDto {
            callCount++
            requestedStartDates.add(startDate)
            return responder(startDate)
        }
    }

    private class FakePriceHistoryDao : PriceHistoryDao {
        val saved = mutableListOf<PriceHistoryEntity>()
        override suspend fun upsertAll(entries: List<PriceHistoryEntity>) {
            saved.addAll(entries)
        }
        override suspend fun getForDate(date: String): List<PriceHistoryEntity> = saved.filter { it.date == date }
        override suspend fun getSince(fromDate: String): List<PriceHistoryEntity> = saved.filter { it.date >= fromDate }
        override suspend fun deleteOlderThan(cutoffDate: String) {
            saved.removeAll { it.date < cutoffDate }
        }
    }

    @Test
    fun `retries with seconds format when primary format lacks PVPC series for a future date`() = runBlocking {
        // El formato sin segundos (":" aparece una sola vez, en "T") simula la caché obsoleta de REE.
        val api = FakeReeApiService { startDate ->
            if (startDate.count { it == ':' } == 1) pvpcResponseWithoutPvpcSeries else pvpcResponseWithData
        }
        val repository = PvpcRepository(api, FakePriceHistoryDao())

        val prices = repository.getPricesForDate(LocalDate.now(MadridZone).plusDays(1))

        assertEquals(2, api.callCount)
        assertTrue(api.requestedStartDates[0].count { it == ':' } == 1)
        assertTrue(api.requestedStartDates[1].count { it == ':' } == 2)
        assertTrue(prices.isNotEmpty())
    }

    @Test
    fun `does not retry for today even if the response lacks the PVPC series`() = runBlocking {
        val api = FakeReeApiService { pvpcResponseWithoutPvpcSeries }
        val repository = PvpcRepository(api, FakePriceHistoryDao())

        val prices = repository.getPricesForDate(LocalDate.now(MadridZone))

        assertEquals(1, api.callCount)
        assertTrue(prices.isEmpty())
    }

    @Test
    fun `returns empty for a future date when both formats lack the PVPC series`() = runBlocking {
        val api = FakeReeApiService { pvpcResponseWithoutPvpcSeries }
        val repository = PvpcRepository(api, FakePriceHistoryDao())

        val prices = repository.getPricesForDate(LocalDate.now(MadridZone).plusDays(1))

        assertEquals(2, api.callCount)
        assertTrue(prices.isEmpty())
    }

    @Test
    fun `does not retry for a future date when the primary format already succeeds`() = runBlocking {
        val api = FakeReeApiService { pvpcResponseWithData }
        val repository = PvpcRepository(api, FakePriceHistoryDao())

        val prices = repository.getPricesForDate(LocalDate.now(MadridZone).plusDays(1))

        assertEquals(1, api.callCount)
        assertTrue(prices.isNotEmpty())
    }
}
