package cn.xmeverylucky.pane.ring

import android.media.MediaPlayer
import android.os.Handler
import android.os.SystemClock

private const val CRESCENDO_FROM = 0.12f

fun startCrescendo(mp: MediaPlayer, durationMs: Long, handler: Handler, isActive: () -> Boolean): Runnable {
    runCatching { mp.setVolume(CRESCENDO_FROM, CRESCENDO_FROM) }
    val start = SystemClock.elapsedRealtime()
    return object : Runnable {
        override fun run() {
            if (!isActive()) return
            val p = ((SystemClock.elapsedRealtime() - start).toFloat() / durationMs).coerceAtMost(1f)
            val v = CRESCENDO_FROM + (1f - CRESCENDO_FROM) * p * p
            runCatching { mp.setVolume(v, v) }
            if (p < 1f) handler.postDelayed(this, 100)
        }
    }.also { handler.post(it) }
}
