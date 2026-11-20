package cn.xmeverylucky.pane.data

import cn.xmeverylucky.pane.domain.HabitType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * 每日健康习惯打卡记录（喝水次数、起身活动次数）。
 * 按日期（YYYY-MM-DD）存储在本地 JSON 文件中，写入方式与 AlarmStore 一致保证原子性。
 */
class HabitStore(private val file: File) {

    data class DailyCounts(
        val water: Int = 0,
        val stand: Int = 0,
    )

    private val _records = MutableStateFlow(load())
    val records: StateFlow<Map<LocalDate, DailyCounts>> = _records.asStateFlow()

    fun countsOn(date: LocalDate): DailyCounts = _records.value[date] ?: DailyCounts()

    /** 增加或减少指定日期的打卡次数（下限为 0），返回更新后的当日统计。 */
    @Synchronized
    fun add(type: HabitType, date: LocalDate, delta: Int = 1): DailyCounts {
        if (type == HabitType.NONE || delta == 0) return countsOn(date)
        val currentMap = _records.value.toMutableMap()
        val prev = currentMap[date] ?: DailyCounts()
        val updated = when (type) {
            HabitType.WATER -> prev.copy(water = (prev.water + delta).coerceAtLeast(0))
            HabitType.STAND -> prev.copy(stand = (prev.stand + delta).coerceAtLeast(0))
            HabitType.NONE -> prev
        }
        if (updated.water == 0 && updated.stand == 0) {
            currentMap.remove(date)
        } else {
            currentMap[date] = updated
        }
        // 只保留最近 90 天的记录，避免文件无限增长
        val cutoff = date.minusDays(90)
        val pruned = currentMap.filterKeys { !it.isBefore(cutoff) }
        persist(pruned)
        return updated
    }

    private fun persist(map: Map<LocalDate, DailyCounts>) {
        val root = JSONObject()
        map.entries.sortedBy { it.key }.forEach { (date, counts) ->
            root.put(
                date.toString(),
                JSONObject().apply {
                    put("water", counts.water)
                    put("stand", counts.stand)
                },
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
        _records.value = map
    }

    private fun load(): Map<LocalDate, DailyCounts> = runCatching {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText())
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
                val obj = root.optJSONObject(key) ?: continue
                put(date, DailyCounts(water = obj.optInt("water", 0), stand = obj.optInt("stand", 0)))
            }
        }
    }.getOrElse { emptyMap() }
}
