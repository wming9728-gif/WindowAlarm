package cn.xmeverylucky.pane.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import cn.xmeverylucky.pane.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

object Glass {
    val BaseTop = Color(0xFFFAFAFC)
    val BaseBottom = Color(0xFFEBEDF1)
    val Ink = Color(0xFF18181B)
    val InkSoft = Color(0xFF52525B)
    val InkMuted = Color(0xFF71717A)
    val Accent = Color(0xFF18181B)
    val SwitchOn = Color(0xFF34C759)
    val SwitchOff = Color(0xFFC4C8CF)
    val Danger = Color(0xFFB3261E)
    val Fill = Color.White.copy(alpha = 0.28f)
    val Shadow = Color.Black.copy(alpha = 0.10f)
    val Rim = Color.Black.copy(alpha = 0.14f)
    val Divider = Color.Black.copy(alpha = 0.09f)
    val Chip = Color.White.copy(alpha = 0.62f)
    val Track = Color.Black.copy(alpha = 0.14f)
}

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) =
    Font(R.font.outfit, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Outfit = FontFamily(outfit(FontWeight.ExtraLight), outfit(FontWeight.Light), outfit(FontWeight.Normal))

fun clockStyle(size: TextUnit, weight: FontWeight = FontWeight.ExtraLight) = TextStyle(
    fontFamily = Outfit,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * 1.1f,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum",
)

private val GlassColors = lightColorScheme(
    primary = Glass.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Glass.Ink,
    secondary = Glass.InkSoft,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Glass.Ink,
    tertiary = Color(0xFF3F3F46),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E4E7),
    onTertiaryContainer = Glass.Ink,
    background = Glass.BaseTop,
    onBackground = Glass.Ink,
    surface = Color(0xFFFCFCFD),
    onSurface = Glass.Ink,
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Glass.InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF4F4F5),
    surfaceContainerHigh = Color(0xFFEEEEF0),
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFFD4D4D8),
    error = Glass.Danger,
)

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
            ?: return@DisposableEffect onDispose {}
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    MaterialTheme(colorScheme = GlassColors) {
        CompositionLocalProvider(LocalContentColor provides Glass.Ink, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class Glow(val color: Color, val alpha: Float, val x: Float, val y: Float, val radius: Dp, val blur: Dp)

val ListGlows = listOf(
    Glow(Color(0xFFD4D6DC), 0.70f, x = 0.12f, y = 0.10f, radius = 170.dp, blur = 70.dp),
    Glow(Color(0xFFE2E4E9), 0.85f, x = 0.92f, y = 0.45f, radius = 160.dp, blur = 70.dp),
    Glow(Color(0xFFC8CBD3), 0.55f, x = 0.22f, y = 0.92f, radius = 170.dp, blur = 80.dp),
)

val EditGlows = listOf(
    Glow(Color(0xFFD4D6DC), 0.70f, x = 0.88f, y = 0.08f, radius = 180.dp, blur = 70.dp),
    Glow(Color(0xFFE2E4E9), 0.85f, x = 0.06f, y = 0.42f, radius = 160.dp, blur = 70.dp),
    Glow(Color(0xFFD4D6DC), 0.60f, x = 0.92f, y = 0.76f, radius = 170.dp, blur = 80.dp),
    Glow(Color(0xFFC8CBD3), 0.50f, x = 0.16f, y = 1.00f, radius = 160.dp, blur = 80.dp),
)

private val GLOW_EDGE = listOf(-2f to 0.98f, -1f to 0.84f, 0f to 0.5f, 1f to 0.16f, 2f to 0.02f, 2.5f to 0f)

fun Modifier.glassBackground(glows: List<Glow>): Modifier = drawWithCache {
    val bgBrush = Brush.verticalGradient(listOf(Glass.BaseTop, Glass.BaseBottom))
    val cachedGlows = glows.map { glow ->
        val r = glow.radius.toPx()
        val blur = glow.blur.toPx()
        val outer = r + GLOW_EDGE.last().first * blur
        val center = Offset(size.width * glow.x, size.height * glow.y)
        val stops = GLOW_EDGE.map { (d, cover) ->
            ((r + d * blur) / outer).coerceIn(0f, 1f) to glow.color.copy(alpha = glow.alpha * cover)
        }
        Triple(Brush.radialGradient(*stops.toTypedArray(), center = center, radius = outer), outer, center)
    }
    onDrawBehind {
        drawRect(bgBrush)
        cachedGlows.forEach { (brush, outer, center) ->
            drawCircle(brush, outer, center)
        }
    }
}

fun Modifier.fadeTopEdge(height: Dp = 16.dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Black.copy(alpha = 0.2f),
                1f to Color.Black,
                startY = 0f,
                endY = h,
            ),
            size = Size(size.width, h),
            blendMode = BlendMode.DstIn,
        )
    }

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.75.dp)
            .background(Glass.Divider),
    )
}

