package cn.xmeverylucky.pane.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.App
import cn.xmeverylucky.pane.diag.DiagLog
import cn.xmeverylucky.pane.ring.RingService

@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(buildReport(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("诊断记录") },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("诊断记录", report))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }) { Text("复制") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    DiagLog.clear()
                    report = buildReport(context)
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

private fun buildReport(context: Context): String {
    val scheduler = App.of(context).scheduler
    val nm = context.getSystemService(NotificationManager::class.java)
    val status = listOf(
        "设备：${Build.MANUFACTURER} ${Build.MODEL}，Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
        "精确闹钟权限：${scheduler.canScheduleExact()}",
        "通知已允许：${nm.areNotificationsEnabled()}",
        "全屏通知权限：${Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()}",
        "响铃通知重要程度：${RingService.ringChannelImportance(context)}（4 = 紧急，低于 4 锁屏不弹界面）",
        "悬浮窗权限：${Settings.canDrawOverlays(context)}",
        "上次锁屏响铃界面未弹出：${RingService.lockScreenPopupFailed(context)}",
        "已忽略电池优化：${context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)}",
        "推断本次开机广播被拦截：${scheduler.bootBroadcastBlocked()}",
        DiagLog.nextAlarmClock(context.getSystemService(AlarmManager::class.java)),
    )
    val entries = mutableListOf<String>()
    DiagLog.read().trimEnd().lines().filter { it.isNotEmpty() }.forEach { line ->
        if (line.startsWith("    ") && entries.isNotEmpty()) entries[entries.lastIndex] += "\n" + line else entries += line
    }
    val log = entries.reversed().joinToString("\n").ifBlank { "（暂无记录）" }
    return status.joinToString("\n") + "\n\n—— 记录（最新在上）——\n" + log
}
