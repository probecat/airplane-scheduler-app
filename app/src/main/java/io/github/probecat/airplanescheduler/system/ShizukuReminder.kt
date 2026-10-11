package io.github.probecat.airplanescheduler.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.ui.MainActivity
import java.time.ZonedDateTime
import java.util.Date

object ShizukuReminder {
    private const val CHANNEL = "shizuku_reminder"
    private const val NOTIFICATION = 1

    // One check before each start instead of watching Shizuku: stay silent when the schedule will work.
    fun check(context: Context, done: () -> Unit) {
        val app = App.from(context)
        AirplaneController.awaitBinder {
            app.refreshShizuku()
            val state = app.shizuku.value
            val start = ScheduleResolver.nextStart(app.schedules.all, ZonedDateTime.now())
            if (state != ShizukuState.READY && app.settings.current.remindShizuku && start != null) {
                notify(app, state == ShizukuState.PERMISSION_NEEDED, start)
            }
            done()
        }
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
    }

    private fun notify(context: Context, running: Boolean, start: ZonedDateTime) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Shizuku reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val time = DateFormat.getTimeFormat(context).format(Date.from(start.toInstant()))
        val open = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (running) "Shizuku permission missing" else "Shizuku isn't running")
            .setContentText(
                if (running) {
                    "Airplane mode may not turn on at $time. Open the app to grant access."
                } else {
                    "Airplane mode may not turn on at $time. Start Shizuku before then."
                },
            )
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        notifications.notify(NOTIFICATION, notification)
    }
}
