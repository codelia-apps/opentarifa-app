package com.opentarifa.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Update
    suspend fun update(alert: AlertEntity)

    @Delete
    suspend fun delete(alert: AlertEntity)

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getById(id: Long): AlertEntity?

    @Query("SELECT * FROM alerts ORDER BY createdAt")
    fun observeAll(): Flow<List<AlertEntity>>

    /** Alertas activas de un [type]/[scope] concretos para una fecha (p.ej. Tipo A de hoy). */
    @Query("SELECT * FROM alerts WHERE type = :type AND scope = :scope AND date = :date AND isEnabled = 1")
    fun observeActive(type: String, scope: String, date: String): Flow<List<AlertEntity>>

    /** Alertas activas de un [scope] concreto, sin filtrar por fecha (p.ej. Tipo B recurrentes). */
    @Query("SELECT * FROM alerts WHERE scope = :scope AND isEnabled = 1")
    suspend fun getActiveByScope(scope: String): List<AlertEntity>
}
