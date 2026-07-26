package com.voltia.app.data.local

import androidx.room.Entity

/**
 * Histórico de precios PVPC guardado localmente. La clave primaria compuesta
 * (date, hourStart) evita duplicados: al volver a sincronizar el mismo día y
 * hora, [PriceHistoryDao.upsertAll] sobreescribe la fila en vez de insertar
 * una nueva.
 */
@Entity(tableName = "price_history", primaryKeys = ["date", "hourStart"])
data class PriceHistoryEntity(
    /** Fecha en formato ISO-8601 (yyyy-MM-dd), hora local Europe/Madrid. */
    val date: String,
    /** Hora de inicio del rango horario (0-23). */
    val hourStart: Int,
    val priceEurPerKwh: Double
)
