package com.voltia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voltia.app.data.local.ThemePreferencesRepository
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.ui.navigation.Screen
import com.voltia.app.ui.pvpc.PvpcUiState
import com.voltia.app.ui.pvpc.PvpcViewModel
import com.voltia.app.ui.settings.SettingsScreen
import com.voltia.app.ui.theme.VoltiaGreen40
import com.voltia.app.ui.theme.VoltiaGreen80
import com.voltia.app.ui.theme.VoltiaNeutral40
import com.voltia.app.ui.theme.VoltiaNeutral80
import com.voltia.app.ui.theme.VoltiaRed40
import com.voltia.app.ui.theme.VoltiaRed80
import com.voltia.app.ui.theme.VoltiaTheme
import com.voltia.app.ui.theme.VoltiaYellow40
import com.voltia.app.ui.theme.VoltiaYellow80
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themePreferencesRepository = remember { ThemePreferencesRepository(context) }
            val systemDarkTheme = isSystemInDarkTheme()
            val storedDarkModeEnabled by themePreferencesRepository.darkModeEnabled.collectAsState(initial = null)
            val darkTheme = storedDarkModeEnabled ?: systemDarkTheme

            VoltiaTheme(darkTheme = darkTheme) {
                VoltiaApp()
            }
        }
    }
}

/**
 * Contenedor de navegación de la app: Scaffold + TopAppBar compartidos por
 * todas las pantallas, y el NavHost con las rutas de [Screen]. Cuando se
 * añada la bottom navigation bar (Hoy, Mañana, Resumen, Evolución), su
 * Scaffold(bottomBar = ...) se añade aquí sin tocar el resto.
 */
@Composable
fun VoltiaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        topBar = {
            VoltiaTopBar(
                currentRoute = currentRoute,
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onBackClick = { navController.popBackStack() }
            )
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
            composable(Screen.Settings.route) {
                SettingsScreen()
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
    val isSettings = currentRoute == Screen.Settings.route
    TopAppBar(
        title = { Text(if (isSettings) "Ajustes" else "Voltia") },
        navigationIcon = {
            if (isSettings) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = {
            if (!isSettings) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }
    )
}

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
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

private enum class ExtremeMarker { NONE, CHEAP, EXPENSIVE }

/** Margen (proporción del rango del día) para considerar un precio "entre los extremos". */
private const val EXTREME_MARGIN_RATIO = 0.03

/**
 * true si el tema actual es oscuro, a partir del color de fondo resuelto por
 * MaterialTheme (funciona tanto si el oscuro viene del sistema como del
 * switch manual de Ajustes, sin tener que pasar el flag por todos los niveles).
 */
@Composable
private fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
private fun PriceList(prices: List<HourlyPrice>) {
    val priceValues = prices.map { it.priceEurPerKwh }
    val minPrice = priceValues.minOrNull() ?: 0.0
    val maxPrice = priceValues.maxOrNull() ?: 0.0
    val averagePrice = if (prices.isEmpty()) 0.0 else priceValues.sum() / prices.size
    val extremeMarkers = findExtremeMarkers(priceValues, minPrice, maxPrice)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Precio PVPC de hoy (€/kWh)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Media del día: ${formatPrice(averagePrice)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(prices) { index, price ->
                PriceRow(
                    price = price,
                    color = priceIndicatorColor(price.priceEurPerKwh, minPrice, maxPrice),
                    marker = extremeMarkers[index]
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * Verde/amarillo/rojo según el percentil del precio dentro del rango real
 * (mínimo-máximo) del día. Si todos los precios del día son iguales (rango
 * = 0) se usa un único color neutro para evitar dividir por cero. En modo
 * oscuro se usan los tonos "80" (más claros) en vez de los "40" para
 * mantener contraste sobre fondos oscuros, igual que hace el resto del
 * tema (ver Theme.kt).
 */
@Composable
private fun priceIndicatorColor(price: Double, minPrice: Double, maxPrice: Double): Color {
    val isDark = isAppInDarkTheme()
    val range = maxPrice - minPrice
    if (range <= 0.0) return if (isDark) VoltiaNeutral80 else VoltiaNeutral40

    return when {
        price <= minPrice + range / 3 -> if (isDark) VoltiaGreen80 else VoltiaGreen40
        price <= minPrice + range * 2 / 3 -> if (isDark) VoltiaYellow80 else VoltiaYellow40
        else -> if (isDark) VoltiaRed80 else VoltiaRed40
    }
}

/**
 * Marca como "entre las más baratas"/"entre las más caras" cualquier hora
 * cuyo precio esté dentro de un margen pequeño ([EXTREME_MARGIN_RATIO] del
 * rango del día) respecto al mínimo o al máximo absoluto, sin exigir que
 * sean horas consecutivas: pueden quedar dispersas en cualquier punto del
 * día. Si el rango es 0 no se marca nada.
 */
private fun findExtremeMarkers(
    prices: List<Double>,
    minPrice: Double,
    maxPrice: Double
): List<ExtremeMarker> {
    val range = maxPrice - minPrice
    if (range <= 0.0) return List(prices.size) { ExtremeMarker.NONE }

    val margin = range * EXTREME_MARGIN_RATIO
    return prices.map { price ->
        when {
            price <= minPrice + margin -> ExtremeMarker.CHEAP
            price >= maxPrice - margin -> ExtremeMarker.EXPENSIVE
            else -> ExtremeMarker.NONE
        }
    }
}

private fun formatPrice(price: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%.5f €/kWh", price)

@Composable
private fun PriceRow(price: HourlyPrice, color: Color, marker: ExtremeMarker) {
    val isDark = isAppInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color = color, shape = CircleShape)
            )
            Text(
                text = price.hour,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
            when (marker) {
                ExtremeMarker.CHEAP -> Text(
                    text = "▼",
                    color = if (isDark) VoltiaGreen80 else VoltiaGreen40,
                    modifier = Modifier.padding(start = 8.dp)
                )
                ExtremeMarker.EXPENSIVE -> Text(
                    text = "▲",
                    color = if (isDark) VoltiaRed80 else VoltiaRed40,
                    modifier = Modifier.padding(start = 8.dp)
                )
                ExtremeMarker.NONE -> Unit
            }
        }
        Text(
            text = formatPrice(price.priceEurPerKwh),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PriceListPreview() {
    VoltiaTheme {
        PriceList(
            prices = listOf(
                HourlyPrice("0h - 1h", 0.18318),
                HourlyPrice("1h - 2h", 0.18153),
                HourlyPrice("9h - 10h", 0.04377)
            )
        )
    }
}
