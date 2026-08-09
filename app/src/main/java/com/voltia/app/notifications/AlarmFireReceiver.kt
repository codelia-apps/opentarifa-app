package com.voltia.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.voltia.app.MainActivity
import com.voltia.app.data.local.AlertChannel
import com.voltia.app.data.local.VoltiaDatabase
import com.voltia.app.ui.pvpc.PriceCategory
import com.voltia.app.ui.pvpc.categoryLabel
import com.voltia.app.ui.pvpc.formatPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe el disparo de AlarmManager para una alerta Tipo A: muestra la
 * notificación (si el canal la incluye) y siempre marca la alerta como
 * completada — un solo uso, no debe repetirse al día siguiente.
 */
class AlarmFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(AlarmScheduler.EXTRA_ALERT_ID, -1L)
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val price = intent.getDoubleExtra(AlarmScheduler.EXTRA_PRICE, 0.0)
        val category = runCatching {
            PriceCategory.valueOf(intent.getStringExtra(AlarmScheduler.EXTRA_CATEGORY).orEmpty())
        }.getOrDefault(PriceCategory.MID)
        val channel = runCatching {
            AlertChannel.valueOf(intent.getStringExtra(AlarmScheduler.EXTRA_CHANNEL).orEmpty())
        }.getOrDefault(AlertChannel.SYSTEM_NOTIFICATION)

        if (channel == AlertChannel.SYSTEM_NOTIFICATION || channel == AlertChannel.BOTH) {
            showNotification(context, hour, price, category)
        }

        if (alertId >= 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = VoltiaDatabase.getInstance(context).alertDao()
                    dao.getById(alertId)?.let { dao.update(it.copy(isEnabled = false)) }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, hour: Int, price: Double, category: PriceCategory) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val hourLabel = "%02d-%02dh".format(hour, (hour + 1) % 24)
        val contentIntent = PendingIntent.getActivity(
            context,
            hour,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, VoltiaNotificationChannels.PRICE_ALERTS_CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Alerta de precio · $hourLabel")
            .setContentText("${categoryLabel(category)}: ${formatPrice(price)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(hour, notification)
    }
}
