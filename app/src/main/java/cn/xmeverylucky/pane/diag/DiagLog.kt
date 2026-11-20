package cn.xmeverylucky.pane.diag

import android.app.AlarmManager
import cn.xmeverylucky.pane.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DiagLog {

    private const val FILE_NAME = "diag.log"
    private const val MAX_BYTES = 64 * 1024
    private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS xxx")
    private var file: File? = null

    fun init(dir: File) {
        if (!BuildConfig.DEBUG) return
        file = File(dir, FILE_NAME)
    }

    fun purge(dir: File) {
        file = null
        runCatching { File(dir, FILE_NAME).delete() }
    }

    @Synchronized
    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                val text = f.readText()
                f.writeText(text.substring(text.length / 2).substringAfter('\n'))
            }
            f.appendText("${time(Instant.now())}  $message\n")
        }
    }

    fun error(message: String, t: Throwable) {
        if (!BuildConfig.DEBUG) return
        val trace = t.stackTraceToString().lineSequence().take(12).joinToString("\n    ")
        log("$message\n    $trace")
    }

    @Synchronized
    fun read(): String {
        if (!BuildConfig.DEBUG) return ""
        return file?.takeIf { it.exists() }?.runCatching { readText() }?.getOrNull().orEmpty()
    }

    @Synchronized
    fun clear() {
        file?.delete()
    }

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = instant.atZone(zone).format(TIME)

    fun nextAlarmClock(alarmManager: AlarmManager): String {
        if (!BuildConfig.DEBUG) return ""
        val next = alarmManager.nextAlarmClock ?: return "系统中没有待响的闹钟"
        val owner = next.showIntent?.creatorPackage ?: "未知"
        return "系统下一个闹钟 ${time(Instant.ofEpochMilli(next.triggerTime))}，来自 $owner"
    }
}
