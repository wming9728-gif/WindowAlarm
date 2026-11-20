package cn.xmeverylucky.pane.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val AboutTopBarHeight = 60.dp

/** 应用内「关于与隐私」页面：版本信息、隐私政策、用户协议、权限清单、使用引导、开源许可、撤回同意。 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenReliabilityGuide: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onRevokePrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val versionInfo = remember(context) { readAppVersion(context) }
    var activeDoc by remember { mutableStateOf<LegalDoc?>(null) }
    var showRevokeConfirm by remember { mutableStateOf(false) }

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
                        .padding(start = 20.dp, top = AboutTopBarHeight + 8.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = cardShape(26.dp),
                        fill = Color.White.copy(alpha = 0.36f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_pane_app_icon),
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(cardShape(14.dp))
                                    .border(0.7.dp, Glass.Rim, cardShape(14.dp)),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.app_name),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Glass.Ink,
                                )
                                Text(
                                    stringResource(R.string.about_version_format, versionInfo.first, versionInfo.second),
                                    fontSize = 13.sp,
                                    color = Glass.InkSoft,
                                )
                            }
                        }
                        GlassDivider()
                        Text(
                            stringResource(R.string.about_tagline),
                            fontSize = 13.sp,
                            color = Glass.InkSoft,
                        )
                    }

                    AboutSectionCard(title = stringResource(R.string.about_section_legal)) {
                        AboutRow(stringResource(R.string.about_item_privacy)) {
                            activeDoc = LegalDoc.PRIVACY_POLICY
                        }
                        GlassDivider()
                        AboutRow(stringResource(R.string.about_item_agreement)) {
                            activeDoc = LegalDoc.USER_AGREEMENT
                        }
                        GlassDivider()
                        AboutRow(stringResource(R.string.about_item_permissions)) {
                            activeDoc = LegalDoc.PERMISSIONS
                        }
                    }

                    AboutSectionCard(title = stringResource(R.string.about_section_help)) {
                        AboutRow(stringResource(R.string.about_item_reliability), onClick = onOpenReliabilityGuide)
                        GlassDivider()
                        AboutRow(stringResource(R.string.about_item_guide), onClick = onOpenOnboarding)
                        GlassDivider()
                        AboutRow(stringResource(R.string.about_item_licenses)) {
                            activeDoc = LegalDoc.LICENSES
                        }
                        GlassDivider()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.about_contact_label),
                                fontSize = 15.sp,
                                color = Glass.Ink,
                            )
                            Text(
                                stringResource(R.string.developer_contact),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Glass.InkSoft,
                            )
                        }
                    }

                    AboutSectionCard(title = stringResource(R.string.about_section_control)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showRevokeConfirm = true }
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.about_item_revoke),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Glass.Danger,
                            )
                            Text(
                                stringResource(R.string.about_item_revoke_desc),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Glass.InkSoft,
                            )
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
                    .height(AboutTopBarHeight + 24.dp),
            )
        }
        AboutTopBar(
            backdrop = pageLayer,
            onBack = onBack,
            modifier = Modifier.windowInsetsPadding(topInsets),
        )
    }

    activeDoc?.let { doc ->
        LegalDocumentDialog(
            doc = doc,
            onDismiss = { activeDoc = null },
            onOpenSystemSettings = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
        )
    }

    if (showRevokeConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text(stringResource(R.string.about_revoke_dialog_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.about_revoke_dialog_message), fontSize = 14.sp, lineHeight = 21.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeConfirm = false
                    onRevokePrivacy()
                }) {
                    Text(stringResource(R.string.about_revoke_confirm), color = Glass.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AboutTopBar(
    backdrop: Backdrop,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(AboutTopBarHeight).padding(horizontal = 16.dp)) {
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
            stringResource(R.string.about_title),
            Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = cardShape(24.dp),
        fill = Color.White.copy(alpha = 0.32f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Glass.InkSoft,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun AboutRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, color = Glass.Ink)
        Icon(GlassIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Glass.InkSoft)
    }
}

private fun readAppVersion(context: android.content.Context): Pair<String, Long> = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    (info.versionName ?: "1.0") to code
}.getOrDefault("1.0" to 1L)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AboutScreenPreview() {
    GlassTheme {
        AboutScreen(onBack = {}, onOpenReliabilityGuide = {}, onOpenOnboarding = {}, onRevokePrivacy = {})
    }
}
