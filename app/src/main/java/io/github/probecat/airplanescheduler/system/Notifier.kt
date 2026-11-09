package io.github.probecat.airplanescheduler.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Ending
import io.github.probecat.airplanescheduler.core.Notice
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.MainActivity
import io.github.probecat.airplanescheduler.ui.format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

// One notification per schedule, replaced as its window comes up, runs and ends. Notices before and during a window
// follow the schedules, and every change computes them again. A report of how a window ended is posted once. It
// stays until the user dismisses it, or until the next notice of the schedule. A report of what an end left switched
// has its own notification, which goes away when nothing is left switched.
object Notifier {
    const val SKIP = "io.github.probecat.airplanescheduler.SKIP"
    const val END = "io.github.probecat.airplanescheduler.END"
    const val POSTPONE = "io.github.probecat.airplanescheduler.POSTPONE"
    const val UNDO = "io.github.probecat.airplanescheduler.UNDO"
    const val TURN_ON = "io.github.probecat.airplanescheduler.TURN_ON"
    const val RETRY = "io.github.probecat.airplanescheduler.RETRY"
    const val DISMISSED = "io.github.probecat.airplanescheduler.DISMISSED"

    const val ID = "id"
    const val DATE = "date"
    const val MINUTES = "minutes"
    const val CHANGES = "changes"
    const val TITLE = "title"
    const val KEY = "key"

    private const val SCHEDULES = "schedules_v2"
    private const val PROBLEMS = "problems"
    private const val TAG = "schedule"
    private const val PROBLEM_TAG = "problem"
    private const val KIND = "kind"
    private const val CHANNEL = "channel"
    private const val CLEAN_END_SHOWS_MINUTES = 60L

    private enum class Kind { NOTICE, REPORT, PROBLEM }

    // Android applies posts and cancels a moment later, so its list can still show a notice that a report
    // just replaced. For a few seconds, what this process last did with a notification wins over the list.
    // A cancel leaves an empty bundle.
    private class Latest(val extras: Bundle, val at: Long = SystemClock.elapsedRealtime())

    private val latest = ConcurrentHashMap<Long, Latest>()

    @VisibleForTesting
    internal fun forgetPosts() = latest.clear()

    // Who a report is about. The schedule can be deleted before the user taps Retry.
    data class Report(val id: Long, val title: String)

    private data class Action(val action: String, val label: String)

