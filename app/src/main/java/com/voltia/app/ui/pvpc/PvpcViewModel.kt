package com.voltia.app.ui.pvpc

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

sealed interface PvpcUiState {
    data object Loading : PvpcUiState
    data class Success(val prices: List<HourlyPrice>) : PvpcUiState
    data class Error(val message: String) : PvpcUiState
}

class PvpcViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PvpcRepository(
        api = NetworkModule.reeApiService,
        priceHistoryDao = VoltiaDatabase.getInstance(application).priceHistoryDao()
    )

    var uiState: PvpcUiState by mutableStateOf(PvpcUiState.Loading)
        private set

    init {
        loadPrices()
    }

    fun loadPrices() {
        viewModelScope.launch {
            uiState = PvpcUiState.Loading
            uiState = try {
                PvpcUiState.Success(repository.getTodayPrices())
            } catch (e: IOException) {
                PvpcUiState.Error("No se pudo conectar con el servidor de REE")
            } catch (e: retrofit2.HttpException) {
                PvpcUiState.Error("Error del servidor (${e.code()})")
            }
        }
    }
}
