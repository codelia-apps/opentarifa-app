package com.voltia.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PriceHistoryEntity::class], version = 1, exportSchema = false)
abstract class VoltiaDatabase : RoomDatabase() {
    abstract fun priceHistoryDao(): PriceHistoryDao

    companion object {
        @Volatile
        private var instance: VoltiaDatabase? = null

        fun getInstance(context: Context): VoltiaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoltiaDatabase::class.java,
                    "voltia.db"
                ).build().also { instance = it }
            }
    }
}
