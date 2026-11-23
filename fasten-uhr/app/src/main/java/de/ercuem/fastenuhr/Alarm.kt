package de.ercuem.fastenuhr

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

const val KEY_ZIEL = "ziel_std"
val ZIEL_STUFEN = listOf(4, 8, 16)

fun zielStd(ctx: Context) =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ZIEL, 16)

/** Alarm nach Erreichen des Fastenziels: Benachrichtigung + Vibration. */
object Alarm {
    private const val CHANNEL = "fastenziel"

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Stellt den Alarm auf Start + Ziel. Liegt das schon in der Vergangenheit: kein Alarm. */
    fun schedule(ctx: Context, start: Long) {
        cancel(ctx)
        val zeit = start + zielStd(ctx) * 3_600_000L
        if (zeit <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zeit, pending(ctx))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zeit, pending(ctx))
        }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx))
    }

    fun notify(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Fastenziel", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) },
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("Fastenziel erreicht")
            .setContentText("${zielStd(ctx)} Std. ohne Essen geschafft!")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(1, n) } catch (_: SecurityException) { }
    }
}

/** Wird zur Alarmzeit aufgerufen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Alarm.notify(ctx)
}

/** Nach Watch-Neustart den Alarm neu stellen (Android löscht Alarme beim Neustart). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val start = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_START, 0L)
        if (start != 0L) Alarm.schedule(ctx, start)
    }
}
