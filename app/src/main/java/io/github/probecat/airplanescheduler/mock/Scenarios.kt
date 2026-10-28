package io.github.probecat.airplanescheduler.mock

import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.ui.AppViewModel
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

enum class Destination { SCHEDULES, SETTINGS, HELP, DEBUG, MOCK }

// A state the real app can reach, set up in one go. Display options such as theme and text size are
// left alone so they can be combined with any scenario.
class Scenario(
    val id: String,
    val title: String,
    val destination: Destination = Destination.SCHEDULES,
    val day: DayOfWeek = WEDNESDAY,
    val minute: Int = 15 * 60,
    val shizuku: ShizukuState = ShizukuState.READY,
    val shizukuInstalled: Boolean = true,
    val sui: Boolean = false,
    val notifications: Boolean = true,
    val batteryOptimized: Boolean = false,
    val hideShizukuReady: Boolean = true,
    // The app couldn't turn airplane mode on at the running window's start.
    val missedStart: Boolean = false,
    val schedules: (today: LocalDate) -> List<Schedule> = { emptyList() },
    val drafts: Map<Long, (Schedule) -> Schedule> = emptyMap(),
) {
    // [day] in the current week, so the mock clock stays within a few days of the real one.
    fun now(real: LocalDateTime): LocalDateTime =
        real.toLocalDate().with(TemporalAdjusters.previousOrSame(MONDAY)).plusDays(day.ordinal.toLong())
            .atTime(minute / 60, minute % 60)

    fun apply(app: App, viewModel: AppViewModel) {
        val now = now(LocalDateTime.now())
        Mock.update(app) {
            it.copy(
                enabled = true,
                scenario = id,
                shizuku = shizuku,
                shizukuInstalled = shizukuInstalled,
                sui = sui,
                notifications = notifications,
                batteryOptimized = batteryOptimized,
                clockOffset = Mock.offsetTo(now),
            )
        }
        val schedules = schedules(now.toLocalDate())
        app.schedules.replaceAll(schedules)
        app.settings.update { it.copy(hideShizukuReady = hideShizukuReady) }
        app.settings.started = ScheduleResolver.window(schedules, now)?.let {
            val wanted = Changes(airplane = true, wifi = it.disableWifi)
            Boundary.Started(it.start, if (missedStart) Changes.NONE else wanted, wanted)
        }
        viewModel.clearDrafts()
        for ((id, edit) in drafts) {
            schedules.find { it.id == id }?.let { viewModel.keepDraft(it, edit(it)) }
        }
    }
}

private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

private val NIGHTS = setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)

private val WEEKDAYS = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

private fun everyday(today: LocalDate) = listOf(
    Schedule(id = 1, name = "Sleep", start = at(22, 30), end = at(6, 30), days = NIGHTS),
    Schedule(id = 2, name = "Weekend", start = at(23, 30), end = at(9), days = setOf(FRIDAY, SATURDAY)),
    Schedule(
        id = 3,
        name = "Flight",
        start = at(13),
        end = at(15, 45),
        days = emptySet(),
        date = today.plusDays(1),
        disableWifi = false,
        enableWifi = false,
    ),
    Schedule(id = 4, start = at(13), end = at(14), days = setOf(SATURDAY, SUNDAY), enabled = false),
)

// At 21:45 on a Wednesday: one running, one starting soon, one skipped and one far off.
private fun skips(today: LocalDate) = listOf(
    Schedule(id = 1, name = "Reading", start = at(20), end = at(22)),
    Schedule(
        id = 2,
        name = "Sleep, with the phone charging in another room",
        start = at(22, 30),
        end = at(6, 30),
        days = NIGHTS,
    ),
    Schedule(id = 3, name = "Gym", start = at(7, 45), end = at(9), days = WEEKDAYS, skipped = setOf(today.plusDays(1))),
    Schedule(id = 4, name = "Flight", start = at(13), end = at(15, 45), days = emptySet(), date = today.plusDays(1)),
)

private val NAMES = listOf("Gym", "", "Focus", "Reading", "", "Meeting", "Lunch")

