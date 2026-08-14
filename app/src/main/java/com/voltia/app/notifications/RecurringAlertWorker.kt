package com.voltia.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.voltia.app.data.local.NotificationPreferencesRepository
import com.voltia.app.data.local.VoltiaDatabase
import com.voltia.app.data.remote.NetworkModule
import com.voltia.app.data.repository.AlertRepository
import com.voltia.app.data.repository.PvpcRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private val MadridZone: ZoneId = ZoneId.of("Europe/Madrid")

/** Ventana de mañana: cálculo de alertas Tipo B, con los precios de hoy recién publicados/estables. */
private val MorningRunTime: LocalTime = LocalTime.of(8, 0)

/** Ventana de tarde: los precios de mañana ya se han publicado (~20:30h), para el aviso opcional. */
private val EveningRunTime: LocalTime = LocalTime.of(21, 30)

private const val MORNING_WORK_NAME = "recurring_alerts_daily_morning"
private const val EVENING_WORK_NAME = "recurring_alerts_daily_evening"

/**
 * Tarea diaria con la app cerrada, con dos horarios (ver [schedulePeriodicRecurringAlertWork]):
 *  - (re)programa las alertas Tipo B con los precios de hoy ([scheduleTodaysRecurringAlerts]).
 *  - si el aviso está activado en Ajustes, comprueba si ya se pueden avisar los precios de mañana
 *    ([checkAndNotifyTomorrowPricesPublished]).
 * Ambas comprobaciones corren en las dos ejecuciones: a las 8:00 la de mañana normalmente no
 * encuentra nada publicado todavía y no hace nada; a las 21:30 la de Tipo B simplemente
 * actualiza la misma alarma que ya se programó por la mañana (idempotente).
 */
class RecurringAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = VoltiaDatabase.getInstance(applicationContext)
        val pvpcRepository = PvpcRepository(NetworkModule.reeApiService, database.priceHistoryDao())
        val alertRepository = AlertRepository(database.alertDao())
        val notificationPreferencesRepository = NotificationPreferencesRepository(applicationContext)

        return try {
            val today = LocalDate.now(MadridZone)
            val todayPrices = pvpcRepository.getTodayPrices()
            scheduleTodaysRecurringAlerts(applicationContext, alertRepository, today, todayPrices)
            checkAndNotifyTomorrowPricesPublished(applicationContext, notificationPreferencesRepository, pvpcRepository, today)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Registra las dos tareas periódicas diarias si no existen ya (ver [ExistingPeriodicWorkPolicy.KEEP]:
 * abrir la app varias veces no debe duplicarlas ni reiniciar su ventana de ejecución). WorkManager
 * persiste el trabajo periódico y lo reprograma solo tras un reinicio del dispositivo (para eso
 * hace falta el permiso RECEIVE_BOOT_COMPLETED en el manifest), así que basta con llamar esto una
 * vez por arranque de la app.
 */
fun schedulePeriodicRecurringAlertWork(context: Context) {
    enqueueDaily(context, MORNING_WORK_NAME, MorningRunTime)
    enqueueDaily(context, EVENING_WORK_NAME, EveningRunTime)
}

private fun enqueueDaily(context: Context, uniqueWorkName: String, runTime: LocalTime) {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    val request = PeriodicWorkRequestBuilder<RecurringAlertWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(millisUntilNextRunTime(runTime), TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        uniqueWorkName,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

/** Milisegundos hasta la próxima ocurrencia de [runTime]: hoy si todavía no ha pasado, si no mañana. */
private fun millisUntilNextRunTime(runTime: LocalTime): Long {
    val now = ZonedDateTime.now(MadridZone)
    var nextRun = now.with(runTime)
    if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
    return Duration.between(now, nextRun).toMillis()
}
