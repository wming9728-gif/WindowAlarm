package cn.xmeverylucky.pane.ui

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.data.BuiltinRingtones
import cn.xmeverylucky.pane.data.RingtoneFiles
import cn.xmeverylucky.pane.domain.Alarm
import cn.xmeverylucky.pane.domain.AlarmMode
import cn.xmeverylucky.pane.domain.HabitType
import cn.xmeverylucky.pane.domain.NextTriggerCalculator
import cn.xmeverylucky.pane.domain.ReminderStyle
import cn.xmeverylucky.pane.ring.GentleReminderNotifier
import cn.xmeverylucky.pane.ring.startCrescendo
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    initial: Alarm,
    alarmZone: ZoneId,
    pinned: List<PinnedZone>,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var timeDialog by remember { mutableStateOf<TimeField?>(null) }
    var dateDialog by remember { mutableStateOf<DateField?>(null) }
    var zoneDialog by remember { mutableStateOf(false) }
    var builtinDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            draft = draft.copy(ringtoneUri = uri?.toString())
        }
    }

    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { RingtoneFiles.import(context, uri) }
            importing = false
            result
                .onSuccess { draft = draft.copy(ringtoneUri = it.toString()) }
                .onFailure { Toast.makeText(context, it.message ?: "导入失败", Toast.LENGTH_LONG).show() }
        }
    }

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
                        .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                        .fadeTopEdge()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = EditTopBarHeight + 8.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TimingCard(draft, alarmZone = alarmZone, onChange = { draft = it }, onPickTime = { timeDialog = it })
                    RepeatCard(draft, onChange = { draft = it }, onPickDate = { dateDialog = it })
                    SettingsCard(
                        draft = draft,
                        alarmZone = alarmZone,
                        importing = importing,
                        onChange = { draft = it },
                        onPickZone = { zoneDialog = true },
                        onPickBuiltin = { builtinDialog = true },
                        onPickSystem = {
                            val existingUri = draft.ringtoneUri?.let(Uri::parse)?.takeIf { it.scheme == "content" }
                            runCatching {
                                ringtonePicker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri),
                                )
                            }.onFailure {
                                Toast.makeText(context, "当前设备不支持系统铃声选择器", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPickMusic = {
                            runCatching {
                                musicPicker.launch(arrayOf("audio/*"))
                            }.onFailure {
                                Toast.makeText(context, "当前设备不支持系统文件选择器", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    UpcomingCard(draft, alarmZone)


                    LiquidButton(
                        onClick = { onSave(draft) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(54.dp),
                    ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    if (initial.id != 0L) {
                        TextButton(onClick = { onDelete(initial) }, modifier = Modifier.fillMaxWidth()) {
                            Text("删除闹钟", fontSize = 14.sp, color = Glass.Danger)
                        }
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
                    .height(EditTopBarHeight + 24.dp),
            )
        }
        EditTopBar(
            title = if (initial.id == 0L) "新建闹钟" else "编辑闹钟",
            backdrop = pageLayer,
            onCancel = onCancel,
            onSave = { onSave(draft) },
            modifier = Modifier.windowInsetsPadding(topInsets),
        )
    }

    if (builtinDialog) {
        BuiltinRingtoneDialog(
            selectedUri = draft.ringtoneUri,
            onDismiss = { builtinDialog = false },
            onSelect = { uri ->
                draft = draft.copy(ringtoneUri = uri)
                builtinDialog = false
            },
        )
    }

    timeDialog?.let { field ->
        val current = if (field == TimeField.START) draft.windowStart else (draft.windowEnd ?: draft.windowStart)
        TimeDialog(
            initial = current,
            draft = draft,
            field = field,
            alarmZone = alarmZone,
            onDismiss = { timeDialog = null },
            onConfirm = { time ->
                draft = if (field == TimeField.START) draft.copy(windowStart = time) else draft.copy(windowEnd = time)
                timeDialog = null
            },
        )
    }

    dateDialog?.let { field ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (draft.oneShotDate ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateDialog = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        draft = if (field == DateField.SKIP) draft.copy(skipDates = draft.skipDates + date)
                        else draft.copy(oneShotDate = date)
                    }
                    dateDialog = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = null }) { Text("取消") } },
        ) { DatePicker(state) }
    }

    if (zoneDialog) {
        TimeZonePickerDialog(
            title = "这个闹钟按哪个时区响？",
            selected = draft.zoneId,
            followOption = "跟随闹钟时区" to "首页切换时区时一起变（当前：${placeName(alarmZone)}）",
            pinned = pinned,
            onDismiss = { zoneDialog = false },
            onSelect = {
                draft = draft.copy(zoneId = it)
                zoneDialog = false
            },
        )
    }
}

