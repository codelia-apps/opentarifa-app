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

    /**
     * Independiente de [uiState]: controla solo el spinner de PullToRefreshBox y el estado de
     * carga del botón "Reintentar". Se separa de uiState porque este último representa el
     * *contenido* a mostrar (que puede seguir siendo el de una carga anterior mientras se
     * refresca), mientras que isRefreshing representa si hay una petición en curso ahora mismo.
     * Se resetea en un finally que cubre los 3 posibles resultados (éxito, error, no publicado
     * todavía) y también cualquier excepción no prevista, para que nunca quede colgado.
     */
    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    /**
     * No hay carga en `init{}`: el ViewModel sobrevive a la navegación entre pestañas (scope del
     * backstack entry con Compose Navigation), así que si solo cargara en su creación, volver a
     * entrar en Mañana ya con precios publicados no los mostraría hasta cerrar y reabrir la app.
     * La carga la dispara la propia pantalla en cada composición (ver TomorrowScreen), lo que ya
     * cubre tanto la primera entrada como cualquier reentrada posterior.
     */
    fun loadPrices() {
        viewModelScope.launch {
            isRefreshing = true
            uiState = TomorrowUiState.Loading
            try {
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
            } catch (e: Exception) {
                // Red de seguridad: cualquier excepción no prevista (p.ej. un JSON inesperado de
                // Gson, que no es IOException) no debe dejar el spinner colgado indefinidamente.
                uiState = TomorrowUiState.Error("Ha ocurrido un error inesperado")
            } finally {
                isRefreshing = false
            }
        }
    }
}
