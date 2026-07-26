package com.voltia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.ui.pvpc.PvpcUiState
import com.voltia.app.ui.pvpc.PvpcViewModel
import com.voltia.app.ui.theme.VoltiaGreen40
import com.voltia.app.ui.theme.VoltiaNeutral40
import com.voltia.app.ui.theme.VoltiaRed40
import com.voltia.app.ui.theme.VoltiaTheme
import com.voltia.app.ui.theme.VoltiaYellow40
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoltiaTheme {
                Scaffold { innerPadding ->
                    PriceScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel()
                    )
                }
            }
        }
    }
}

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

private enum class ExtremeMarker { NONE, CHEAP, EXPENSIVE }

/** Margen (proporción del rango del día) para considerar un precio "entre los extremos". */
private const val EXTREME_MARGIN_RATIO = 0.03

@Composable
private fun PriceList(prices: List<HourlyPrice>) {
    val priceValues = prices.map { it.priceEurPerKwh }
    val minPrice = priceValues.minOrNull() ?: 0.0
    val maxPrice = priceValues.maxOrNull() ?: 0.0
    val averagePrice = if (prices.isEmpty()) 0.0 else priceValues.sum() / prices.size
    val extremeMarkers = findExtremeMarkers(priceValues, minPrice, maxPrice)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Precio PVPC de hoy (€/kWh)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Media del día: ${formatPrice(averagePrice)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(prices) { index, price ->
                PriceRow(
                    price = price,
                    color = priceIndicatorColor(price.priceEurPerKwh, minPrice, maxPrice),
                    marker = extremeMarkers[index]
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * Verde/amarillo/rojo según el percentil del precio dentro del rango real
 * (mínimo-máximo) del día. Si todos los precios del día son iguales (rango
 * = 0) se usa un único color neutro para evitar dividir por cero.
 */
private fun priceIndicatorColor(price: Double, minPrice: Double, maxPrice: Double): Color {
    val range = maxPrice - minPrice
    if (range <= 0.0) return VoltiaNeutral40

    return when {
        price <= minPrice + range / 3 -> VoltiaGreen40
        price <= minPrice + range * 2 / 3 -> VoltiaYellow40
        else -> VoltiaRed40
    }
}

/**
 * Marca como "entre las más baratas"/"entre las más caras" cualquier hora
 * cuyo precio esté dentro de un margen pequeño ([EXTREME_MARGIN_RATIO] del
 * rango del día) respecto al mínimo o al máximo absoluto, sin exigir que
 * sean horas consecutivas: pueden quedar dispersas en cualquier punto del
 * día. Si el rango es 0 no se marca nada.
 */
private fun findExtremeMarkers(
    prices: List<Double>,
    minPrice: Double,
    maxPrice: Double
): List<ExtremeMarker> {
    val range = maxPrice - minPrice
    if (range <= 0.0) return List(prices.size) { ExtremeMarker.NONE }

    val margin = range * EXTREME_MARGIN_RATIO
    return prices.map { price ->
        when {
            price <= minPrice + margin -> ExtremeMarker.CHEAP
            price >= maxPrice - margin -> ExtremeMarker.EXPENSIVE
            else -> ExtremeMarker.NONE
        }
    }
}

private fun formatPrice(price: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%.5f €/kWh", price)

@Composable
private fun PriceRow(price: HourlyPrice, color: Color, marker: ExtremeMarker) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color = color, shape = CircleShape)
            )
            Text(
                text = price.hour,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
            when (marker) {
                ExtremeMarker.CHEAP -> Text(
                    text = "▼",
                    color = VoltiaGreen40,
                    modifier = Modifier.padding(start = 8.dp)
                )
                ExtremeMarker.EXPENSIVE -> Text(
                    text = "▲",
                    color = VoltiaRed40,
                    modifier = Modifier.padding(start = 8.dp)
                )
                ExtremeMarker.NONE -> Unit
            }
        }
        Text(
            text = formatPrice(price.priceEurPerKwh),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PriceListPreview() {
    VoltiaTheme {
        PriceList(
            prices = listOf(
                HourlyPrice("0h - 1h", 0.18318),
                HourlyPrice("1h - 2h", 0.18153),
                HourlyPrice("9h - 10h", 0.04377)
            )
        )
    }
}
