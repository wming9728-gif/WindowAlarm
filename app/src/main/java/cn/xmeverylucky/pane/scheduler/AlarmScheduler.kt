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

/**
 * 每个闹钟在 AlarmManager 里只挂「下一次」；响完后由 [AlarmReceiver] 再挂下一次。
 * 用 setAlarmClock：精确触发、不受 Doze 限制，状态栏会显示闹钟图标。
 *
 * 关机/重启会清空 AlarmManager，所以另外记下每个闹钟挂上的时刻（[prefs]，设备加密存储），
 * 开机后据此判断哪些闹钟在关机期间错过了。
 */
class AlarmScheduler(
    private val context: Context,
    private val store: AlarmStore,
    private val settings: AppSettings,
    private val prefs: SharedPreferences,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 重新计算并挂上（或取消）这个闹钟，返回挂上的时刻。 */
    fun sync(alarm: Alarm, now: Instant = Instant.now()): Instant? {
        cancel(alarm.id)
        if (!alarm.enabled) return null
        val next = NextTriggerCalculator.nextIncludingSnooze(alarm, now, settings.alarmZone())
        if (next == null) {
            DiagLog.log("闹钟#${alarm.id} 没有下一次响铃")
            // 单次闹钟已经响完：自动关闭并清掉临时状态
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

    /**
     * 第三方 App 无法像部分厂商的系统闹钟那样让关机的手机自动开机响铃。退而求其次：
     * 开机后刚错过不久的闹钟立刻补响一次，错过太久的发一条通知告诉用户。
     * 补响借用贪睡字段交给 AlarmManager 触发——开机广播里不允许直接启动媒体播放前台服务。
     */
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

    /** 用系统的开机次数判断：这是不是本次开机后第一次运行（无论是被开机广播还是被用户打开唤起）。 */
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

    /** 收到开机广播时调用，用来发现开机广播被系统拦截的情况。 */
    fun markBootBroadcastReceived() {
        prefs.edit().putInt(KEY_BOOT_RECEIVED, bootCount()).apply()
    }

    /**
     * 本次开机系统没有把开机广播发给我们——多见于厂商系统的「自启动」管理默认禁止第三方 App。
     * 这种情况下重启后闹钟不会恢复，要等用户打开 App 才重新挂上。
     * 开机头几分钟广播可能还没到，先不下结论。
     */
    fun bootBroadcastBlocked(): Boolean =
        prefs.getBoolean(KEY_RAN_IN_EARLIER_BOOT, false) &&
            prefs.getInt(KEY_BOOT_RECEIVED, Int.MIN_VALUE) != bootCount() &&
            SystemClock.elapsedRealtime() > BOOT_BROADCAST_DELAY.toMillis()

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** 这个闹钟当前挂在 AlarmManager 上的时刻 */
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
            // Android 12/12L 未授予精确闹钟权限时退化为非精确，UI 会提示用户去授权
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
        /** 开机时距离应响时刻不超过这么久就补响，更久的只提示 */
        val LATE_RING_GRACE: Duration = Duration.ofMinutes(30)
    }
}
