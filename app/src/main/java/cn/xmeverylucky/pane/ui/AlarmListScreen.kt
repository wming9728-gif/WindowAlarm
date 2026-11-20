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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.R
import cn.xmeverylucky.pane.data.AppSettings
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import cn.xmeverylucky.pane.domain.ReminderStyle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    zoneState: AppSettings.ZoneState,
    alarmZone: ZoneId,
    issues: List<PermissionIssue>,
    showReliabilityBanner: Boolean,
    onFixIssue: (PermissionIssue) -> Unit,
    onOpenReliabilityGuide: () -> Unit,
    onDismissReliabilityBanner: () -> Unit,
    onSwitchZone: () -> Unit,
    onSwitchBack: () -> Unit,
    onChangeHome: () -> Unit,
    onAdd: () -> Unit,
    onOpenHabits: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onSkipNext: (Alarm, LocalDate) -> Unit,
    onClearSkips: (Alarm) -> Unit,
    onCancelSnooze: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onRestore: (Alarm) -> Unit,
    onUndoExpired: () -> Unit,
    onDeleteDisabledOneShots: () -> Unit,
    onShowDiagnostics: (() -> Unit)? = null,
    onOpenAbout: () -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(alarms) {
        now = Instant.now()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    var deletedUndo by remember { mutableStateOf<Pair<Int, Alarm>?>(null) }
    var undoSeq by remember { mutableIntStateOf(0) }
    LaunchedEffect(deletedUndo?.first) {
        val current = deletedUndo ?: return@LaunchedEffect
        delay(5_000)
        if (deletedUndo?.first == current.first) {
            deletedUndo = null
            onUndoExpired()
        }
    }

    val home = remember(zoneState.home) { zoneState.home?.let { ZoneId.of(it) } }
    val sortedAlarms = remember(alarms) { alarms.sortedBy { it.windowStart } }
    val hasDisabledOneShots = remember(alarms) { alarms.any { !it.isRepeating && !it.enabled } }
    val upcoming = remember(alarms, now, alarmZone) {
        alarms.filter { it.enabled }
            .mapNotNull { alarm -> NextTriggerCalculator.nextIncludingSnooze(alarm, now, alarmZone)?.let { alarm to it } }
            .minByOrNull { it.second }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val backgroundLayer = rememberLayerBackdrop()
    val pageLayer = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageLayer)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backgroundLayer).glassBackground(ListGlows))
            CompositionLocalProvider(LocalGlassBackdrop provides backgroundLayer) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .fadeTopEdge(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 16.dp,
                        end = 20.dp,
                        bottom = navBottom + if (deletedUndo != null) 168.dp else 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "header") {
                        ListHeader(
                            alarmZone = alarmZone,
                            now = now,
                            hasDisabledOneShots = hasDisabledOneShots,
                            onSwitchZone = onSwitchZone,
                            onChangeHome = onChangeHome,
                            onDeleteDisabledOneShots = onDeleteDisabledOneShots,
                            onOpenReliabilityGuide = onOpenReliabilityGuide,
                            onShowDiagnostics = onShowDiagnostics,
                            onOpenAbout = onOpenAbout,
                        )
                    }
                    if (zoneState.isAway && home != null) {
                        item(key = "away") { AwayCard(alarmZone, home, now, onSwitchBack) }
                    }
                    if (alarms.isNotEmpty()) {
                        item(key = "next") {
                            NextAlarmCard(
                                next = upcoming,
                                now = now,
                                appZone = alarmZone,
                                onEdit = onEdit,
                                onToggle = onToggle,
                                onSkipNext = onSkipNext,
                                onCancelSnooze = onCancelSnooze,
                            )
                        }
                    }
                    items(issues, key = { "issue-${it.name}" }) { issue ->
                        IssueBanner(issue, onFix = { onFixIssue(issue) })
                    }
                    if (showReliabilityBanner) {
                        item(key = "reliability-banner") {
                            ReliabilityBanner(
                                onOpenGuide = onOpenReliabilityGuide,
                                onDismiss = onDismissReliabilityBanner,
                            )
                        }
                    }
                    if (alarms.isEmpty()) {
                        item {
                            Text(
                                "还没有闹钟\n点下方的「新建闹钟」：可以设固定时刻、时段内重复响或随机响",
                                Modifier.fillMaxWidth().padding(top = 72.dp),
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                textAlign = TextAlign.Center,
                                color = Glass.InkSoft,
                            )
                        }
                    }
                    items(sortedAlarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            now = now,
                            appZone = alarmZone,
                            onClick = { onEdit(alarm) },
                            onToggle = { onToggle(alarm, it) },
                            onSkipNext = { date -> onSkipNext(alarm, date) },
                            onClearSkips = { onClearSkips(alarm) },
                            onCancelSnooze = { onCancelSnooze(alarm) },
                            onDelete = {
                                undoSeq += 1
                                deletedUndo = undoSeq to alarm
                                onDelete(alarm)
                            },
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            deletedUndo?.let { (_, deleted) ->
                CompositionLocalProvider(LocalGlassBackdrop provides pageLayer) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(20.dp),
                        fill = Color.White.copy(alpha = 0.68f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val title = deleted.label.ifBlank { deleted.windowStart.hm() }
                            Text(
                                "已删除「$title」",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                color = Glass.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            LiquidButton(
                                onClick = {
                                    deletedUndo = null
                                    onRestore(deleted)
                                },
                                modifier = Modifier.height(34.dp),
                                backdrop = pageLayer,
                                tint = Glass.Accent,
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) {
                                Text("撤销", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidButton(
                    onClick = onAdd,
                    modifier = Modifier.height(54.dp),
                    backdrop = pageLayer,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    Icon(GlassIcons.Plus, contentDescription = null, Modifier.size(16.dp))
                    Text("新建闹钟", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                LiquidButton(
                    onClick = onOpenHabits,
                    modifier = Modifier.height(54.dp),
                    backdrop = pageLayer,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    Icon(GlassIcons.CheckCircle, contentDescription = null, Modifier.size(17.dp))
                    Text("习惯打卡", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ListHeader(
    alarmZone: ZoneId,
    now: Instant,
    hasDisabledOneShots: Boolean,
    onSwitchZone: () -> Unit,
    onChangeHome: () -> Unit,
    onDeleteDisabledOneShots: () -> Unit,
    onOpenReliabilityGuide: () -> Unit,
    onShowDiagnostics: (() -> Unit)?,
    onOpenAbout: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.app_name),
            Modifier.weight(1f),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        LiquidButton(
            onClick = onSwitchZone,
            modifier = Modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            onClickLabel = "切换闹钟时区",
        ) {
            Icon(GlassIcons.Globe, contentDescription = null, Modifier.size(14.dp), tint = Glass.Ink)
            Text(
                "${placeName(alarmZone)} · ${shortUtcOffset(alarmZone, now)}",
                fontSize = 13.sp,
                color = Glass.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            var menu by remember { mutableStateOf(false) }
            LiquidButton(onClick = { menu = true }, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(GlassIcons.More, contentDescription = "更多", Modifier.size(18.dp), tint = Glass.Ink)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = RoundedRectangle(16.dp),
            ) {
                DropdownMenuItem(text = { Text("更改常驻时区") }, onClick = { menu = false; onChangeHome() })
                if (hasDisabledOneShots) {
                    DropdownMenuItem(
                        text = { Text("清理已关闭的单次闹钟") },
                        onClick = { menu = false; onDeleteDisabledOneShots() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reliability_menu_item)) },
                    onClick = { menu = false; onOpenReliabilityGuide() },
                )
                if (onShowDiagnostics != null) {
                    DropdownMenuItem(text = { Text("诊断") }, onClick = { menu = false; onShowDiagnostics() })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.about_menu_item)) },
                    onClick = { menu = false; onOpenAbout() },
                )
            }
        }
    }
}

@Composable
private fun AwayCard(alarmZone: ZoneId, home: ZoneId, now: Instant, onSwitchBack: () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(24.dp),
        fill = Color.White.copy(alpha = 0.34f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "闹钟正按「${placeName(alarmZone)}」时间响（当地 ${now.atZone(alarmZone).toLocalTime().hm()}）",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "常驻时区：${placeName(home)}（当地 ${now.atZone(home).toLocalTime().hm()}）· 回去后记得切回",
            Modifier.padding(top = 2.dp),
            fontSize = 12.sp,
            color = Glass.InkSoft,
        )
        GlassDivider(Modifier.padding(vertical = 10.dp))
        LiquidButton(
            onClick = onSwitchBack,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) { Text("切回${placeName(home)}", fontSize = 13.sp) }
    }
}

@Composable
private fun NextAlarmCard(
    next: Pair<Alarm, Instant>?,
    now: Instant,
    appZone: ZoneId,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onSkipNext: (Alarm, LocalDate) -> Unit,
    onCancelSnooze: (Alarm) -> Unit,
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        onClick = next?.let { (alarm, _) -> { onEdit(alarm) } },
    ) {
        Text("下一次响铃", fontSize = 13.sp, color = Glass.InkSoft)
        GlassDivider(Modifier.padding(top = 8.dp, bottom = 6.dp))
        if (next == null) {
            Text("没有要响的闹钟", Modifier.padding(top = 2.dp), fontSize = 20.sp)
        } else {
            val (alarm, at) = next
            val snoozing = alarm.snoozeAt != null && at.toEpochMilli() == alarm.snoozeAt
            val ownZone = NextTriggerCalculator.zoneOf(alarm, appZone)
            val todayInOwnZone = now.atZone(ownZone).toLocalDate()
            val skipDate = if (!snoozing && alarm.isRepeating) {
                NextTriggerCalculator.nextOccurrenceDate(alarm, now, appZone)
            } else {
                null
            }
            Row(Modifier.padding(top = 2.dp)) {
                Text(at.atZone(appZone).toLocalTime().hm(), Modifier.alignByBaseline(), style = clockStyle(64.sp))
                Spacer(Modifier.width(10.dp))
                Text(
                    (if (snoozing) "贪睡中 · " else "") + formatRemaining(now, at),
                    Modifier.alignByBaseline(),
                    fontSize = 14.sp,
                    color = Glass.InkSoft,
                )
            }
            val fixedZone = alarm.zoneId?.let { ZoneId.of(it) }?.takeIf { it.rules != appZone.rules }
            val window = describeWindow(alarm) + (fixedZone?.let { "（${placeName(it)}时间）" } ?: "")
            Text(
                listOfNotNull(alarm.label.ifBlank { null }, window).joinToString(" · "),
                Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = Glass.InkSoft,
            )
            if (snoozing || skipDate != null || !alarm.isRepeating) {
                GlassDivider(Modifier.padding(top = 12.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        snoozing -> {
                            LiquidButton(
                                onClick = { onCancelSnooze(alarm) },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text("取消贪睡", fontSize = 13.sp) }
                        }
                        skipDate != null -> {
                            LiquidButton(
                                onClick = { onSkipNext(alarm, skipDate) },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text(skipButtonLabel(skipDate, todayInOwnZone), fontSize = 13.sp) }
                        }
                        else -> {
                            LiquidButton(
                                onClick = { onToggle(alarm, false) },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text("关闭这次闹钟", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueBanner(issue: PermissionIssue, onFix: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = cardShape(18.dp), fill = Color(0x59FFECE2)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                issue.message,
                Modifier.weight(1f).padding(vertical = 10.dp),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Color(0xFF6B3420),
            )
            TextButton(onClick = onFix, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8A3A1E))) {
                Text("去设置", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ReliabilityBanner(
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(22.dp),
        fill = Color.White.copy(alpha = 0.36f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(GlassIcons.Shield, contentDescription = null, modifier = Modifier.size(15.dp), tint = Glass.Ink)
            Text(
                stringResource(R.string.reliability_banner_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Glass.Ink,
            )
        }
        Text(
            stringResource(R.string.reliability_banner_desc),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Glass.InkSoft,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiquidButton(
                onClick = onOpenGuide,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                tint = Glass.Accent,
            ) {
                Text(
                    stringResource(R.string.reliability_banner_action),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.reliability_banner_dismiss),
                    fontSize = 13.sp,
                    color = Glass.InkSoft,
                )
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Instant,
    appZone: ZoneId,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkipNext: (LocalDate) -> Unit,
    onClearSkips: () -> Unit,
    onCancelSnooze: () -> Unit,
    onDelete: () -> Unit,
) {
    val next = if (alarm.enabled) NextTriggerCalculator.nextIncludingSnooze(alarm, now, appZone) else null
    val snoozing = next != null && alarm.snoozeAt != null && next.toEpochMilli() == alarm.snoozeAt
    val ownZone = NextTriggerCalculator.zoneOf(alarm, appZone)
    val todayInOwnZone = now.atZone(ownZone).toLocalDate()
    val fixedZone = alarm.zoneId?.let { ZoneId.of(it) }?.takeIf { it.rules != appZone.rules }
    val activeSkips = remember(alarm.skipDates, alarm.windowStart, alarm.windowEnd, now, ownZone) {
        activeSkippedDates(alarm, now, ownZone)
    }
    val skipDate = if (alarm.enabled && alarm.isRepeating) {
        NextTriggerCalculator.nextOccurrenceDate(alarm, now, appZone)
    } else {
        null
    }
    var menu by remember { mutableStateOf(false) }

    val main = if (alarm.enabled) Glass.Ink else Glass.InkMuted
    val soft = if (alarm.enabled) Glass.InkSoft else Glass.InkMuted

    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(24.dp),
        fill = Color.White.copy(alpha = if (alarm.enabled) 0.28f else 0.12f),
        rim = if (alarm.enabled) 1f else 0.7f,
        onClick = onClick,
        onLongClick = { menu = true },
    ) {
        Row(Modifier.padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        alarm.windowStart.hm(),
                        Modifier.alignByBaseline(),
                        style = clockStyle(34.sp, FontWeight.Light),
                        color = main,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(windowTail(alarm), Modifier.alignByBaseline(), fontSize = 12.sp, color = soft)
                }
                val styleTag = if (alarm.reminderStyle == ReminderStyle.GENTLE) "轻提醒" else null
                Text(
                    listOfNotNull(alarm.label.ifBlank { null }, styleTag, if (alarm.enabled) describeDays(alarm) else "已关闭")
                        .joinToString(" · "),
                    Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    color = soft,
                )
                if (fixedZone != null) {
                    val converted = alarm.windowStart.atDate(now.atZone(fixedZone).toLocalDate())
                        .atZone(fixedZone).withZoneSameInstant(appZone).toLocalTime()
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(GlassIcons.Globe, contentDescription = null, Modifier.size(12.dp), tint = soft)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${placeName(fixedZone)}时间（= ${placeName(appZone)} ${converted.hm()}）",
                            fontSize = 12.sp,
                            color = soft,
                        )
                    }
                }
                if (activeSkips.isNotEmpty()) {
                    val summary = activeSkips.take(2).joinToString("、") { shortSkipDate(it, todayInOwnZone) } +
                        if (activeSkips.size > 2) " 等 ${activeSkips.size} 天" else ""
                    Text(
                        "已跳过 $summary",
                        Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = soft,
                    )
                }
                if (snoozing) {
                    Text(
                        "贪睡中 · ${next!!.atZone(appZone).toLocalTime().hm()} 响",
                        Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Glass.Accent,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(PillShape)
                        .clickable(onClick = { menu = true })
                        .semantics { contentDescription = "${alarm.label.ifBlank { alarm.windowStart.hm() }} 更多操作" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GlassIcons.More, contentDescription = null, modifier = Modifier.size(18.dp), tint = soft)
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = RoundedRectangle(16.dp),
                ) {
                    if (snoozing) {
                        DropdownMenuItem(
                            text = { Text("取消贪睡") },
                            onClick = { menu = false; onCancelSnooze() },
                        )
                    }
                    if (skipDate != null) {
                        DropdownMenuItem(
                            text = { Text("${skipButtonLabel(skipDate, todayInOwnZone)}（${skipDate.monthValue}月${skipDate.dayOfMonth}日）") },
                            onClick = { menu = false; onSkipNext(skipDate) },
                        )
                    }
                    if (activeSkips.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("恢复已跳过的日期") },
                            onClick = { menu = false; onClearSkips() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("编辑闹钟") },
                        onClick = { menu = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除闹钟", color = Glass.Danger) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            GlassSwitch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "${alarm.label.ifBlank { alarm.windowStart.hm() }} 闹钟开关" },
            )
        }
    }
}

private fun activeSkippedDates(alarm: Alarm, now: Instant, alarmZone: ZoneId): List<LocalDate> {
    val localNow = now.atZone(alarmZone)
    val today = localNow.toLocalDate()
    val end = alarm.windowEnd
    val inCrossMidnightTail = end != null && end.isBefore(alarm.windowStart) && !localNow.toLocalTime().isAfter(end)
    val cutoff = if (inCrossMidnightTail) today.minusDays(1) else today
    return alarm.skipDates.filter { !it.isBefore(cutoff) }.sorted()
}

private fun skipButtonLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "跳过今天"
    today.plusDays(1) -> "跳过明天"
    else -> "跳过 ${date.monthValue}月${date.dayOfMonth}日"
}

private fun shortSkipDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    else -> "${date.monthValue}月${date.dayOfMonth}日"
}

private fun windowTail(alarm: Alarm): String {
    val end = alarm.windowEnd
    val nextDay = if (end != null && end.isBefore(alarm.windowStart)) "（次日）" else ""
    return when {
        alarm.mode == AlarmMode.FIXED || end == null -> "准时响"
        alarm.mode == AlarmMode.REPEAT_IN_WINDOW -> "– ${end.hm()}$nextDay · 每 ${alarm.intervalMinutes} 分钟"
        else -> "– ${end.hm()}$nextDay · 随机一次"
    }
}
