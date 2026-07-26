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
}
