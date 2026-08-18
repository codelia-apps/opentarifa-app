package com.opentarifa.app.ui.pvpc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Notifications as FilledNotifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Notifications as OutlinedNotifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentarifa.app.data.model.HourlyPrice
import com.opentarifa.app.ui.theme.OpenTarifaHighContainerDark
import com.opentarifa.app.ui.theme.OpenTarifaHighContainerLight
import com.opentarifa.app.ui.theme.OpenTarifaHighDark
import com.opentarifa.app.ui.theme.OpenTarifaHighLight
import com.opentarifa.app.ui.theme.OpenTarifaHighOnContainerDark
import com.opentarifa.app.ui.theme.OpenTarifaHighOnContainerLight
import com.opentarifa.app.ui.theme.OpenTarifaLowContainerDark
import com.opentarifa.app.ui.theme.OpenTarifaLowContainerLight
import com.opentarifa.app.ui.theme.OpenTarifaLowDark
import com.opentarifa.app.ui.theme.OpenTarifaLowLight
import com.opentarifa.app.ui.theme.OpenTarifaLowOnContainerDark
import com.opentarifa.app.ui.theme.OpenTarifaLowOnContainerLight
import com.opentarifa.app.ui.theme.OpenTarifaMidContainerDark
import com.opentarifa.app.ui.theme.OpenTarifaMidContainerLight
import com.opentarifa.app.ui.theme.OpenTarifaMidDark
import com.opentarifa.app.ui.theme.OpenTarifaMidLight
import com.opentarifa.app.ui.theme.OpenTarifaMidOnContainerDark
import com.opentarifa.app.ui.theme.OpenTarifaMidOnContainerLight
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Piezas compartidas entre las pestañas "Hoy" y "Mañana": misma paleta,
 * cálculo de categoría/extreme/tendencia, y la fila [HourPriceRow]. Cada
 * pestaña solo aporta su propia cabecera y el estado específico de su día
 * (p.ej. "aún no publicado" en Mañana).
 */

internal val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

/** Margen (proporción del rango del día) para considerar un precio "good"/"bad" respecto al extremo absoluto. */
private const val EXTREME_MARGIN_RATIO = 0.03

/** Umbral (proporción del rango del día) por debajo del cual dos precios se consideran empatados en el extremo. */
private const val TIE_RATIO = 1e-6

/** Umbral absoluto por debajo del cual una variación respecto a la hora anterior se considera nula. */
private const val TREND_EPSILON = 1e-6

private val RowShape = RoundedCornerShape(16.dp)
private val HourColumnWidth: Dp = 54.dp
private val TrendColumnWidth: Dp = 40.dp
private val ExtremeColumnWidth: Dp = 18.dp

/** Subconjunto de tokens de tipografía del sistema de diseño (tokens/typography.css) usado en Hoy y Mañana. */
internal val TypeBodyM = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
internal val TypeLabelS = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
internal val TypeLabelM = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
internal val TypeLabelL = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
internal val TypeRowPrice = TextStyle(fontSize = 18.sp, lineHeight = 24.sp)
internal val TypeHeaderPrice = TextStyle(fontWeight = FontWeight.Medium, fontSize = 31.sp, lineHeight = 34.sp, letterSpacing = (-0.31).sp)

internal enum class PriceCategory { LOW, MID, HIGH, NEUTRAL }
internal enum class PriceExtreme { GOOD, BEST, BAD, WORST }

internal data class CategoryPalette(val base: Color, val container: Color, val onContainer: Color)

@Composable
internal fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * true si el tema actual es oscuro, a partir del color de fondo resuelto por
 * MaterialTheme (funciona tanto si el oscuro viene del sistema como del
 * switch manual de Ajustes, sin tener que pasar el flag por todos los niveles).
 */
@Composable
internal fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Paleta base/container/onContainer de la categoría, importada 1:1 de
 * tokens/colors.css del sistema de diseño de OpenTarifa (--price-{cat},
 * --price-{cat}-container, --price-{cat}-on-container). NEUTRAL (rango del
 * día = 0) no existe en el sistema de diseño; reutiliza los neutros de
 * MaterialTheme en su lugar.
 */
