package com.opentarifa.app.ui.pvpc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentarifa.app.data.model.HourlyPrice
import com.opentarifa.app.ui.theme.OpenTarifaTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryScreen(modifier: Modifier = Modifier, viewModel: SummaryViewModel = viewModel()) {
    val uiState = viewModel.uiState
    Surface(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is SummaryUiState.Loading -> LoadingContent()
            is SummaryUiState.Error -> ErrorContent(message = uiState.message)
            is SummaryUiState.Success -> SummaryContent(
                todayPrices = uiState.todayPrices,
                dailyAverages = uiState.dailyAverages
            )
        }
    }
}

@Composable
private fun SummaryContent(todayPrices: List<HourlyPrice>, dailyAverages: List<DailyAverage>) {
    if (todayPrices.isEmpty()) {
        ErrorContent(message = "No hay datos de hoy disponibles")
        return
    }

    val priceValues = todayPrices.map { it.priceEurPerKwh }
    val averagePrice = priceValues.average()
    val cheapest = todayPrices.minBy { it.priceEurPerKwh }
    val priciest = todayPrices.maxBy { it.priceEurPerKwh }
    val savings = priciest.priceEurPerKwh - cheapest.priceEurPerKwh

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SummaryHeader(averagePrice = averagePrice)
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtremeCard(
                    modifier = Modifier.weight(1f),
                    label = "Hora más barata",
                    hourLabel = cheapest.hour,
                    price = cheapest.priceEurPerKwh,
                    category = PriceCategory.LOW
                )
                ExtremeCard(
                    modifier = Modifier.weight(1f),
                    label = "Hora más cara",
                    hourLabel = priciest.hour,
                    price = priciest.priceEurPerKwh,
                    category = PriceCategory.HIGH
                )
            }
            SavingsCard(savings = savings)
            Text(
                text = "Precio de hoy hora a hora",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Las 24 horas del día actual",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TodayHourlyChart(todayPrices = todayPrices, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Evolución del precio medio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = if (dailyAverages.size == 1) "Solo hay datos de hoy todavía" else "Últimos ${dailyAverages.size} días",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WeeklyChart(dailyAverages = dailyAverages, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Cabecera de Resumen: mismo patrón que Hoy/Mañana, con el precio medio del día. */
@Composable
private fun SummaryHeader(averagePrice: Double) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = containerColor,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = formatFullDate(LocalDate.now(MadridZone)),
            style = TypeBodyM,
            color = onContainerColor.copy(alpha = 0.8f)
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatPriceValue(averagePrice),
                style = TypeHeaderPrice,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "€/kWh · media de hoy",
                style = TypeLabelL,
                color = onContainerColor.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun ExtremeCard(
    modifier: Modifier = Modifier,
    label: String,
    hourLabel: String,
    price: Double,
    category: PriceCategory
) {
    val palette = categoryPalette(category)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = palette.container
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                Text(text = label, style = TypeLabelM, color = palette.onContainer)
            }
            Text(text = hourLabel, style = MaterialTheme.typography.titleMedium, color = palette.onContainer)
            Text(
                text = formatPrice(price),
                style = TypeRowPrice,
                fontWeight = FontWeight.SemiBold,
                color = palette.base
            )
        }
    }
}

@Composable
private fun SavingsCard(savings: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = "Ahorro estimado",
                    style = TypeLabelM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ahorro potencial: ${String.format(Locale.forLanguageTag("es-ES"), "%.2f", savings)} €/kWh",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private val ChartDayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("es-ES"))

private const val ChartMinBarHeightFraction = 0.2f
private const val ChartBarAreaHeight = 100

/**
 * Mismo patrón visual que [WeeklyChart] pero con las 24 horas del día actual en vez de promedios
 * diarios. Con 24 barras no cabe una etiqueta por hora, así que solo se rotula 1 de cada 3
 * (00, 03, 06...) para mantener el estilo simple sin apelotonar texto.
 */
@Composable
private fun TodayHourlyChart(todayPrices: List<HourlyPrice>, modifier: Modifier = Modifier) {
    if (todayPrices.isEmpty()) return

    val minPrice = todayPrices.minOf { it.priceEurPerKwh }
    val maxPrice = todayPrices.maxOf { it.priceEurPerKwh }
    val range = maxPrice - minPrice

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartBarAreaHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            todayPrices.forEach { hourly ->
                val category = priceCategory(hourly.priceEurPerKwh, minPrice, maxPrice)
                val palette = categoryPalette(category)
                val heightFraction = if (range <= 0.0) {
                    1f
                } else {
                    ChartMinBarHeightFraction + (1f - ChartMinBarHeightFraction) *
                        ((hourly.priceEurPerKwh - minPrice) / range).toFloat()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction)
                            .background(color = palette.base, shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            todayPrices.forEach { hourly ->
                Text(
                    text = if (hourly.hourStart % 3 == 0) hourly.hourStart.toString() else "",
                    style = TypeLabelS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Barras de altura proporcional al precio medio de cada día, coloreadas con la misma paleta
 * bajo/medio/alto que el resto de la app. Sin ejes ni valores numéricos: solo el día de la semana
 * debajo de cada barra, para mantener el estilo "Soft" simple del resto de OpenTarifa. Las barras y las
 * etiquetas van en filas separadas (en vez de una Column por barra) para que una barra al 100% de
 * altura no empuje su etiqueta fuera del área visible.
 */
@Composable
private fun WeeklyChart(dailyAverages: List<DailyAverage>, modifier: Modifier = Modifier) {
    if (dailyAverages.isEmpty()) return

    val minAvg = dailyAverages.minOf { it.avgPrice }
    val maxAvg = dailyAverages.maxOf { it.avgPrice }
    val range = maxAvg - minAvg

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartBarAreaHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dailyAverages.forEach { day ->
                val category = priceCategory(day.avgPrice, minAvg, maxAvg)
                val palette = categoryPalette(category)
                val heightFraction = if (range <= 0.0) {
                    1f
                } else {
                    ChartMinBarHeightFraction + (1f - ChartMinBarHeightFraction) *
                        ((day.avgPrice - minAvg) / range).toFloat()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction)
                            .background(color = palette.base, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dailyAverages.forEach { day ->
                Text(
                    text = day.date.format(ChartDayLabelFormatter).replaceFirstChar { it.uppercase() }.take(3),
                    style = TypeLabelS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SummaryContentPreview() {
    OpenTarifaTheme {
        SummaryContent(
            todayPrices = listOf(
                HourlyPrice("00-01h", 0, 0.18318),
                HourlyPrice("01-02h", 1, 0.18153),
                HourlyPrice("09-10h", 9, 0.04377),
                HourlyPrice("20-21h", 20, 0.28153)
            ),
            dailyAverages = listOf(
                DailyAverage(LocalDate.now(MadridZone).minusDays(2), 0.19),
                DailyAverage(LocalDate.now(MadridZone).minusDays(1), 0.21),
                DailyAverage(LocalDate.now(MadridZone), 0.17)
            )
        )
    }
}
