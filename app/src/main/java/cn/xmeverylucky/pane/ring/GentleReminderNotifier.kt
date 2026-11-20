package cn.xmeverylucky.pane.ring

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.R
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.HabitType
import cn.xmeverylucky.pane.ui.MainActivity
import cn.xmeverylucky.pane.ui.describeWindow
import java.time.LocalDate

/**
 * 轻提醒通知器：不弹出全屏页面、不拉起常驻前台服务，
 * 只发送横幅通知并附带短提示音或短震动，尽量不打断当前工作。
 */
object GentleReminderNotifier {

    const val SILENT_URI = "silent"
    private const val NOTIFICATION_BASE_ID = 2000
    private const val MAX_SOUND_MS = 2_500L

    fun notificationId(alarmId: Long): Int = NOTIFICATION_BASE_ID + alarmId.toInt()

    fun cancel(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(alarmId))
    }

    fun notify(context: Context, alarm: Alarm, pendingResult: BroadcastReceiver.PendingResult? = null) {
        val app = App.of(context)
        val today = LocalDate.now(app.settings.alarmZone())
        val counts = app.habitStore.countsOn(today)

        val title = alarm.label.ifBlank {
            when (alarm.habitType) {
                HabitType.WATER -> "💧 该喝水了"
                HabitType.STAND -> "🧍 该站起来活动了"
                HabitType.NONE -> "轻提醒"
            }
        }
        val text = when (alarm.habitType) {
            HabitType.WATER -> "今日已喝水 ${counts.water} 次 · 喝一口再点下方打卡"
            HabitType.STAND -> "今日已起身活动 ${counts.stand} 次 · 站起来伸展一下"
            HabitType.NONE -> describeWindow(alarm)
        }

        val openApp = PendingIntent.getActivity(
            context,
            notificationId(alarm.id),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, App.CHANNEL_GENTLE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp)

        if (alarm.habitType != HabitType.NONE) {
            val checkInText = if (alarm.habitType == HabitType.WATER) "已喝水 (+1)" else "已活动 (+1)"
            builder.addAction(
                0,
                checkInText,
                HabitActionReceiver.actionIntent(context, HabitActionReceiver.ACTION_CHECK_IN, alarm),
            )
        }
        builder.addAction(
            0,
            "${alarm.snoozeMinutes}分钟后再提醒",
            HabitActionReceiver.actionIntent(context, HabitActionReceiver.ACTION_SNOOZE, alarm),
        )
        if (alarm.mode == AlarmMode.REPEAT_IN_WINDOW) {
            builder.addAction(
                0,
                "结束本轮",
                HabitActionReceiver.actionIntent(context, HabitActionReceiver.ACTION_END_WINDOW, alarm),
            )
        }

        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(notificationId(alarm.id), builder.build())
        }.onFailure { DiagLog.error("闹钟#${alarm.id} 发送轻提醒通知失败", it) }
        DiagLog.log("闹钟#${alarm.id} 已发送轻提醒通知（类型=${alarm.habitType}）")

        if (alarm.vibrate) {
            vibrateOnce(context)
        }
        playShortSound(context, alarm, pendingResult)
    }

    private fun vibrateOnce(context: Context) {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 160, 100, 160), -1))
        }
    }

    private fun playShortSound(
        context: Context,
        alarm: Alarm,
        pendingResult: BroadcastReceiver.PendingResult?,
    ) {
        if (alarm.ringtoneUri == SILENT_URI) {
            pendingResult?.finish()
            return
        }
        val uri = alarm.ringtoneUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (uri == null) {
            pendingResult?.finish()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val mp = MediaPlayer()
        var finished = false
        fun cleanup() {
            if (finished) return
            finished = true
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
            pendingResult?.finish()
        }

        val timeout = Runnable { cleanup() }
        val started = runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(context, uri)
            mp.isLooping = false
            mp.setOnCompletionListener {
                handler.removeCallbacks(timeout)
                cleanup()
            }
            mp.setOnErrorListener { _, _, _ ->
                handler.removeCallbacks(timeout)
                cleanup()
                true
            }
            mp.prepare()
            mp.start()
            handler.postDelayed(timeout, MAX_SOUND_MS)
        }.isSuccess

        if (!started) {
            handler.removeCallbacks(timeout)
            cleanup()
        }
    }
}
