package cn.xmeverylucky.pane.ring

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.HabitType
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import java.time.Instant
import java.time.LocalDate

/** 处理轻提醒通知栏上的快捷按钮：打卡(+1)、稍后提醒、结束本轮。 */
class HabitActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val app = App.of(context)
        when (intent.action) {
            ACTION_CHECK_IN -> {
                val typeName = intent.getStringExtra(EXTRA_HABIT_TYPE)
                val type = typeName?.let { runCatching { HabitType.valueOf(it) }.getOrNull() } ?: HabitType.NONE
                if (type != HabitType.NONE) {
                    val today = LocalDate.now(app.settings.alarmZone())
                    val updated = app.habitStore.add(type, today, 1)
                    DiagLog.log("轻提醒#$alarmId 打卡成功：类型=$type，今日喝水=${updated.water}，今日起身=${updated.stand}")
                }
                GentleReminderNotifier.cancel(context, alarmId)
            }
            ACTION_SNOOZE -> {
                app.store.update(alarmId) {
                    it.copy(enabled = true, snoozeAt = Instant.now().plusSeconds(it.snoozeMinutes * 60L).toEpochMilli())
                }?.let { app.scheduler.sync(it) }
                GentleReminderNotifier.cancel(context, alarmId)
            }
            ACTION_END_WINDOW -> {
                val now = Instant.now()
                app.store.update(alarmId) { alarm ->
                    val end = NextTriggerCalculator.currentWindowEnd(alarm, now, app.settings.alarmZone())
                    alarm.copy(dismissedUntil = (end ?: now).toEpochMilli(), snoozeAt = null)
                }?.let { app.scheduler.sync(it) }
                GentleReminderNotifier.cancel(context, alarmId)
            }
        }
    }

    companion object {
        const val ACTION_CHECK_IN = "cn.xmeverylucky.pane.HABIT_CHECK_IN"
        const val ACTION_SNOOZE = "cn.xmeverylucky.pane.HABIT_SNOOZE"
        const val ACTION_END_WINDOW = "cn.xmeverylucky.pane.HABIT_END_WINDOW"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_HABIT_TYPE = "habit_type"

        fun actionIntent(context: Context, action: String, alarm: Alarm): PendingIntent {
            val intent = Intent(context, HabitActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ALARM_ID, alarm.id)
                .putExtra(EXTRA_HABIT_TYPE, alarm.habitType.name)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode() * 31 + alarm.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
