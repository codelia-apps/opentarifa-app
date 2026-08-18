package com.opentarifa.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SavingsTips = listOf(
    "Desplaza el uso de electrodomésticos de alto consumo (lavadora, lavavajillas, secadora) a " +
        "las horas más baratas del día — puedes comprobarlas en la pestaña Hoy o usar la Calculadora.",
    "Evita usar varios electrodomésticos de alto consumo a la vez durante las horas más caras del día.",
    "No asumas que una franja horaria es siempre barata o cara — con el sistema actual, las horas " +
        "con más generación solar (normalmente a mediodía) suelen ser las más baratas, no la noche. " +
        "Consulta la app antes de decidir cuándo programar tus electrodomésticos.",
    "Si tienes coche eléctrico, cárgalo durante las horas más baratas del día, normalmente de " +
        "madrugada o a mediodía en días soleados.",
    "Revisa el etiquetado energético de tus electrodomésticos — los de clase A tienen un consumo " +
        "significativamente menor que los de clases inferiores, aunque el precio de compra sea mayor.",
    "Desconecta o usa regletas con interruptor para los aparatos en modo \"stand-by\" — el consumo " +
        "fantasma puede suponer un porcentaje notable de la factura anual."
)

/** Lista estática de consejos, numerados, con el mismo estilo de tarjeta que el resto de Herramientas. */
@Composable
fun SavingsTipsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SavingsTips.forEachIndexed { index, tip ->
            TipCard(number = index + 1, text = tip)
        }
    }
}

@Composable
private fun TipCard(number: Int, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
