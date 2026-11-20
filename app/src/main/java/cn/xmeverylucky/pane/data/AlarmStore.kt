package cn.xmeverylucky.pane.data

import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.HabitType
import cn.xmeverylucky.pane.domain.ReminderStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * 闹钟数量很少，直接整体存成一个 JSON 文件；写入先写临时文件再改名，避免写一半断电。
 * 进程内单例，Activity、Receiver、Service 共享同一份 [alarms]。
 */
class AlarmStore(private val file: File) {

    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun get(id: Long): Alarm? = _alarms.value.firstOrNull { it.id == id }

    /** 新闹钟（id == 0）会分配 id；返回保存后的闹钟。 */
    @Synchronized
    fun upsert(alarm: Alarm): Alarm {
        val list = _alarms.value
        val saved = if (alarm.id == 0L) alarm.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        val updated = if (list.any { it.id == saved.id }) {
            list.map { if (it.id == saved.id) saved else it }
        } else {
            list + saved
        }
        persist(updated)
        return saved
    }

    @Synchronized
    fun update(id: Long, transform: (Alarm) -> Alarm): Alarm? {
        val current = get(id) ?: return null
        return upsert(transform(current))
    }

    @Synchronized
    fun delete(id: Long) {
        persist(_alarms.value.filterNot { it.id == id })
    }

    private fun persist(list: List<Alarm>) {
        val json = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
        _alarms.value = list
    }

    private fun load(): List<Alarm> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { array.getJSONObject(it).toAlarm() }
    }.getOrElse { emptyList() }
}

private fun Alarm.toJson() = JSONObject().apply {
    put("id", id)
    put("label", label)
    put("enabled", enabled)
    putOpt("zoneId", zoneId)
    put("mode", mode.name)
    put("windowStart", windowStart.toString())
    putOpt("windowEnd", windowEnd?.toString())
    put("intervalMinutes", intervalMinutes)
    put("weekdays", weekdays)
    putOpt("oneShotDate", oneShotDate?.toString())
    put("skipDates", JSONArray(skipDates.sorted().map { it.toString() }))
    putOpt("ringtoneUri", ringtoneUri)
    put("vibrate", vibrate)
    put("snoozeMinutes", snoozeMinutes)
    putOpt("dismissedUntil", dismissedUntil)
    putOpt("snoozeAt", snoozeAt)
    put("reminderStyle", reminderStyle.name)
    put("habitType", habitType.name)
}

private fun JSONObject.toAlarm(): Alarm {
    val skip = optJSONArray("skipDates")
    return Alarm(
        id = getLong("id"),
        label = optString("label"),
        enabled = optBoolean("enabled", true),
        zoneId = optStringOrNull("zoneId"),
        mode = runCatching { AlarmMode.valueOf(getString("mode")) }.getOrDefault(AlarmMode.FIXED),
        windowStart = LocalTime.parse(getString("windowStart")),
        windowEnd = optStringOrNull("windowEnd")?.let(LocalTime::parse),
        intervalMinutes = optInt("intervalMinutes", 5),
        weekdays = optInt("weekdays"),
        oneShotDate = optStringOrNull("oneShotDate")?.let(LocalDate::parse),
        skipDates = if (skip == null) emptySet() else (0 until skip.length()).map { LocalDate.parse(skip.getString(it)) }.toSet(),
        ringtoneUri = optStringOrNull("ringtoneUri"),
        vibrate = optBoolean("vibrate", true),
        snoozeMinutes = optInt("snoozeMinutes", 5),
        dismissedUntil = if (has("dismissedUntil")) getLong("dismissedUntil") else null,
        snoozeAt = if (has("snoozeAt")) getLong("snoozeAt") else null,
        reminderStyle = optStringOrNull("reminderStyle")
            ?.let { runCatching { ReminderStyle.valueOf(it) }.getOrNull() }
            ?: ReminderStyle.FULL_ALARM,
        habitType = optStringOrNull("habitType")
            ?.let { runCatching { HabitType.valueOf(it) }.getOrNull() }
            ?: HabitType.NONE,
    )
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
