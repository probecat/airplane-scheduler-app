package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

fun friday(hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(2026, 10, 9, hour, minute)

val thursdayNight: LocalDateTime = friday(23).minusDays(1)

val night = Schedule(id = 1, start = at(23), end = at(7))
