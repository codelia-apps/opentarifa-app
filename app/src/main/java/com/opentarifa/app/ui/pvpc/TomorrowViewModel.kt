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
import java.time.LocalTime

sealed interface TomorrowUiState {
    data object Loading : TomorrowUiState
    data class Success(val prices: List<HourlyPrice>) : TomorrowUiState
    /** Antes de la publicación diaria de REE (~20:30h) o si la API aún no devuelve datos. */
    data object NotPublishedYet : TomorrowUiState
    data class Error(val message: String) : TomorrowUiState
}

/** Hora aproximada a la que REE publica los precios PVPC del día siguiente. */
private val PublicationTime: LocalTime = LocalTime.of(20, 30)

class TomorrowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PvpcRepository(
        api = NetworkModule.reeApiService,
        priceHistoryDao = OpenTarifaDatabase.getInstance(application).priceHistoryDao()
    )

    var uiState: TomorrowUiState by mutableStateOf(TomorrowUiState.Loading)
        private set

    init {
        loadPrices()
    }

    fun loadPrices() {
        viewModelScope.launch {
            uiState = TomorrowUiState.Loading

            if (LocalTime.now(MadridZone) < PublicationTime) {
                uiState = TomorrowUiState.NotPublishedYet
                return@launch
            }

            uiState = try {
                val prices = repository.getTomorrowPrices()
                if (prices.isEmpty()) TomorrowUiState.NotPublishedYet else TomorrowUiState.Success(prices)
            } catch (e: IOException) {
                TomorrowUiState.Error("No se pudo conectar con el servidor de REE")
            } catch (e: retrofit2.HttpException) {
                TomorrowUiState.Error("Error del servidor (${e.code()})")
            }
        }
    }
}
