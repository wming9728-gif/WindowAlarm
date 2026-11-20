package cn.xmeverylucky.pane.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import cn.xmeverylucky.pane.data.AlarmStore
import cn.xmeverylucky.pane.data.AppSettings
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import cn.xmeverylucky.pane.ui.MainActivity
import java.time.Duration
import java.time.Instant

class AlarmScheduler(
    private val context: Context,
    private val store: AlarmStore,
    private val settings: AppSettings,
    private val prefs: SharedPreferences,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun sync(alarm: Alarm, now: Instant = Instant.now()): Instant? {
        cancel(alarm.id)
        if (!alarm.enabled) return null
        val next = NextTriggerCalculator.nextIncludingSnooze(alarm, now, settings.alarmZone())
        if (next == null) {
            DiagLog.log("闹钟#${alarm.id} 没有下一次响铃")
            if (!alarm.isRepeating) {
                store.upsert(alarm.copy(enabled = false, snoozeAt = null, dismissedUntil = null))
            }
            return null
        }
        set(alarm.id, next)
        return next
    }

    fun rescheduleAll(reason: String) {
        val now = Instant.now()
        if (isFirstRunSinceBoot()) {
            DiagLog.log("本次开机后首次运行（开机次数 ${bootCount()}，已开机 ${SystemClock.elapsedRealtime() / 1000} 秒）")
            recoverMissed(now)
        }
        val alarms = store.alarms.value
        DiagLog.log("重排全部闹钟（$reason）：共 ${alarms.size} 个，已启用 ${alarms.count { it.enabled }} 个")
        alarms.forEach { sync(it, now) }
        DiagLog.log(DiagLog.nextAlarmClock(alarmManager))
    }

    fun cancel(id: Long) {
        alarmManager.cancel(firePendingIntent(id))
        prefs.edit().remove(keyScheduled(id)).apply()
    }

    private fun recoverMissed(now: Instant) {
        store.alarms.value.forEach { alarm ->
            val missed = scheduledAt(alarm.id)?.takeIf { alarm.enabled && !it.isAfter(now) } ?: return@forEach
            DiagLog.log("闹钟#${alarm.id} 在关机期间错过了 ${DiagLog.time(missed)}")
            if (Duration.between(missed, now) <= LATE_RING_GRACE) {
                store.upsert(alarm.copy(snoozeAt = now.plusSeconds(3).toEpochMilli()))
            } else {
                MissedAlarmNotifier.notify(context, alarm, missed, NextTriggerCalculator.zoneOf(alarm, settings.alarmZone()))
            }
        }
    }

    private fun isFirstRunSinceBoot(): Boolean {
        val boot = bootCount()
        val last = prefs.getInt(KEY_BOOT_COUNT, Int.MIN_VALUE)
        if (boot == last) return false
        prefs.edit()
            .putInt(KEY_BOOT_COUNT, boot)
            .putBoolean(KEY_RAN_IN_EARLIER_BOOT, last != Int.MIN_VALUE)
            .apply()
        return true
    }

    fun markBootBroadcastReceived() {
        prefs.edit().putInt(KEY_BOOT_RECEIVED, bootCount()).apply()
    }

    fun bootBroadcastBlocked(): Boolean =
        prefs.getBoolean(KEY_RAN_IN_EARLIER_BOOT, false) &&
            prefs.getInt(KEY_BOOT_RECEIVED, Int.MIN_VALUE) != bootCount() &&
            SystemClock.elapsedRealtime() > BOOT_BROADCAST_DELAY.toMillis()

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun scheduledAt(id: Long): Instant? =
        prefs.getLong(keyScheduled(id), -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    private fun keyScheduled(id: Long) = "scheduled_$id"

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun set(id: Long, at: Instant) {
        prefs.edit().putLong(keyScheduled(id), at.toEpochMilli()).apply()
        val fire = firePendingIntent(id)
        DiagLog.log("闹钟#$id 已排到 ${DiagLog.time(at)}${if (canScheduleExact()) "" else "（非精确）"}")
        if (canScheduleExact()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), fire)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), fire)
        }
    }

    private fun firePendingIntent(id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val KEY_BOOT_COUNT = "boot_count"
        const val KEY_BOOT_RECEIVED = "boot_broadcast_received"
        const val KEY_RAN_IN_EARLIER_BOOT = "ran_in_earlier_boot"
        val BOOT_BROADCAST_DELAY: Duration = Duration.ofMinutes(3)
        val LATE_RING_GRACE: Duration = Duration.ofMinutes(30)
    }
}
