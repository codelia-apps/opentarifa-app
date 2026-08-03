package com.voltia.app.ui.pvpc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltia.app.data.local.NotificationPreferencesRepository
import com.voltia.app.data.local.VoltiaDatabase
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.data.repository.AlertRepository
import com.voltia.app.ui.theme.VoltiaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alertRepository = remember { AlertRepository(VoltiaDatabase.getInstance(context).alertDao()) }
    val notificationPreferencesRepository = remember { NotificationPreferencesRepository(context) }
    val today = remember { LocalDate.now(MadridZone) }

    val activeAlertsFlow = remember(alertRepository, today) { alertRepository.observeActiveFixedHourAlerts(today) }
    val activeAlerts by activeAlertsFlow.collectAsState(initial = emptyList())
    val alertByHour = remember(activeAlerts) { activeAlerts.mapNotNull { alert -> alert.hour?.let { it to alert } }.toMap() }

    var pendingAlertHour by remember { mutableStateOf<HourlyPrice?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingPrice = pendingAlertHour
        pendingAlertHour = null
        if (granted && pendingPrice != null) {
            scope.launch {
                val channel = notificationPreferencesRepository.defaultChannel.first()
                alertRepository.createFixedHourAlert(today, pendingPrice.hourStart, channel)
            }
        }
    }

    fun onToggleAlert(price: HourlyPrice) {
        val existing = alertByHour[price.hourStart]
        if (existing != null) {
            scope.launch { alertRepository.deleteAlert(existing) }
            return
        }

        val notificationsNeedPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (notificationsNeedPermission) {
            pendingAlertHour = price
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launch {
                val channel = notificationPreferencesRepository.defaultChannel.first()
                alertRepository.createFixedHourAlert(today, price.hourStart, channel)
            }
        }
    }

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
                    isCurrentHour = index == currentIndex,
                    hasActiveAlert = alertByHour.containsKey(price.hourStart),
                    onToggleAlert = { onToggleAlert(price) }
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
