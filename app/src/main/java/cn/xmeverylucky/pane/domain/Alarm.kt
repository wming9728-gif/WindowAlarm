package cn.xmeverylucky.pane.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class AlarmMode {
    FIXED,
    REPEAT_IN_WINDOW,
    RANDOM_IN_WINDOW,
}

enum class ReminderStyle {
    FULL_ALARM,
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
    val zoneId: String? = null,
    val mode: AlarmMode = AlarmMode.FIXED,
    val windowStart: LocalTime = LocalTime.of(7, 0),
    val windowEnd: LocalTime? = null,
    val intervalMinutes: Int = 5,
    val weekdays: Int = 0,
    val oneShotDate: LocalDate? = null,
    val skipDates: Set<LocalDate> = emptySet(),
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val dismissedUntil: Long? = null,
    val snoozeAt: Long? = null,
    val reminderStyle: ReminderStyle = ReminderStyle.FULL_ALARM,
    val habitType: HabitType = HabitType.NONE,
) {
    val isRepeating: Boolean get() = weekdays != 0

    val hasWindow: Boolean get() = mode != AlarmMode.FIXED && windowEnd != null

    fun repeatsOn(day: DayOfWeek): Boolean = weekdays and dayBit(day) != 0

    companion object {
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        const val MON_TO_FRI = 0b0011111
        const val SAT_SUN = 0b1100000
        const val EVERYDAY = 0b1111111
    }
}
