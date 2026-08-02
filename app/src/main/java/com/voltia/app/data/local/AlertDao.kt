package com.voltia.app.data.local

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

    @Query("SELECT * FROM alerts ORDER BY createdAt")
    fun observeAll(): Flow<List<AlertEntity>>
}
