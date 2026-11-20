package cn.xmeverylucky.pane.scheduler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.R
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.ui.MainActivity
import cn.xmeverylucky.pane.ui.formatInstant
import java.time.Instant
import java.time.ZoneId

/** 关机期间错过、开机时已来不及补响的闹钟：发一条通知告知。 */
object MissedAlarmNotifier {

    private const val NOTIFICATION_ID_BASE = 1000

    fun notify(context: Context, alarm: Alarm, missedAt: Instant, zone: ZoneId) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, App.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("错过的闹钟：${alarm.label.ifBlank { "闹钟" }}")
            .setContentText("手机关机期间错过了 ${formatInstant(missedAt, zone)} 的响铃")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID_BASE + alarm.id.toInt(), notification)
        }
    }
}
