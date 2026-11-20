package cn.xmeverylucky.pane.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * 首次使用引导（4 页）：
 * 1. 时段闹钟三种响铃模式
 * 2. 独立闹钟时区与一键切回
 * 3. 轻提醒与习惯打卡
 * 4. 系统权限与防拦截指引（用户主动点击才触发授权或跳转设置）
 */
@Composable
fun OnboardingScreen(
    permissions: ReliabilityPermissionState,
    isReplay: Boolean = false,
    onRequestNotification: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onOpenBackgroundRun: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenLockScreenNotification: () -> Unit,
    onOpenBackgroundPopup: () -> Unit,
    onFinish: () -> Unit,
) {
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val backgroundLayer = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backgroundLayer).glassBackground(EditGlows))
        CompositionLocalProvider(LocalGlassBackdrop provides backgroundLayer) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFinish) {
                        Text(
                            stringResource(if (isReplay) R.string.onboarding_done else R.string.onboarding_skip),
                            fontSize = 14.sp,
                            color = Glass.InkSoft,
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        when (page) {
                            0 -> OnboardingWindowPage()
                            1 -> OnboardingZonePage()
                            2 -> OnboardingHabitPage()
                            else -> OnboardingPermissionPage(
                                permissions = permissions,
                                onRequestNotification = onRequestNotification,
                                onRequestExactAlarm = onRequestExactAlarm,
                                onRequestFullScreen = onRequestFullScreen,
                                onOpenBackgroundRun = onOpenBackgroundRun,
                                onOpenAutostart = onOpenAutostart,
                                onOpenLockScreenNotification = onOpenLockScreenNotification,
                                onOpenBackgroundPopup = onOpenBackgroundPopup,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pageCount) { index ->
                        val active = pagerState.currentPage == index
                        val width by animateDpAsState(if (active) 22.dp else 7.dp, label = "dotWidth")
                        val color by animateColorAsState(
                            if (active) Glass.Accent else Glass.Ink.copy(alpha = 0.20f),
                            label = "dotColor",
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = width, height = 7.dp)
                                .clip(PillShape)
                                .background(color),
                        )
                    }
                }

                val isLast = pagerState.currentPage == pageCount - 1
                LiquidButton(
                    onClick = {
                        if (isLast) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    tint = Glass.Accent,
                ) {
                    Text(
                        stringResource(
                            when {
                                !isLast -> R.string.onboarding_next
                                isReplay -> R.string.onboarding_done
                                else -> R.string.onboarding_start
                            },
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(tag: String, icon: ImageVector, title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(Glass.Chip)
                .border(0.7.dp, Glass.Rim, PillShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Glass.Ink)
            Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Glass.Ink)
        }
        Text(title, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Glass.Ink)
        Text(desc, fontSize = 14.sp, lineHeight = 22.sp, color = Glass.InkSoft)
    }
}

@Composable
private fun OnboardingWindowPage() {
    OnboardingHeader(
        tag = stringResource(R.string.onboarding_page1_tag),
        icon = GlassIcons.Clock,
        title = stringResource(R.string.onboarding_page1_title),
        desc = stringResource(R.string.onboarding_page1_desc),
    )
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(26.dp),
        fill = Color.White.copy(alpha = 0.36f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("起床防睡过头示例", fontSize = 12.sp, color = Glass.InkSoft)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("07:00", style = clockStyle(52.sp, FontWeight.ExtraLight), color = Glass.Ink)
            Spacer(Modifier.width(8.dp))
            Text("– 07:30 · 每 5 分钟", Modifier.padding(bottom = 8.dp), fontSize = 13.sp, color = Glass.InkSoft)
        }
        GlassDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoPill("固定时刻")
            DemoPill("时段重复")
            DemoPill("时段随机")
        }
        Text("响铃页可滑动关闭、贪睡或一键「结束本轮」。", fontSize = 12.sp, color = Glass.InkSoft)
    }
}

@Composable
private fun OnboardingZonePage() {
    OnboardingHeader(
        tag = stringResource(R.string.onboarding_page2_tag),
        icon = GlassIcons.Globe,
        title = stringResource(R.string.onboarding_page2_title),
        desc = stringResource(R.string.onboarding_page2_desc),
    )
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(26.dp),
        fill = Color.White.copy(alpha = 0.36f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("当前闹钟时区", fontSize = 12.sp, color = Glass.InkSoft)
                Text("东京 · UTC+9", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Glass.Ink)
            }
            DemoPill("出行中")
        }
        GlassDivider()
        Text("常驻时区：上海（UTC+8）· 首页顶部置顶提示，回家点一下即可切回，无需改手机系统时区。", fontSize = 13.sp, lineHeight = 20.sp, color = Glass.InkSoft)
    }
}

@Composable
private fun OnboardingHabitPage() {
    OnboardingHeader(
        tag = stringResource(R.string.onboarding_page3_tag),
        icon = GlassIcons.CheckCircle,
        title = stringResource(R.string.onboarding_page3_title),
        desc = stringResource(R.string.onboarding_page3_desc),
    )
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(26.dp),
        fill = Color.White.copy(alpha = 0.36f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("工作时段轻提醒示例", fontSize = 12.sp, color = Glass.InkSoft)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(cardShape(18.dp))
                    .background(Glass.Chip)
                    .border(0.7.dp, Glass.Rim, cardShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("💧 喝水提醒", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Glass.Ink)
                Text("09:30 – 21:30 · 每 60 分钟", fontSize = 11.sp, color = Glass.InkSoft)
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(cardShape(18.dp))
                    .background(Glass.Chip)
                    .border(0.7.dp, Glass.Rim, cardShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("🧍 起身活动", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Glass.Ink)
                Text("通知栏直接点 +1 打卡", fontSize = 11.sp, color = Glass.InkSoft)
            }
        }
    }
}

@Composable
private fun OnboardingPermissionPage(
    permissions: ReliabilityPermissionState,
    onRequestNotification: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onOpenBackgroundRun: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenLockScreenNotification: () -> Unit,
    onOpenBackgroundPopup: () -> Unit,
) {
    OnboardingHeader(
        tag = stringResource(R.string.onboarding_page4_tag),
        icon = GlassIcons.Bell,
        title = stringResource(R.string.onboarding_page4_title),
        desc = stringResource(R.string.onboarding_page4_desc),
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
}

@Composable
private fun DemoPill(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Glass.Ink,
        modifier = Modifier
            .clip(PillShape)
            .background(Glass.Chip)
            .border(0.6.dp, Glass.Rim, PillShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun OnboardingScreenPreview() {
    GlassTheme {
        OnboardingScreen(
            permissions = ReliabilityPermissionState(
                notificationGranted = false,
                exactAlarmGranted = true,
                fullScreenGranted = false,
                batteryIgnoring = false,
                ringChannelHigh = true,
            ),
            onRequestNotification = {},
            onRequestExactAlarm = {},
            onRequestFullScreen = {},
            onOpenBackgroundRun = {},
            onOpenAutostart = {},
            onOpenLockScreenNotification = {},
            onOpenBackgroundPopup = {},
            onFinish = {},
        )
    }
}
