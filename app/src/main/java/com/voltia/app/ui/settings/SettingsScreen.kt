package com.voltia.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.voltia.app.data.local.ThemeMode
import com.voltia.app.data.local.ThemePreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themePreferencesRepository = remember { ThemePreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    val selectedThemeMode by themePreferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Tema", style = MaterialTheme.typography.titleMedium)

        ThemeModeOption(
            label = "Claro",
            selected = selectedThemeMode == ThemeMode.LIGHT,
            onClick = { scope.launch { themePreferencesRepository.setThemeMode(ThemeMode.LIGHT) } }
        )
        ThemeModeOption(
            label = "Oscuro",
            selected = selectedThemeMode == ThemeMode.DARK,
            onClick = { scope.launch { themePreferencesRepository.setThemeMode(ThemeMode.DARK) } }
        )
        ThemeModeOption(
            label = "Automático (seguir sistema)",
            selected = selectedThemeMode == ThemeMode.SYSTEM,
            onClick = { scope.launch { themePreferencesRepository.setThemeMode(ThemeMode.SYSTEM) } }
        )
    }
}

@Composable
private fun ThemeModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
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
