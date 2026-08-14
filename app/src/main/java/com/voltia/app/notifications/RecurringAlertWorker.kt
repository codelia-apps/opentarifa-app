package com.voltia.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

/** Ventana aproximada en la que WorkManager debe intentar ejecutar la tarea diaria (7:00-9:00). */
private val DailyRunTime: LocalTime = LocalTime.of(8, 0)

private const val UNIQUE_WORK_NAME = "recurring_alerts_daily"

/**
 * Ejecuta [scheduleTodaysRecurringAlerts] con la app cerrada: pide los precios de hoy (misma
 * llamada de red que ya hace [PvpcRepository]) y reprograma las alarmas Tipo B a partir de ellos.
 * Sustituye al `LaunchedEffect` que antes hacía esto solo al abrir la app.
 */
class RecurringAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = VoltiaDatabase.getInstance(applicationContext)
        val pvpcRepository = PvpcRepository(NetworkModule.reeApiService, database.priceHistoryDao())
        val alertRepository = AlertRepository(database.alertDao())

        return try {
            val prices = pvpcRepository.getTodayPrices()
            scheduleTodaysRecurringAlerts(applicationContext, alertRepository, LocalDate.now(MadridZone), prices)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Registra la tarea periódica diaria si no existe ya (ver [ExistingPeriodicWorkPolicy.KEEP]:
 * abrir la app varias veces no debe duplicarla ni reiniciar su ventana de ejecución). WorkManager
 * persiste el trabajo periódico y lo reprograma solo tras un reinicio del dispositivo (para eso
 * hace falta el permiso RECEIVE_BOOT_COMPLETED en el manifest), así que basta con llamar esto una
 * vez por arranque de la app.
 */
fun schedulePeriodicRecurringAlertWork(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    val request = PeriodicWorkRequestBuilder<RecurringAlertWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(millisUntilNextRunTime(), TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

/** Milisegundos hasta la próxima [DailyRunTime]: hoy si todavía no ha pasado, si no mañana. */
private fun millisUntilNextRunTime(): Long {
    val now = ZonedDateTime.now(MadridZone)
    var nextRun = now.with(DailyRunTime)
    if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
    return Duration.between(now, nextRun).toMillis()
}
