package cn.xmeverylucky.pane.zone

import android.app.NotificationManager
import android.content.Context

/**
 * 清理旧版本遗留的「时区状态」通知栏常驻通知与对应通知渠道。
 * 当前版本仅在 App 首页内展示非本地时区提醒卡片，不再占用系统通知栏。
 */
object ZoneStatusNotifier {

    private const val LEGACY_NOTIFICATION_ID = 2
    private const val LEGACY_CHANNEL_ZONE = "zone_status"

    fun clearLegacy(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(LEGACY_NOTIFICATION_ID) }
        runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ZONE) }
    }
}
