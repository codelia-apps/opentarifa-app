package com.opentarifa.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PriceHistoryEntity::class, AlertEntity::class], version = 4, exportSchema = false)
abstract class OpenTarifaDatabase : RoomDatabase() {
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var instance: OpenTarifaDatabase? = null

        fun getInstance(context: Context): OpenTarifaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenTarifaDatabase::class.java,
                    "opentarifa.db"
                )
                    // v1->v2 añade la tabla "alerts"; v2->v3 le añade scope/date
                    // (alertas Tipo A); v3->v4 le añade name (alertas Tipo B).
                    // price_history es solo caché (se resincroniza sola) y
                    // todavía no hay usuarios con alertas guardadas, así que
                    // no hace falta una Migration real.
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
    }
}
