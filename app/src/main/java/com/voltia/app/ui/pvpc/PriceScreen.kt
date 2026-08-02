package com.voltia.app.ui.pvpc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.ui.theme.VoltiaTheme
import java.time.LocalDateTime
import java.time.LocalTime

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

private fun formatHeaderDateTime(dateTime: LocalDateTime, hourLabel: String): String =
    "${formatFullDate(dateTime.toLocalDate())} · $hourLabel"

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