    // Everything a notification shows, so an unchanged one is not posted again.
    private data class Content(
        val kind: Kind,
        val channel: String,
        val subject: String,
        val text: String,
        val date: LocalDate? = null,
        val actions: List<Action> = emptyList(),
        val until: LocalDateTime? = null,
        val minutes: Int = 0,
        val changes: Changes = Changes.NONE,
    ) {
        fun title(context: Context): String =
            if (kind == Kind.NOTICE) subject else context.getString(R.string.notification_ended, subject)
    }

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.deleteNotificationChannel("shizuku_reminder")
        val blocked = manager.getNotificationChannelCompat("schedules")?.importance ==
            NotificationManagerCompat.IMPORTANCE_NONE
        manager.deleteNotificationChannel("schedules")
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(
                    SCHEDULES,
                    if (blocked) {
                        NotificationManagerCompat.IMPORTANCE_NONE
                    } else {
                        NotificationManagerCompat.IMPORTANCE_DEFAULT
                    },
                )
                    .setName(context.getString(R.string.channel_schedules))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .build(),
                NotificationChannelCompat.Builder(PROBLEMS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_problems))
                    .build(),
            ),
        )
    }

    fun refresh(context: Context) {
        val app = App.from(context)
        if (!canPost(app)) return
        val manager = NotificationManagerCompat.from(app)
        val now = LocalDateTime.now()
        val wanted = wanted(app, now)
        val active = manager.activeNotifications
        val posted = active.filter { it.tag == TAG }
            .associate { it.id.toLong() to it.notification.extras } +
            latest.filterValues { SystemClock.elapsedRealtime() - it.at < 5_000 }.mapValues { it.value.extras }
        val dismissed = dismissed(app)
        dismissed.edit {
            for (id in dismissed.all.keys) {
                if (dismissed.getString(id, null) != wanted[id.toLong()]?.toString()) remove(id)
            }
        }
        for ((id, content) in wanted) {
            val key = content.toString()
            if (posted[id]?.getString(KEY) == key || dismissed.getString(id.toString(), null) == key) continue
            // A notice that turns into a problem alerts the user. Other updates are silent.
            val alert = content.channel == PROBLEMS && posted[id]?.getString(CHANNEL) != PROBLEMS
            post(app, id, content, now, alert)
        }
        for ((id, extras) in posted) {
            if (id !in wanted && extras.getString(KIND) == Kind.NOTICE.name) {
                manager.cancel(TAG, id.toInt())
                latest[id] = Latest(Bundle())
            }
        }

        val airplane = AirplaneController.airplaneOn(app)
        val wifi = AirplaneController.wifiOn(app)
        for (problem in active.filter { it.tag == PROBLEM_TAG }) {
            val left = Changes.of(problem.notification.extras.getInt(CHANGES))
            if ((!left.airplane || !airplane) && (!left.wifi || wifi)) manager.cancel(PROBLEM_TAG, problem.id)
        }
    }

    // Takes down a schedule's notification as if swiped away, so what it shows now does not come back.
    fun dismiss(context: Context, id: Long) {
        val app = App.from(context)
        if (!canPost(app)) return
        wanted(app, LocalDateTime.now())[id]?.let { dismissed(app, id, it.toString()) }
        NotificationManagerCompat.from(app).cancel(TAG, id.toInt())
        latest[id] = Latest(Bundle())
    }

    private fun wanted(app: App, now: LocalDateTime): Map<Long, Content> {
        val schedules = app.schedules.all
        val missed = AirplaneController.missedSchedule(app, now)?.id
        val format = format(app)
        val problem = accessProblem(app)
        val shown = listOf(SCHEDULES, PROBLEMS).filter { shows(app, it) }
        return schedules.mapNotNull { schedule ->
            Notice.of(schedule, now, schedule.id == missed)
                ?.let { notice(app, schedule, it, now, format, problem) }
                ?.takeIf { it.channel in shown }
                ?.let { schedule.id to it }
        }.toMap()
    }

    fun dismissed(context: Context, id: Long, key: String) {
        dismissed(context).edit { putString(id.toString(), key) }
    }

    fun report(context: Context, schedule: Schedule) = Report(schedule.id, format(context).title(schedule))

    fun ended(context: Context, schedule: Schedule, ending: Ending) = ended(context, report(context, schedule), ending)

    fun ended(context: Context, report: Report, ending: Ending) {
        val app = App.from(context)
        if (!canPost(app)) return
        val failed = ending.failed
        val content = if (!failed.isEmpty) {
            val text = accessProblem(app)?.takeIf { ending.nothingSwitched }
                ?.let { app.getString(R.string.notification_no_action_because, it) }
                ?: app.getString(
                    when {
                        failed.airplane && failed.wifi -> R.string.notification_failed_both
                        failed.airplane -> R.string.notification_failed_airplane
                        else -> R.string.notification_failed_wifi
                    },
                )
            Content(
                Kind.PROBLEM,
                PROBLEMS,
                report.title,
                text,
                actions = listOf(Action(RETRY, app.getString(R.string.notification_retry))),
                changes = failed,
            )
        } else {
            val airplane = when {
                ending.missedStart -> R.string.notification_missed_start
                ending.airplaneWasOn -> R.string.notification_airplane_was_on
                ending.airplaneTurnedOff -> R.string.notification_airplane_turned_off
                ending.airplaneStaysOn -> R.string.notification_airplane_stays_on
                else -> R.string.notification_airplane_off
            }
            val wifi = when {
                ending.wifiWasOff -> R.string.notification_wifi_was_off
                ending.wifiBackOn -> R.string.notification_wifi_back_on
                else -> null
            }
            val now = LocalDateTime.now()
            Content(
                Kind.REPORT,
                SCHEDULES,
                report.title,
                if (airplane == R.string.notification_airplane_was_on && wifi == R.string.notification_wifi_was_off) {
                    app.getString(R.string.notification_both_were_set)
                } else {
                    sentences(app, app.getString(airplane), wifi?.let(app::getString))
                },
                until = if (ending.clean) now.plusMinutes(CLEAN_END_SHOWS_MINUTES) else null,
            )
        }
        if (content.kind != Kind.PROBLEM) NotificationManagerCompat.from(app).cancel(PROBLEM_TAG, report.id.toInt())
        if (shows(app, content.channel)) post(app, report.id, content, LocalDateTime.now())
    }

    // The app was not running when a window ended, so what it switched stays switched.
    fun missedEnd(context: Context, schedule: Schedule?, left: Changes) {
        val app = App.from(context)
        if (!canPost(app)) return
        val retry = if (left.airplane) R.string.notification_turn_off else R.string.notification_turn_on_wifi
        val content = Content(
            Kind.PROBLEM,
            PROBLEMS,
            schedule?.let { format(app).title(it) } ?: app.getString(R.string.notification_unknown_schedule),
            app.getString(R.string.notification_missed_end),
            actions = listOf(Action(RETRY, app.getString(retry))),
            changes = left,
        )
        post(app, schedule?.id ?: 0, content, LocalDateTime.now())
    }

    private fun notice(
        app: App,
        schedule: Schedule,
        notice: Notice,
        now: LocalDateTime,
        format: Format,
        problem: String?,
    ): Content {
        val title = format.title(schedule)
        val minutes = app.settings.current.postponeMinutes
        val postpone = ScheduleResolver.postponedTo(schedule, now, minutes) != null
        return when (notice) {
            is Notice.Upcoming -> {
                // A window can be postponed only once, as in the app.
                val postponable = postpone && !notice.postponed
                val time = format.time(notice.start)
                val text = when {
                    problem != null -> app.getString(R.string.notification_could_miss, time, problem)
                    notice.postponed -> app.getString(R.string.notification_postponed_to, time)
                    else -> app.getString(R.string.notification_starts_at, time)
                }
                Content(
                    Kind.NOTICE,
                    if (problem != null) PROBLEMS else SCHEDULES,
                    title,
                    text,
                    notice.date,
                    listOfNotNull(
                        Action(POSTPONE, app.getString(R.string.action_postpone, format.duration(minutes)))
                            .takeIf { postponable },
                        Action(SKIP, app.getString(R.string.action_skip)),
                    ),
                    minutes = minutes,
                )
            }
            is Notice.Paused -> Content(
                Kind.NOTICE,
                SCHEDULES,
                title,
                app.getString(R.string.notification_resumes_at, format.time(notice.until)),
                notice.date,
                listOf(
                    Action(UNDO, app.getString(R.string.action_resume_now)),
                    Action(END, app.getString(R.string.action_end)),
                ),
            )
            is Notice.Active -> if (notice.missed) {
                Content(
                    Kind.NOTICE,
                    PROBLEMS,
                    title,
                    problem?.let { app.getString(R.string.notification_missed_start_because, it) }
                        ?: app.getString(R.string.notification_missed_start_no_action),
                    notice.date,
                    listOfNotNull(
                        Action(TURN_ON, app.getString(R.string.action_turn_on)).takeIf { problem == null },
                        Action(SKIP, app.getString(R.string.action_skip)),
                    ),
                )
            } else {
                Content(
                    Kind.NOTICE,
                    SCHEDULES,
                    title,
                    app.getString(R.string.notification_ends_at, format.time(notice.end)),
                    notice.date,
                    listOfNotNull(
                        Action(POSTPONE, app.getString(R.string.action_pause, format.duration(minutes)))
                            .takeIf { postpone },
                        Action(END, app.getString(R.string.action_end)),
                    ),
                    minutes = minutes,
                )
            }
            is Notice.Skipped -> Content(
                Kind.NOTICE,
                SCHEDULES,
                title,
                format.skippedNotice(schedule, notice.date, now),
                notice.date,
                until = notice.until,
            )
        }
    }

    private fun sentences(context: Context, first: String, second: String?): String =
        second?.let { context.getString(R.string.sentences, first, it) } ?: first

    // Android drops posts to a blocked channel. The notice that a post replaces then stays up.
    private fun shows(context: Context, channel: String): Boolean =
        NotificationManagerCompat.from(context).getNotificationChannelCompat(channel)?.importance !=
            NotificationManagerCompat.IMPORTANCE_NONE

    private fun accessProblem(app: App): String? {
        val access = app.switcher.access()
        return when (access.state) {
            AccessState.READY -> null
            AccessState.OFFLINE -> app.getString(
                if (access.kind == Access.Kind.SHIZUKU && ShizukuStatus.launchIntent(app) == null) {
                    R.string.reason_shizuku_missing
                } else {
                    R.string.reason_shizuku_offline
                },
            )
            AccessState.PERMISSION_NEEDED -> app.getString(
                when (access.kind) {
                    Access.Kind.SHIZUKU -> R.string.reason_shizuku_permission
                    Access.Kind.SUI -> R.string.reason_sui_permission
                    Access.Kind.ROOT -> R.string.reason_root_permission
                },
            )
        }
    }

    private fun canPost(context: Context) =
        notificationsAllowed(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // Callers check canPost.
    private fun post(app: App, id: Long, content: Content, now: LocalDateTime, alert: Boolean = false) {
        val tag = if (content.kind == Kind.PROBLEM) PROBLEM_TAG else TAG
        val key = content.toString()
        val extras = bundleOf(
            KEY to key,
            KIND to content.kind.name,
            CHANNEL to content.channel,
            CHANGES to content.changes.bits,
        )
        val open = Intent(app, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val builder = NotificationCompat.Builder(app, content.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title(app))
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(!alert)
            .setShowWhen(false)
            .setCategory(
                if (content.channel == PROBLEMS) {
                    NotificationCompat.CATEGORY_ERROR
                } else {
                    NotificationCompat.CATEGORY_STATUS
                },
            )
            .addExtras(extras)
        content.until?.let { builder.setTimeoutAfter(maxOf(millis(it) - millis(now), 1)) }
        if (content.kind == Kind.NOTICE) {
            builder.setDeleteIntent(broadcast(app, DISMISSED, id, content) { putExtra(KEY, key) })
        }
        for (action in content.actions) {
            builder.addAction(0, action.label, broadcast(app, action.action, id, content) {})
        }
        NotificationManagerCompat.from(app).notify(tag, id.toInt(), builder.build())
        if (tag == TAG) latest[id] = Latest(extras)
    }

    private fun broadcast(app: App, action: String, id: Long, content: Content, extras: Intent.() -> Unit) =
        PendingIntent.getBroadcast(
            app,
            0,
            Intent(app, NotificationReceiver::class.java)
                .setAction(action)
                .setData("${if (content.kind == Kind.PROBLEM) PROBLEM_TAG else TAG}:$id".toUri())
                .putExtra(ID, id)
                .putExtra(DATE, content.date?.toString())
                .putExtra(MINUTES, content.minutes)
                .putExtra(CHANGES, content.changes.bits)
                .putExtra(TITLE, content.subject)
                .apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun millis(time: LocalDateTime): Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dismissed(context: Context) = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
}
