package cn.xmeverylucky.pane.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xmeverylucky.pane.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

enum class LegalDoc {
    PRIVACY_POLICY,
    USER_AGREEMENT,
    PERMISSIONS,
    LICENSES,
}

/**
 * 首次启动时的隐私政策与用户协议同意页。
 * 用户点击「同意并继续」前，应用不会主动弹窗申请任何系统运行时权限。
 */
@Composable
fun PrivacyConsentScreen(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
) {
    var activeDoc by remember { mutableStateOf<LegalDoc?>(null) }
    val backgroundLayer = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backgroundLayer).glassBackground(ListGlows))
        CompositionLocalProvider(LocalGlassBackdrop provides backgroundLayer) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(Glass.Chip)
                            .border(0.7.dp, Glass.Rim, PillShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(GlassIcons.Shield, contentDescription = null, modifier = Modifier.size(15.dp), tint = Glass.Ink)
                        Text(
                            stringResource(R.string.privacy_consent_badge),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Glass.Ink,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.privacy_consent_title),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Glass.Ink,
                        )
                        Text(
                            stringResource(R.string.privacy_consent_subtitle),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Glass.InkSoft,
                        )
                    }

                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = cardShape(26.dp),
                        fill = Color.White.copy(alpha = 0.36f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.privacy_summary_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Glass.Ink,
                        )
                        GlassDivider()
                        PrivacyBullet("1", stringResource(R.string.privacy_summary_point_1))
                        PrivacyBullet("2", stringResource(R.string.privacy_summary_point_2))
                        PrivacyBullet("3", stringResource(R.string.privacy_summary_point_3))
                    }

                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = cardShape(22.dp),
                        fill = Color.White.copy(alpha = 0.28f),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.privacy_read_prefix),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Glass.InkSoft,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.privacy_policy_label),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Glass.Accent,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clip(PillShape)
                                    .clickable { activeDoc = LegalDoc.PRIVACY_POLICY }
                                    .padding(vertical = 4.dp),
                            )
                            Text(
                                stringResource(R.string.user_agreement_label),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Glass.Accent,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clip(PillShape)
                                    .clickable { activeDoc = LegalDoc.USER_AGREEMENT }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LiquidButton(
                        onClick = onAgree,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        tint = Glass.Accent,
                    ) {
                        Text(
                            stringResource(R.string.privacy_agree_button),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(
                        onClick = onDisagree,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(
                            stringResource(R.string.privacy_disagree_button),
                            fontSize = 14.sp,
                            color = Glass.InkSoft,
                        )
                    }
                }
            }
        }
    }

    activeDoc?.let { doc ->
        LegalDocumentDialog(doc = doc, onDismiss = { activeDoc = null })
    }
}

@Composable
private fun PrivacyBullet(index: String, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .clip(PillShape)
                .background(Glass.Chip)
                .border(0.6.dp, Glass.Rim, PillShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(index, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Glass.Ink)
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Glass.Ink,
        )
    }
}

/** 通用合规文档弹窗：用于《隐私政策》《用户协议》《权限使用说明》《开源与素材许可》。 */
@Composable
fun LegalDocumentDialog(
    doc: LegalDoc,
    onDismiss: () -> Unit,
    onOpenSystemSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val title = when (doc) {
        LegalDoc.PRIVACY_POLICY -> stringResource(R.string.about_item_privacy)
        LegalDoc.USER_AGREEMENT -> stringResource(R.string.about_item_agreement)
        LegalDoc.PERMISSIONS -> stringResource(R.string.about_item_permissions)
        LegalDoc.LICENSES -> stringResource(R.string.about_item_licenses)
    }
    val body = when (doc) {
        LegalDoc.PRIVACY_POLICY -> stringResource(R.string.privacy_policy_content)
        LegalDoc.USER_AGREEMENT -> stringResource(R.string.user_agreement_content)
        LegalDoc.PERMISSIONS -> stringResource(R.string.permissions_detail_content)
        LegalDoc.LICENSES -> stringResource(R.string.licenses_content)
    }
    val webUrl = when (doc) {
        LegalDoc.PRIVACY_POLICY -> stringResource(R.string.privacy_policy_url)
        LegalDoc.USER_AGREEMENT -> stringResource(R.string.user_agreement_url)
        else -> ""
    }.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(body, fontSize = 13.sp, lineHeight = 21.sp, color = Glass.Ink)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_dialog_close))
            }
        },
        dismissButton = when {
            webUrl.isNotEmpty() -> {
                {
                    TextButton(onClick = { openUrlSafely(context, webUrl) }) {
                        Text(stringResource(R.string.about_open_in_browser))
                    }
                }
            }
            doc == LegalDoc.PERMISSIONS && onOpenSystemSettings != null -> {
                {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenSystemSettings()
                    }) {
                        Text(stringResource(R.string.about_open_settings))
                    }
                }
            }
            else -> null
        },
    )
}

internal fun openUrlSafely(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "未找到可打开网页的应用", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PrivacyConsentScreenPreview() {
    GlassTheme {
        PrivacyConsentScreen(onAgree = {}, onDisagree = {})
    }
}
