package cn.xmeverylucky.pane.zone

import android.app.NotificationManager
import android.content.Context

object ZoneStatusNotifier {

    private const val LEGACY_NOTIFICATION_ID = 2
    private const val LEGACY_CHANNEL_ZONE = "zone_status"

    fun clearLegacy(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(LEGACY_NOTIFICATION_ID) }
        runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ZONE) }
    }
}
