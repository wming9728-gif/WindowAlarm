package cn.xmeverylucky.pane.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

object NextTriggerCalculator {

    private const val MAX_DAYS_AHEAD = 400L

    fun next(alarm: Alarm, now: Instant, defaultZone: ZoneId): Instant? =
        upcoming(alarm, now, defaultZone, 1).firstOrNull()

    fun upcoming(alarm: Alarm, now: Instant, defaultZone: ZoneId, limit: Int): List<Instant> {
        val zone = zoneOf(alarm, defaultZone)
        val dismissed = alarm.dismissedUntil?.let(Instant::ofEpochMilli)
        val today = now.atZone(zone).toLocalDate()
        return candidateDates(alarm, today.minusDays(1))
            .flatMap { occurrence(alarm, it, zone).asSequence() }
            .filter { it.isAfter(now) && (dismissed == null || it.isAfter(dismissed)) }
            .take(limit)
            .toList()
    }

    fun nextOccurrenceDate(alarm: Alarm, now: Instant, defaultZone: ZoneId): LocalDate? {
        val zone = zoneOf(alarm, defaultZone)
        val dismissed = alarm.dismissedUntil?.let(Instant::ofEpochMilli)
        val today = now.atZone(zone).toLocalDate()
        return candidateDates(alarm, today.minusDays(1))
            .firstOrNull { date ->
                occurrence(alarm, date, zone).any { it.isAfter(now) && (dismissed == null || it.isAfter(dismissed)) }
            }
    }

    fun nextIncludingSnooze(alarm: Alarm, now: Instant, defaultZone: ZoneId): Instant? {
        val snooze = alarm.snoozeAt?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now) }
        val regular = next(alarm, now, defaultZone)
        return listOfNotNull(snooze, regular).minOrNull()
    }

    fun currentWindowEnd(alarm: Alarm, at: Instant, defaultZone: ZoneId): Instant? {
        val zone = zoneOf(alarm, defaultZone)
        val day = at.atZone(zone).toLocalDate()
        return listOf(day.minusDays(1), day)
            .filter { matchesDate(alarm, it) }
            .map { occurrence(alarm, it, zone) }
            .firstOrNull { it.isNotEmpty() && !at.isBefore(it.first().minusSeconds(60)) && !at.isAfter(it.last()) }
            ?.last()
    }

    fun firstOneShotDate(alarm: Alarm, now: Instant, defaultZone: ZoneId): LocalDate {
        val zone = zoneOf(alarm, defaultZone)
        val today = now.atZone(zone).toLocalDate()
        return if (occurrence(alarm, today, zone).any { it.isAfter(now) }) today else today.plusDays(1)
    }

    fun zoneOf(alarm: Alarm, defaultZone: ZoneId): ZoneId =
        alarm.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone

    internal fun occurrence(alarm: Alarm, date: LocalDate, zone: ZoneId): List<Instant> =
        localPoints(alarm, date)
            .map { ZonedDateTime.of(it, zone).toInstant() }
            .distinct()
            .sorted()

    internal fun localPoints(alarm: Alarm, date: LocalDate): List<LocalDateTime> {
        val start = date.atTime(alarm.windowStart)
        val end = windowEnd(alarm, start)
        return when (alarm.mode) {
            AlarmMode.FIXED -> listOf(start)
            AlarmMode.REPEAT_IN_WINDOW -> {
                val step = alarm.intervalMinutes.coerceAtLeast(1).toLong()
                generateSequence(start) { it.plusMinutes(step) }
                    .takeWhile { !it.isAfter(end) }
                    .toList()
            }
            AlarmMode.RANDOM_IN_WINDOW -> {
                val span = Duration.between(start, end).toMinutes()
                val random = Random(alarm.id * 31 + date.toEpochDay())
                listOf(start.plusMinutes(random.nextLong(span + 1)))
            }
        }
    }

    private fun windowEnd(alarm: Alarm, start: LocalDateTime): LocalDateTime {
        val endTime = alarm.windowEnd ?: return start
        val end = start.toLocalDate().atTime(endTime)
        return if (end.isBefore(start)) end.plusDays(1) else end
    }

    private fun candidateDates(alarm: Alarm, from: LocalDate): Sequence<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .take(MAX_DAYS_AHEAD.toInt())
            .filter { matchesDate(alarm, it) }

    private fun matchesDate(alarm: Alarm, date: LocalDate): Boolean {
        if (date in alarm.skipDates) return false
        return if (alarm.isRepeating) alarm.repeatsOn(date.dayOfWeek) else date == alarm.oneShotDate
    }
}
