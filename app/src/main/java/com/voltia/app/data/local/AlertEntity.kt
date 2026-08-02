package com.voltia.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertType {
    /** Avisa siempre a la misma hora del día (ver [AlertEntity.hour]). */
    FIXED_HOUR,
    /** Avisa en la hora más barata del día. */
    CHEAPEST_TODAY,
    /** Avisa en la hora más cara del día. */
    PRICIEST_TODAY
}

/** Dónde se entrega la alerta; también es la opción de "canal predeterminado" de Ajustes. */
enum class AlertChannel {
    SYSTEM_NOTIFICATION,
    CALENDAR_EVENT,
    BOTH
}

/**
 * Alerta de precio configurada por el usuario. Fase de infraestructura: por
 * ahora solo la estructura de datos, sin pantalla ni lógica de disparo.
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [AlertType.name]; Room guarda los enums de esta app como su nombre en texto, ver [com.voltia.app.data.local.ThemePreferencesRepository]. */
    val type: String,
    /** Hora del día (0-23) para [AlertType.FIXED_HOUR]; null para el resto de tipos. */
    val hour: Int?,
    /** Nombres de [java.time.DayOfWeek] separados por coma (p.ej. "MONDAY,TUESDAY"); null = todos los días. */
    val activeDays: String?,
    /** [AlertChannel.name]. */
    val channel: String,
    val isEnabled: Boolean = true,
    /** Instant ISO-8601 de creación. */
    val createdAt: String
)
