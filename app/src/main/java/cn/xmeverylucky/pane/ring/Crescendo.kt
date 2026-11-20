package cn.xmeverylucky.pane.ring

import android.media.MediaPlayer
import android.os.Handler
import android.os.SystemClock

private const val CRESCENDO_FROM = 0.12f

/**
 * 渐强：音量从很小开始，[durationMs] 内按平方曲线（开头升得慢，听感柔和）升到最大。
 * 响铃和试听共用，保证试听听到的就是真正响铃时的效果。
 *
 * 必须在 mp.start() **之前**调用：这里先同步把音量压到最小，否则开头几毫秒会以满音量冒出来，
 * 第一声听着特别响。之后的调节在 handler 上进行；[isActive] 返回 false 时停止
 * （播放器已停止或换了铃声）。返回的 Runnable 可交给 handler 取消。
 */
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
