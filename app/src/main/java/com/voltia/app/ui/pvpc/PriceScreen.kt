package com.voltia.app.ui.pvpc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.voltia.app.calendar.CalendarEventWriter
import com.voltia.app.data.local.AlertChannel
import com.voltia.app.data.local.AlertEntity
import com.voltia.app.data.local.NotificationPreferencesRepository
import com.voltia.app.data.local.VoltiaDatabase
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.data.repository.AlertRepository
import com.voltia.app.notifications.AlarmScheduler
import com.voltia.app.ui.theme.VoltiaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Filas de hora anterior que se dejan visibles por encima de la hora actual al hacer scroll automático. */
private const val RowsOfContextAboveCurrentHour = 2

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

    var pendingAlert by remember { mutableStateOf<Pair<HourlyPrice, PriceCategory>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val (pendingPrice, pendingCategory) = pendingAlert ?: (null to null)
        pendingAlert = null
        if (pendingPrice != null && pendingCategory != null && results.values.all { it }) {
            scope.launch {
                val channel = notificationPreferencesRepository.defaultChannel.first()
                activateAlert(context, alertRepository, today, pendingPrice, pendingCategory, channel)
            }
        }
    }

    fun onToggleAlert(price: HourlyPrice, category: PriceCategory) {
        val existing = alertByHour[price.hourStart]
        if (existing != null) {
            scope.launch {
                AlarmScheduler.cancel(context, existing.id)
                alertRepository.deleteAlert(existing)
            }
            return
        }

        scope.launch {
            val channel = notificationPreferencesRepository.defaultChannel.first()
            val missing = requiredPermissions(channel).filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                activateAlert(context, alertRepository, today, price, category, channel)
            } else {
                pendingAlert = price to category
                permissionLauncher.launch(missing.toTypedArray())
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

    val listState = rememberLazyListState()
    LaunchedEffect(prices) {
        if (currentIndex >= 0) {
            // Dos filas de contexto por encima en vez de pegarla al borde superior exacto,
            // para que se note que hay horas anteriores justo encima sin hacer scroll.
            val targetIndex = (currentIndex - RowsOfContextAboveCurrentHour).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Mientras la fila resaltada de la hora actual esté en pantalla, el precio grande de la
    // cabecera solo duplicaría lo que ya se ve ahí abajo — se oculta y vuelve a aparecer en
    // cuanto se pierde de vista al hacer scroll (en cualquier dirección).
    val isCurrentHourRowVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentIndex >= 0) {
            CurrentPriceHeader(
                price = prices[currentIndex],
                category = categories[currentIndex],
                showPrice = !isCurrentHourRowVisible
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(prices) { index, price ->
                // Una hora ya pasada no puede recibir una alerta Tipo A (nunca llegaría a
                // dispararse), así que la campana se deshabilita — pero la columna se sigue
                // reservando (showAlertColumn=true siempre en Hoy) para no desalinear el resto
                // de la fila respecto a las horas que sí la tienen activa.
                val isPastHour = price.hourStart < currentHour
                HourPriceRow(
                    price = price,
                    category = categories[index],
                    extreme = extremes[index],
                    delta = deltas[index],
                    isCurrentHour = index == currentIndex,
                    hasActiveAlert = alertByHour.containsKey(price.hourStart),
                    showAlertColumn = true,
                    onToggleAlert = if (isPastHour) {
                        null
                    } else {
                        { onToggleAlert(price, categories[index]) }
                    }
                )
            }
        }
    }
}

/** Permisos runtime que hacen falta para que una alerta con este canal funcione de verdad. */
private fun requiredPermissions(channel: AlertChannel): List<String> = buildList {
    if (channel == AlertChannel.SYSTEM_NOTIFICATION || channel == AlertChannel.BOTH) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (channel == AlertChannel.CALENDAR_EVENT || channel == AlertChannel.BOTH) {
        add(Manifest.permission.WRITE_CALENDAR)
        add(Manifest.permission.READ_CALENDAR)
    }
}

/**
 * Crea la alerta Tipo A, programa su disparo con [AlarmScheduler] y, si el
 * canal lo pide, crea también el evento de calendario. Si falta el permiso
 * de alarma exacta (Android 12+, sin diálogo propio) lleva al usuario a
 * Ajustes y no crea nada todavía — tendrá que volver a tocar la campana tras
 * concederlo.
 */
private suspend fun activateAlert(
    context: Context,
    alertRepository: AlertRepository,
    date: LocalDate,
    price: HourlyPrice,
    category: PriceCategory,
    channel: AlertChannel
) {
    if (!AlarmScheduler.canScheduleExactAlarms(context)) {
        AlarmScheduler.requestExactAlarmPermission(context)
        return
    }

    val alert: AlertEntity = alertRepository.createFixedHourAlert(date, price.hourStart, channel)
    AlarmScheduler.schedule(context, alert.id, date, price.hourStart, price.priceEurPerKwh, category, channel)

    if (channel == AlertChannel.CALENDAR_EVENT || channel == AlertChannel.BOTH) {
        val eventCreated = withContext(Dispatchers.IO) {
            CalendarEventWriter.createFixedHourEvent(context, date, price.hourStart, price.hour, price.priceEurPerKwh)
        }
        if (!eventCreated) {
            Toast.makeText(
                context,
                "No se pudo crear el evento: no hay calendario configurado en el dispositivo",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun formatHeaderDateTime(dateTime: LocalDateTime, hourLabel: String): String =
    "${formatFullDate(dateTime.toLocalDate())} · $hourLabel"

/**
 * Cabecera "precio grande + color de categoría" (variante 1b de header-options.html). Cuando
 * [showPrice] es falso (la fila resaltada de esa misma hora ya está visible en la lista, justo
 * debajo) se oculta el precio grande para no duplicar la información — solo queda fecha/hora y
 * categoría. La transición entre ambos estados se anima en vez de ser un corte brusco.
 */
@Composable
private fun CurrentPriceHeader(price: HourlyPrice, category: PriceCategory, showPrice: Boolean) {
    val palette = categoryPalette(category)
    val now = LocalDateTime.now(MadridZone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = palette.container,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp)
            .animateContentSize(),
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
        AnimatedVisibility(
            visible = showPrice,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
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
