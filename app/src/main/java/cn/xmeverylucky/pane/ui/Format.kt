package cn.xmeverylucky.pane.ui

import android.icu.text.TimeZoneNames
import android.icu.util.ULocale
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone

/** App 界面语言（简体中文，不含地区）。不读取系统 Locale，避免内容随系统地区设置变化。 */
private val UI_LOCALE: Locale = Locale.forLanguageTag("zh-Hans")

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val MD_HM = DateTimeFormatter.ofPattern("M月d日 E HH:mm", UI_LOCALE)
private val MD_E = DateTimeFormatter.ofPattern("M月d日 E", UI_LOCALE)

fun LocalTime.hm(): String = format(HM)

fun LocalDate.mdE(): String = format(MD_E)

/** 「07:00–07:30 每 5 分钟」这类时段描述。 */
fun describeWindow(alarm: Alarm): String {
    val end = alarm.windowEnd
    return when {
        alarm.mode == AlarmMode.FIXED || end == null -> "${alarm.windowStart.hm()} 准时响"
        alarm.mode == AlarmMode.REPEAT_IN_WINDOW ->
            "${alarm.windowStart.hm()}–${end.hm()}${crossMidnight(alarm)} 每 ${alarm.intervalMinutes} 分钟"
        else -> "${alarm.windowStart.hm()}–${end.hm()}${crossMidnight(alarm)} 随机一次"
    }
}

private fun crossMidnight(alarm: Alarm): String {
    val end = alarm.windowEnd ?: return ""
    return if (end.isBefore(alarm.windowStart)) "（次日）" else ""
}

fun describeDays(alarm: Alarm): String = when {
    !alarm.isRepeating -> alarm.oneShotDate?.let { "仅 ${it.monthValue}月${it.dayOfMonth}日" } ?: "仅一次"
    alarm.weekdays == Alarm.EVERYDAY -> "每天"
    alarm.weekdays == Alarm.MON_TO_FRI -> "周一至周五"
    alarm.weekdays == Alarm.SAT_SUN -> "周六、周日"
    else -> DayOfWeek.entries.filter { alarm.repeatsOn(it) }.joinToString(" ") { it.shortCn() }
}

fun DayOfWeek.shortCn(): String = getDisplayName(TextStyle.NARROW, UI_LOCALE).let { "周$it" }

/** 例如「日本标准时间 · UTC+09:00」 */
fun zoneLabel(zone: ZoneId, at: Instant = Instant.now()): String {
    val name = TimeZone.getTimeZone(zone).getDisplayName(
        zone.rules.isDaylightSavings(at), TimeZone.LONG, UI_LOCALE,
    )
    return "$name · ${utcOffset(zone, at)}"
}

/** 时区的代表地名，如「东京」「洛杉矶」；取不到时用 ID 末段 */
fun placeName(zone: ZoneId): String =
    runCatching { TimeZoneNames.getInstance(ULocale.forLocale(UI_LOCALE)).getExemplarLocationName(zone.id) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: zone.id.substringAfterLast('/').replace('_', ' ')

fun utcOffset(zone: ZoneId, at: Instant = Instant.now()): String {
    val offset = zone.rules.getOffset(at)
    return if (offset.totalSeconds == 0) "UTC" else "UTC${offset.id}"
}

/** 「UTC+08」「UTC-07」这类短写法；不是整点的偏移保留分钟，如「UTC+05:30」 */
fun shortUtcOffset(zone: ZoneId, at: Instant = Instant.now()): String = utcOffset(zone, at).removeSuffix(":00")

fun formatInstant(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(MD_HM)

fun formatRemaining(from: Instant, to: Instant): String {
    val d = Duration.between(from, to)
    val days = d.toDays()
    val hours = d.toHours() % 24
    val minutes = (d.toMinutes() % 60).let { if (d.seconds % 60 > 0) it + 1 else it }
    return when {
        days > 0 -> "${days}天${hours}小时后"
        hours > 0 -> "${hours}小时${minutes}分钟后"
        minutes > 0 -> "${minutes}分钟后"
        else -> "即将响铃"
    }
}
