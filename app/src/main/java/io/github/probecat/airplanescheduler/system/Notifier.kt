package io.github.probecat.airplanescheduler.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Ending
import io.github.probecat.airplanescheduler.core.Notice
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.mock.Mock
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.MainActivity
import io.github.probecat.airplanescheduler.ui.format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

// One notification per schedule, replaced as its window comes up, runs and ends. Notices before and
// during a window follow the schedules and are worked out again on every change. Reports of how a
// window ended are posted once and stay until dismissed, or until the schedule's next notice. A
// report of what an end left switched has a place of its own, and goes once nothing is left.
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
    // just replaced. For a few seconds, what this process last did with a notification wins over the list;
    // a cancel leaves an empty bundle.
    private class Latest(val extras: Bundle, val at: Long = SystemClock.elapsedRealtime())

    private val latest = ConcurrentHashMap<Long, Latest>()

    // Who a report is about. The schedule may be gone by the time it's retried.
    data class Report(val id: Long, val title: String)

    private data class Action(val action: String, val label: String)

    // Everything a notification shows, so an unchanged one isn't posted again.
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
        val now = Mock.now()
        val wanted = wanted(app, now)
        val posted = manager.activeNotifications.filter { it.tag == TAG }
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
            // A notice that turns into a problem alerts, though updates otherwise don't.
            val alert = content.channel == PROBLEMS && posted[id]?.getString(CHANNEL) != PROBLEMS
            post(app, id, content, now, alert)
        }
        for ((id, extras) in posted) {
            if (id !in wanted && extras.getString(KIND) == Kind.NOTICE.name) {
                manager.cancel(TAG, id.toInt())
                latest[id] = Latest(Bundle())
            }
        }
        // Mock data doesn't switch anything, so its problems stay until dismissed.
        if (Mock.active) return
        val airplane = AirplaneController.airplaneOn(app)
        val wifi = AirplaneController.wifiOn(app)
        for (problem in manager.activeNotifications.filter { it.tag == PROBLEM_TAG }) {
            val left = Changes.of(problem.notification.extras.getInt(CHANGES))
            if ((!left.airplane || !airplane) && (!left.wifi || wifi)) manager.cancel(PROBLEM_TAG, problem.id)
        }
    }

    // Takes down a schedule's notification as if swiped away, so what it shows now doesn't come back.
    fun dismiss(context: Context, id: Long) {
        val app = App.from(context)
        if (!canPost(app)) return
        wanted(app, Mock.now())[id]?.let { dismissed(app, id, it.toString()) }
        NotificationManagerCompat.from(app).cancel(TAG, id.toInt())
        latest[id] = Latest(Bundle())
    }

    private fun wanted(app: App, now: LocalDateTime): Map<Long, Content> {
        val schedules = app.schedules.all
        // Mock data reads airplane mode as off, so only the started window decides.
        val airplane = AirplaneController.airplaneOn(app) && !Mock.active
        val started = app.settings.started
        // Not while the start is still switching, nor before its alarm had a chance to.
        val missed = Boundary.missed(schedules, now, started, airplane)
            ?.takeIf {
                !AirplaneController.switching && (started?.window == it.start || now >= it.start.plusMinutes(1))
            }
            ?.let { ScheduleResolver.activeSchedule(schedules, now)?.id }
        val format = format(app)
        return schedules.mapNotNull { schedule ->
            Notice.of(schedule, schedules, now, schedule.id == missed)
                ?.let { notice(app, schedule, it, now, format) }
                ?.takeIf { shows(app, it.channel) }
                ?.let { schedule.id to it }
        }.toMap()
    }

    // Notices come back after mock data turns on or off, and reports of the other data go.
    fun reset(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        for (id in manager.activeNotifications.filter { it.tag == TAG }.map { it.id.toLong() } + latest.keys) {
            latest[id] = Latest(Bundle())
        }
        manager.cancelAll()
        refresh(context)
    }

    // Posts every notice again, even swiped away ones, for the mock screen. False when there are none.
    fun repost(context: Context): Boolean {
        val app = App.from(context)
        if (!canPost(app)) return false
        dismissed(app).edit { clear() }
        val manager = NotificationManagerCompat.from(app)
        for (id in manager.activeNotifications.filter { it.tag == TAG }.map { it.id.toLong() } + latest.keys) {
            manager.cancel(TAG, id.toInt())
            latest[id] = Latest(Bundle())
        }
        refresh(app)
        return wanted(app, Mock.now()).isNotEmpty()
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
            val what = when {
                failed.airplane && failed.wifi -> R.string.notification_failed_both
                failed.airplane -> R.string.notification_failed_airplane
                else -> R.string.notification_failed_wifi
            }
            Content(
                Kind.PROBLEM,
                PROBLEMS,
                report.title,
                sentences(app, app.getString(what), shizukuProblem(app)),
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
                ending.wifiKeptOff -> R.string.notification_wifi_kept_off
                ending.wifiBackOn -> R.string.notification_wifi_back_on
                else -> null
            }
            val now = Mock.now()
            Content(
                Kind.REPORT,
                SCHEDULES,
                report.title,
                // One sentence for both, rather than two that repeat each other.
                if (airplane == R.string.notification_airplane_was_on && wifi == R.string.notification_wifi_was_off) {
                    app.getString(R.string.notification_both_were_set)
                } else {
                    sentences(app, app.getString(airplane), wifi?.let(app::getString))
                },
                until = if (ending.clean) now.plusMinutes(CLEAN_END_SHOWS_MINUTES) else null,
            )
        }
        if (content.kind != Kind.PROBLEM) NotificationManagerCompat.from(app).cancel(PROBLEM_TAG, report.id.toInt())
        if (shows(app, content.channel)) post(app, report.id, content, Mock.now())
    }

    // The app wasn't running when a window ended, so what it switched stays switched.
    fun missedEnd(context: Context, schedule: Schedule?, left: Changes) {
        val app = App.from(context)
        if (!canPost(app)) return
        val text = when {
            left.airplane && left.wifi -> R.string.notification_missed_end_both
            left.airplane -> R.string.notification_missed_end_airplane
            else -> R.string.notification_missed_end_wifi
        }
        val retry = if (left.airplane) R.string.notification_turn_off else R.string.notification_turn_on_wifi
        val content = Content(
            Kind.PROBLEM,
            PROBLEMS,
            schedule?.let { format(app).title(it) } ?: app.getString(R.string.notification_unknown_schedule),
            app.getString(text),
            actions = listOf(Action(RETRY, app.getString(retry))),
            changes = left,
        )
        post(app, schedule?.id ?: 0, content, Mock.now())
    }

    private fun notice(app: App, schedule: Schedule, notice: Notice, now: LocalDateTime, format: Format): Content {
        val title = format.title(schedule)
        val minutes = app.settings.current.postponeMinutes
        val postpone = minutes > 0 && ScheduleResolver.postponedTo(schedule, now, minutes) != null
        return when (notice) {
            is Notice.Upcoming -> {
                val problem = shizukuProblem(app)
                val start = when {
                    notice.after != null -> app.getString(
                        R.string.notification_continues_from,
                        format.title(notice.after),
                        format.time(notice.start),
                    )
                    notice.postponed -> format.delayed(notice.start)
                    else -> app.getString(R.string.starts_at, format.time(notice.start))
                }
                // Leaving out a window that continues another one ends the other one at its end.
                val breaks = notice.after?.let {
                    app.getString(
                        if (postpone) {
                            R.string.notification_skip_or_postpone_breaks
                        } else {
                            R.string.notification_skip_breaks
                        },
                        format.label(it, now.toLocalDate()),
                    )
                }
                Content(
                    Kind.NOTICE,
                    if (problem != null) PROBLEMS else SCHEDULES,
                    title,
                    sentences(app, start, breaks, problem, phrase = true),
                    notice.date,
                    listOfNotNull(
                        Action(SKIP, app.getString(R.string.action_skip)),
                        Action(POSTPONE, app.getString(R.string.action_postpone))
                            .takeIf { postpone },
                        Action(UNDO, app.getString(R.string.action_undo)).takeIf { notice.postponed },
                    ),
                    minutes = minutes,
                )
            }
            is Notice.Paused -> Content(
                Kind.NOTICE,
                SCHEDULES,
                title,
                format.delayed(notice.until),
                notice.date,
                listOf(
                    Action(UNDO, app.getString(R.string.action_resume)),
                    Action(END, app.getString(R.string.action_end)),
                ),
            )
            is Notice.Active -> if (notice.missed) {
                val problem = shizukuProblem(app)
                Content(
                    Kind.NOTICE,
                    PROBLEMS,
                    title,
                    sentences(app, app.getString(R.string.notification_missed_start), problem),
                    notice.date,
                    listOfNotNull(
                        Action(TURN_ON, app.getString(R.string.action_turn_on)).takeIf { problem == null },
                        Action(END, app.getString(R.string.action_end)),
                    ),
                )
            } else {
                Content(
                    Kind.NOTICE,
                    SCHEDULES,
                    title,
                    notice.next?.let {
                        app.getString(R.string.notification_continues_as, format.title(it), format.time(notice.end))
                    } ?: app.getString(R.string.notification_ends_at, format.time(notice.end)),
                    notice.date,
                    listOfNotNull(
                        Action(POSTPONE, app.getString(R.string.action_pause))
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
                format.skipped(schedule, notice.date, now),
                notice.date,
                listOf(Action(UNDO, app.getString(R.string.action_undo))),
                until = notice.until,
            )
        }
    }

    // Whole sentences in a row. A notice's first line is a phrase with no period, unless more follow.
    private fun sentences(context: Context, first: String, vararg more: String?, phrase: Boolean = false): String {
        val rest = more.filterNotNull()
        if (rest.isEmpty()) return first
        val lead = if (phrase) context.getString(R.string.sentence, first) else first
        return rest.fold(lead) { text, next -> context.getString(R.string.sentences, text, next) }
    }

    // Android drops posts to a blocked channel, which would leave up the notice they replace.
    private fun shows(context: Context, channel: String): Boolean =
        NotificationManagerCompat.from(context).getNotificationChannelCompat(channel)?.importance !=
            NotificationManagerCompat.IMPORTANCE_NONE

    private fun shizukuProblem(context: Context): String? = when (ShizukuStatus.current()) {
        ShizukuState.READY -> null
        ShizukuState.OFFLINE -> context.getString(
            if (ShizukuStatus.launchIntent(context) == null) {
                R.string.notification_shizuku_missing
            } else {
                R.string.notification_shizuku_offline
            },
        )
        ShizukuState.PERMISSION_NEEDED -> context.getString(R.string.notification_shizuku_permission)
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

    // Mock times are moved back to the real clock that notifications count with.
    private fun millis(time: LocalDateTime): Long =
        Mock.real(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dismissed(context: Context) =
        context.getSharedPreferences(if (Mock.active) "mock_notifications" else "notifications", Context.MODE_PRIVATE)
}
