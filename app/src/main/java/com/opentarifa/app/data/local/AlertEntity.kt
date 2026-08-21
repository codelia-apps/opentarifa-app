package com.opentarifa.app.data.local

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

/**
 * Etiqueta legible de [AlertType], compartida entre Gestionar notificaciones
 * (NotificationsScreen), las notificaciones de sistema (AlarmFireReceiver) y los eventos de
 * calendario (CalendarEventWriter) de alertas recurrentes, para que los tres canales usen
 * siempre el mismo texto.
 */
fun alertTypeLabel(type: String): String = when (runCatching { AlertType.valueOf(type) }.getOrNull()) {
    AlertType.FIXED_HOUR -> "Hora fija"
    AlertType.CHEAPEST_TODAY -> "Más barata del día"
    AlertType.PRICIEST_TODAY -> "Más cara del día"
    null -> type
}

/**
 * Título de una alerta recurrente (CHEAPEST_TODAY/PRICIEST_TODAY) para notificaciones y eventos
 * de calendario: la etiqueta del tipo, con el nombre personalizado añadido si existe (no lo
 * sustituye). Ver [com.opentarifa.app.ui.notifications.alertTitle] para el mismo criterio en
 * Gestionar notificaciones.
 */
fun alertTypeTitle(type: String, name: String?): String {
    val typeLabel = alertTypeLabel(type)
    return if (!name.isNullOrBlank()) "$typeLabel — $name" else typeLabel
}

/** Dónde se entrega la alerta; también es la opción de "canal predeterminado" de Ajustes. */
enum class AlertChannel {
    SYSTEM_NOTIFICATION,
    CALENDAR_EVENT,
    BOTH
}

/**
 * Alcance temporal de la alerta:
 *  - [ONCE]: puntual, para un [AlertEntity.date] concreto (Tipo A: activada desde una fila de
 *    precio de Hoy). Se completa sola tras dispararse — ver [AlertEntity.isEnabled].
 *  - [RECURRING]: se repite según [AlertEntity.activeDays] (todavía sin implementar).
 */
enum class AlertScope { ONCE, RECURRING }

/**
 * Alerta de precio configurada por el usuario.
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [AlertType.name]; Room guarda los enums de esta app como su nombre en texto, ver [com.opentarifa.app.data.local.ThemePreferencesRepository]. */
    val type: String,
    /** [AlertScope.name]. */
    val scope: String,
    /** Fecha ISO-8601 (yyyy-MM-dd) a la que aplica una alerta [AlertScope.ONCE]; null en [AlertScope.RECURRING]. */
    val date: String?,
    /** Hora del día (0-23) para [AlertType.FIXED_HOUR]; null para el resto de tipos. */
    val hour: Int?,
    /** Nombres de [java.time.DayOfWeek] separados por coma (p.ej. "MONDAY,TUESDAY"); solo para [AlertScope.RECURRING], null = todos los días. */
    val activeDays: String?,
    /** [AlertChannel.name]. */
    val channel: String,
    /** Nombre opcional del usuario (p.ej. "Lavadora fin de semana"); null = sin nombre, se usa la etiqueta del tipo. */
    val name: String? = null,
    /** false tras dispararse (Tipo A) o al cancelarla el usuario antes de tiempo. */
    val isEnabled: Boolean = true,
    /** Instant ISO-8601 de creación. */
    val createdAt: String,
    /**
     * Fecha ISO-8601 (yyyy-MM-dd) del último día para el que ya se creó un evento de calendario
     * para esta alerta; null si nunca se ha creado uno. Solo relevante para Tipo B con canal
     * CALENDAR_EVENT/BOTH: evita que el worker de las 8:00 y el de las 21:30 dupliquen el evento
     * del mismo día (ver [com.opentarifa.app.notifications.scheduleTodaysRecurringAlerts]).
     */
    val lastCalendarEventDate: String? = null
)