@Composable
internal fun categoryPalette(category: PriceCategory): CategoryPalette {
    val isDark = isAppInDarkTheme()
    return when (category) {
        PriceCategory.LOW -> if (isDark) {
            CategoryPalette(OpenTarifaLowDark, OpenTarifaLowContainerDark, OpenTarifaLowOnContainerDark)
        } else {
            CategoryPalette(OpenTarifaLowLight, OpenTarifaLowContainerLight, OpenTarifaLowOnContainerLight)
        }
        PriceCategory.MID -> if (isDark) {
            CategoryPalette(OpenTarifaMidDark, OpenTarifaMidContainerDark, OpenTarifaMidOnContainerDark)
        } else {
            CategoryPalette(OpenTarifaMidLight, OpenTarifaMidContainerLight, OpenTarifaMidOnContainerLight)
        }
        PriceCategory.HIGH -> if (isDark) {
            CategoryPalette(OpenTarifaHighDark, OpenTarifaHighContainerDark, OpenTarifaHighOnContainerDark)
        } else {
            CategoryPalette(OpenTarifaHighLight, OpenTarifaHighContainerLight, OpenTarifaHighOnContainerLight)
        }
        PriceCategory.NEUTRAL -> CategoryPalette(
            base = MaterialTheme.colorScheme.onSurfaceVariant,
            container = MaterialTheme.colorScheme.surfaceVariant,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Categoría de precio según el percentil del precio dentro del rango real
 * (mínimo-máximo) del día. Si todos los precios del día son iguales (rango
 * = 0) se usa [PriceCategory.NEUTRAL] para evitar dividir por cero y para no
 * sugerir un nivel de precio ("medio") que no tendría sentido ese día.
 */
internal fun priceCategory(price: Double, minPrice: Double, maxPrice: Double): PriceCategory {
    val range = maxPrice - minPrice
    if (range <= 0.0) return PriceCategory.NEUTRAL

    return when {
        price <= minPrice + range / 3 -> PriceCategory.LOW
        price <= minPrice + range * 2 / 3 -> PriceCategory.MID
        else -> PriceCategory.HIGH
    }
}

/**
 * Amplía la antigua marca binaria cheapest/priciest a 5 niveles. Dentro de
 * las horas en zona barata, la hora con el precio mínimo absoluto (o
 * empatadas dentro de [TIE_RATIO] del rango) se marca 'best'; el resto de
 * horas dentro de [EXTREME_MARGIN_RATIO] del rango respecto a ese mínimo se
 * marcan 'good'. Simétrico para 'worst'/'bad' en la zona cara. 'mid' nunca
 * recibe marca de extreme porque el margen (2-3%) es mucho menor que el
 * ancho de cada tercio (33%), así que nunca llega a invadirlo.
 */
internal fun findExtremes(
    prices: List<Double>,
    minPrice: Double,
    maxPrice: Double
): List<PriceExtreme?> {
    val range = maxPrice - minPrice
    if (range <= 0.0) return List(prices.size) { null }

    val margin = range * EXTREME_MARGIN_RATIO
    val tieThreshold = range * TIE_RATIO
    return prices.map { price ->
        when {
            price <= minPrice + tieThreshold -> PriceExtreme.BEST
            price <= minPrice + margin -> PriceExtreme.GOOD
            price >= maxPrice - tieThreshold -> PriceExtreme.WORST
            price >= maxPrice - margin -> PriceExtreme.BAD
            else -> null
        }
    }
}

/** Variación respecto a la hora anterior; null en la primera hora del día, que no tiene hora previa. */
internal fun computeDeltas(prices: List<Double>): List<Double?> =
    prices.mapIndexed { index, price -> if (index == 0) null else price - prices[index - 1] }

internal fun formatPriceValue(price: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%.5f", price)

internal fun formatPrice(price: Double): String =
    "${formatPriceValue(price)} €/kWh"

internal fun formatDelta(delta: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%+.3f", delta)

private val FullDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES"))

/** "Domingo, 2 de agosto" — fecha completa capitalizada, usada por las cabeceras de Hoy y Mañana. */
internal fun formatFullDate(date: LocalDate): String =
    date.format(FullDateFormatter).replaceFirstChar { it.uppercase() }

internal fun categoryLabel(category: PriceCategory): String = when (category) {
    PriceCategory.LOW -> "Precio bajo"
    PriceCategory.MID -> "Precio medio"
    PriceCategory.HIGH -> "Precio alto"
    PriceCategory.NEUTRAL -> "Precio estable"
}

/** Icono de nivel: mismo criterio que el icono de extreme (doble flecha abajo/arriba), "remove" para medio/neutro. */
internal fun categoryIcon(category: PriceCategory): ImageVector = when (category) {
    PriceCategory.LOW -> Icons.Filled.KeyboardDoubleArrowDown
    PriceCategory.HIGH -> Icons.Filled.KeyboardDoubleArrowUp
    PriceCategory.MID, PriceCategory.NEUTRAL -> Icons.Filled.Remove
}

internal fun priceFontWeight(extreme: PriceExtreme?): FontWeight = when (extreme) {
    null -> FontWeight.Medium
    PriceExtreme.GOOD, PriceExtreme.BAD -> FontWeight.SemiBold
    PriceExtreme.BEST, PriceExtreme.WORST -> FontWeight.ExtraBold
}

/**
 * best/worst usan el color "on-container" (máximo contraste); good/bad se
 * quedan a medio camino entre el color base y "on-container" (mezcla 45/55,
 * igual que el color-mix del sistema de diseño); sin extreme, el color base.
 */
internal fun priceColor(palette: CategoryPalette, extreme: PriceExtreme?): Color = when (extreme) {
    PriceExtreme.BEST, PriceExtreme.WORST -> palette.onContainer
    PriceExtreme.GOOD, PriceExtreme.BAD -> lerp(palette.base, palette.onContainer, 0.55f)
    null -> palette.base
}

/** Mismo icono para good/best (lado barato) y su espejo para bad/worst (lado caro); el color no cambia entre niveles, solo tamaño y opacidad. */
internal fun extremeIcon(extreme: PriceExtreme): ImageVector = when (extreme) {
    PriceExtreme.GOOD, PriceExtreme.BEST -> Icons.Filled.KeyboardDoubleArrowDown
    PriceExtreme.BAD, PriceExtreme.WORST -> Icons.Filled.KeyboardDoubleArrowUp
}

internal fun extremeIconSize(extreme: PriceExtreme): Dp = when (extreme) {
    PriceExtreme.GOOD, PriceExtreme.BAD -> 14.dp
    PriceExtreme.BEST, PriceExtreme.WORST -> 18.dp
}

internal fun extremeAlpha(extreme: PriceExtreme): Float = when (extreme) {
    PriceExtreme.GOOD, PriceExtreme.BAD -> 0.5f
    PriceExtreme.BEST, PriceExtreme.WORST -> 1f
}

@Composable
internal fun HourPriceRow(
    price: HourlyPrice,
    category: PriceCategory,
    extreme: PriceExtreme?,
    delta: Double?,
    isCurrentHour: Boolean,
    hasActiveAlert: Boolean = false,
    /** false = ni siquiera se reserva la columna de la campana (p.ej. en Mañana, donde las alertas Tipo A no aplican). */
    showAlertColumn: Boolean = false,
    /** null con [showAlertColumn]=true = columna reservada pero campana deshabilitada (hora ya pasada en Hoy). */
    onToggleAlert: (() -> Unit)? = null
) {
    val palette = categoryPalette(category)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RowShape,
        color = palette.container,
        shadowElevation = if (isCurrentHour) 4.dp else 1.dp,
        border = if (isCurrentHour) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = price.hour,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onContainer,
                maxLines = 1,
                modifier = Modifier.width(HourColumnWidth)
            )
            Text(
                text = formatPrice(price.priceEurPerKwh),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TypeRowPrice.copy(
                    fontWeight = priceFontWeight(extreme),
                    color = priceColor(palette, extreme)
                ),
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .widthIn(min = TrendColumnWidth),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val trendIcon = when {
                    delta == null -> Icons.Filled.Remove
                    delta > TREND_EPSILON -> Icons.Filled.ArrowUpward
                    delta < -TREND_EPSILON -> Icons.Filled.ArrowDownward
                    else -> Icons.Filled.Remove
                }
                Icon(
                    imageVector = trendIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (delta != null) {
                    Text(
                        text = formatDelta(delta),
                        style = TypeLabelS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.width(ExtremeColumnWidth),
                contentAlignment = Alignment.Center
            ) {
                if (extreme != null) {
                    Icon(
                        imageVector = extremeIcon(extreme),
                        contentDescription = null,
                        tint = palette.base.copy(alpha = extremeAlpha(extreme)),
                        modifier = Modifier.size(extremeIconSize(extreme))
                    )
                }
            }
            // Ancho fijo igual que la columna de extreme: en horas pasadas de Hoy la campana se
            // deshabilita (onToggleAlert=null) pero el hueco se mantiene, para no desalinear el
            // precio/flecha/delta respecto a las filas que sí la tienen activa.
            if (showAlertColumn) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (onToggleAlert != null) {
                        IconButton(onClick = onToggleAlert, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (hasActiveAlert) Icons.Filled.FilledNotifications else Icons.Outlined.OutlinedNotifications,
                                contentDescription = if (hasActiveAlert) "Quitar alerta de esta hora" else "Avisarme a esta hora",
                                tint = if (hasActiveAlert) palette.base else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
