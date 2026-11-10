package pink.yussp.dates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "dates_reminders"
    const val EXTRA_ENTRY_ID = "entry_id"
    private const val HOUR_OF_DAY = 9
    private const val BRAND_COLOR = 0xFFC2614FL

    // Checks once a day at about 9:00 local time, so birthdays and anniversaries
    // notify on the day itself and the advance reminders arrive at a sensible hour.
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(HOUR_OF_DAY, 0)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val delay = Duration.between(now, first).toMinutes()
        val request = PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "daily-check",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Birthdays and anniversaries, on the day and before"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, title: String, text: String, id: Int, entryId: String? = null) {
        channel(ctx)
        if (!canNotify(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (entryId != null) putExtra(EXTRA_ENTRY_ID, entryId)
        }
        val tap = PendingIntent.getActivity(
            ctx,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
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
        val ctx = applicationContext
        val today = LocalDate.now()
        Store.load(ctx).forEach { e ->
            val days = e.daysUntil(today)
            val anniversary = e.type == "anniversary"
            if (days == 0L) {
                val title = ctx.getString(
                    if (anniversary) R.string.notif_today_anniv else R.string.notif_today_birthday,
                    e.name
                )
                Reminders.notify(ctx, title, ctx.getString(R.string.notif_today_text), "${e.id}-day".hashCode(), e.id)
            } else if (e.remind > 0 && days == e.remind.toLong()) {
                val title = ctx.getString(
                    if (anniversary) R.string.notif_soon_anniv else R.string.notif_soon_birthday,
                    e.name
                )
                val text = ctx.getString(R.string.notif_soon_text, remindLabel(e.remind, ctx))
                Reminders.notify(ctx, title, text, "${e.id}-soon".hashCode(), e.id)
            }
        }
        return ListenableWorker.Result.success()
    }
}
