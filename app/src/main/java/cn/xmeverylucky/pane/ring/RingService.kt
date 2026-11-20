package cn.xmeverylucky.pane.ring

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.R
import cn.xmeverylucky.pane.data.BuiltinRingtones
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import cn.xmeverylucky.pane.ui.describeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class RingService : Service() {

    private var player: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRinging() }
    private var popupCheck: Runnable? = null
    private var volumeRamp: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ALARM_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING -> ring(id)
            ACTION_SNOOZE -> snooze(id)
            ACTION_END_WINDOW -> endWindow(id)
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Long) {
        val alarm = App.of(this).store.get(id)
        if (alarm == null) {
            startInForeground(buildNotification(Alarm(id = id)))
            return stopRinging()
        }
        startInForeground(buildNotification(alarm))
        DiagLog.log("闹钟#$id 响铃服务已在前台运行，全屏通知权限=${canFullScreen()}，响铃通知重要程度=${ringChannelImportance(this)}")
        _ringing.value = Ringing(alarm.id, Instant.now())
        showRingScreen(alarm.id)

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WindowAlarm:ring")
                .apply { acquire(MAX_RING_MS + 10_000) }
        }
        startSound(alarm)
        if (alarm.vibrate) startVibration() else stopVibration()

        val duration = if (alarm.mode == AlarmMode.REPEAT_IN_WINDOW) {
            (alarm.intervalMinutes * 60_000L - 10_000).coerceIn(20_000, 60_000)
        } else {
            MAX_RING_MS
        }
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, duration)
    }

    private fun snooze(id: Long) {
        val app = App.of(this)
        app.store.update(id) {
            it.copy(enabled = true, snoozeAt = Instant.now().plusSeconds(it.snoozeMinutes * 60L).toEpochMilli())
        }?.let { app.scheduler.sync(it) }
        stopRinging()
    }

    private fun endWindow(id: Long) {
        val app = App.of(this)
        val firedAt = _ringing.value?.takeIf { it.alarmId == id }?.firedAt ?: Instant.now()
        app.store.update(id) { alarm ->
            val end = NextTriggerCalculator.currentWindowEnd(alarm, firedAt, app.settings.alarmZone())
            alarm.copy(dismissedUntil = (end ?: firedAt).toEpochMilli(), snoozeAt = null)
        }?.let { app.scheduler.sync(it) }
        stopRinging()
    }

    private fun startSound(alarm: Alarm) {
        if (player?.isPlaying == true) return
        val uris = listOfNotNull(
            alarm.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        requestAudioFocus(alarmAttrs)
        for (uri in uris) {
            val crescendoMs = BuiltinRingtones.find(uri.toString())?.crescendoMs ?: 0
            val mp = MediaPlayer()
            val ok = runCatching {
                mp.setAudioAttributes(alarmAttrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                if (crescendoMs > 0) volumeRamp = startCrescendo(mp, crescendoMs, handler) { player === mp }
                mp.start()
            }.isSuccess
            if (ok) {
                DiagLog.log("闹钟#${alarm.id} 正在播放铃声 $uri${if (crescendoMs > 0) "（${crescendoMs / 1000} 秒渐强）" else ""}")
                player = mp
                return
            }
            volumeRamp?.let(handler::removeCallbacks)
            DiagLog.log("闹钟#${alarm.id} 铃声无法播放 $uri")
            mp.release()
        }
        abandonAudioFocus()
    }

    private fun requestAudioFocus(attrs: AudioAttributes) {
        if (audioFocusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .build()
        val am = getSystemService(AudioManager::class.java)
        if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = req
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { req ->
            runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(req) }
        }
        audioFocusRequest = null
    }

    private fun showRingScreen(id: Long) {
        if (RingActivity.shown) return
        runCatching {
            startActivity(
                Intent(this, RingActivity::class.java)
                    .putExtra(EXTRA_ALARM_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        }.onFailure { DiagLog.error("闹钟#$id 直接打开响铃界面失败", it) }
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        popupCheck?.let(handler::removeCallbacks)
        popupCheck = Runnable {
            if (_ringing.value?.alarmId != id) return@Runnable
            when {
                RingActivity.shown -> setLockScreenPopupFailed(this, false)
                locked -> {
                    DiagLog.log("闹钟#$id 锁屏响铃时响铃界面没有自动弹出")
                    setLockScreenPopupFailed(this, true)
                }
            }
        }.also { handler.postDelayed(it, POPUP_CHECK_MS) }
    }

    private fun canFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        vibrator = v
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopRinging() {
        handler.removeCallbacks(autoStop)
        popupCheck?.let(handler::removeCallbacks)
        popupCheck = null
        volumeRamp?.let(handler::removeCallbacks)
        volumeRamp = null
        player?.runCatching { stop() }
        player?.release()
        player = null
        abandonAudioFocus()
        stopVibration()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_ringing.value != null) stopRinging()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val open = PendingIntent.getActivity(
            this, alarm.id.toInt(),
            Intent(this, RingActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, App.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.label.ifBlank { "闹钟" })
            .setContentText(describeWindow(alarm))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "贪睡 ${alarm.snoozeMinutes} 分钟", actionIntent(ACTION_SNOOZE, alarm.id))
            .addAction(0, "关闭", actionIntent(ACTION_DISMISS, alarm.id))
        if (alarm.mode == AlarmMode.REPEAT_IN_WINDOW) {
            builder.addAction(0, "结束本轮", actionIntent(ACTION_END_WINDOW, alarm.id))
        }
        return builder.build()
    }

    private fun actionIntent(action: String, id: Long): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode() + id.toInt(),
            Intent(this, RingService::class.java).setAction(action).putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    data class Ringing(val alarmId: Long, val firedAt: Instant)

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ACTION_RING = "cn.xmeverylucky.pane.RING"
        const val ACTION_SNOOZE = "cn.xmeverylucky.pane.SNOOZE"
        const val ACTION_DISMISS = "cn.xmeverylucky.pane.DISMISS"
        const val ACTION_END_WINDOW = "cn.xmeverylucky.pane.END_WINDOW"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RING_MS = 5 * 60_000L
        private const val POPUP_CHECK_MS = 4_000L
        private const val PREFS = "ring"
        private const val KEY_POPUP_FAILED = "lock_screen_popup_failed"

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        fun start(context: Context, alarmId: Long) {
            context.startForegroundService(
                Intent(context, RingService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ALARM_ID, alarmId),
            )
        }

        fun lockScreenPopupFailed(context: Context): Boolean =
            prefs(context).getBoolean(KEY_POPUP_FAILED, false)

        private fun setLockScreenPopupFailed(context: Context, failed: Boolean) {
            prefs(context).edit().putBoolean(KEY_POPUP_FAILED, failed).apply()
        }

        private fun prefs(context: Context) =
            context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun ringChannelImportance(context: Context): Int =
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(App.CHANNEL_RING)?.importance ?: NotificationManager.IMPORTANCE_NONE

        fun send(context: Context, action: String, alarmId: Long) {
            context.startService(
                Intent(context, RingService::class.java).setAction(action).putExtra(EXTRA_ALARM_ID, alarmId),
            )
        }
    }
}
