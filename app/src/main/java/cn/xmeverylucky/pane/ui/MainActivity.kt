package cn.xmeverylucky.pane.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.BuildConfig
import cn.xmeverylucky.pane.R
import cn.xmeverylucky.pane.data.AppSettings
import cn.xmeverylucky.pane.data.HabitStore
import cn.xmeverylucky.pane.data.RingtoneFiles
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.HabitType
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import cn.xmeverylucky.pane.domain.ReminderStyle
import cn.xmeverylucky.pane.ring.RingService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private val permissionIssues = mutableStateOf<List<PermissionIssue>>(emptyList())
    private val batteryIgnoringState = mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = App.of(this)
        setContent {
            GlassTheme {
                val privacyAccepted by app.settings.privacyAccepted.collectAsState()
                val onboardingCompleted by app.settings.onboardingCompleted.collectAsState()
                val reliabilityGuideDismissed by app.settings.reliabilityGuideDismissed.collectAsState()
                val alarms by app.store.alarms.collectAsState()
                val habitRecords by app.habitStore.records.collectAsState()
                val zoneState by app.settings.zone.collectAsState()
                var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
                var showHabitPage by rememberSaveable { mutableStateOf(false) }
                var showAboutPage by rememberSaveable { mutableStateOf(false) }
                var showReliabilityGuide by rememberSaveable { mutableStateOf(false) }
                var replayOnboarding by rememberSaveable { mutableStateOf(false) }
                var zoneDialog by rememberSaveable { mutableStateOf<ZoneDialog?>(null) }
                var showDiagnostics by rememberSaveable { mutableStateOf(false) }
                var showNotificationRationale by rememberSaveable { mutableStateOf(false) }

                val issues = permissionIssues.value
                val reliabilityPermissions = ReliabilityPermissionState(
                    notificationGranted = PermissionIssue.NOTIFICATIONS !in issues,
                    exactAlarmGranted = PermissionIssue.EXACT_ALARM !in issues,
                    fullScreenGranted = PermissionIssue.FULL_SCREEN !in issues,
                    batteryIgnoring = batteryIgnoringState.value,
                    ringChannelHigh = PermissionIssue.RING_CHANNEL !in issues,
                )

                val editing = editingId?.let { id ->
                    when (id) {
                        0L -> Alarm()
                        -1L -> habitTemplate(HabitType.WATER)
                        -2L -> habitTemplate(HabitType.STAND)
                        else -> alarms.firstOrNull { it.id == id }
                    }
                }
                val alarmZone = app.settings.alarmZone()
                val today = LocalDate.now(alarmZone)
                val todayCounts = habitRecords[today] ?: HabitStore.DailyCounts()
                val pinned = pinnedZones(zoneState)

                when {
                    !privacyAccepted -> {
                        PrivacyConsentScreen(
                            onAgree = {
                                app.settings.acceptPrivacy()
                                app.scheduler.rescheduleAll("同意隐私政策")
                                refreshPermissions()
                            },
                            onDisagree = { finishAffinity() },
                        )
                    }
                    !onboardingCompleted -> {
                        OnboardingScreen(
                            permissions = reliabilityPermissions,
                            isReplay = false,
                            onRequestNotification = ::requestNotificationPermission,
                            onRequestExactAlarm = { fix(PermissionIssue.EXACT_ALARM) },
                            onRequestFullScreen = { fix(PermissionIssue.FULL_SCREEN) },
                            onOpenBackgroundRun = { OemPermissionNavigator.openBackgroundRun(this, batteryIgnoringState.value) },
                            onOpenAutostart = { OemPermissionNavigator.openAutostart(this) },
                            onOpenLockScreenNotification = { OemPermissionNavigator.openLockScreenNotification(this) },
                            onOpenBackgroundPopup = { OemPermissionNavigator.openBackgroundPopup(this) },
                            onFinish = { app.settings.completeOnboarding() },
                        )
                    }
                    !zoneState.isConfigured -> {
                        ZoneSetupScreen(
                            pinned = pinned,
                            onSelect = { id -> app.changeZone { setHome(id) } },
                        )
                    }
                    replayOnboarding -> {
                        BackHandler { replayOnboarding = false }
                        OnboardingScreen(
                            permissions = reliabilityPermissions,
                            isReplay = true,
                            onRequestNotification = ::requestNotificationPermission,
                            onRequestExactAlarm = { fix(PermissionIssue.EXACT_ALARM) },
                            onRequestFullScreen = { fix(PermissionIssue.FULL_SCREEN) },
                            onOpenBackgroundRun = { OemPermissionNavigator.openBackgroundRun(this, batteryIgnoringState.value) },
                            onOpenAutostart = { OemPermissionNavigator.openAutostart(this) },
                            onOpenLockScreenNotification = { OemPermissionNavigator.openLockScreenNotification(this) },
                            onOpenBackgroundPopup = { OemPermissionNavigator.openBackgroundPopup(this) },
                            onFinish = { replayOnboarding = false },
                        )
                    }
                    showReliabilityGuide -> {
                        BackHandler { showReliabilityGuide = false }
                        ReliabilityGuideScreen(
                            permissions = reliabilityPermissions,
                            onBack = { showReliabilityGuide = false },
                            onDone = {
                                app.settings.dismissReliabilityGuide()
                                showReliabilityGuide = false
                            },
                            onRequestNotification = ::requestNotificationPermission,
                            onRequestExactAlarm = { fix(PermissionIssue.EXACT_ALARM) },
                            onRequestFullScreen = { fix(PermissionIssue.FULL_SCREEN) },
                            onOpenBackgroundRun = { OemPermissionNavigator.openBackgroundRun(this, batteryIgnoringState.value) },
                            onOpenAutostart = { OemPermissionNavigator.openAutostart(this) },
                            onOpenLockScreenNotification = { OemPermissionNavigator.openLockScreenNotification(this) },
                            onOpenBackgroundPopup = { OemPermissionNavigator.openBackgroundPopup(this) },
                        )
                    }
                    editing != null -> {
                        BackHandler { editingId = null }
                        AlarmEditScreen(
                            initial = editing,
                            alarmZone = alarmZone,
                            pinned = pinned,
                            onSave = { alarm ->
                                val saved = app.store.upsert(prepareForSave(alarm))
                                app.scheduler.sync(saved)
                                if (!saved.enabled) stopIfRinging(saved.id)
                                RingtoneFiles.prune(this, app.store.alarms.value.map { it.ringtoneUri })
                                editingId = null
                            },
                            onDelete = { alarm ->
                                stopIfRinging(alarm.id)
                                app.scheduler.cancel(alarm.id)
                                app.store.delete(alarm.id)
                                RingtoneFiles.prune(this, app.store.alarms.value.map { it.ringtoneUri })
                                editingId = null
                            },
                            onCancel = { editingId = null },
                        )
                    }
                    showAboutPage -> {
                        BackHandler { showAboutPage = false }
                        AboutScreen(
                            onBack = { showAboutPage = false },
                            onOpenReliabilityGuide = { showReliabilityGuide = true },
                            onOpenOnboarding = { replayOnboarding = true },
                            onRevokePrivacy = {
                                app.settings.revokePrivacy()
                                finishAffinity()
                            },
                        )
                    }
                    showHabitPage -> {
                        BackHandler { showHabitPage = false }
                        HabitScreen(
                            today = today,
                            todayCounts = todayCounts,
                            habitRecords = habitRecords,
                            alarms = alarms,
                            onBack = { showHabitPage = false },
                            onHabitDelta = { type, delta ->
                                app.habitStore.add(type, LocalDate.now(alarmZone), delta)
                            },
                            onQuickAddHabit = { type ->
                                editingId = if (type == HabitType.WATER) -1L else -2L
                            },
                            onEditAlarm = { editingId = it.id },
                            onToggleAlarm = { alarm, enabled ->
                                if (!enabled) stopIfRinging(alarm.id)
                                val saved = app.store.upsert(prepareForSave(alarm.copy(enabled = enabled)))
                                app.scheduler.sync(saved)
                            },
                        )
                    }
                    else -> {
                        AlarmListScreen(
                            alarms = alarms,
                            zoneState = zoneState,
                            alarmZone = alarmZone,
                            issues = issues,
                            showReliabilityBanner = !reliabilityGuideDismissed,
                            onFixIssue = { issue ->
                                if (issue == PermissionIssue.NOTIFICATIONS) {
                                    showNotificationRationale = true
                                } else {
                                    fix(issue)
                                }
                            },
                            onOpenReliabilityGuide = { showReliabilityGuide = true },
                            onDismissReliabilityBanner = { app.settings.dismissReliabilityGuide() },
                            onSwitchZone = { zoneDialog = ZoneDialog.SWITCH },
                            onSwitchBack = { app.changeZone { switchBackHome() } },
                            onChangeHome = { zoneDialog = ZoneDialog.HOME },
                            onAdd = { editingId = 0L },
                            onOpenHabits = { showHabitPage = true },
                            onEdit = { editingId = it.id },
                            onToggle = { alarm, enabled ->
                                if (!enabled) stopIfRinging(alarm.id)
                                val saved = app.store.upsert(prepareForSave(alarm.copy(enabled = enabled)))
                                app.scheduler.sync(saved)
                            },
                            onSkipNext = { alarm, date ->
                                app.store.update(alarm.id) {
                                    it.copy(skipDates = it.skipDates + date, snoozeAt = null)
                                }?.let { app.scheduler.sync(it) }
                            },
                            onClearSkips = { alarm ->
                                app.store.update(alarm.id) {
                                    it.copy(skipDates = emptySet())
                                }?.let { app.scheduler.sync(it) }
                            },
                            onCancelSnooze = { alarm ->
                                app.store.update(alarm.id) {
                                    it.copy(snoozeAt = null)
                                }?.let { app.scheduler.sync(it) }
                            },
                            onDelete = { alarm ->
                                stopIfRinging(alarm.id)
                                app.scheduler.cancel(alarm.id)
                                app.store.delete(alarm.id)
                            },
                            onRestore = { alarm ->
                                val saved = app.store.upsert(prepareForSave(alarm))
                                app.scheduler.sync(saved)
                            },
                            onUndoExpired = {
                                RingtoneFiles.prune(this, app.store.alarms.value.map { it.ringtoneUri })
                            },
                            onDeleteDisabledOneShots = {
                                alarms.filter { !it.isRepeating && !it.enabled }.forEach { a ->
                                    app.scheduler.cancel(a.id)
                                    app.store.delete(a.id)
                                }
                                RingtoneFiles.prune(this, app.store.alarms.value.map { it.ringtoneUri })
                            },
                            onShowDiagnostics = if (BuildConfig.DEBUG) ({ showDiagnostics = true }) else null,
                            onOpenAbout = { showAboutPage = true },
                        )
                    }
                }

                if (BuildConfig.DEBUG && showDiagnostics) DiagnosticsDialog(onDismiss = { showDiagnostics = false })

                if (showNotificationRationale) {
                    AlertDialog(
                        onDismissRequest = { showNotificationRationale = false },
                        title = {
                            Text(
                                stringResource(R.string.perm_rationale_notif_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        text = {
                            Text(
                                stringResource(R.string.perm_rationale_notif_desc),
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationRationale = false
                                requestNotificationPermission()
                            }) { Text(stringResource(R.string.perm_rationale_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationRationale = false }) {
                                Text(stringResource(R.string.perm_rationale_cancel))
                            }
                        },
                    )
                }

                zoneDialog?.let { dialog ->
                    TimeZonePickerDialog(
                        title = if (dialog == ZoneDialog.SWITCH) "闹钟按哪个时区响？" else "更改常驻时区",
                        selected = if (dialog == ZoneDialog.SWITCH) zoneState.activeId else zoneState.home,
                        followOption = null,
                        pinned = pinned,
                        onDismiss = { zoneDialog = null },
                        onSelect = { id ->
                            if (id != null) {
                                app.changeZone { if (dialog == ZoneDialog.SWITCH) switchTo(id) else setHome(id) }
                            }
                            zoneDialog = null
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        val app = App.of(this)
        if (app.settings.privacyAccepted.value) {
            app.scheduler.rescheduleAll("打开 App 主界面")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                refreshPermissions()
                return
            }
            val settings = App.of(this).settings
            val canPromptInApp = !settings.hasPromptedNotification() ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
            if (canPromptInApp) {
                settings.markNotificationPrompted()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        fix(PermissionIssue.NOTIFICATIONS)
    }

    private fun stopIfRinging(alarmId: Long) {
        if (RingService.ringing.value?.alarmId == alarmId) {
            RingService.send(this, RingService.ACTION_DISMISS, alarmId)
        }
    }

    private fun refreshPermissions() {
        val issues = mutableListOf<PermissionIssue>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            issues += PermissionIssue.NOTIFICATIONS
        }
        if (!App.of(this).scheduler.canScheduleExact()) issues += PermissionIssue.EXACT_ALARM
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            issues += PermissionIssue.FULL_SCREEN
        }
        if (RingService.ringChannelImportance(this) < NotificationManager.IMPORTANCE_HIGH) issues += PermissionIssue.RING_CHANNEL
        if (RingService.lockScreenPopupFailed(this)) issues += PermissionIssue.LOCK_SCREEN_POPUP
        if (App.of(this).scheduler.bootBroadcastBlocked()) issues += PermissionIssue.AUTOSTART
        val ignoringBattery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        batteryIgnoringState.value = ignoringBattery
        if (BuildConfig.SHOW_BATTERY_GUIDE && !ignoringBattery) {
            issues += PermissionIssue.BATTERY
        }
        permissionIssues.value = issues
    }

    @SuppressLint("BatteryLife", "InlinedApi")
    private fun fix(issue: PermissionIssue) {
        val pkg = Uri.parse("package:$packageName")
        when (issue) {
            PermissionIssue.BATTERY -> {
                OemPermissionNavigator.openBackgroundRun(this, batteryIgnoringState.value)
                return
            }
            PermissionIssue.AUTOSTART -> {
                OemPermissionNavigator.openAutostart(this)
                return
            }
            PermissionIssue.RING_CHANNEL -> {
                OemPermissionNavigator.openLockScreenNotification(this)
                return
            }
            PermissionIssue.LOCK_SCREEN_POPUP -> {
                OemPermissionNavigator.openBackgroundPopup(this)
                return
            }
            else -> Unit
        }
        val intent = when (issue) {
            PermissionIssue.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            PermissionIssue.EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            PermissionIssue.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        runCatching { startActivity(intent) }
            .onFailure { OemPermissionNavigator.openAppDetails(this) }
    }

    private fun prepareForSave(alarm: Alarm): Alarm {
        val now = Instant.now()
        val zone = App.of(this).settings.alarmZone()
        var a = alarm.copy(snoozeAt = null, dismissedUntil = null)
        if (!a.isRepeating && a.enabled) {
            val stale = a.oneShotDate == null ||
                NextTriggerCalculator.next(a, now, zone) == null
            if (stale) {
                a = a.copy(
                    oneShotDate = NextTriggerCalculator.firstOneShotDate(a, now, zone),
                )
            }
        }
        return a
    }
}

private fun habitTemplate(type: HabitType): Alarm = when (type) {
    HabitType.WATER -> Alarm(
        label = "喝水提醒",
        mode = AlarmMode.REPEAT_IN_WINDOW,
        windowStart = LocalTime.of(9, 30),
        windowEnd = LocalTime.of(21, 30),
        intervalMinutes = 60,
        weekdays = Alarm.EVERYDAY,
        vibrate = true,
        snoozeMinutes = 15,
        reminderStyle = ReminderStyle.GENTLE,
        habitType = HabitType.WATER,
    )
    HabitType.STAND -> Alarm(
        label = "起身活动",
        mode = AlarmMode.REPEAT_IN_WINDOW,
        windowStart = LocalTime.of(9, 30),
        windowEnd = LocalTime.of(21, 30),
        intervalMinutes = 60,
        weekdays = Alarm.EVERYDAY,
        vibrate = true,
        snoozeMinutes = 15,
        reminderStyle = ReminderStyle.GENTLE,
        habitType = HabitType.STAND,
    )
    HabitType.NONE -> Alarm()
}

private enum class ZoneDialog { SWITCH, HOME }

private fun pinnedZones(state: AppSettings.ZoneState): List<PinnedZone> = buildList {
    state.home?.let { add(PinnedZone(it, "常驻")) }
    state.recents.filterNot { it == state.home }.forEach { add(PinnedZone(it, "最近")) }
    val system = ZoneId.systemDefault().id
    if (none { it.id == system }) add(PinnedZone(system, "系统时区"))
}

enum class PermissionIssue(val message: String) {
    NOTIFICATIONS("未允许通知，闹钟响铃时可能看不到提醒"),
    EXACT_ALARM("未允许「闹钟和提醒」，闹钟可能不准时"),
    FULL_SCREEN("未允许全屏通知，锁屏时不会弹出响铃界面"),
    BATTERY("建议关闭电池优化，避免被系统省电策略拦截"),
    RING_CHANNEL("「闹钟响铃」通知被调低了重要程度，锁屏时不会弹出响铃界面，请改回「紧急/横幅」"),
    LOCK_SCREEN_POPUP("上次锁屏响铃时没有弹出关闭界面：请在应用设置的权限里允许「锁屏显示」和「后台弹出界面」"),
    AUTOSTART("这次开机后系统没让闹钟自动恢复，请在应用设置里允许「自启动」，否则重启后要先打开一次 App 闹钟才会响"),
}
