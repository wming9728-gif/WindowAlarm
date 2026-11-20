package cn.xmeverylucky.pane.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.BuildConfig
import cn.xmeverylucky.pane.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val GuideTopBarHeight = 60.dp

/** 响铃防拦截与系统权限的实时状态快照。 */
data class ReliabilityPermissionState(
    val notificationGranted: Boolean,
    val exactAlarmGranted: Boolean,
    val fullScreenGranted: Boolean,
    val batteryIgnoring: Boolean,
    val ringChannelHigh: Boolean,
)

/**
 * 厂商防拦截设置跳转与机型路径指引。
 * 不申请 QUERY_ALL_PACKAGES，所有厂商页面跳转均做安全捕获，失败自动兜底到系统应用详情页并弹出操作提示。
 */
object OemPermissionNavigator {

    data class BrandGuide(
        val brandLabel: String,
        val steps: List<String>,
    )

    private fun vendorKey(): String =
        "${Build.MANUFACTURER.orEmpty()} ${Build.BRAND.orEmpty()}".lowercase()

    fun brandGuide(): BrandGuide {
        val v = vendorKey()
        return when {
            "vivo" in v || "iqoo" in v -> BrandGuide(
                brandLabel = "vivo / iQOO（OriginOS / FuntouchOS）",
                steps = listOf(
                    "允许后台运行：应用信息 → 耗电管理/电池 → 允许后台高耗电。",
                    "开机自启动：应用信息 → 权限 → 自启动 → 开启。",
                    "允许锁屏通知：应用信息 → 通知 → 闹钟响铃 → 开启锁屏通知与横幅提示。",
                    "后台弹出界面：应用信息 → 权限 → 同时开启「锁屏显示」和「后台弹出界面」。",
                ),
            )
            "xiaomi" in v || "redmi" in v || "poco" in v -> BrandGuide(
                brandLabel = "小米 / Redmi（HyperOS / MIUI）",
                steps = listOf(
                    "允许后台运行：应用信息 → 省电策略 → 选择「无限制」。",
                    "开机自启动：应用信息 → 自启动 → 开启。",
                    "允许锁屏通知：应用信息 → 通知管理 → 闹钟响铃 → 开启锁屏通知与悬浮通知。",
                    "后台弹出界面：应用信息 → 其他权限 → 开启「锁屏显示」和「后台弹出界面」。",
                ),
            )
            "oppo" in v || "oneplus" in v || "realme" in v -> BrandGuide(
                brandLabel = "OPPO / 一加 / realme（ColorOS）",
                steps = listOf(
                    "允许后台运行：应用信息 → 耗电管理 → 开启「允许完全后台行为」。",
                    "开机自启动：应用信息 → 耗电管理 → 开启「允许应用自启动」。",
                    "允许锁屏通知：应用信息 → 通知管理 → 闹钟响铃 → 开启锁屏通知与横幅。",
                    "后台弹出界面：应用信息 → 权限管理/其他权限 → 允许悬浮窗与锁屏显示。",
                ),
            )
            "huawei" in v || "honor" in v -> BrandGuide(
                brandLabel = "华为 / 荣耀（HarmonyOS / MagicOS）",
                steps = listOf(
                    "后台运行与自启动：应用信息 → 耗电详情/应用启动管理 → 关闭自动管理，开启「允许自启动」和「允许后台活动」。",
                    "允许锁屏通知：应用信息 → 通知管理 → 闹钟响铃 → 开启锁屏通知与横幅。",
                    "后台弹出界面：应用信息 → 权限 → 开启悬浮窗与锁屏显示（如设备提供）。",
                ),
            )
            "samsung" in v -> BrandGuide(
                brandLabel = "三星（One UI）",
                steps = listOf(
                    "允许后台运行：应用信息 → 电池 → 选择「不受限制」。",
                    "允许锁屏通知：应用信息 → 通知 → 通知类别 → 闹钟响铃 → 允许锁屏显示与弹出。",
                    "开机自启动：确保本应用未加入「休眠/深度休眠应用程序」列表。",
                ),
            )
            else -> BrandGuide(
                brandLabel = "Android 通用路径",
                steps = listOf(
                    "允许后台运行：应用信息 → 电池/耗电管理 → 选择「无限制」或关闭电池优化。",
                    "开机自启动：在应用信息或系统管家中允许本应用自启动。",
                    "允许锁屏通知：应用信息 → 通知 → 闹钟响铃 → 开启锁屏通知与紧急/横幅提示。",
                    "后台弹出界面：应用信息 → 权限 → 开启「锁屏显示」与「后台弹出界面」（如有）。",
                ),
            )
        }
    }

