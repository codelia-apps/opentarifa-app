package com.voltia.app.ui.tools

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltia.app.data.local.VoltiaDatabase
import com.voltia.app.data.model.HourlyPrice
import com.voltia.app.data.remote.NetworkModule
import com.voltia.app.data.repository.PvpcRepository
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ApplianceCalculatorUiState {
    data object Loading : ApplianceCalculatorUiState
    data class Success(val todayPrices: List<HourlyPrice>) : ApplianceCalculatorUiState
    data class Error(val message: String) : ApplianceCalculatorUiState
}

class ApplianceCalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PvpcRepository(
        api = NetworkModule.reeApiService,
        priceHistoryDao = VoltiaDatabase.getInstance(application).priceHistoryDao()
    )

    var uiState: ApplianceCalculatorUiState by mutableStateOf(ApplianceCalculatorUiState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = ApplianceCalculatorUiState.Loading
            uiState = try {
                ApplianceCalculatorUiState.Success(repository.getTodayPrices())
            } catch (e: IOException) {
                ApplianceCalculatorUiState.Error("No se pudo conectar con el servidor de REE")
            } catch (e: retrofit2.HttpException) {
                ApplianceCalculatorUiState.Error("Error del servidor (${e.code()})")
            }
        }
    }
}