private enum class TimeField { START, END }
private enum class DateField { ONE_SHOT, SKIP }

private val EditTopBarHeight = 60.dp

@Composable
private fun EditTopBar(
    title: String,
    backdrop: Backdrop,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(EditTopBarHeight).padding(horizontal = 16.dp)) {
        LiquidButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterStart).height(44.dp),
            backdrop = backdrop,
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) { Text("取消", fontSize = 16.sp) }
        Text(title, Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        LiquidButton(
            onClick = onSave,
            modifier = Modifier.align(Alignment.CenterEnd).height(44.dp),
            backdrop = backdrop,
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun TimingCard(
    draft: Alarm,
    alarmZone: ZoneId,
    onChange: (Alarm) -> Unit,
    onPickTime: (TimeField) -> Unit,
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        NameField(draft.label) { onChange(draft.copy(label = it)) }
        HorizontalDivider(color = Glass.Divider)
        ModeSelector(draft.mode) { mode ->
            val end = draft.windowEnd ?: draft.windowStart.plusMinutes(30)
            onChange(draft.copy(mode = mode, windowEnd = if (mode == AlarmMode.FIXED) draft.windowEnd else end))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (draft.mode == AlarmMode.FIXED) {
                TimeBlock("时间", draft.windowStart, Modifier.weight(1f)) { onPickTime(TimeField.START) }
            } else {
                val end = draft.windowEnd ?: draft.windowStart
                TimeBlock("开始", draft.windowStart, Modifier.weight(1f)) { onPickTime(TimeField.START) }
                Box(
                    Modifier
                        .padding(start = 10.dp, top = 18.dp, end = 10.dp)
                        .width(22.dp)
                        .height(1.dp)
                        .background(Glass.InkSoft),
                )
                TimeBlock(
                    if (end.isBefore(draft.windowStart)) "结束（次日）" else "结束",
                    end,
                    Modifier.weight(1f),
                ) { onPickTime(TimeField.END) }
            }
        }
        when (draft.mode) {
            AlarmMode.REPEAT_IN_WINDOW -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelValue("间隔", "每 ${draft.intervalMinutes} 分钟", labelSize = 13)
                GlassSlider(
                    value = draft.intervalMinutes.toFloat().coerceIn(1f, 120f),
                    onValueChange = { onChange(draft.copy(intervalMinutes = it.roundToInt())) },
                    valueRange = 1f..120f,
                    steps = 118,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        val selected = draft.intervalMinutes == mins
                        val chipShape = PillShape
                        Box(
                            Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(chipShape)
                                .background(if (selected) Glass.Accent.copy(alpha = 0.16f) else Glass.Ink.copy(alpha = 0.06f))
                                .clickable { onChange(draft.copy(intervalMinutes = mins)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${mins}分",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) Glass.Accent else Glass.InkSoft,
                            )
                        }
                    }
                }
            }
            AlarmMode.RANDOM_IN_WINDOW -> Text(
                "每天在这个时段内随机选一个时刻响，同一天内保持不变",
                fontSize = 12.sp,
                color = Glass.InkSoft,
            )
            AlarmMode.FIXED -> Unit
        }
        val now = Instant.now()
        val nextAt = nextTriggerForPreview(draft, alarmZone, now)
        if (nextAt != null) {
            HorizontalDivider(color = Glass.Divider)
            val prefix = if (draft.enabled) "将在 " else "开启后将在 "
            Text(
                "$prefix${formatRemaining(now, nextAt)}响铃",
                fontSize = 13.sp,
                color = Glass.InkSoft,
            )
        }
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("名称", fontSize = 12.sp, color = Glass.InkSoft)
        val shape = RoundedRectangle(12.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "名称（可选）" },
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = Glass.Ink),
            cursorBrush = SolidColor(Glass.Accent),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.4f))
                        .border(1.dp, Glass.Rim, shape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text("可选，例如：起床", fontSize = 16.sp, color = Glass.InkSoft.copy(alpha = 0.6f))
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ModeSelector(selected: AlarmMode, onSelect: (AlarmMode) -> Unit) {
    SegmentedSelector(
        options = listOf(
            AlarmMode.FIXED to "固定时刻",
            AlarmMode.REPEAT_IN_WINDOW to "时段内重复",
            AlarmMode.RANDOM_IN_WINDOW to "时段内随机",
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> SegmentedSelector(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(12.dp))
            .background(Glass.Ink.copy(alpha = 0.07f))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val shape = RoundedRectangle(9.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .then(if (isSelected) Modifier.shadow(2.dp, shape, spotColor = Glass.Shadow) else Modifier)
                    .clip(shape)
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) Glass.Ink else Glass.InkSoft,
                )
            }
        }
    }
}

