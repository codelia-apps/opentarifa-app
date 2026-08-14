package com.voltia.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PriceHistoryDao {
    /** Inserta o, si ya existe (date, hourStart), sobreescribe con el precio actual. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PriceHistoryEntity>)

    @Query("SELECT * FROM price_history WHERE date = :date ORDER BY hourStart")
    suspend fun getForDate(date: String): List<PriceHistoryEntity>

    /** Histórico desde [fromDate] (inclusive) hasta hoy, para el gráfico de evolución de Resumen. */
    @Query("SELECT * FROM price_history WHERE date >= :fromDate ORDER BY date, hourStart")
    suspend fun getSince(fromDate: String): List<PriceHistoryEntity>

    /** Purga el histórico antiguo. [cutoffDate] en formato ISO-8601 (yyyy-MM-dd): se borra todo lo anterior. */
    @Query("DELETE FROM price_history WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
