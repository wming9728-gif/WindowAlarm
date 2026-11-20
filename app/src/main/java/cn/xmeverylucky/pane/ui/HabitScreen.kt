package cn.xmeverylucky.pane.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.data.HabitStore
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.HabitType
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.time.LocalDate

private val HabitTopBarHeight = 60.dp

@Composable
fun HabitScreen(
    today: LocalDate,
    todayCounts: HabitStore.DailyCounts,
    habitRecords: Map<LocalDate, HabitStore.DailyCounts>,
    alarms: List<Alarm>,
    onBack: () -> Unit,
    onHabitDelta: (HabitType, Int) -> Unit,
    onQuickAddHabit: (HabitType) -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onToggleAlarm: (Alarm, Boolean) -> Unit,
) {
    val backgroundLayer = rememberLayerBackdrop()
    val pageLayer = rememberLayerBackdrop()
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val habitAlarms = remember(alarms) {
        alarms.filter { it.habitType != HabitType.NONE }.sortedBy { it.windowStart }
    }
    val hasWaterReminder = habitAlarms.any { it.habitType == HabitType.WATER }
    val hasStandReminder = habitAlarms.any { it.habitType == HabitType.STAND }

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
                        .padding(start = 20.dp, top = HabitTopBarHeight + 8.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HabitTodayCard(
                        today = today,
                        counts = todayCounts,
                        onDelta = onHabitDelta,
                    )
                    HabitReminderCard(
                        habitAlarms = habitAlarms,
                        hasWaterReminder = hasWaterReminder,
                        hasStandReminder = hasStandReminder,
                        onQuickAdd = onQuickAddHabit,
                        onEditAlarm = onEditAlarm,
                        onToggleAlarm = onToggleAlarm,
                    )
                    HabitHistoryCard(
                        today = today,
                        records = habitRecords,
                    )
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
                    .height(HabitTopBarHeight + 24.dp),
            )
        }
        HabitTopBar(
            backdrop = pageLayer,
            onBack = onBack,
            modifier = Modifier.windowInsetsPadding(topInsets),
        )
    }
}

@Composable
private fun HabitTopBar(
    backdrop: Backdrop,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(HabitTopBarHeight).padding(horizontal = 16.dp)) {
        LiquidButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).height(44.dp),
            backdrop = backdrop,
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        ) {
            Icon(GlassIcons.ChevronLeft, contentDescription = null, Modifier.size(16.dp))
            Text("返回", fontSize = 16.sp)
        }
        Text("习惯打卡", Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 今日喝水与起身活动打卡卡片，支持手动 +1 / -1。 */
@Composable
private fun HabitTodayCard(
    today: LocalDate,
    counts: HabitStore.DailyCounts,
    onDelta: (HabitType, Int) -> Unit,
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(26.dp),
        fill = Color.White.copy(alpha = 0.34f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("今日习惯打卡", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Glass.Ink)
            Text(today.mdE(), fontSize = 12.sp, color = Glass.InkSoft)
        }
        GlassDivider(Modifier.padding(top = 10.dp, bottom = 14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HabitCounterColumn(
                title = "💧 喝水",
                count = counts.water,
                unit = "次",
                modifier = Modifier.weight(1f),
                onMinus = { onDelta(HabitType.WATER, -1) },
                onPlus = { onDelta(HabitType.WATER, 1) },
            )
            HabitCounterColumn(
                title = "🧍 起身活动",
                count = counts.stand,
                unit = "次",
                modifier = Modifier.weight(1f),
                onMinus = { onDelta(HabitType.STAND, -1) },
                onPlus = { onDelta(HabitType.STAND, 1) },
            )
        }
    }
}

@Composable
private fun HabitCounterColumn(
    title: String,
    count: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 14.sp, color = Glass.InkSoft)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$count",
                style = clockStyle(40.sp, FontWeight.Medium),
                color = Glass.Ink,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 13.sp, color = Glass.InkSoft, modifier = Modifier.alignByBaseline())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (count > 0) {
                LiquidButton(
                    onClick = onMinus,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("-1", fontSize = 13.sp, color = Glass.InkSoft) }
            }
            LiquidButton(
                onClick = onPlus,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("+1", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Glass.Accent) }
        }
    }
}

