package cn.xmeverylucky.pane.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.diag.DiagLog

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ALLOWED_ACTIONS) return
        val app = App.of(context)
        DiagLog.log("收到系统事件 $action")
        if (action in BOOT_ACTIONS) app.scheduler.markBootBroadcastReceived()
        app.scheduler.rescheduleAll(action.substringAfterLast('.'))
    }

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
        val ALLOWED_ACTIONS = BOOT_ACTIONS + setOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
