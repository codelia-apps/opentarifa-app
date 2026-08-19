package com.opentarifa.app.ui.notifications

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opentarifa.app.data.local.AlertChannel
import com.opentarifa.app.data.local.AlertEntity
import com.opentarifa.app.data.local.AlertScope
import com.opentarifa.app.data.local.AlertType
import com.opentarifa.app.data.local.NotificationPreferencesRepository
import com.opentarifa.app.data.local.OpenTarifaDatabase
import com.opentarifa.app.data.repository.AlertRepository
import com.opentarifa.app.notifications.AlarmScheduler
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * Gestión de alertas: lista todo lo guardado en la tabla "alerts" (Tipo A
 * puntuales creadas desde Hoy y Tipo B recurrentes creadas aquí), con
 * interruptor para desactivarlas, gesto de deslizar para eliminarlas, y un
 * botón flotante para crear alertas Tipo B nuevas. Las Tipo B creadas aquí
 * todavía no tienen cálculo diario ni disparo real — solo se guardan y se
 * listan.
 */
@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alertRepository = remember { AlertRepository(OpenTarifaDatabase.getInstance(context).alertDao()) }
    val notificationPreferencesRepository = remember { NotificationPreferencesRepository(context) }
    val alertsFlow = remember(alertRepository) { alertRepository.observeAll() }
    val allAlerts by alertsFlow.collectAsState(initial = emptyList())
    // Con canal CALENDAR_EVENT puro no hay nada que gestionar aquí: el evento ya está creado en
    // el calendario del sistema y el interruptor de esta pantalla no tiene ningún efecto sobre
    // él. El usuario las gestiona desde su propia app de Calendario.
    val alerts = remember(allAlerts) { allAlerts.filter { it.channel != AlertChannel.CALENDAR_EVENT.name } }
    val defaultChannel by notificationPreferencesRepository.defaultChannel.collectAsState(initial = AlertChannel.SYSTEM_NOTIFICATION)

    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva alerta")
            }
        }
    ) { innerPadding ->
        if (alerts.isEmpty()) {
            EmptyNotificationsState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertRow(
                        alert = alert,
                        onToggleEnabled = {
                            scope.launch {
                                AlarmScheduler.cancel(context, alert.id)
                                // Tipo A (ONCE) puntual, ya no aporta nada al desactivarse: se
                                // elimina en vez de dejarla "Inactiva" para siempre. Tipo B
                                // (RECURRING) es una regla persistente, así que sigue solo
                                // desactivándose.
                                if (alert.scope == AlertScope.ONCE.name) {
                                    alertRepository.deleteAlert(alert)
                                } else {
                                    alertRepository.disable(alert)
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                AlarmScheduler.cancel(context, alert.id)
                                alertRepository.deleteAlert(alert)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddRecurringAlertSheet(
            defaultChannel = defaultChannel,
            onDismiss = { showAddSheet = false },
            onSave = { type, days, channel, name ->
                scope.launch {
                    alertRepository.createRecurringAlert(type, days, channel, name)
                }
                showAddSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertRow(alert: AlertEntity, onToggleEnabled: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Eliminar alerta",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = alertTitle(alert), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = alertSubtitle(alert),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Tipo A se elimina al desactivarse (ver onToggleEnabled), así que esta fila
                // "Inactiva" con el switch bloqueado solo puede corresponder a una Tipo B:
                // reactivarla requeriría recalcular la hora más barata/cara del día, que esta
                // pantalla no tiene disponible.
                Switch(
                    checked = alert.isEnabled,
                    enabled = alert.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }
        }
    }
}

private fun alertTypeLabel(type: String): String = when (runCatching { AlertType.valueOf(type) }.getOrNull()) {
    AlertType.FIXED_HOUR -> "Hora fija"
    AlertType.CHEAPEST_TODAY -> "Más barata del día"
    AlertType.PRICIEST_TODAY -> "Más cara del día"
    null -> type
}

private fun alertChannelLabel(channel: String): String = when (runCatching { AlertChannel.valueOf(channel) }.getOrNull()) {
    AlertChannel.SYSTEM_NOTIFICATION -> "Notificación del sistema"
    AlertChannel.CALENDAR_EVENT -> "Evento de calendario"
    AlertChannel.BOTH -> "Ambos"
    null -> channel
}

private fun dayShortLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
}

/** "L-M-X-J-V" a partir de "MONDAY,TUESDAY,..."; null/vacío = todos los días. */
private fun formatActiveDays(activeDays: String?): String {
    if (activeDays.isNullOrBlank()) return "Todos los días"
    return activeDays.split(",")
        .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
        .sortedBy { it.value }
        .joinToString("-") { dayShortLabel(it) }
}

private fun alertTitle(alert: AlertEntity): String =
    alert.name?.takeIf { it.isNotBlank() } ?: alertTypeLabel(alert.type)

private fun alertSubtitle(alert: AlertEntity): String {
    val typePart = if (!alert.name.isNullOrBlank()) alertTypeLabel(alert.type) else null
    val hourPart = alert.hour?.let { "%02d:00".format(it) }
    val daysPart = if (alert.scope == AlertScope.RECURRING.name) formatActiveDays(alert.activeDays) else null
    val statePart = if (!alert.isEnabled) "Inactiva" else null
    return listOfNotNull(typePart, hourPart, daysPart, alertChannelLabel(alert.channel), statePart)
        .joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringAlertSheet(
    defaultChannel: AlertChannel,
    onDismiss: () -> Unit,
    onSave: (type: AlertType, days: Set<DayOfWeek>, channel: AlertChannel, name: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf(AlertType.CHEAPEST_TODAY) }
    var selectedDays by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var selectedChannel by remember { mutableStateOf(defaultChannel) }
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(text = "Nueva alerta recurrente", style = MaterialTheme.typography.titleLarge)

            Text(
                text = "Tipo",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                FilterChip(
                    selected = selectedType == AlertType.CHEAPEST_TODAY,
                    onClick = { selectedType = AlertType.CHEAPEST_TODAY },
                    label = { Text("Más barata del día") }
                )
                FilterChip(
                    selected = selectedType == AlertType.PRICIEST_TODAY,
                    onClick = { selectedType = AlertType.PRICIEST_TODAY },
                    label = { Text("Más cara del día") }
                )
            }

            Text(
                text = "Días activos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in selectedDays,
                        onClick = {
                            selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                        },
                        label = { Text(dayShortLabel(day)) }
                    )
                }
            }

            Text(
                text = "Canal",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            ChannelRadioOption(
                label = "Notificación del sistema",
                selected = selectedChannel == AlertChannel.SYSTEM_NOTIFICATION,
                onClick = { selectedChannel = AlertChannel.SYSTEM_NOTIFICATION }
            )
            ChannelRadioOption(
                label = "Evento de calendario",
                selected = selectedChannel == AlertChannel.CALENDAR_EVENT,
                onClick = { selectedChannel = AlertChannel.CALENDAR_EVENT }
            )
            ChannelRadioOption(
                label = "Ambos",
                selected = selectedChannel == AlertChannel.BOTH,
                onClick = { selectedChannel = AlertChannel.BOTH }
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre (opcional)") },
                placeholder = { Text("p.ej. Lavadora fin de semana") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )

            Button(
                onClick = { onSave(selectedType, selectedDays, selectedChannel, name) },
                enabled = selectedDays.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Text("Guardar")
            }
            if (selectedDays.isEmpty()) {
                Text(
                    text = "Selecciona al menos un día",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelRadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun EmptyNotificationsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.NotificationsNone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Sin alertas todavía",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Las alertas te avisan cuando el precio de la luz llega a una hora que te interesa, por notificación o evento de calendario. Actívalas desde la campana de cada hora en la pestaña Hoy, o crea una recurrente con el botón +.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
