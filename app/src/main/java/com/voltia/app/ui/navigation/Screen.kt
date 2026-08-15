package com.voltia.app.ui.navigation

/**
 * Rutas de navegación de la app. Today/Tomorrow/Summary son las pestañas de
 * la bottom navigation bar (Hoy/Mañana/Resumen); Settings se abre como
 * pantalla apilada desde el icono de la TopBar, fuera de las pestañas.
 */
sealed class Screen(val route: String) {
    data object Today : Screen("today")
    data object Tomorrow : Screen("tomorrow")
    data object Summary : Screen("summary")
    data object Settings : Screen("settings")
    data object Notifications : Screen("notifications")
    data object Tools : Screen("tools")
    data object ApplianceCalculator : Screen("appliance_calculator")
}
