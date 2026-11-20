package cn.xmeverylucky.pane.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.domain.ReminderStyle
import cn.xmeverylucky.pane.ring.GentleReminderNotifier
import cn.xmeverylucky.pane.ring.RingService
import java.time.Duration
import java.time.Instant

/** AlarmManager 到点后的入口：开始响铃，并立刻挂上这个闹钟的下一次。 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val app = App.of(context)
        val alarm = app.store.get(id)
        DiagLog.log("闹钟#$id 触发：${if (alarm == null) "找不到这个闹钟" else if (alarm.enabled) "开始响铃" else "已关闭，忽略"}，${deviceState(context, app.scheduler.scheduledAt(id))}")
        if (alarm == null || !alarm.enabled) return

        var pendingResult: PendingResult? = null
        try {
            if (alarm.reminderStyle == ReminderStyle.GENTLE) {
                pendingResult = goAsync()
                GentleReminderNotifier.notify(context, alarm, pendingResult)
            } else {
                RingService.start(context, id)
            }
        } catch (e: RuntimeException) {
            pendingResult?.finish()
            DiagLog.error("闹钟#$id 启动提醒失败", e)
            throw e
        }

        val now = Instant.now()
        // 贪睡已触发：清除；之后按规则计算下一次（单次闹钟会在 sync 里自动关闭）
        val updated = if (alarm.snoozeAt != null && alarm.snoozeAt <= now.toEpochMilli() + 1000) {
            app.store.upsert(alarm.copy(snoozeAt = null))
        } else {
            alarm
        }
        app.scheduler.sync(updated, now)
    }

    /** 记下比预定晚了多久、触发时手机的状态，用来排查厂商系统推迟闹钟的问题 */
    private fun deviceState(context: Context, scheduled: Instant?): String {
        val power = context.getSystemService(PowerManager::class.java)
        val late = scheduled?.let { "比预定晚 ${Duration.between(it, Instant.now()).toMillis() / 1000.0} 秒" } ?: "预定时刻未知"
        return "$late，屏幕亮=${power.isInteractive}，锁屏=${context.getSystemService(KeyguardManager::class.java).isKeyguardLocked}，" +
            "Doze=${power.isDeviceIdleMode}，省电模式=${power.isPowerSaveMode}"
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
