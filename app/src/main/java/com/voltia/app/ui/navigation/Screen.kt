package com.voltia.app.ui.navigation

/**
 * Rutas de navegación de la app. "Today" es la única pestaña de contenido
 * por ahora; cuando se implemente la bottom navigation bar, las futuras
 * pestañas (Mañana, Resumen, Evolución) se añaden aquí como nuevos objetos
 * y se registran en [com.voltia.app.ui.navigation.VoltiaNavHost] y en la
 * barra inferior, sin tocar el resto de la navegación.
 */
sealed class Screen(val route: String) {
    data object Today : Screen("today")
    data object Settings : Screen("settings")
}
