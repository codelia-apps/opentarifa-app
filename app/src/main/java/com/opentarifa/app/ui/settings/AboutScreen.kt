package com.opentarifa.app.ui.settings

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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val RepositoryUrl = "https://github.com/codelia-apps/opentarifa-app"
private const val IssuesUrl = "https://github.com/codelia-apps/opentarifa-app/issues"
private const val FeedbackEmail = "opentarifa@disroot.org"
private const val FeedbackEmailSubject = "OpenTarifa - Sugerencia/Error"

/** Contenido estático: qué es la app, fuente de datos, enlace al repo y licencia. */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "OpenTarifa",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Precios de la luz (PVPC) hora a hora, claros y sin complicaciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InfoCard(
            title = "Fuente de los datos",
            body = "Los datos de precios se obtienen de la API pública de Red Eléctrica de España (REE)."
        )

        Text(
            text = "Los precios no incluyen Ceuta y Melilla, que tienen un sistema eléctrico propio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        InfoCard(title = "Licencia", body = "Software libre bajo licencia GPLv3.")

        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RepositoryUrl)))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(text = "Ver repositorio en GitHub", modifier = Modifier.padding(start = 8.dp))
        }

        InfoCard(
            title = "Sugerencias y errores",
            body = "¿Algo no funciona como debería o se te ocurre una mejora? Cuéntanoslo."
        )

        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IssuesUrl)))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(text = "Reportar en GitHub", modifier = Modifier.padding(start = 8.dp))
        }

        OutlinedButton(
            onClick = {
                val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackEmail))
                    putExtra(Intent.EXTRA_SUBJECT, FeedbackEmailSubject)
                }
                context.startActivity(emailIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(text = "Enviar por email", modifier = Modifier.padding(start = 8.dp))
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
