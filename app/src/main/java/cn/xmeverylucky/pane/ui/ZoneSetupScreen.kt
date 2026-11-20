package cn.xmeverylucky.pane.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun ZoneSetupScreen(pinned: List<PinnedZone>, onSelect: (String) -> Unit) {
    GlassTheme {
        val backgroundLayer = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backgroundLayer).glassBackground(ListGlows))
            CompositionLocalProvider(LocalGlassBackdrop provides backgroundLayer) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("选择常驻时区", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Glass.Ink)
                        Text(
                            "闹钟会按 App 里设定的时区计算，不需要修改手机的系统时区。\n" +
                                "出行时可在首页临时切换；回来后一键切回常驻时区。",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Glass.InkSoft,
                        )
                    }
                    GlassCard(
                        Modifier.fillMaxWidth().weight(1f),
                        shape = cardShape(26.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        TimeZoneList(
                            selected = null,
                            followOption = null,
                            pinned = pinned,
                            onSelect = { id -> if (id != null) onSelect(id) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
