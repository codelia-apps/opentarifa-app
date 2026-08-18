package com.opentarifa.app.ui.pvpc

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentarifa.app.data.local.OpenTarifaDatabase
import com.opentarifa.app.data.model.HourlyPrice
import com.opentarifa.app.data.remote.NetworkModule
import com.opentarifa.app.data.repository.PvpcRepository
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/** Precio medio de un día del histórico, para el gráfico de evolución. */
data class DailyAverage(val date: LocalDate, val avgPrice: Double)

/** Días de histórico que muestra el gráfico de evolución de Resumen. */
private const val ChartDays = 7L

sealed interface SummaryUiState {
    data object Loading : SummaryUiState
    data class Success(val todayPrices: List<HourlyPrice>, val dailyAverages: List<DailyAverage>) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}

class SummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = OpenTarifaDatabase.getInstance(application)
    private val repository = PvpcRepository(NetworkModule.reeApiService, database.priceHistoryDao())
    private val priceHistoryDao = database.priceHistoryDao()

    var uiState: SummaryUiState by mutableStateOf(SummaryUiState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = SummaryUiState.Loading
            uiState = try {
                // getTodayPrices() ya deja hoy guardado en price_history como efecto secundario,
                // así que el histórico consultado justo después ya lo incluye.
                val todayPrices = repository.getTodayPrices()
                val today = LocalDate.now(MadridZone)
                val history = priceHistoryDao.getSince(today.minusDays(ChartDays - 1).toString())
                val dailyAverages = history
                    .groupBy { it.date }
                    .map { (date, entries) -> DailyAverage(LocalDate.parse(date), entries.map { it.priceEurPerKwh }.average()) }
                    .sortedBy { it.date }
                SummaryUiState.Success(todayPrices, dailyAverages)
            } catch (e: IOException) {
                SummaryUiState.Error("No se pudo conectar con el servidor de REE")
            } catch (e: retrofit2.HttpException) {
                SummaryUiState.Error("Error del servidor (${e.code()})")
            }
        }
    }
}