fun cardShape(radius: Dp = 28.dp) = RoundedRectangle(radius)

val PillShape = Capsule(RoundedCornerStyle.Circular)

private fun DrawScope.drawGlassSurface(fill: Color) {
    drawRect(
        Brush.verticalGradient(
            listOf(fill.copy(alpha = (fill.alpha * 1.5f).coerceAtMost(1f)), fill.copy(alpha = fill.alpha * 0.8f)),
        ),
    )
    drawRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.2f),
            0.4f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedRectangle = cardShape(),
    fill: Color = Glass.Fill,
    rim: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(minOf(14.dp, shape.cornerRadius * 0.6f).toPx(), 32.dp.toPx())
                },
                highlight = { Highlight(width = 1.dp, alpha = rim) },
                shadow = { Shadow(radius = 14.dp, color = Glass.Shadow) },
                onDrawSurface = { drawGlassSurface(fill) },
            )
            .border(0.8.dp, Glass.Rim.copy(alpha = (Glass.Rim.alpha * rim).coerceAtLeast(0.08f)), shape)
            .clip(shape)
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.06f else 1f, label = "press")
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { PillShape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(minOf(12.dp.toPx(), size.minDimension / 2f), minOf(24.dp.toPx(), size.minDimension))
                },
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                shadow = { Shadow(radius = 10.dp, color = (tint ?: Glass.Ink).copy(alpha = 0.16f)) },
                onDrawSurface = {
                    if (tint != null) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.72f))
                    } else {
                        drawRect(Color.White.copy(alpha = 0.42f))
                    }
                },
            )
            .then(if (tint == null) Modifier.border(0.8.dp, Glass.Rim, PillShape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (tint != null) Color.White else Glass.Accent) {
            content()
        }
    }
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) Glass.SwitchOn else Glass.SwitchOff, label = "track")
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "position")
    val knobWidth by animateDpAsState(if (pressed) 33.dp else 27.dp, label = "knobWidth")
    Box(
        modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .toggleable(
                            value = checked,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(51.dp, 31.dp)
                .background(track, PillShape)
                .border(0.6.dp, Color.Black.copy(alpha = 0.10f), PillShape),
        ) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (2.dp + (47.dp - knobWidth) * position).roundToPx(),
                            y = 2.dp.roundToPx(),
                        )
                    }
                    .size(knobWidth, 27.dp)
                    .shadow(3.dp, PillShape)
                    .background(Color.White, PillShape)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.08f), PillShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(0.6.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
            )
        },
        track = { state ->
            val range = state.valueRange
            val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(Glass.Track, cornerRadius = radius)
                drawRoundRect(Glass.Accent, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            }
        },
    )
}

object GlassIcons {
    val Globe = strokeIcon(
        "Globe",
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18",
    )
    val ChevronLeft = strokeIcon("ChevronLeft", "M15 6l-6 6 6 6")
    val ChevronRight = strokeIcon("ChevronRight", "M9 6l6 6-6 6")
    val Plus = strokeIcon("Plus", "M12 5v14M5 12h14", width = 2.2f)
    val Close = strokeIcon("Close", "M6 6l12 12M18 6L6 18")
    val More = strokeIcon("More", "M5 12h.01M12 12h.01M19 12h.01", width = 3f)
    val CheckCircle = strokeIcon(
        "CheckCircle",
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M8.5 12.5l2.5 2.5 4.5-5",
    )
    val Shield = strokeIcon(
        "Shield",
        "M12 3l7 3v5c0 4.6-3 8.4-7 10-4-1.6-7-5.4-7-10V6l7-3zM9.5 12l1.8 1.8 3.7-3.8",
    )
    val Bell = strokeIcon(
        "Bell",
        "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M10.3 21a2 2 0 0 0 3.4 0",
    )
    val Clock = strokeIcon(
        "Clock",
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M12 7v5l3.5 2",
    )
    val Info = strokeIcon(
        "Info",
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M12 16v-4M12 8h.01",
    )
}

private fun strokeIcon(name: String, path: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
