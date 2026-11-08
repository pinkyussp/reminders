package pink.yussp.dates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "dates_reminders"

    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "daily-check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Reminders before birthdays and anniversaries"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, title: String, text: String, id: Int) {
        channel(ctx)
        if (!canNotify(ctx)) return
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call; nothing to show.
        }
    }
}

class CheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): ListenableWorker.Result {
        val today = LocalDate.now()
        Store.load(applicationContext).forEach { e ->
            val days = e.daysUntil(today)
            if (days == e.remind.toLong()) {
                val kind = if (e.type == "anniversary") "anniversary" else "birthday"
                val whenText = if (days == 0L) "today" else "in $days days"
                Reminders.notify(applicationContext, "Coming up", "${e.name}'s $kind is $whenText", e.id.hashCode())
            }
        }
        return ListenableWorker.Result.success()
    }
}