    /** 1. 允许后台运行（忽略电池优化 / 厂商后台高耗电） */
    @SuppressLint("BatteryLife")
    fun openBackgroundRun(context: Context, batteryIgnoring: Boolean) {
        val pkgUri = Uri.parse("package:${context.packageName}")
        if (BuildConfig.SHOW_BATTERY_GUIDE && !batteryIgnoring) {
            val requested = runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
            }.isSuccess
            if (requested) return
        }
        openAppDetails(context, "请在「耗电管理 / 电池」中选择「允许后台高耗电 / 无限制」")
    }

    /** 2. 开机自启动 */
    fun openAutostart(context: Context) {
        val v = vendorKey()
        val candidates = buildList {
            when {
                "vivo" in v || "iqoo" in v -> {
                    add(componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
                    add(componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
                }
                "xiaomi" in v || "redmi" in v || "poco" in v -> {
                    add(componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                }
                "oppo" in v || "oneplus" in v || "realme" in v -> {
                    add(componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
                    add(componentIntent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"))
                }
                "huawei" in v || "honor" in v -> {
                    add(componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
                    add(componentIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
                }
            }
        }
        if (tryStartAny(context, candidates)) {
            showTip(context, "请找到「Pane 闹钟」并开启「自启动」")
        } else {
            openAppDetails(context, "请在「权限」或「耗电管理」中开启「自启动」")
        }
    }

    /** 3. 允许锁屏通知（直达「闹钟响铃」通知渠道设置） */
    fun openLockScreenNotification(context: Context) {
        val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, App.CHANNEL_RING)
        val appNotifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (tryStartAny(context, listOf(channelIntent, appNotifIntent))) {
            showTip(context, "请开启「锁屏通知」与「横幅 / 悬浮通知」")
        } else {
            openAppDetails(context, "请在「通知 → 闹钟响铃」中开启锁屏通知")
        }
    }

    /** 4. 允许锁屏显示与后台弹出界面 */
    fun openBackgroundPopup(context: Context) {
        val v = vendorKey()
        val pkgName = context.packageName
        val candidates = buildList {
            when {
                "vivo" in v || "iqoo" in v -> {
                    add(
                        componentIntent(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
                        ).putExtra("packagename", pkgName),
                    )
                }
                "xiaomi" in v || "redmi" in v || "poco" in v -> {
                    add(Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkgName))
                }
            }
        }
        if (tryStartAny(context, candidates)) {
            showTip(context, "请开启「锁屏显示」和「后台弹出界面」")
        } else {
            openAppDetails(context, "请在「权限」中开启「锁屏显示」和「后台弹出界面」")
        }
    }

    fun openAppDetails(context: Context, toastMessage: String? = null) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        }
        if (!toastMessage.isNullOrBlank()) showTip(context, toastMessage)
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun tryStartAny(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private fun showTip(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

/**
 * 可复用的「系统基础权限 + 防拦截必备设置 + 当前机型路径」卡片组，
 * 同时用于首次引导第 4 页与独立的「响铃防拦截与权限指引」页面。
 */
@Composable
fun ReliabilityGuideCards(
    permissions: ReliabilityPermissionState,
    onRequestNotification: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onOpenBackgroundRun: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenLockScreenNotification: () -> Unit,
    onOpenBackgroundPopup: () -> Unit,
) {
    val brandGuide = remember { OemPermissionNavigator.brandGuide() }

    // 卡片一：Android 系统基础权限（可实时检测状态）
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(26.dp),
        fill = Color.White.copy(alpha = 0.36f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.reliability_section_system),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Glass.InkSoft,
        )
        SystemPermissionRow(
            title = stringResource(R.string.onboarding_perm_notif_title),
            desc = stringResource(R.string.onboarding_perm_notif_desc),
            granted = permissions.notificationGranted,
            onRequest = onRequestNotification,
        )
        GlassDivider()
        SystemPermissionRow(
            title = stringResource(R.string.onboarding_perm_exact_title),
            desc = stringResource(R.string.onboarding_perm_exact_desc),
            granted = permissions.exactAlarmGranted,
            onRequest = onRequestExactAlarm,
        )
        if (Build.VERSION.SDK_INT >= 34) {
            GlassDivider()
            SystemPermissionRow(
                title = stringResource(R.string.onboarding_perm_fullscreen_title),
                desc = stringResource(R.string.onboarding_perm_fullscreen_desc),
                granted = permissions.fullScreenGranted,
                onRequest = onRequestFullScreen,
            )
        }
    }

    // 卡片二：4 项防拦截必备设置（后台运行 / 开机自启 / 锁屏通知 / 后台弹出界面）
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(26.dp),
        fill = Color.White.copy(alpha = 0.36f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.reliability_section_oem),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Glass.InkSoft,
        )
        OemGuideRow(
            title = stringResource(R.string.reliability_bg_title),
            desc = stringResource(R.string.reliability_bg_desc),
            statusBadge = if (BuildConfig.SHOW_BATTERY_GUIDE && permissions.batteryIgnoring) {
                stringResource(R.string.reliability_bg_ignored_badge) to false
            } else {
                null
            },
            actionLabel = stringResource(
                if (BuildConfig.SHOW_BATTERY_GUIDE && permissions.batteryIgnoring) {
                    R.string.reliability_action_check
                } else {
                    R.string.reliability_action_setup
                },
            ),
            onAction = onOpenBackgroundRun,
        )
        GlassDivider()
        OemGuideRow(
            title = stringResource(R.string.reliability_autostart_title),
            desc = stringResource(R.string.reliability_autostart_desc),
            statusBadge = null,
            actionLabel = stringResource(R.string.reliability_action_setup),
            onAction = onOpenAutostart,
        )
        GlassDivider()
        OemGuideRow(
            title = stringResource(R.string.reliability_lockscreen_notif_title),
            desc = stringResource(R.string.reliability_lockscreen_notif_desc),
            statusBadge = if (!permissions.ringChannelHigh) {
                stringResource(R.string.reliability_lockscreen_notif_warning) to true
            } else {
                null
            },
            actionLabel = stringResource(R.string.reliability_action_setup),
            onAction = onOpenLockScreenNotification,
        )
        GlassDivider()
        OemGuideRow(
            title = stringResource(R.string.reliability_bg_popup_title),
            desc = stringResource(R.string.reliability_bg_popup_desc),
            statusBadge = null,
            actionLabel = stringResource(R.string.reliability_action_setup),
            onAction = onOpenBackgroundPopup,
        )
    }

    // 卡片三：按手机品牌定制的操作路径参考
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(24.dp),
        fill = Color.White.copy(alpha = 0.28f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.reliability_section_brand_format, brandGuide.brandLabel),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Glass.InkSoft,
        )
        brandGuide.steps.forEachIndexed { idx, step ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                        .clip(PillShape)
                        .background(Glass.Chip)
                        .border(0.6.dp, Glass.Rim, PillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Glass.Ink)
                }
                Text(
                    step,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Glass.Ink,
                )
            }
        }
    }
}

