package com.voltia.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voltia.app.data.local.AlertChannel
import com.voltia.app.data.local.NotificationPreferencesRepository
import com.voltia.app.data.local.ThemeMode
import com.voltia.app.data.local.ThemePreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onManageNotificationsClick: () -> Unit = {}) {
    val context = LocalContext.current
    val themePreferencesRepository = remember { ThemePreferencesRepository(context) }
    val notificationPreferencesRepository = remember { NotificationPreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    val selectedThemeMode by themePreferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val selectedChannel by notificationPreferencesRepository.defaultChannel.collectAsState(initial = AlertChannel.SYSTEM_NOTIFICATION)
    val notifyTomorrowPublished by notificationPreferencesRepository.notifyTomorrowPublished.collectAsState(initial = false)

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Tema", style = MaterialTheme.typography.titleMedium)

        SettingsRadioOption(
            label = "Claro",
            selected = selectedThemeMode == ThemeMode.LIGHT,
            onClick = { scope.launch { themePreferencesRepository.setThemeMode(ThemeMode.LIGHT) } }
        )
        SettingsRadioOption(
            label = "Oscuro",
            selected = selectedThemeMode == ThemeMode.DARK,
            onClick = { scope.launch { themePreferencesRepository.setThemeMode(ThemeMode.DARK) } }
        )
        SettingsRadioOption(
            label = "Automático (seguir sistema)",
            selected = selectedThemeMode == ThemeMode.SYSTEM,
            onClick = { scope.launch { themePreferencesRepository.setThemeMode(ThemeMode.SYSTEM) } }
        )

        Text(
            text = "Notificaciones",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = "Canal predeterminado",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsRadioOption(
            label = "Notificación del sistema",
            selected = selectedChannel == AlertChannel.SYSTEM_NOTIFICATION,
            onClick = { scope.launch { notificationPreferencesRepository.setDefaultChannel(AlertChannel.SYSTEM_NOTIFICATION) } }
        )
        SettingsRadioOption(
            label = "Evento de calendario",
            selected = selectedChannel == AlertChannel.CALENDAR_EVENT,
            onClick = { scope.launch { notificationPreferencesRepository.setDefaultChannel(AlertChannel.CALENDAR_EVENT) } }
        )
        SettingsRadioOption(
            label = "Ambos",
            selected = selectedChannel == AlertChannel.BOTH,
            onClick = { scope.launch { notificationPreferencesRepository.setDefaultChannel(AlertChannel.BOTH) } }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Avisarme cuando se publiquen los precios de mañana",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = notifyTomorrowPublished,
                onCheckedChange = { scope.launch { notificationPreferencesRepository.setNotifyTomorrowPublished(it) } }
            )
        }
        Text(
            text = "Gestionar notificaciones →",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable(onClick = onManageNotificationsClick)
        )
    }
}

@Composable
private fun SettingsRadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
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
