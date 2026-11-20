package cn.xmeverylucky.pane.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * 根据闹钟规则计算响铃时刻。纯 Kotlin，不依赖 Android，便于单元测试。
 *
 * 所有日期/时刻都在闹钟自己的时区（未指定时用 App 设置的闹钟时区）中解释，
 * 最后转成绝对时刻 [Instant] 交给 AlarmManager。
 */
object NextTriggerCalculator {

    private const val MAX_DAYS_AHEAD = 400L

    /** 严格晚于 [now] 的下一次响铃时刻；没有则返回 null（例如单次闹钟已过）。 */
    fun next(alarm: Alarm, now: Instant, defaultZone: ZoneId): Instant? =
        upcoming(alarm, now, defaultZone, 1).firstOrNull()

    /** 接下来 [limit] 次响铃时刻（不含贪睡），用于编辑页预览。 */
    fun upcoming(alarm: Alarm, now: Instant, defaultZone: ZoneId, limit: Int): List<Instant> {
        val zone = zoneOf(alarm, defaultZone)
        val dismissed = alarm.dismissedUntil?.let(Instant::ofEpochMilli)
        val today = now.atZone(zone).toLocalDate()
        // 从昨天开始扫描：跨午夜的窗口可能在昨天开始、今天才结束
        return candidateDates(alarm, today.minusDays(1))
            .flatMap { occurrence(alarm, it, zone).asSequence() }
            .filter { it.isAfter(now) && (dismissed == null || it.isAfter(dismissed)) }
            .take(limit)
            .toList()
    }

    /** 下一次响铃对应的规则日期（可用于加入 skipDates 跳过这一轮，含跨午夜正在进行的窗口）。 */
    fun nextOccurrenceDate(alarm: Alarm, now: Instant, defaultZone: ZoneId): LocalDate? {
        val zone = zoneOf(alarm, defaultZone)
        val dismissed = alarm.dismissedUntil?.let(Instant::ofEpochMilli)
        val today = now.atZone(zone).toLocalDate()
        return candidateDates(alarm, today.minusDays(1))
            .firstOrNull { date ->
                occurrence(alarm, date, zone).any { it.isAfter(now) && (dismissed == null || it.isAfter(dismissed)) }
            }
    }

    /** 下一次真正要挂到 AlarmManager 的时刻：规则响铃与贪睡取较早者。 */
    fun nextIncludingSnooze(alarm: Alarm, now: Instant, defaultZone: ZoneId): Instant? {
        val snooze = alarm.snoozeAt?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now) }
        val regular = next(alarm, now, defaultZone)
        return listOfNotNull(snooze, regular).minOrNull()
    }

    /**
     * 包含 [at] 的那一轮窗口的最后一个响铃点。用于「结束今天这一轮」：
     * 把 dismissedUntil 设为它，本轮剩余的重复响铃就都被跳过。
     */
    fun currentWindowEnd(alarm: Alarm, at: Instant, defaultZone: ZoneId): Instant? {
        val zone = zoneOf(alarm, defaultZone)
        val day = at.atZone(zone).toLocalDate()
        return listOf(day.minusDays(1), day)
            .filter { matchesDate(alarm, it) }
            .map { occurrence(alarm, it, zone) }
            .firstOrNull { it.isNotEmpty() && !at.isBefore(it.first().minusSeconds(60)) && !at.isAfter(it.last()) }
            ?.last()
    }

    /** 单次闹钟未指定日期时，选出第一个还会响的日期（今天来得及就是今天，否则明天）。 */
    fun firstOneShotDate(alarm: Alarm, now: Instant, defaultZone: ZoneId): LocalDate {
        val zone = zoneOf(alarm, defaultZone)
        val today = now.atZone(zone).toLocalDate()
        return if (occurrence(alarm, today, zone).any { it.isAfter(now) }) today else today.plusDays(1)
    }

    fun zoneOf(alarm: Alarm, defaultZone: ZoneId): ZoneId =
        alarm.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone

    /** 某个日期这一轮的全部响铃点（按时间升序，已去重）。 */
    internal fun occurrence(alarm: Alarm, date: LocalDate, zone: ZoneId): List<Instant> =
        localPoints(alarm, date)
            // 夏令时缺口里的时刻会被顺延，重叠时取较早的偏移
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
                // 以 (id, 日期) 作种子：同一天无论重排多少次都选出同一个时刻
                val random = Random(alarm.id * 31 + date.toEpochDay())
                listOf(start.plusMinutes(random.nextLong(span + 1)))
            }
        }
    }

    private fun windowEnd(alarm: Alarm, start: LocalDateTime): LocalDateTime {
        val endTime = alarm.windowEnd ?: return start
        val end = start.toLocalDate().atTime(endTime)
        // 结束早于开始才算跨午夜；相等视为零长度时段（只响开始那一次），不能当成 24 小时
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
