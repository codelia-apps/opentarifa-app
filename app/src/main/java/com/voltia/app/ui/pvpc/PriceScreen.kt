package com.voltia.app.ui.pvpc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.ui.theme.VoltiaHighContainerDark
import com.voltia.app.ui.theme.VoltiaHighContainerLight
import com.voltia.app.ui.theme.VoltiaHighDark
import com.voltia.app.ui.theme.VoltiaHighLight
import com.voltia.app.ui.theme.VoltiaHighOnContainerDark
import com.voltia.app.ui.theme.VoltiaHighOnContainerLight
import com.voltia.app.ui.theme.VoltiaLowContainerDark
import com.voltia.app.ui.theme.VoltiaLowContainerLight
import com.voltia.app.ui.theme.VoltiaLowDark
import com.voltia.app.ui.theme.VoltiaLowLight
import com.voltia.app.ui.theme.VoltiaLowOnContainerDark
import com.voltia.app.ui.theme.VoltiaLowOnContainerLight
import com.voltia.app.ui.theme.VoltiaMidContainerDark
import com.voltia.app.ui.theme.VoltiaMidContainerLight
import com.voltia.app.ui.theme.VoltiaMidDark
import com.voltia.app.ui.theme.VoltiaMidLight
import com.voltia.app.ui.theme.VoltiaMidOnContainerDark
import com.voltia.app.ui.theme.VoltiaMidOnContainerLight
import com.voltia.app.ui.theme.VoltiaTheme
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MadridZone = ZoneId.of("Europe/Madrid")

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

/** Subconjunto de tokens de tipografía del sistema de diseño (tokens/typography.css) usado en esta pantalla. */
private val TypeBodyM = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
private val TypeLabelS = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
private val TypeLabelM = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
private val TypeLabelL = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
private val TypeRowPrice = TextStyle(fontSize = 18.sp, lineHeight = 24.sp)
private val TypeHeaderPrice = TextStyle(fontWeight = FontWeight.Medium, fontSize = 31.sp, lineHeight = 34.sp, letterSpacing = (-0.31).sp)

private enum class PriceCategory { LOW, MID, HIGH, NEUTRAL }
private enum class PriceExtreme { GOOD, BEST, BAD, WORST }

private data class CategoryPalette(val base: Color, val container: Color, val onContainer: Color)

