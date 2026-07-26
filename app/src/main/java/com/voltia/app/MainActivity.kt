package com.voltia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.ui.pvpc.PvpcUiState
import com.voltia.app.ui.pvpc.PvpcViewModel
import com.voltia.app.ui.theme.VoltiaTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoltiaTheme {
                Scaffold { innerPadding ->
                    PriceScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel()
                    )
                }
            }
        }
    }
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

@Composable
private fun PriceList(prices: List<HourlyPrice>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Precio PVPC de hoy (€/kWh)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(prices) { price ->
                PriceRow(price)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PriceRow(price: HourlyPrice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = price.hour, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = String.format(Locale.forLanguageTag("es-ES"), "%.5f €/kWh", price.priceEurPerKwh),
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
                HourlyPrice("00:00", 0.18318),
                HourlyPrice("01:00", 0.18153),
                HourlyPrice("09:00", 0.04377)
            )
        )
    }
}
