package com.voltia.app.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.voltia.app.data.local.ThemePreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themePreferencesRepository = remember { ThemePreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    val systemDarkTheme = isSystemInDarkTheme()
    val storedDarkModeEnabled by themePreferencesRepository.darkModeEnabled.collectAsState(initial = null)
    val darkModeEnabled = storedDarkModeEnabled ?: systemDarkTheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Modo oscuro", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = darkModeEnabled,
            onCheckedChange = { enabled ->
                scope.launch { themePreferencesRepository.setDarkModeEnabled(enabled) }
            }
        )
    }
}
