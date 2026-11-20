package cn.xmeverylucky.pane.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class AlarmMode {
    /** 在 windowStart 准时响一次 */
    FIXED,

    /** 从 windowStart 到 windowEnd，每 intervalMinutes 分钟响一次 */
    REPEAT_IN_WINDOW,

    /** 在 windowStart..windowEnd 之间随机挑一个分钟响 */
    RANDOM_IN_WINDOW,
}

enum class ReminderStyle {
    /** 全屏界面 + 持续响铃（传统闹钟） */
    FULL_ALARM,

    /** 轻提醒：横幅通知 + 短提示音/短震动，不打断当前工作 */
    GENTLE,
}

enum class HabitType {
    NONE,
    WATER,
    STAND,
}

data class Alarm(
    val id: Long = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** null 表示跟随 App 设置的闹钟时区（不是系统时区） */
    val zoneId: String? = null,
    val mode: AlarmMode = AlarmMode.FIXED,
    val windowStart: LocalTime = LocalTime.of(7, 0),
    /** 结束时间早于开始时间时视为跨午夜（次日结束）；与开始相同则只响一次 */
    val windowEnd: LocalTime? = null,
    val intervalMinutes: Int = 5,
    /** 星期位掩码：bit0 = 周一 … bit6 = 周日；0 表示不按星期重复 */
    val weekdays: Int = 0,
    /** weekdays == 0 时只在这一天响 */
    val oneShotDate: LocalDate? = null,
    val skipDates: Set<LocalDate> = emptySet(),
    /** null 表示系统默认（全屏用闹铃，轻提醒用通知音）；"silent" 表示静音 */
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** 早于等于此时刻（epoch 毫秒）的响铃点全部跳过，用于「结束今天这一轮」 */
    val dismissedUntil: Long? = null,
    /** 贪睡后的下一次响铃（epoch 毫秒） */
    val snoozeAt: Long? = null,
    val reminderStyle: ReminderStyle = ReminderStyle.FULL_ALARM,
    val habitType: HabitType = HabitType.NONE,
) {
    val isRepeating: Boolean get() = weekdays != 0

    val hasWindow: Boolean get() = mode != AlarmMode.FIXED && windowEnd != null

    fun repeatsOn(day: DayOfWeek): Boolean = weekdays and dayBit(day) != 0

    companion object {
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        // 只是按位组合的快捷选项，不代表任何地区的工作周定义
        const val MON_TO_FRI = 0b0011111
        const val SAT_SUN = 0b1100000
        const val EVERYDAY = 0b1111111
    }
}