/** 独立「响铃防拦截与权限指引」页面，可从首页提醒卡片、更多菜单及「关于与隐私」进入。 */
@Composable
fun ReliabilityGuideScreen(
    permissions: ReliabilityPermissionState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onOpenBackgroundRun: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenLockScreenNotification: () -> Unit,
    onOpenBackgroundPopup: () -> Unit,
) {
    val backgroundLayer = rememberLayerBackdrop()
    val pageLayer = rememberLayerBackdrop()
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageLayer)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backgroundLayer).glassBackground(EditGlows))
            CompositionLocalProvider(LocalGlassBackdrop provides backgroundLayer) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(topInsets)
                        .fadeTopEdge()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = GuideTopBarHeight + 8.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.reliability_intro),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Glass.InkSoft,
                    )

                    ReliabilityGuideCards(
                        permissions = permissions,
                        onRequestNotification = onRequestNotification,
                        onRequestExactAlarm = onRequestExactAlarm,
                        onRequestFullScreen = onRequestFullScreen,
                        onOpenBackgroundRun = onOpenBackgroundRun,
                        onOpenAutostart = onOpenAutostart,
                        onOpenLockScreenNotification = onOpenLockScreenNotification,
                        onOpenBackgroundPopup = onOpenBackgroundPopup,
                    )

                    LiquidButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(52.dp),
                        tint = Glass.Accent,
                    ) {
                        Text(
                            stringResource(R.string.reliability_done_button),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0f to Glass.BaseTop.copy(alpha = 0.97f),
                                0.72f to Glass.BaseTop.copy(alpha = 0.92f),
                                1f to Glass.BaseTop.copy(alpha = 0f),
                            ),
                        )
                    }
                    .windowInsetsPadding(topInsets)
                    .height(GuideTopBarHeight + 24.dp),
            )
        }
        ReliabilityTopBar(
            backdrop = pageLayer,
            onBack = onBack,
            modifier = Modifier.windowInsetsPadding(topInsets),
        )
    }
}

