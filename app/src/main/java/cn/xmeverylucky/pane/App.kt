package cn.xmeverylucky.pane

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.UserManager
import cn.xmeverylucky.pane.data.AlarmStore
import cn.xmeverylucky.pane.data.AppSettings
import cn.xmeverylucky.pane.data.HabitStore
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.scheduler.AlarmScheduler
import cn.xmeverylucky.pane.zone.ZoneStatusNotifier
import java.io.File

class App : Application() {

    lateinit var store: AlarmStore
        private set
    lateinit var habitStore: HabitStore
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var scheduler: AlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        val storage = createDeviceProtectedStorageContext()
        if (BuildConfig.DEBUG) {
            DiagLog.init(storage.filesDir)
            installCrashLogger()
            DiagLog.log("进程启动 已解锁=${getSystemService(UserManager::class.java).isUserUnlocked}")
        } else {
            DiagLog.purge(storage.filesDir)
        }
        migrateToDeviceStorage(storage)
        store = AlarmStore(File(storage.filesDir, ALARMS_FILE))
        habitStore = HabitStore(File(storage.filesDir, HABITS_FILE))
        settings = AppSettings(storage)
        scheduler = AlarmScheduler(this, store, settings, storage.getSharedPreferences(SCHEDULER_PREFS, MODE_PRIVATE))
        createChannels()
        ZoneStatusNotifier.clearLegacy(this)
        if (settings.privacyAccepted.value || store.alarms.value.isNotEmpty()) {
            scheduler.rescheduleAll("进程启动")
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            DiagLog.error("崩溃（线程 ${thread.name}）", e)
            previous?.uncaughtException(thread, e)
        }
    }

    fun changeZone(change: AppSettings.() -> Unit) {
        settings.change()
        scheduler.rescheduleAll("修改时区")
    }

    private fun migrateToDeviceStorage(storage: Context) {
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return
        val old = File(filesDir, ALARMS_FILE)
        val new = File(storage.filesDir, ALARMS_FILE)
        if (old.exists() && !new.exists()) {
            old.copyTo(new)
            old.delete()
            DiagLog.log("已把闹钟数据迁移到设备加密存储")
        }
        if (File(dataDir, "shared_prefs/${AppSettings.PREFS_NAME}.xml").exists()) {
            storage.moveSharedPreferencesFrom(this, AppSettings.PREFS_NAME)
        }
    }

    private fun createChannels() {
        val ring = NotificationChannel(CHANNEL_RING, "闹钟响铃", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "闹钟响铃时的全屏提醒"
            setSound(null, null)
            enableVibration(false)
        }
        val gentle = NotificationChannel(CHANNEL_GENTLE, "轻提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "喝水、起身活动等轻量横幅提醒"
            setSound(null, null)
            enableVibration(false)
        }
        val missed = NotificationChannel(CHANNEL_MISSED, "错过的闹钟", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "手机关机期间错过的闹钟，开机后提示"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(ring, gentle, missed))
    }

    companion object {
        const val CHANNEL_RING = "ring"
        const val CHANNEL_GENTLE = "gentle"
        const val CHANNEL_MISSED = "missed"
        private const val ALARMS_FILE = "alarms.json"
        private const val HABITS_FILE = "habits.json"
        private const val SCHEDULER_PREFS = "scheduler"

        fun of(context: Context): App = context.applicationContext as App
    }
}