@Composable
fun PriceScreen(modifier: Modifier = Modifier, viewModel: PvpcViewModel = viewModel()) {
    val uiState = viewModel.uiState
    Surface(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is PvpcUiState.Loading -> LoadingContent()
            is PvpcUiState.Error -> ErrorContent(message = uiState.message)
            is PvpcUiState.Success -> PriceList(prices = uiState.prices)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String) {
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
private fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Paleta base/container/onContainer de la categoría, importada 1:1 de
 * tokens/colors.css del sistema de diseño de Voltia (--price-{cat},
 * --price-{cat}-container, --price-{cat}-on-container). NEUTRAL (rango del
 * día = 0) no existe en el sistema de diseño; reutiliza los neutros de
 * MaterialTheme en su lugar.
 */
@Composable
private fun categoryPalette(category: PriceCategory): CategoryPalette {
    val isDark = isAppInDarkTheme()
    return when (category) {
        PriceCategory.LOW -> if (isDark) {
            CategoryPalette(VoltiaLowDark, VoltiaLowContainerDark, VoltiaLowOnContainerDark)
        } else {
            CategoryPalette(VoltiaLowLight, VoltiaLowContainerLight, VoltiaLowOnContainerLight)
        }
        PriceCategory.MID -> if (isDark) {
            CategoryPalette(VoltiaMidDark, VoltiaMidContainerDark, VoltiaMidOnContainerDark)
        } else {
            CategoryPalette(VoltiaMidLight, VoltiaMidContainerLight, VoltiaMidOnContainerLight)
        }
        PriceCategory.HIGH -> if (isDark) {
            CategoryPalette(VoltiaHighDark, VoltiaHighContainerDark, VoltiaHighOnContainerDark)
        } else {
            CategoryPalette(VoltiaHighLight, VoltiaHighContainerLight, VoltiaHighOnContainerLight)
        }
        PriceCategory.NEUTRAL -> CategoryPalette(
            base = MaterialTheme.colorScheme.onSurfaceVariant,
            container = MaterialTheme.colorScheme.surfaceVariant,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PriceList(prices: List<HourlyPrice>) {
    val priceValues = prices.map { it.priceEurPerKwh }
    val minPrice = priceValues.minOrNull() ?: 0.0
    val maxPrice = priceValues.maxOrNull() ?: 0.0
    val categories = priceValues.map { priceCategory(it, minPrice, maxPrice) }
    val extremes = findExtremes(priceValues, minPrice, maxPrice)
    val deltas = computeDeltas(priceValues)

    val currentHour = LocalTime.now(MadridZone).hour
    val currentIndex = prices.indexOfFirst { it.hourStart == currentHour }

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentIndex >= 0) {
            CurrentPriceHeader(price = prices[currentIndex], category = categories[currentIndex])
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(prices) { index, price ->
                HourPriceRow(
                    price = price,
                    category = categories[index],
                    extreme = extremes[index],
                    delta = deltas[index],
                    isCurrentHour = index == currentIndex
                )
            }
        }
    }
}

/**
 * Categoría de precio según el percentil del precio dentro del rango real
 * (mínimo-máximo) del día. Si todos los precios del día son iguales (rango
 * = 0) se usa [PriceCategory.NEUTRAL] para evitar dividir por cero y para no
 * sugerir un nivel de precio ("medio") que no tendría sentido ese día.
 */
private fun priceCategory(price: Double, minPrice: Double, maxPrice: Double): PriceCategory {
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
private fun findExtremes(
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
private fun computeDeltas(prices: List<Double>): List<Double?> =
    prices.mapIndexed { index, price -> if (index == 0) null else price - prices[index - 1] }

private fun formatPriceValue(price: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%.5f", price)

private fun formatPrice(price: Double): String =
    "${formatPriceValue(price)} €/kWh"

private fun formatDelta(delta: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%+.3f", delta)

private val HeaderDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES"))

private fun formatHeaderDateTime(dateTime: LocalDateTime, hourLabel: String): String {
    val datePart = dateTime.format(HeaderDateFormatter).replaceFirstChar { it.uppercase() }
    return "$datePart · $hourLabel"
}

private fun categoryLabel(category: PriceCategory): String = when (category) {
    PriceCategory.LOW -> "Precio bajo"
    PriceCategory.MID -> "Precio medio"
    PriceCategory.HIGH -> "Precio alto"
    PriceCategory.NEUTRAL -> "Precio estable"
}

/** Icono de nivel: mismo criterio que el icono de extreme (doble flecha abajo/arriba), "remove" para medio/neutro. */
private fun categoryIcon(category: PriceCategory): ImageVector = when (category) {
    PriceCategory.LOW -> Icons.Filled.KeyboardDoubleArrowDown
    PriceCategory.HIGH -> Icons.Filled.KeyboardDoubleArrowUp
    PriceCategory.MID, PriceCategory.NEUTRAL -> Icons.Filled.Remove
}

private fun priceFontWeight(extreme: PriceExtreme?): FontWeight = when (extreme) {
    null -> FontWeight.Medium
    PriceExtreme.GOOD, PriceExtreme.BAD -> FontWeight.SemiBold
    PriceExtreme.BEST, PriceExtreme.WORST -> FontWeight.ExtraBold
}

/**
 * best/worst usan el color "on-container" (máximo contraste); good/bad se
 * quedan a medio camino entre el color base y "on-container" (mezcla 45/55,
 * igual que el color-mix del sistema de diseño); sin extreme, el color base.
 */
private fun priceColor(palette: CategoryPalette, extreme: PriceExtreme?): Color = when (extreme) {
    PriceExtreme.BEST, PriceExtreme.WORST -> palette.onContainer
    PriceExtreme.GOOD, PriceExtreme.BAD -> lerp(palette.base, palette.onContainer, 0.55f)
    null -> palette.base
}

/** Mismo icono para good/best (lado barato) y su espejo para bad/worst (lado caro); el color no cambia entre niveles, solo tamaño y opacidad. */
private fun extremeIcon(extreme: PriceExtreme): ImageVector = when (extreme) {
    PriceExtreme.GOOD, PriceExtreme.BEST -> Icons.Filled.KeyboardDoubleArrowDown
    PriceExtreme.BAD, PriceExtreme.WORST -> Icons.Filled.KeyboardDoubleArrowUp
}

private fun extremeIconSize(extreme: PriceExtreme): Dp = when (extreme) {
    PriceExtreme.GOOD, PriceExtreme.BAD -> 14.dp
    PriceExtreme.BEST, PriceExtreme.WORST -> 18.dp
}

private fun extremeAlpha(extreme: PriceExtreme): Float = when (extreme) {
    PriceExtreme.GOOD, PriceExtreme.BAD -> 0.5f
    PriceExtreme.BEST, PriceExtreme.WORST -> 1f
}

/** Cabecera "precio grande + color de categoría" (variante 1b de header-options.html). */
@Composable
private fun CurrentPriceHeader(price: HourlyPrice, category: PriceCategory) {
    val palette = categoryPalette(category)
    val now = LocalDateTime.now(MadridZone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = palette.container,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatHeaderDateTime(now, price.hour),
                style = TypeBodyM,
                color = palette.onContainer.copy(alpha = 0.8f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = categoryIcon(category),
                    contentDescription = null,
                    tint = palette.base,
                    modifier = Modifier.size(16.dp)
                )
                Text(text = categoryLabel(category), style = TypeLabelM, color = palette.onContainer)
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatPriceValue(price.priceEurPerKwh),
                style = TypeHeaderPrice,
                color = palette.base
            )
            Text(
                text = "€/kWh",
                style = TypeLabelL,
                color = palette.onContainer.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun HourPriceRow(
    price: HourlyPrice,
    category: PriceCategory,
    extreme: PriceExtreme?,
    delta: Double?,
    isCurrentHour: Boolean
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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PriceListPreview() {
    VoltiaTheme {
        PriceList(
            prices = listOf(
                HourlyPrice("00-01h", 0, 0.18318),
                HourlyPrice("01-02h", 1, 0.18153),
                HourlyPrice("09-10h", 9, 0.04377)
            )
        )
    }
}
