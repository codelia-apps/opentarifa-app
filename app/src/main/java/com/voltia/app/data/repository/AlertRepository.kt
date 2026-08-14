package com.voltia.app.data.repository

import com.voltia.app.data.local.AlertChannel
import com.voltia.app.data.local.AlertDao
import com.voltia.app.data.local.AlertEntity
import com.voltia.app.data.local.AlertScope
import com.voltia.app.data.local.AlertType
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class AlertRepository(private val alertDao: AlertDao) {

    /** Todas las alertas guardadas, de cualquier tipo/canal/estado (pantalla de gestión de Ajustes). */
    fun observeAll(): Flow<List<AlertEntity>> = alertDao.observeAll()

    /** Alertas Tipo A (hora fija, puntuales, "solo para hoy") activas para [date]. */
    fun observeActiveFixedHourAlerts(date: LocalDate): Flow<List<AlertEntity>> =
        alertDao.observeActive(AlertType.FIXED_HOUR.name, AlertScope.ONCE.name, date.toString())

    suspend fun createFixedHourAlert(date: LocalDate, hour: Int, channel: AlertChannel): AlertEntity {
        val alert = AlertEntity(
            type = AlertType.FIXED_HOUR.name,
            scope = AlertScope.ONCE.name,
            date = date.toString(),
            hour = hour,
            activeDays = null,
            channel = channel.name,
            isEnabled = true,
            createdAt = Instant.now().toString()
        )
        val id = alertDao.insert(alert)
        return alert.copy(id = id)
    }

    /**
     * Alerta Tipo B (recurrente: "más barata/cara del día"), sin cálculo ni
     * disparo real todavía — solo el registro y su aparición en el listado
     * de gestión. [type] debe ser [AlertType.CHEAPEST_TODAY] o [AlertType.PRICIEST_TODAY].
     */
    suspend fun createRecurringAlert(
        type: AlertType,
        activeDays: Set<DayOfWeek>,
        channel: AlertChannel,
        name: String?
    ): AlertEntity {
        val alert = AlertEntity(
            type = type.name,
            scope = AlertScope.RECURRING.name,
            date = null,
            hour = null,
            activeDays = activeDays.joinToString(",") { it.name },
            channel = channel.name,
            name = name?.takeIf { it.isNotBlank() },
            isEnabled = true,
            createdAt = Instant.now().toString()
        )
        val id = alertDao.insert(alert)
        return alert.copy(id = id)
    }

    suspend fun deleteAlert(alert: AlertEntity) = alertDao.delete(alert)

    /** Desactiva una alerta desde la pantalla de gestión, sin borrarla. */
    suspend fun disable(alert: AlertEntity) = alertDao.update(alert.copy(isEnabled = false))

    /** Marca la alerta como completada tras dispararse; no debe repetirse (Tipo A = un solo uso). */
    suspend fun markCompleted(id: Long) {
        alertDao.getById(id)?.let { alertDao.update(it.copy(isEnabled = false)) }
    }
}