@Composable
private fun TimeBlock(label: String, time: LocalTime, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedRectangle(16.dp))
            .clickable(onClickLabel = "修改$label", role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 12.sp, color = Glass.InkSoft)
        BasicText(
            time.hm(),
            style = clockStyle(54.sp).copy(color = Glass.Ink, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 54.sp),
        )
    }
}

@Composable
private fun LabelValue(label: String, value: String, labelSize: Int = 15) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = labelSize.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatCard(draft: Alarm, onChange: (Alarm) -> Unit, onPickDate: (DateField) -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("重复", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(describeDays(draft), fontSize = 12.sp, color = Glass.InkSoft)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                DayCircle(day, draft.repeatsOn(day)) {
                    onChange(draft.copy(weekdays = draft.weekdays xor Alarm.dayBit(day)))
                }
            }
        }
        HorizontalDivider(color = Glass.Divider)
        if (draft.isRepeating) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("跳过的日期（如节假日）", fontSize = 12.sp, color = Glass.InkSoft)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draft.skipDates.sorted().forEach { date ->
                        SkipDateChip(date) { onChange(draft.copy(skipDates = draft.skipDates - date)) }
                    }
                    AddChip { onPickDate(DateField.SKIP) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("只响一次", fontSize = 12.sp, color = Glass.InkSoft)
                Text(
                    draft.oneShotDate?.let { "只在 $it 响 · 点击更改" } ?: "最近的那天 · 点击指定日期",
                    Modifier
                        .clip(PillShape)
                        .background(Glass.Chip)
                        .clickable(role = Role.Button) { onPickDate(DateField.ONE_SHOT) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun DayCircle(day: DayOfWeek, selected: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) Glass.Accent else Color.White.copy(alpha = 0.35f))
            .then(if (selected) Modifier else Modifier.border(1.dp, Glass.Rim, CircleShape))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = day.shortCn() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.shortCn().removePrefix("周"),
            Modifier.clearAndSetSemantics {},
            fontSize = 14.sp,
            color = if (selected) Color.White else Glass.Ink,
        )
    }
}

@Composable
private fun SkipDateChip(date: LocalDate, onRemove: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(PillShape)
            .background(Glass.Chip)
            .clickable(onClickLabel = "移除", onClick = onRemove)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$date", fontSize = 13.sp)
        Icon(GlassIcons.Close, contentDescription = null, Modifier.size(14.dp), tint = Glass.InkSoft)
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    val dash = Glass.Accent.copy(alpha = 0.5f)
    Box(
        Modifier
            .height(32.dp)
            .clip(PillShape)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    dash,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋ 添加", fontSize = 13.sp, color = Glass.Accent)
    }
}

