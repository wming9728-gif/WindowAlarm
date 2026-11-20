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
        // 首次安装且用户尚未同意隐私政策时，不提前调用系统排程或通知服务；已有老用户升级仍正常恢复闹钟
        if (settings.privacyAccepted.value || store.alarms.value.isNotEmpty()) {
            // 进程被杀/更新/开机后重新挂一遍，保证 AlarmManager 与数据一致；开机后第一次还会处理关机期间错过的闹钟
            scheduler.rescheduleAll("进程启动")
        }
    }

    /** 未捕获的异常写进诊断记录，再交给系统默认处理（仅 Debug 生效） */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            DiagLog.error("崩溃（线程 ${thread.name}）", e)
            previous?.uncaughtException(thread, e)
        }
    }

    /** 修改 App 时区设置后：重排所有闹钟。 */
    fun changeZone(change: AppSettings.() -> Unit) {
        settings.change()
        scheduler.rescheduleAll("修改时区")
    }

    /**
     * 数据放在设备加密存储：重启后还没解锁也能读写，开机时才能及时把闹钟挂回去（系统闹钟也是这么做的）。
     * 旧版本数据在凭据加密存储里，解锁状态下首次启动时搬过来。
     */
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
            setSound(null, null) // 声音由 RingService 用闹钟音频流自己播放
            enableVibration(false)
        }
        val gentle = NotificationChannel(CHANNEL_GENTLE, "轻提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "喝水、起身活动等轻量横幅提醒"
            setSound(null, null) // 短提示音和短震动由 GentleReminderNotifier 精确控制
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
