package com.opentarifa.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

/** Ventana de mañana: cálculo de alertas Tipo B, con los precios de hoy recién publicados/estables. */
private val MorningRunTime: LocalTime = LocalTime.of(8, 0)

private const val MORNING_WORK_NAME = "recurring_alerts_daily_morning"

/**
 * Tarea diaria con la app cerrada, a las 8:00 (ver [schedulePeriodicRecurringAlertWork]):
 * (re)programa las alertas Tipo B con los precios de hoy ([scheduleTodaysRecurringAlerts]). El
 * aviso de "precios de mañana publicados" tiene su propia cadena de reintentos, ver
 * [scheduleTomorrowPublishedCheckChain] / [TomorrowPublishedCheckWorker].
 */
class RecurringAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = OpenTarifaDatabase.getInstance(applicationContext)
        val pvpcRepository = PvpcRepository(NetworkModule.reeApiService, database.priceHistoryDao())
        val alertRepository = AlertRepository(database.alertDao())

        return try {
            val today = LocalDate.now(MadridZone)
            val todayPrices = pvpcRepository.getTodayPrices()
            scheduleTodaysRecurringAlerts(applicationContext, alertRepository, today, todayPrices)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Registra la tarea periódica diaria de las 8:00 si no existe ya (ver [ExistingPeriodicWorkPolicy.KEEP]:
 * abrir la app varias veces no debe duplicarla ni reiniciar su ventana de ejecución). WorkManager
 * persiste el trabajo periódico y lo reprograma solo tras un reinicio del dispositivo (para eso
 * hace falta el permiso RECEIVE_BOOT_COMPLETED en el manifest), así que basta con llamar esto una
 * vez por arranque de la app.
 */
fun schedulePeriodicRecurringAlertWork(context: Context) {
    enqueueDaily(context, MORNING_WORK_NAME, MorningRunTime)
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