/** 习惯轻提醒管理：已设置的提醒可直接开关或点击编辑，未设置的提供一键快捷创建。 */
@Composable
private fun HabitReminderCard(
    habitAlarms: List<Alarm>,
    hasWaterReminder: Boolean,
    hasStandReminder: Boolean,
    onQuickAdd: (HabitType) -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onToggleAlarm: (Alarm, Boolean) -> Unit,
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(24.dp),
        fill = Color.White.copy(alpha = 0.28f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("习惯轻提醒", fontSize = 13.sp, color = Glass.InkSoft)
        if (habitAlarms.isNotEmpty()) {
            GlassDivider(Modifier.padding(top = 8.dp, bottom = 6.dp))
            habitAlarms.forEachIndexed { index, alarm ->
                if (index > 0) GlassDivider(Modifier.padding(vertical = 4.dp))
                val main = if (alarm.enabled) Glass.Ink else Glass.InkMuted
                val soft = if (alarm.enabled) Glass.InkSoft else Glass.InkMuted
                val title = alarm.label.ifBlank {
                    if (alarm.habitType == HabitType.WATER) "喝水提醒" else "起身活动"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEditAlarm(alarm) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = main)
                        Text(
                            "${describeWindow(alarm)} · ${if (alarm.enabled) describeDays(alarm) else "已关闭"}",
                            Modifier.padding(top = 2.dp),
                            fontSize = 12.sp,
                            color = soft,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassSwitch(
                        checked = alarm.enabled,
                        onCheckedChange = { onToggleAlarm(alarm, it) },
                        modifier = Modifier.semantics { contentDescription = "$title 开关" },
                    )
                }
            }
        }
        if (!hasWaterReminder || !hasStandReminder) {
            GlassDivider(Modifier.padding(top = 8.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasWaterReminder) {
                    LiquidButton(
                        onClick = { onQuickAdd(HabitType.WATER) },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text("+ 喝水轻提醒", fontSize = 12.sp, color = Glass.Ink) }
                }
                if (!hasStandReminder) {
                    LiquidButton(
                        onClick = { onQuickAdd(HabitType.STAND) },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text("+ 起身轻提醒", fontSize = 12.sp, color = Glass.Ink) }
                }
            }
        }
    }
}

/** 最近 7 天打卡记录汇总。 */
@Composable
private fun HabitHistoryCard(
    today: LocalDate,
    records: Map<LocalDate, HabitStore.DailyCounts>,
) {
    val days = remember(today) { (0L..6L).map { today.minusDays(it) } }
    val totalWater = remember(days, records) { days.sumOf { records[it]?.water ?: 0 } }
    val totalStand = remember(days, records) { days.sumOf { records[it]?.stand ?: 0 } }

    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(24.dp),
        fill = Color.White.copy(alpha = 0.28f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近 7 天", fontSize = 13.sp, color = Glass.InkSoft)
            Text(
                "💧 $totalWater 次 · 🧍 $totalStand 次",
                fontSize = 12.sp,
                color = Glass.InkSoft,
            )
        }
        GlassDivider(Modifier.padding(top = 8.dp, bottom = 6.dp))
        days.forEachIndexed { index, date ->
            if (index > 0) GlassDivider(Modifier.padding(vertical = 2.dp))
            val counts = records[date] ?: HabitStore.DailyCounts()
            val label = when (date) {
                today -> "今天 · ${date.mdE()}"
                today.minusDays(1) -> "昨天 · ${date.mdE()}"
                else -> date.mdE()
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (date == today) FontWeight.Medium else FontWeight.Normal,
                    color = if (date == today) Glass.Ink else Glass.InkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "💧 ${counts.water}",
                        fontSize = 13.sp,
                        color = if (counts.water > 0) Glass.Ink else Glass.InkMuted,
                    )
                    Text(
                        "🧍 ${counts.stand}",
                        fontSize = 13.sp,
                        color = if (counts.stand > 0) Glass.Ink else Glass.InkMuted,
                    )
                }
            }
        }
    }
}
