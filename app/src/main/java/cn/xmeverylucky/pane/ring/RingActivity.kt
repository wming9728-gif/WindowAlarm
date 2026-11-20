package cn.xmeverylucky.pane.ring

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import cn.xmeverylucky.pane.ui.EditGlows
import cn.xmeverylucky.pane.ui.Glass
import cn.xmeverylucky.pane.ui.GlassCard
import cn.xmeverylucky.pane.ui.GlassDivider
import cn.xmeverylucky.pane.ui.GlassIcons
import cn.xmeverylucky.pane.ui.GlassTheme
import cn.xmeverylucky.pane.ui.LiquidButton
import cn.xmeverylucky.pane.ui.LocalGlassBackdrop
import cn.xmeverylucky.pane.ui.PillShape
import cn.xmeverylucky.pane.ui.cardShape
import cn.xmeverylucky.pane.ui.clockStyle
import cn.xmeverylucky.pane.ui.describeWindow
import cn.xmeverylucky.pane.ui.glassBackground
import cn.xmeverylucky.pane.ui.hm
import cn.xmeverylucky.pane.ui.placeName
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

class RingActivity : ComponentActivity() {

    private var initialAlarmId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val id = intent.getLongExtra(RingService.EXTRA_ALARM_ID, -1)
        initialAlarmId = id
        DiagLog.log("闹钟#$id 响铃界面已显示")
        shown = true
        setContent {
            GlassTheme {
                val ringing by RingService.ringing.collectAsState()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                RingScreen(
                    alarmId = ringing?.alarmId ?: id,
                    onAction = { action, alarmId ->
                        RingService.send(this, action, alarmId)
                        finish()
                    },
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.repeatCount == 0) {
                val currentId = RingService.ringing.value?.alarmId ?: initialAlarmId
                RingService.send(this, RingService.ACTION_SNOOZE, currentId)
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        if (isFinishing) shown = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var shown = false
            private set
    }
}

@Composable
private fun RingScreen(alarmId: Long, onAction: (String, Long) -> Unit) {
    val alarms by App.of(LocalContext.current).store.alarms.collectAsState()
    val alarm = alarms.firstOrNull { it.id == alarmId }
    val appZone = App.of(LocalContext.current).settings.alarmZone()
    val zone = alarm?.let { NextTriggerCalculator.zoneOf(it, appZone) } ?: appZone
    val clock = remember { mutableStateOf(LocalTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            clock.value = LocalTime.now(zone)
            delay(1000)
        }
    }

    RingScreenContent(
        zoneName = placeName(zone),
        timeText = clock.value.hm(),
        label = alarm?.label?.ifBlank { "闹钟" } ?: "闹钟",
        windowDesc = alarm?.let(::describeWindow),
        snoozeMinutes = alarm?.snoozeMinutes ?: 5,
        showEndWindow = alarm?.mode == AlarmMode.REPEAT_IN_WINDOW,
        onDismiss = { onAction(RingService.ACTION_DISMISS, alarmId) },
        onSnooze = { onAction(RingService.ACTION_SNOOZE, alarmId) },
        onEndWindow = { onAction(RingService.ACTION_END_WINDOW, alarmId) },
    )
}

@Composable
private fun RingScreenContent(
    zoneName: String,
    timeText: String,
    label: String,
    windowDesc: String?,
    snoozeMinutes: Int,
    showEndWindow: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onEndWindow: () -> Unit,
) {
    val backgroundLayer = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backgroundLayer).glassBackground(EditGlows))
        CompositionLocalProvider(LocalGlassBackdrop provides backgroundLayer) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(16.dp))
                GlassCard(
                    Modifier.fillMaxWidth(),
                    shape = cardShape(32.dp),
                    fill = Color.White.copy(alpha = 0.36f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        zoneName,
                        fontSize = 13.sp,
                        color = Glass.InkSoft,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        timeText,
                        style = clockStyle(84.sp, FontWeight.ExtraLight),
                        color = Glass.Ink,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    GlassDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Glass.Ink,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    if (windowDesc != null) {
                        Text(
                            windowDesc,
                            fontSize = 14.sp,
                            color = Glass.InkSoft,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SlideToDismissButton(
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    )
                    LiquidButton(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("贪睡 $snoozeMinutes 分钟", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                    if (showEndWindow) {
                        LiquidButton(
                            onClick = onEndWindow,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text("结束本轮（今天这个时段不再响）", fontSize = 14.sp, color = Glass.InkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun RingScreenPreview() {
    val sample = Alarm(
        label = "起床闹钟",
        mode = AlarmMode.REPEAT_IN_WINDOW,
        windowStart = LocalTime.of(7, 0),
        windowEnd = LocalTime.of(7, 30),
        intervalMinutes = 5,
        snoozeMinutes = 5,
    )
    GlassTheme {
        RingScreenContent(
            zoneName = "上海",
            timeText = "07:15",
            label = sample.label,
            windowDesc = describeWindow(sample),
            snoozeMinutes = sample.snoozeMinutes,
            showEndWindow = true,
            onDismiss = {},
            onSnooze = {},
            onEndWindow = {},
        )
    }
}

@Composable
private fun SlideToDismissButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbSize = 50.dp
    val innerPadding = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .shadow(10.dp, PillShape, spotColor = Glass.Shadow)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.65f))
            .border(0.8.dp, Glass.Rim, PillShape)
            .padding(innerPadding)
            .semantics { contentDescription = "向右滑动关闭闹钟" },
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxOffsetPx = with(density) { (maxWidth - thumbSize).toPx().coerceAtLeast(1f) }
        val offsetX = remember { Animatable(0f) }
        val progress = (offsetX.value / maxOffsetPx).coerceIn(0f, 1f)

        Text(
            text = "向右滑动关闭",
            color = Glass.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha((1f - progress * 1.4f).coerceIn(0f, 1f)),
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSize)
                .shadow(4.dp, PillShape)
                .clip(PillShape)
                .background(Glass.Accent)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(0f, maxOffsetPx))
                        }
                    },
                    onDragStopped = {
                        if (offsetX.value >= maxOffsetPx * 0.78f) {
                            offsetX.animateTo(maxOffsetPx, tween(100))
                            onDismiss()
                        } else {
                            offsetX.animateTo(0f, tween(220))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GlassIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.White,
            )
        }
    }
}
