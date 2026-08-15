package com.voltia.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.ui.pvpc.ErrorContent
import com.voltia.app.ui.pvpc.LoadingContent
import com.voltia.app.ui.pvpc.MadridZone
import com.voltia.app.ui.pvpc.TypeLabelM
import java.time.LocalTime
import java.util.Locale

@Composable
fun ApplianceCalculatorScreen(modifier: Modifier = Modifier, viewModel: ApplianceCalculatorViewModel = viewModel()) {
    val uiState = viewModel.uiState
    Surface(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is ApplianceCalculatorUiState.Loading -> LoadingContent()
            is ApplianceCalculatorUiState.Error -> ErrorContent(message = uiState.message)
            is ApplianceCalculatorUiState.Success -> CalculatorContent(todayPrices = uiState.todayPrices)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalculatorContent(todayPrices: List<HourlyPrice>) {
    var selectedAppliance by remember { mutableStateOf<Appliance?>(PresetAppliances.first()) }
    var manualWattsText by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("1") }

    val watts = selectedAppliance?.watts ?: manualWattsText.replace(',', '.').toDoubleOrNull()?.toInt()
    val duration = durationText.replace(',', '.').toDoubleOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Electrodoméstico", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetAppliances.forEach { appliance ->
                FilterChip(
                    selected = selectedAppliance == appliance,
                    onClick = { selectedAppliance = appliance },
                    label = { Text("${appliance.label} (${appliance.watts} W)") }
                )
            }
            FilterChip(
                selected = selectedAppliance == null,
                onClick = { selectedAppliance = null },
                label = { Text("Potencia manual") }
            )
        }

        if (selectedAppliance == null) {
            OutlinedTextField(
                value = manualWattsText,
                onValueChange = { manualWattsText = it },
                label = { Text("Potencia (W)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(text = "Duración de uso", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it },
            label = { Text("Horas (p.ej. 1.5)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (watts != null && watts > 0 && duration != null && duration > 0) {
            val best = remember(todayPrices, watts, duration) {
                findCheapestWindow(todayPrices, duration, watts)
            }
            if (best != null) {
                val pricesByHour = remember(todayPrices) { todayPrices.associate { it.hourStart to it.priceEurPerKwh } }
                val currentHour = LocalTime.now(MadridZone).hour
                val nowCost = remember(pricesByHour, currentHour, duration, watts) {
                    windowCost(pricesByHour, currentHour, duration, watts)
                }
                ResultCard(best = best, nowCost = nowCost)
            } else {
                Text(
                    text = "No hay una franja de esa duración disponible hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "Introduce una potencia y una duración válidas para calcular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultCard(best: WindowResult, nowCost: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Mejor franja de hoy",
                    style = TypeLabelM,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatWindowLabel(best.startHour, best.durationHours),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Coste estimado: ${formatCost(best.cost)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (nowCost != null) {
            val savings = nowCost - best.cost
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Si lo pones ahora mismo: ${formatCost(nowCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (savings > MinRelevantSavingsEur) {
                            "Ahorras ${formatCost(savings)} esperando a la mejor franja."
                        } else {
                            "Ya estás en la mejor franja: usarlo ahora es tan barato como esperar."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Por debajo de este umbral, la diferencia se considera ruido de redondeo, no un ahorro real. */
private const val MinRelevantSavingsEur = 0.0001

private fun formatCost(value: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%.4f €", value)
