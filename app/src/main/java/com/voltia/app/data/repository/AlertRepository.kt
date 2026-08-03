package com.voltia.app.data.repository

import com.voltia.app.data.local.AlertChannel
import com.voltia.app.data.local.AlertDao
import com.voltia.app.data.local.AlertEntity
import com.voltia.app.data.local.AlertScope
import com.voltia.app.data.local.AlertType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class AlertRepository(private val alertDao: AlertDao) {

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

    suspend fun deleteAlert(alert: AlertEntity) = alertDao.delete(alert)

    /** Marca la alerta como completada tras dispararse; no debe repetirse (Tipo A = un solo uso). */
    suspend fun markCompleted(id: Long) {
        alertDao.getById(id)?.let { alertDao.update(it.copy(isEnabled = false)) }
    }
}