@Composable
private fun SettingsCard(
    draft: Alarm,
    alarmZone: ZoneId,
    importing: Boolean,
    onChange: (Alarm) -> Unit,
    onPickZone: () -> Unit,
    onPickBuiltin: () -> Unit,
    onPickSystem: () -> Unit,
    onPickMusic: () -> Unit,
) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
        val zone = NextTriggerCalculator.zoneOf(draft, alarmZone)
        Column(Modifier.clickable(onClick = onPickZone).padding(top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("时区", fontSize = 15.sp)
                Spacer(Modifier.width(12.dp))
                ValueWithChevron(
                    if (draft.zoneId == null) "跟随 · ${placeName(alarmZone)}"
                    else "${placeName(zone)} · ${shortUtcOffset(zone)}",
                    Modifier.weight(1f).wrapContentWidth(Alignment.End),
                )
            }
            Text(
                if (draft.zoneId == null) "在首页切换闹钟时区后，仍按那里的 ${draft.windowStart.hm()} 响"
                else "无论首页切到哪个时区，都按${placeName(zone)}的 ${draft.windowStart.hm()} 响",
                Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = Glass.InkSoft,
            )
        }
        HorizontalDivider(color = Glass.Divider)

        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("提醒方式", fontSize = 15.sp)
            SegmentedSelector(
                options = listOf(
                    ReminderStyle.FULL_ALARM to "全屏闹铃",
                    ReminderStyle.GENTLE to "轻提醒",
                ),
                selected = draft.reminderStyle,
                onSelect = { style ->
                    val nextUri = if (style == ReminderStyle.FULL_ALARM && draft.ringtoneUri == GentleReminderNotifier.SILENT_URI) {
                        null
                    } else {
                        draft.ringtoneUri
                    }
                    onChange(draft.copy(reminderStyle = style, ringtoneUri = nextUri))
                },
            )
            Text(
                if (draft.reminderStyle == ReminderStyle.GENTLE) {
                    "不弹全屏界面，仅横幅通知 + 短提示音或短震动，适合工作时提醒"
                } else {
                    "锁定屏幕时弹出全屏响铃界面并持续响铃"
                },
                fontSize = 12.sp,
                color = Glass.InkSoft,
            )
        }
        HorizontalDivider(color = Glass.Divider)

        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("打卡记录", fontSize = 15.sp)
            SegmentedSelector(
                options = listOf(
                    HabitType.NONE to "普通",
                    HabitType.WATER to "💧 喝水",
                    HabitType.STAND to "🧍 起身活动",
                ),
                selected = draft.habitType,
                onSelect = { type ->
                    val autoLabel = when {
                        draft.label.isNotBlank() -> draft.label
                        type == HabitType.WATER -> "喝水提醒"
                        type == HabitType.STAND -> "起身活动"
                        else -> ""
                    }
                    onChange(draft.copy(habitType = type, label = autoLabel))
                },
            )
            if (draft.habitType != HabitType.NONE) {
                Text(
                    "通知栏可直接点「+1」记录当日次数，并同步在首页统计",
                    fontSize = 12.sp,
                    color = Glass.InkSoft,
                )
            }
        }
        HorizontalDivider(color = Glass.Divider)

        val ringtoneTitle = remember(draft.ringtoneUri, draft.reminderStyle) {
            when (val uri = draft.ringtoneUri) {
                GentleReminderNotifier.SILENT_URI -> "静音（仅震动）"
                null -> if (draft.reminderStyle == ReminderStyle.GENTLE) "系统默认通知音" else "系统默认闹铃"
                else -> BuiltinRingtones.find(uri)?.title
                    ?: RingtoneFiles.displayName(context, uri)
                    ?: runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
                    ?: "系统默认"
            }
        }
        var menu by remember { mutableStateOf(false) }
        SettingRow(Modifier.clickable(onClickLabel = "选择铃声") { menu = true }) {
            Text(if (draft.reminderStyle == ReminderStyle.GENTLE) "提示音" else "铃声", fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (draft.ringtoneUri != GentleReminderNotifier.SILENT_URI) {
                    RingtonePreviewButton(draft.ringtoneUri)
                }
                Box(Modifier.weight(1f, fill = false)) {
                    ValueWithChevron(if (importing) "正在导入…" else ringtoneTitle)
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = RoundedRectangle(16.dp),
                    ) {
                        if (draft.reminderStyle == ReminderStyle.GENTLE) {
                            DropdownMenuItem(
                                text = { Text("静音（仅震动）") },
                                onClick = {
                                    menu = false
                                    onChange(draft.copy(ringtoneUri = GentleReminderNotifier.SILENT_URI))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("系统默认通知音") },
                                onClick = {
                                    menu = false
                                    onChange(draft.copy(ringtoneUri = null))
                                },
                            )
                        }
                        DropdownMenuItem(text = { Text("内置铃声") }, onClick = { menu = false; onPickBuiltin() })
                        DropdownMenuItem(text = { Text("系统铃声") }, onClick = { menu = false; onPickSystem() })
                        DropdownMenuItem(
                            text = { Text("本机音乐") },
                            enabled = !importing,
                            onClick = { menu = false; onPickMusic() },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Glass.Divider)

        val vibrateInteraction = remember { MutableInteractionSource() }
        SettingRow(
            Modifier.toggleable(
                value = draft.vibrate,
                interactionSource = vibrateInteraction,
                indication = null,
                role = Role.Switch,
                onValueChange = { onChange(draft.copy(vibrate = it)) },
            ),
        ) {
            Text("振动", Modifier.weight(1f), fontSize = 15.sp)
            GlassSwitch(checked = draft.vibrate, onCheckedChange = null, interactionSource = vibrateInteraction)
        }
        HorizontalDivider(color = Glass.Divider)

        Column(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LabelValue(if (draft.reminderStyle == ReminderStyle.GENTLE) "稍后提醒" else "贪睡", "${draft.snoozeMinutes} 分钟")
            GlassSlider(
                value = draft.snoozeMinutes.toFloat(),
                onValueChange = { onChange(draft.copy(snoozeMinutes = it.roundToInt())) },
                valueRange = 1f..30f,
                steps = 28,
            )
        }
    }
}

@Composable
private fun SettingRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ValueWithChevron(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            fontSize = 13.sp,
            color = Glass.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(GlassIcons.ChevronRight, contentDescription = null, Modifier.size(14.dp), tint = Glass.InkSoft)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initial: LocalTime,
    draft: Alarm,
    field: TimeField,
    alarmZone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var minuteModified by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(state.minute) {
        if (!initialized) {
            initialized = true
        } else {
            minuteModified = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePicker(state)
                if (minuteModified) {
                    val picked = LocalTime.of(state.hour, state.minute)
                    val previewDraft = if (field == TimeField.START) draft.copy(windowStart = picked) else draft.copy(windowEnd = picked)
                    val now = Instant.now()
                    val nextAt = nextTriggerForPreview(previewDraft, alarmZone, now)
                    val hint = if (nextAt != null) {
                        val prefix = if (draft.enabled) "将在 " else "开启后将在 "
                        "$prefix${formatRemaining(now, nextAt)}响铃"
                    } else {
                        "按当前设置不会再响"
                    }
                    Text(
                        text = hint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (nextAt != null) Glass.Ink else Glass.Danger,
                    )
                }
            }
        },
    )
}

