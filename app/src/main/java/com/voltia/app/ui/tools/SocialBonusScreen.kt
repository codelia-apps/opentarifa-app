package com.voltia.app.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** URL oficial (MITECO) sobre pobreza energética y Bono Social; no se hace scraping, solo se enlaza. */
private const val SocialBonusInfoUrl = "https://www.miteco.gob.es/es/energia/pobreza-energetica/pe-001.html"

/** Contenido estático: qué es y quién puede solicitarlo, con enlace a la fuente oficial actualizada. */
@Composable
fun SocialBonusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bono Social eléctrico",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )

        InfoCard(
            title = "¿Qué es?",
            body = "Un descuento en la factura de la luz pensado para consumidores vulnerables, " +
                "aplicado directamente sobre la tarifa regulada (PVPC)."
        )

        InfoCard(
            title = "¿Quién puede solicitarlo?",
            body = "Hogares con la tarifa PVPC contratada con una comercializadora de referencia, " +
                "potencia contratada igual o inferior a 10 kW, para la vivienda habitual, y que " +
                "cumplan ciertos requisitos de renta o situación familiar. Estos requisitos " +
                "(límites de renta, porcentajes de descuento, colectivos incluidos) cambian con " +
                "cierta frecuencia según la normativa vigente, así que consulta siempre la " +
                "información oficial y actualizada antes de solicitarlo."
        )

        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SocialBonusInfoUrl)))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(text = "Consultar información oficial", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
