package cn.xmeverylucky.pane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.RoundedRectangle
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class PinnedZone(val id: String, val tag: String)

private data class ZoneOption(val id: String, val place: String, val label: String, val offsetSeconds: Int)

private fun optionOf(id: String, now: Instant): ZoneOption {
    val zone = ZoneId.of(id)
    return ZoneOption(id, placeName(zone), zoneLabel(zone, now), zone.rules.getOffset(now).totalSeconds)
}

private fun nowAt(id: String): String = " · 当地 ${LocalTime.now(ZoneId.of(id)).hm()}"

@Composable
fun TimeZoneList(
    selected: String?,
    followOption: Pair<String, String>?,
    pinned: List<PinnedZone>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { Instant.now() }
    val all = remember {
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .map { optionOf(it, now) }
            .sortedWith(compareBy({ it.offsetSeconds }, { it.id }))
    }
    val pinnedOptions = remember(pinned) { pinned.distinctBy { it.id }.map { optionOf(it.id, now) to it.tag } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter {
            it.id.contains(q, ignoreCase = true) || it.place.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true)
        }
    }

    Column(modifier) {
        val searchShape = RoundedRectangle(14.dp)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Glass.Ink),
            cursorBrush = SolidColor(Glass.Accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索时区" },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(searchShape)
                        .background(Color.White.copy(alpha = 0.55f))
                        .border(0.8.dp, Glass.Rim, searchShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text("搜索：城市英文名 / 中文地名 / +09", fontSize = 13.sp, color = Glass.InkSoft.copy(alpha = 0.7f))
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            if (query.isBlank()) {
                followOption?.let { (title, subtitle) ->
                    item { ZoneRow(title, subtitle, null, selected == null) { onSelect(null) } }
                }
                items(pinnedOptions, key = { "pin-${it.first.id}" }) { (option, tag) ->
                    ZoneRow("${option.place}  ${option.id}", option.label + nowAt(option.id), tag, selected == option.id) {
                        onSelect(option.id)
                    }
                }
                if (pinnedOptions.isNotEmpty() || followOption != null) {
                    item {
                        Text(
                            "全部时区",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Glass.InkSoft,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { option ->
                ZoneRow("${option.place}  ${option.id}", option.label + nowAt(option.id), null, selected == option.id) {
                    onSelect(option.id)
                }
            }
        }
    }
}

@Composable
fun TimeZonePickerDialog(
    title: String,
    selected: String?,
    followOption: Pair<String, String>?,
    pinned: List<PinnedZone>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = { TimeZoneList(selected, followOption, pinned, onSelect, Modifier.height(480.dp)) },
    )
}

@Composable
private fun ZoneRow(title: String, subtitle: String, tag: String?, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                (if (selected) "✓ " else "") + title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Glass.Accent else Glass.Ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (tag != null) {
                Text(
                    tag,
                    fontSize = 11.sp,
                    color = Glass.InkSoft,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Glass.Chip)
                        .border(0.6.dp, Glass.Rim, PillShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(subtitle, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Glass.InkSoft)
    }
    GlassDivider()
}
