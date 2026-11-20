package cn.xmeverylucky.pane.data

import android.content.Context

object BuiltinRingtones {

    data class Tone(
        val resName: String,
        val title: String,
        val description: String,
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
