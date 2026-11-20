package cn.xmeverylucky.pane.data

import android.content.Context

/**
 * App 内置铃声（res/raw/ring_*.wav）：
 * - 星光：tools/make_celestial.py 用两段 Pixabay 素材混音（Pixabay Content License，免费使用、无需署名）
 * - 雨后：tools/gen_ringtones.py 合成
 * 闹钟里存的是按名字引用的 android.resource URI，重新编译后资源 id 变了也不影响；
 * 资源在 APK 里，重启后未解锁也能播放。
 */
object BuiltinRingtones {

    data class Tone(
        val resName: String,
        val title: String,
        val description: String,
        /** >0 时响铃音量在这么多毫秒内从小逐渐增大（由 RingService 控制，循环也不会掉回小声） */
        val crescendoMs: Long = 0,
    )

    val all = listOf(
        Tone("ring_celestial", "星光", "清澈钟声 + 轻柔风铃，前 5 秒渐强 · 适合起床", crescendoMs = 5_000),
        Tone("ring_after_rain", "雨后", "木琴 + 轻雨声 · 不想被突然惊醒"),
    )

    fun uri(context: Context, tone: Tone): String = "android.resource://${context.packageName}/raw/${tone.resName}"

    fun find(uri: String?): Tone? =
        uri?.takeIf { it.startsWith("android.resource://") }
            ?.substringAfterLast("/raw/", "")
            ?.let { name -> all.firstOrNull { it.resName == name } }
}
