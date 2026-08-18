package com.opentarifa.app.ui.tools

import com.opentarifa.app.data.model.HourlyPrice

/** Electrodoméstico preconfigurado con una potencia de referencia (vatios). */
data class Appliance(val label: String, val watts: Int)

val PresetAppliances = listOf(
    Appliance("Lavadora", 500),
    Appliance("Lavavajillas", 1500),
    Appliance("Secadora", 2500),
    Appliance("Horno", 2000),
    Appliance("Aire acondicionado", 1000)
)

/** Ventana consecutiva ganadora: hora de inicio (0-23), duración y coste total en €. */
data class WindowResult(val startHour: Int, val durationHours: Double, val cost: Double)

/**
 * Coste de usar un electrodoméstico de [powerWatts] durante [durationHours] horas empezando en
 * [startHour] (0-23), con los precios de [pricesByHour] (€/kWh, indexados por hora 0-23). Si la
 * ventana se sale del día (no hay precio para alguna hora que cubre), devuelve null: no hay datos
 * para esa franja hoy — así ninguna ventana puede "cruzar" a mañana.
 */
internal fun windowCost(pricesByHour: Map<Int, Double>, startHour: Int, durationHours: Double, powerWatts: Int): Double? {
    if (durationHours <= 0.0 || powerWatts <= 0) return null

    val powerKw = powerWatts / 1000.0
    var remaining = durationHours
    var hour = startHour
    var total = 0.0
    while (remaining > 0.0) {
        val price = pricesByHour[hour] ?: return null
        val hourFraction = minOf(1.0, remaining)
        total += price * powerKw * hourFraction
        remaining -= hourFraction
        hour++
    }
    return total
}

/**
 * Franja consecutiva de [durationHours] horas (empezando siempre en punto) con el coste total más
 * bajo del día, sumando el precio de cada hora (o fracción) que cubre la ventana.
 */
internal fun findCheapestWindow(prices: List<HourlyPrice>, durationHours: Double, powerWatts: Int): WindowResult? {
    val pricesByHour = prices.associate { it.hourStart to it.priceEurPerKwh }
    return (0..23).mapNotNull { start ->
        windowCost(pricesByHour, start, durationHours, powerWatts)?.let { WindowResult(start, durationHours, it) }
    }.minByOrNull { it.cost }
}

/** "14h-15h30": etiqueta de la franja que empieza en [startHour] y dura [durationHours]. */
internal fun formatWindowLabel(startHour: Int, durationHours: Double): String {
    val startLabel = "%02dh".format(startHour)
    val endTotal = startHour + durationHours
    val endHour = endTotal.toInt()
    val endMinutes = ((endTotal - endHour) * 60).toInt()
    val endLabel = if (endMinutes == 0) "%02dh".format(endHour) else "%02dh%02d".format(endHour, endMinutes)
    return "$startLabel-$endLabel"
}