@Composable
private fun ReliabilityTopBar(
    backdrop: Backdrop,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(GuideTopBarHeight).padding(horizontal = 16.dp)) {
        LiquidButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).height(44.dp),
            backdrop = backdrop,
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        ) {
            Icon(GlassIcons.ChevronLeft, contentDescription = null, Modifier.size(16.dp))
            Text(stringResource(R.string.about_back), fontSize = 16.sp)
        }
        Text(
            stringResource(R.string.reliability_title),
            Modifier.align(Alignment.Center),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SystemPermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Glass.Ink)
            Text(desc, fontSize = 12.sp, lineHeight = 18.sp, color = Glass.InkSoft)
        }
        if (granted) {
            StatusBadge(text = stringResource(R.string.onboarding_perm_granted), isWarning = false)
        } else {
            LiquidButton(
                onClick = onRequest,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(stringResource(R.string.onboarding_perm_action), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun OemGuideRow(
    title: String,
    desc: String,
    statusBadge: Pair<String, Boolean>?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Glass.Ink)
            Text(desc, fontSize = 12.sp, lineHeight = 18.sp, color = Glass.InkSoft)
            if (statusBadge != null) {
                StatusBadge(text = statusBadge.first, isWarning = statusBadge.second)
            }
        }
        LiquidButton(
            onClick = onAction,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusBadge(text: String, isWarning: Boolean) {
    val bg = if (isWarning) Color(0x28FF8A00) else Color(0x2634C759)
    val fg = if (isWarning) Color(0xFF9A4D00) else Color(0xFF1F7A3E)
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ReliabilityGuideScreenPreview() {
    GlassTheme {
        ReliabilityGuideScreen(
            permissions = ReliabilityPermissionState(
                notificationGranted = true,
                exactAlarmGranted = true,
                fullScreenGranted = false,
                batteryIgnoring = false,
                ringChannelHigh = true,
            ),
            onBack = {},
            onDone = {},
            onRequestNotification = {},
            onRequestExactAlarm = {},
            onRequestFullScreen = {},
            onOpenBackgroundRun = {},
            onOpenAutostart = {},
            onOpenLockScreenNotification = {},
            onOpenBackgroundPopup = {},
        )
    }
}