private fun probeForPreview(alarm: Alarm, appZone: ZoneId, now: Instant): Alarm =
    alarm.copy(enabled = true, snoozeAt = null, dismissedUntil = null).let {
        if (!it.isRepeating && it.oneShotDate == null) {
            it.copy(oneShotDate = NextTriggerCalculator.firstOneShotDate(it, now, appZone))
        } else {
            it
        }
    }

private fun nextTriggerForPreview(alarm: Alarm, appZone: ZoneId, now: Instant): Instant? =
    NextTriggerCalculator.next(probeForPreview(alarm, appZone, now), now, appZone)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpcomingCard(draft: Alarm, appZone: ZoneId) {
    val now = Instant.now()
    val probe = probeForPreview(draft, appZone, now)
    val times = NextTriggerCalculator.upcoming(probe, now, appZone, 5)
    val fixedZone = draft.zoneId?.let { ZoneId.of(it) }?.takeIf { it.rules != appZone.rules }
    val today = now.atZone(appZone).toLocalDate()
    val labels = times.mapIndexed { i, t ->
        val local = t.atZone(appZone)
        val sameDayAsPrevious = i > 0 && times[i - 1].atZone(appZone).toLocalDate() == local.toLocalDate()
        val day = if (sameDayAsPrevious) "" else dayLabel(local.toLocalDate(), today) + " "
        val alt = fixedZone?.let { "（${placeName(it)} ${t.atZone(it).toLocalTime().hm()}）" } ?: ""
        "$day${local.toLocalTime().hm()}$alt"
    }

    GlassCard(
        Modifier.fillMaxWidth(),
        fill = Color.White.copy(alpha = 0.20f),
        rim = 0.85f,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "接下来的响铃（${placeName(appZone)}时间）",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Glass.InkSoft,
        )
        HorizontalDivider(color = Glass.Divider)
        if (labels.isEmpty()) Text("按当前设置不会再响", fontSize = 13.sp, color = Glass.Danger)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { label ->
                Text(
                    label,
                    Modifier
                        .clip(PillShape)
                        .background(Glass.Chip)
                        .border(0.6.dp, Glass.Rim, PillShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "今天"
    1L -> "明天"
    in 2L..6L -> date.dayOfWeek.shortCn()
    else -> "${date.monthValue}月${date.dayOfMonth}日"
}

@Composable
private fun RingtonePreviewButton(ringtoneUri: String?) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stop() {
        player?.release()
        player = null
    }
    DisposableEffect(ringtoneUri) { onDispose { stop() } }
    TextButton(
        onClick = {
            if (player != null) return@TextButton stop()
            player = startPreview(context, ringtoneUri) { stop() }
        },
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) { Text(if (player != null) "停止" else "试听", fontSize = 13.sp) }
}

@Composable
private fun BuiltinRingtoneDialog(selectedUri: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(BuiltinRingtones.find(selectedUri) ?: BuiltinRingtones.all.first()) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stop() {
        player?.release()
        player = null
    }
    DisposableEffect(Unit) { onDispose { stop() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("内置铃声") },
        text = {
            Column {
                BuiltinRingtones.all.forEach { tone ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chosen = tone
                            stop()
                            player = startPreview(context, BuiltinRingtones.uri(context, tone)) { stop() }
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tone == chosen, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(tone.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                tone.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "点一下即可试听（按闹钟音量播放）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(BuiltinRingtones.uri(context, chosen)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun startPreview(context: android.content.Context, ringtoneUri: String?, onDone: () -> Unit): MediaPlayer? {
    val uri = ringtoneUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val mp = MediaPlayer()
    return runCatching {
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        mp.setDataSource(context, uri)
        mp.setOnCompletionListener { onDone() }
        mp.prepare()
        BuiltinRingtones.find(ringtoneUri)?.crescendoMs?.takeIf { it > 0 }?.let { ms ->
            startCrescendo(mp, ms, Handler(Looper.getMainLooper())) { runCatching { mp.isPlaying }.getOrDefault(false) }
        }
        mp.start()
        mp
    }.getOrElse {
        mp.release()
        Toast.makeText(context, "无法播放这个铃声", Toast.LENGTH_SHORT).show()
        null
    }
}
