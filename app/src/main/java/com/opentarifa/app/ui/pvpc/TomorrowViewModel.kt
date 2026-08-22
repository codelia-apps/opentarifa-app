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
import kotlinx.coroutines.delay
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

/** Duración mínima visible del estado "cargando"; ver el comentario en loadPrices(). */
private const val MinRefreshingDurationMillis = 300L

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
            // No se toca uiState aquí: solo se actualiza con el resultado final (más abajo). Si
            // se pusiera a Loading de entrada, NotPublishedYetContent/TomorrowList se
            // desmontarían de inmediato y LoadingContent() ocuparía toda la pantalla durante el
            // refresco — el botón "Reintentar" de NotPublishedYetContent, que debe mostrar su
            // propio spinner mientras espera, desaparecería en vez de mostrarlo (verificado en
            // emulador: con el uiState=Loading anterior, el spinner del botón nunca llegaba a
            // pintarse porque el botón ya no estaba en pantalla). El valor inicial por defecto de
            // uiState ya es Loading, así que la primera carga de la pestaña sigue mostrando
            // LoadingContent() sin necesidad de fijarlo aquí.
            try {
                // Sin esto, cuando el resultado se resuelve sin ningún punto de suspensión real
                // (p.ej. el camino de NotPublishedYet, que no llega a llamar a la red), Compose
                // agrupa isRefreshing=true y su reset a false en la misma recomposición y nunca
                // llega a pintar el frame "true": el indicador de PullToRefreshBox, que espera
                // observar esa transición para retraerse, se queda visualmente colgado en la
                // posición del gesto (verificado: pasa justo en el caso NotPublishedYet, no en
                // Success, donde la llamada de red ya suspende de sobra).
                delay(MinRefreshingDurationMillis)

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
