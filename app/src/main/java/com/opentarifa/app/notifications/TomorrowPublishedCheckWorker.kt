package com.opentarifa.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.opentarifa.app.data.local.NotificationPreferencesRepository
import com.opentarifa.app.data.local.OpenTarifaDatabase
import com.opentarifa.app.data.remote.NetworkModule
import com.opentarifa.app.data.repository.AlertRepository
import com.opentarifa.app.data.repository.PvpcRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

/** Ventana en la que puede haberse publicado el PVPC de mañana (REE publica sobre las 20:30h). */
private val WindowStart: LocalTime = LocalTime.of(20, 15)
private val WindowEnd: LocalTime = LocalTime.of(23, 59)

/** Cadencia real de reintento dentro de la ventana; PeriodicWorkRequest no baja de 15 min. */
private val RetryInterval: Duration = Duration.ofMinutes(12)

private const val CHAIN_WORK_NAME = "tomorrow_published_check_chain"

/**
 * Comprueba si ya se han publicado los precios de mañana, con reintentos cada
 * ~12 min dentro de la ventana 20:15–23:59h. `PeriodicWorkRequest` no permite
 * bajar de 15 min de intervalo, así que en su lugar cada ejecución encadena la
 * siguiente con [OneTimeWorkRequestBuilder.setInitialDelay] (ver
 * [scheduleNextRun]): en cuanto detecta precios publicados, deja de encadenar
 * por hoy (el siguiente eslabón ya se programa para la ventana de mañana),
 * dispara el aviso opcional de publicación y — con independencia de si ese
 * aviso está activado — reprograma aquí mismo las alertas Tipo B ("más
 * barata/cara del día") para el día que empieza mañana a las 00:00, en vez de
 * esperar al worker de las 8:00 del propio día: si la hora más barata/cara
 * cae de madrugada, calcularla a las 8:00 llegaría tarde y la alarma se
 * dispararía casi de inmediato con horas de retraso (ver guard de seguridad
 * en [scheduleTodaysRecurringAlerts] para cuando este cálculo anticipado no
 * pudo correr, p.ej. instalación el mismo día). Fuera de la ventana no debe
 * quedar ningún work en curso salvo el que apunta al próximo inicio de
 * ventana.
 */
class TomorrowPublishedCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now(MadridZone)
        val nowTime = now.toLocalTime()

        if (nowTime.isBefore(WindowStart) || nowTime.isAfter(WindowEnd)) {
            Log.d(LOG_TAG, "$now — fuera de ventana (20:15-23:59), reprogramando para el próximo inicio")
            scheduleNextRun(applicationContext, delayUntilNextWindowStart(now))
            return Result.success()
        }

        val database = OpenTarifaDatabase.getInstance(applicationContext)
        val pvpcRepository = PvpcRepository(NetworkModule.reeApiService, database.priceHistoryDao())
        val notificationPreferencesRepository = NotificationPreferencesRepository(applicationContext)
        val alertRepository = AlertRepository(database.alertDao())

        val tomorrowPrices = try {
            pvpcRepository.getTomorrowPrices()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "$now — error consultando precios de mañana, se reintentará", e)
            emptyList()
        }
        val published = tomorrowPrices.isNotEmpty()

        if (published) {
            val today = now.toLocalDate()
            try {
                checkAndNotifyTomorrowPricesPublished(applicationContext, notificationPreferencesRepository, tomorrowPrices, today)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "$now — error notificando publicación de precios de mañana", e)
            }
            try {
                scheduleTodaysRecurringAlerts(applicationContext, alertRepository, today.plusDays(1), tomorrowPrices)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "$now — error programando alertas recurrentes de mañana", e)
            }
        }

        val delay = if (published) delayUntilNextWindowStart(now) else RetryInterval
        Log.d(LOG_TAG, "$now — intento en ventana, published=$published, siguiente en $delay")
        scheduleNextRun(applicationContext, delay)
        return Result.success()
    }
}

/** Encadena el siguiente eslabón; REPLACE porque solo debe existir uno en curso a la vez. */
private fun scheduleNextRun(context: Context, delay: Duration) {
    val request = OneTimeWorkRequestBuilder<TomorrowPublishedCheckWorker>()
        .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        CHAIN_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request
    )
}

private fun delayUntilNextWindowStart(from: ZonedDateTime): Duration {
    var next = from.with(WindowStart)
    if (!next.isAfter(from)) next = next.plusDays(1)
    return Duration.between(from, next)
}

/**
 * Arranca la cadena si no hay ninguna en curso (KEEP: reabrir la app no debe reiniciar la
 * espera). Si la app se abre ya dentro de la ventana sin ningún work programado (primer arranque,
 * o tras un reinicio del dispositivo), empieza a comprobar de inmediato en vez de esperar a
 * mañana.
 */
fun scheduleTomorrowPublishedCheckChain(context: Context) {
    val now = ZonedDateTime.now(MadridZone)
    val nowTime = now.toLocalTime()
    val initialDelay = if (nowTime.isBefore(WindowStart) || nowTime.isAfter(WindowEnd)) {
        delayUntilNextWindowStart(now)
    } else {
        Duration.ZERO
    }

    val request = OneTimeWorkRequestBuilder<TomorrowPublishedCheckWorker>()
        .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        CHAIN_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        request
    )
}

private const val LOG_TAG = "TomorrowPublishedCheck"
