package com.voltia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voltia.app.data.local.ThemeMode
import com.voltia.app.data.local.ThemePreferencesRepository
import com.voltia.app.notifications.VoltiaNotificationChannels
import com.voltia.app.notifications.schedulePeriodicRecurringAlertWork
import com.voltia.app.ui.navigation.Screen
import com.voltia.app.ui.notifications.NotificationsScreen
import com.voltia.app.ui.pvpc.PriceScreen
import com.voltia.app.ui.pvpc.SummaryScreen
import com.voltia.app.ui.pvpc.TomorrowScreen
import com.voltia.app.ui.settings.SettingsScreen
import com.voltia.app.ui.theme.VoltiaTheme

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Idempotente y sin diálogo: crear el canal no requiere el permiso de notificaciones.
        VoltiaNotificationChannels.ensureChannelCreated(this)
        // enqueueUniquePeriodicWork con KEEP: no duplica ni reinicia la ventana si ya estaba programada.
        schedulePeriodicRecurringAlertWork(this)
        handleIntent(intent)
        setContent {
            val context = LocalContext.current
            val themePreferencesRepository = remember { ThemePreferencesRepository(context) }
            val systemDarkTheme = isSystemInDarkTheme()
            val themeMode by themePreferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }

            VoltiaTheme(darkTheme = darkTheme) {
                VoltiaApp(pendingRoute = pendingRoute, onPendingRouteHandled = { pendingRoute = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** La notificación de una alerta abre la app directamente en Hoy o Mañana, esté ya abierta o no. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            pendingRoute = Screen.Today.route
        } else if (intent?.getBooleanExtra(EXTRA_OPEN_TOMORROW, false) == true) {
            pendingRoute = Screen.Tomorrow.route
        }
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "com.voltia.app.EXTRA_OPEN_TODAY"
        const val EXTRA_OPEN_TOMORROW = "com.voltia.app.EXTRA_OPEN_TOMORROW"
    }
}

/**
 * Contenedor de navegación de la app: Scaffold + TopAppBar + bottom
 * navigation bar compartidos por todas las pantallas, y el NavHost con las
 * rutas de [Screen]. La bottom bar solo se muestra en las 3 pestañas de
 * contenido (Hoy/Mañana/Resumen); Ajustes se abre apilada desde la TopBar
 * y oculta la bottom bar, como una pantalla fuera de las pestañas.
 */
@Composable
fun VoltiaApp(pendingRoute: String? = null, onPendingRouteHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != Screen.Settings.route && currentRoute != Screen.Notifications.route

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onPendingRouteHandled()
        }
    }

    Scaffold(
        topBar = {
            VoltiaTopBar(
                currentRoute = currentRoute,
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onBackClick = { navController.popBackStack() }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                VoltiaBottomNavBar(
                    currentRoute = currentRoute,
                    onSelect = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Today.route) {
                PriceScreen(viewModel = viewModel())
            }
            composable(Screen.Tomorrow.route) {
                TomorrowScreen(viewModel = viewModel())
            }
            composable(Screen.Summary.route) {
                SummaryScreen(viewModel = viewModel())
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onManageNotificationsClick = { navController.navigate(Screen.Notifications.route) }
                )
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoltiaTopBar(
    currentRoute: String?,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val showBackButton = currentRoute == Screen.Settings.route || currentRoute == Screen.Notifications.route
    val title = when (currentRoute) {
        Screen.Settings.route -> "Ajustes"
        Screen.Notifications.route -> "Notificaciones"
        Screen.Tomorrow.route -> "Mañana"
        Screen.Summary.route -> "Resumen"
        else -> "Voltia"
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = {
            if (!showBackButton) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }
    )
}

/** Pestaña de la bottom navigation bar: ruta, etiqueta e icono a mostrar. */
private data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val BottomNavItems = listOf(
    BottomNavItem(Screen.Today, "Hoy", Icons.Filled.Home),
    BottomNavItem(Screen.Tomorrow, "Mañana", Icons.Filled.CalendarMonth),
    BottomNavItem(Screen.Summary, "Resumen", Icons.Filled.Insights)
)

/**
 * Navegación inferior de 3 pestañas fijas (Hoy/Mañana/Resumen), siguiendo
 * BottomNavBar.jsx del sistema de diseño: icono en pastilla + etiqueta,
 * pastilla de fondo (primaryContainer) solo en la pestaña activa.
 */
@Composable
private fun VoltiaBottomNavBar(currentRoute: String?, onSelect: (Screen) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            BottomNavItems.forEach { item ->
                NavigationBarItem(
                    selected = currentRoute == item.screen.route,
                    onClick = { onSelect(item.screen) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