val scenarios = listOf(
    Scenario(
        id = "empty",
        title = "No schedules",
        hideShizukuReady = false,
    ),
    Scenario(
        id = "everyday",
        title = "Everyday use",
        schedules = ::everyday,
    ),
    Scenario(
        id = "active",
        title = "Currently active",
        minute = at(23, 15),
        schedules = ::everyday,
    ),
    Scenario(
        id = "once",
        title = "One-off schedules",
        minute = at(1),
        schedules = { today ->
            listOf(
                Schedule(id = 1, start = at(23), end = at(3), days = emptySet(), date = today.minusDays(1)),
                Schedule(id = 2, name = "Dentist", start = at(18), end = at(19), days = emptySet(), date = today),
                Schedule(id = 3, start = at(9), end = at(10, 30), days = emptySet(), date = today.plusDays(1)),
                Schedule(id = 4, name = "Concert", start = at(20), end = at(23), days = emptySet(), enabled = false),
            )
        },
    ),
    Scenario(
        id = "crowded",
        title = "Many schedules",
        schedules = {
            List(12) { i ->
                Schedule(
                    id = i + 1L,
                    name = NAMES[i % NAMES.size],
                    start = at(8 + i),
                    end = at(9 + i),
                    days = setOf(DayOfWeek.of(i % 7 + 1)),
                    enabled = i % 4 != 3,
                )
            }
        },
    ),
    Scenario(
        id = "long",
        title = "Long text",
        schedules = { today ->
            listOf(
                Schedule(
                    id = 1,
                    name = "Quiet hours while the baby naps upstairs",
                    start = at(12, 45),
                    end = at(10, 55),
                    days = setOf(MONDAY, WEDNESDAY, FRIDAY, SUNDAY),
                ),
                Schedule(
                    id = 2,
                    name = "Red-eye flight home from a long weekend",
                    start = at(11),
                    end = at(12),
                    days = emptySet(),
                    date = today.plusDays(1),
                ),
                Schedule(
                    id = 3,
                    start = at(10, 10),
                    end = at(11, 50),
                    days = setOf(MONDAY, TUESDAY, THURSDAY, FRIDAY, SATURDAY),
                    enabled = false,
                ),
            )
        },
    ),
    Scenario(
        id = "skip",
        title = "Skip and end",
        minute = at(21, 45),
        schedules = ::skips,
    ),
    Scenario(
        id = "ended",
        title = "Ended",
        minute = at(23, 15),
        schedules = { today ->
            skips(today).map { if (it.id == 2L) it.copy(skipped = setOf(today), endedEarly = setOf(today)) else it }
        },
    ),
    Scenario(
        id = "postponed",
        title = "Postponed",
        minute = at(22, 15),
        schedules = { today ->
            skips(today).map { if (it.id == 2L) it.copy(delayed = mapOf(today to 30)) else it }
        },
    ),
    Scenario(
        id = "paused",
        title = "Paused",
        minute = at(23, 15),
        schedules = { today ->
            skips(today).map { if (it.id == 2L) it.copy(delayed = mapOf(today to 75)) else it }
        },
    ),
    Scenario(
        id = "missed-start",
        title = "Missed start",
        minute = at(21, 45),
        shizuku = ShizukuState.OFFLINE,
        missedStart = true,
        schedules = ::skips,
    ),
    Scenario(
        id = "overlaps",
        title = "Overlaps and sheet errors",
        schedules = {
            listOf(
                Schedule(id = 1, name = "Night", start = at(22), end = at(6)),
                Schedule(id = 2, name = "Late show", start = at(23), end = at(1), enabled = false),
                Schedule(id = 3, name = "Early start", start = at(12), end = at(13), days = setOf(MONDAY)),
                Schedule(id = 4, name = "Same times", start = at(14), end = at(15), days = setOf(TUESDAY)),
            )
        },
        drafts = mapOf(
            3L to { it.copy(start = at(5)) },
            4L to { it.copy(end = it.start) },
        ),
    ),
    Scenario(
        id = "offline",
        title = "Shizuku offline",
        shizuku = ShizukuState.OFFLINE,
        schedules = ::everyday,
    ),
    Scenario(
        id = "no-access",
        title = "Shizuku access denied",
        shizuku = ShizukuState.PERMISSION_NEEDED,
        schedules = ::everyday,
    ),
    Scenario(
        id = "no-shizuku-app",
        title = "Shizuku not installed",
        shizuku = ShizukuState.OFFLINE,
        shizukuInstalled = false,
        schedules = ::everyday,
    ),
    Scenario(
        id = "sui",
        title = "Sui instead of Shizuku",
        shizukuInstalled = false,
        sui = true,
        hideShizukuReady = false,
        schedules = ::everyday,
    ),
    Scenario(
        id = "sui-no-access",
        title = "Sui access denied",
        shizuku = ShizukuState.PERMISSION_NEEDED,
        shizukuInstalled = false,
        sui = true,
        schedules = ::everyday,
    ),
    Scenario(
        id = "missing-permissions",
        title = "Missing permissions",
        destination = Destination.SETTINGS,
        shizuku = ShizukuState.PERMISSION_NEEDED,
        notifications = false,
        schedules = ::everyday,
    ),
    Scenario(
        id = "battery-optimized",
        title = "Battery optimization",
        batteryOptimized = true,
        schedules = ::everyday,
    ),
)
