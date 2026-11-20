package cn.xmeverylucky.pane.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * 用户自选的音乐铃声：复制一份到设备加密存储里再用。
 * 原文件在共享存储里，重启后未解锁时读不到，原文件被删/移动也会失效；复制后都不受影响。
 */
object RingtoneFiles {

    private const val DIR = "ringtones"
    private const val MAX_BYTES = 30L * 1024 * 1024

    private fun dir(context: Context) =
        File(context.createDeviceProtectedStorageContext().filesDir, DIR).apply { mkdirs() }

    /** 复制 [source] 进来并确认能播放，成功返回可存进闹钟的 file:// URI；失败返回原因。 */
    fun import(context: Context, source: Uri): Result<Uri> = runCatching {
        val (name, size) = queryNameAndSize(context, source)
        require(size == null || size <= MAX_BYTES) { "文件太大（超过 30MB）" }
        // 时间戳前缀保证不重名，显示时去掉
        val target = File(dir(context), "${System.currentTimeMillis()}_${name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}")
        val input = context.contentResolver.openInputStream(source) ?: error("无法读取这个文件")
        input.use { inp -> target.outputStream().use { inp.copyTo(it) } }
        val problem = when {
            target.length() > MAX_BYTES -> "文件太大（超过 30MB）"
            !isPlayable(target) -> "这个文件无法播放"
            else -> null
        }
        if (problem != null) {
            target.delete()
            error(problem)
        }
        Uri.fromFile(target)
    }

    /** 自选铃声显示的名字；不是自选铃声返回 null */
    fun displayName(context: Context, uri: String): String? {
        val path = Uri.parse(uri).takeIf { it.scheme == "file" }?.path ?: return null
        val file = File(path)
        if (file.parentFile != dir(context)) return null
        return file.name.substringAfter('_').substringBeforeLast('.')
    }

    /** 删掉没有闹钟在用的自选铃声文件 */
    fun prune(context: Context, used: Collection<String?>) {
        val keep = used.mapNotNull { uri -> uri?.let { Uri.parse(it).path } }.toSet()
        dir(context).listFiles()?.filter { it.path !in keep }?.forEach { it.delete() }
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long?> {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: "铃声"
                    val size = if (c.isNull(1)) null else c.getLong(1)
                    return name to size
                }
            }
        return "铃声" to null
    }

    private fun isPlayable(file: File): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(file.path)
            mp.prepare()
            true
        } catch (e: Exception) {
            false
        } finally {
            mp.release()
        }
    }
}
