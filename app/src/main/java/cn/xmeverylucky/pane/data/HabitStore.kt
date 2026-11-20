package cn.xmeverylucky.pane.data

import cn.xmeverylucky.pane.domain.HabitType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class HabitStore(private val file: File) {

    data class DailyCounts(
        val water: Int = 0,
        val stand: Int = 0,
    )

    private val _records = MutableStateFlow(load())
    val records: StateFlow<Map<LocalDate, DailyCounts>> = _records.asStateFlow()

    fun countsOn(date: LocalDate): DailyCounts = _records.value[date] ?: DailyCounts()

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
