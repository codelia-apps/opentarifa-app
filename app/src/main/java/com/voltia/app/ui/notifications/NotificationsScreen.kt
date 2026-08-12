package com.voltia.app.ui.notifications

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voltia.app.data.local.AlertChannel
import com.voltia.app.data.local.AlertEntity
import com.voltia.app.data.local.AlertType
import com.voltia.app.data.local.VoltiaDatabase
import com.voltia.app.data.repository.AlertRepository
import com.voltia.app.notifications.AlarmScheduler
import kotlinx.coroutines.launch

/**
 * Gestión de alertas: lista todo lo guardado en la tabla "alerts" (Tipo A
 * de hoy y, en el futuro, Tipo B), con interruptor para desactivarlas y
 * gesto de deslizar para eliminarlas. Todavía no permite crear alertas
 * nuevas — eso llega con el Tipo B.
 */
@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alertRepository = remember { AlertRepository(VoltiaDatabase.getInstance(context).alertDao()) }
    val alertsFlow = remember(alertRepository) { alertRepository.observeAll() }
    val alerts by alertsFlow.collectAsState(initial = emptyList())

    if (alerts.isEmpty()) {
        EmptyNotificationsState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(alerts, key = { it.id }) { alert ->
            AlertRow(
                alert = alert,
                onToggleEnabled = {
                    scope.launch {
                        AlarmScheduler.cancel(context, alert.id)
                        alertRepository.disable(alert)
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
                    Text(text = alertTypeLabel(alert.type), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = alertSubtitle(alert),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Solo se puede desactivar desde aquí: reactivarla requeriría recalcular el
                // precio/categoría de esa hora, que esta pantalla no tiene disponible.
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
    AlertType.CHEAPEST_TODAY -> "Hora más barata del día"
    AlertType.PRICIEST_TODAY -> "Hora más cara del día"
    null -> type
}

private fun alertChannelLabel(channel: String): String = when (runCatching { AlertChannel.valueOf(channel) }.getOrNull()) {
    AlertChannel.SYSTEM_NOTIFICATION -> "Notificación del sistema"
    AlertChannel.CALENDAR_EVENT -> "Evento de calendario"
    AlertChannel.BOTH -> "Ambos"
    null -> channel
}

private fun alertSubtitle(alert: AlertEntity): String {
    val hourPart = alert.hour?.let { "%02d:00".format(it) }
    val statePart = if (!alert.isEnabled) "Inactiva" else null
    return listOfNotNull(hourPart, alertChannelLabel(alert.channel), statePart).joinToString(" · ")
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
            text = "Las alertas te avisan cuando el precio de la luz llega a una hora que te interesa, por notificación o evento de calendario. Actívalas desde la campana de cada hora en la pestaña Hoy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
