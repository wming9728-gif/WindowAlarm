package cn.xmeverylucky.pane.diag

import android.app.AlarmManager
import cn.xmeverylucky.pane.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 仅 Debug 调试包启用的本地诊断记录；正式包（Release）完全不写入、不读取且会清理旧残留文件。
 */
object DiagLog {

    private const val FILE_NAME = "diag.log"
    private const val MAX_BYTES = 64 * 1024
    private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS xxx")
    private var file: File? = null

    fun init(dir: File) {
        if (!BuildConfig.DEBUG) return
        file = File(dir, FILE_NAME)
    }

    /** 正式包启动时清理旧版本可能残留的诊断日志文件。 */
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
                // 超过上限时只保留后一半
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

    /** 带 UTC 偏移的设备时间，避免看记录时搞混时区 */
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = instant.atZone(zone).format(TIME)

    /** 系统里「下一个闹钟」是谁设的：所有 App 的 setAlarmClock 中最早的那个 */
    fun nextAlarmClock(alarmManager: AlarmManager): String {
        if (!BuildConfig.DEBUG) return ""
        val next = alarmManager.nextAlarmClock ?: return "系统中没有待响的闹钟"
        val owner = next.showIntent?.creatorPackage ?: "未知"
        return "系统下一个闹钟 ${time(Instant.ofEpochMilli(next.triggerTime))}，来自 $owner"
    }
}
