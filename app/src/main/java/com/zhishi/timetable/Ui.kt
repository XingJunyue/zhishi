package com.zhishi.timetable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 标题也用系统无衬线粗体（参考图是干净的无衬线「第1周」）。名字保留，免得到处改。 */
val Serif: FontFamily = FontFamily.Default

private fun scheme() = if (C.dark) darkColorScheme(
    primary = C.Clay, onPrimary = C.OnClay, primaryContainer = C.ClayPale, onPrimaryContainer = C.ClayDeep,
    secondary = C.Sage, background = C.Cream, onBackground = C.Ink, surface = C.Card, onSurface = C.Ink,
    surfaceVariant = C.Sand, onSurfaceVariant = C.Soft, outline = C.Track, outlineVariant = C.Track,
    surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Sand, surfaceContainerLow = C.Card, surfaceContainerLowest = C.Cream,
) else lightColorScheme(
    primary = C.Clay, onPrimary = C.OnClay, primaryContainer = C.ClayPale, onPrimaryContainer = C.ClayDeep,
    secondary = C.Sage, background = C.Cream, onBackground = C.Ink, surface = C.Card, onSurface = C.Ink,
    surfaceVariant = C.Sand, onSurfaceVariant = C.Soft, outline = C.Track, outlineVariant = C.Track,
    surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Sand, surfaceContainerLow = C.Card, surfaceContainerLowest = C.Cream,
)

private val Shapes3 = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZhishiTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typo = base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
    MaterialTheme(colorScheme = scheme(), shapes = Shapes3, typography = typo) {
        // 液态玻璃很透：字后面衬一圈很淡的光晕（浅色白、深色黑），插画花哨的地方也读得清
        val halo = androidx.compose.ui.graphics.Shadow(
            color = if (C.dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.7f),
            offset = Offset.Zero, blurRadius = 6f)
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(shadow = halo), content = content)
    }
}

/** 页面大标题：衬线、略带字距。 */
@Composable
fun PageTitle(t: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(t, fontSize = 28.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = C.Ink, letterSpacing = 1.sp)
        if (sub != null) Text(sub, fontSize = 13.sp, color = C.Soft, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, brush: Brush? = null, pad: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .liquidGlass(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier)
            .padding(pad),
        content = content
    )
}

@Composable
fun Chip(text: String, warm: Boolean = false) {
    Text(
        text, fontSize = 12.sp,
        color = if (warm) C.ClayDeep else C.SageInk,
        modifier = Modifier.padding(top = 8.dp, end = 6.dp)
            .liquidGlass(CircleShape, tint = if (warm) C.Clay else C.Sage, fill = (if (warm) C.ClayPale else C.SagePale).copy(alpha = 0.55f), shadow = 0.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    // 主色染的玻璃胶囊：主色半透，带亮边和顶部水光
    Box(
        modifier.fillMaxWidth().height(52.dp)
            .liquidGlass(CircleShape, tint = C.Clay, fill = if (enabled) C.Clay.copy(alpha = 0.86f) else C.Track.copy(alpha = 0.6f), shadow = if (enabled) 8.dp else 0.dp)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = if (enabled) C.OnClay else C.Faint,
        style = LocalTextStyle.current.copy(shadow = null)) }
}

/** 次要按钮：浅底主色字。 */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).liquidGlass(CircleShape, shadow = 4.dp).clickable { onClick() }.padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = C.ClayDeep, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 只在界面真正显示在前台时才循环执行：切到后台、锁屏后立即停下，回来再继续。
 */
@Composable
fun Every(ms: Long, now: Boolean = true, block: () -> Unit) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val cb = rememberUpdatedState(block)
    LaunchedEffect(resumed) {
        if (resumed && now) cb.value()
        while (resumed) { kotlinx.coroutines.delay(ms); cb.value() }
    }
}

@Composable
fun Segments(options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().liquidGlass(CircleShape, shadow = 0.dp).padding(4.dp)) {
        options.forEachIndexed { i, t ->
            val on = i == sel
            val bg by animateColorAsState(if (on) C.Card.copy(alpha = if (C.dark) 0.45f else 0.7f) else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (on) C.ClayDeep else C.Soft, label = "segT")
            Box(
                Modifier.weight(1f).clip(CircleShape).background(bg).clickable { onSel(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) { Text(t, fontSize = 13.sp, color = fg, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
        }
    }
}

@Composable
fun SectionTitle(t: String, sub: String? = null) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.ClayDeep, letterSpacing = 1.sp, modifier = Modifier.padding(top = 24.dp, start = 4.dp))
    if (sub != null) Text(sub, fontSize = 12.sp, color = C.Faint, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp, start = 4.dp))
    else Spacer(Modifier.height(8.dp))
}

@Composable
fun StepSlider(label: String, value: Int, range: IntRange, step: Int, unit: String, fmt: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 13.sp, color = C.Soft, modifier = Modifier.weight(1f))
        Text(fmt(value), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ClayDeep, fontFamily = Serif)
        if (unit.isNotEmpty()) Text(" $unit", fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(bottom = 2.dp))
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(((Math.round(it / step) * step).coerceIn(range.first, range.last))) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        colors = SliderDefaults.colors(thumbColor = C.Clay, activeTrackColor = C.Clay, inactiveTrackColor = C.Track)
    )
}

@Composable
fun Note(t: String, warm: Boolean = false) {
    Text(
        t, fontSize = 13.sp, color = if (warm) C.ClayDeep else C.SageInk, lineHeight = 21.sp,
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth()
            .liquidGlass(RoundedCornerShape(18.dp), tint = if (warm) C.Clay else C.Sage, fill = (if (warm) C.ClayPale else C.SagePale).copy(alpha = 0.5f), shadow = 0.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
fun Toggle(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!on) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = C.Ink, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = C.Faint, lineHeight = 17.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Clay, checkedThumbColor = C.OnClay,
            uncheckedTrackColor = C.Track, uncheckedBorderColor = C.Track, uncheckedThumbColor = C.Faint))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowLite(content: @Composable RowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth()) { content() }
}

/** 应用图标（和桌面上的一样：用户给的那张图，取中间桌面会显示的部分）。 */
@Composable
fun AppIcon(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_bg), null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayerScale(1.4f))
    }
}
private fun Modifier.graphicsLayerScale(k: Float) = this.then(androidx.compose.ui.Modifier.graphicsLayer { scaleX = k; scaleY = k })

/**
 * 整屏插画背景：当前皮肤 + 深浅色那张图，铺满（插画在右上、右边和底部，中间留白）。
 * 上面再盖一层很淡的底色，保证文字对比度。
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val s = Skins.skinState.value
    val dark = C.dark
    val res = s.art(dark)
    Box(modifier.fillMaxSize().background(C.Cream)) {
        if (res != 0) androidx.compose.animation.Crossfade(res, label = "art") { r ->
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(r), null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize())
        }
        // 左侧（时间列、课块所在）再轻轻提亮 / 压暗一点
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(C.Cream.copy(alpha = if (dark) 0.35f else 0.40f), C.Cream.copy(alpha = if (dark) 0.10f else 0.08f), Color.Transparent))))
    }
}

/** 二级页面左上角的返回按钮。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    Box(Modifier.size(40.dp).liquidGlass(CircleShape, shadow = 4.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
        val ink = C.Ink
        Canvas(Modifier.size(18.dp)) {
            val w = size.width; val sw = w * 0.11f
            drawLine(ink, Offset(w * 0.62f, w * 0.18f), Offset(w * 0.3f, w * 0.5f), sw, StrokeCap.Round)
            drawLine(ink, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.62f, w * 0.82f), sw, StrokeCap.Round)
        }
    }
}

/** 品牌标记：一滴雨，里面是钟的两根指针（好雨知时节）。 */
@Composable
fun Logo(size: Dp, drop: Color = C.Clay, hand: Color = C.OnClay) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 48f
        val p = Path().apply {
            moveTo(24f * u, 3f * u)
            cubicTo(24f * u, 3f * u, 9f * u, 20f * u, 9f * u, 30f * u)
            cubicTo(9f * u, 38.5f * u, 15.7f * u, 45f * u, 24f * u, 45f * u)
            cubicTo(32.3f * u, 45f * u, 39f * u, 38.5f * u, 39f * u, 30f * u)
            cubicTo(39f * u, 20f * u, 24f * u, 3f * u, 24f * u, 3f * u); close()
        }
        drawPath(p, drop)
        drawLine(hand, Offset(24f * u, 31f * u), Offset(24f * u, 22f * u), 3f * u, StrokeCap.Round)
        drawLine(hand, Offset(24f * u, 31f * u), Offset(30f * u, 34.5f * u), 3f * u, StrokeCap.Round)
        drawCircle(hand, 1.9f * u, Offset(24f * u, 31f * u))
    }
}

/** 空状态插画：斜斜的细雨落在一株新芽上，雨丝缓缓下落。 */
@Composable
fun RainArt(modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val t = rememberInfiniteTransition(label = "rain")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "fall")
    val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val rain = C.Clay; val pale = C.ClayPale; val sage = C.Sage; val ink = C.SageInk
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawCircle(pale, w * 0.42f, Offset(w / 2, h * 0.52f))
        // 雨丝
        val cols = listOf(0.22f, 0.36f, 0.5f, 0.64f, 0.78f, 0.3f, 0.58f, 0.72f)
        cols.forEachIndexed { i, x0 ->
            val ph = (fall + i * 0.37f) % 1f
            val y = h * (0.08f + ph * 0.62f)
            val x = w * x0 - ph * w * 0.05f
            val a = (1f - kotlin.math.abs(ph - 0.5f) * 1.6f).coerceIn(0f, 1f)
            drawLine(rain.copy(alpha = 0.55f * a), Offset(x, y), Offset(x - w * 0.025f, y + h * 0.09f), w * 0.016f, StrokeCap.Round)
        }
        // 地面
        drawLine(ink.copy(alpha = 0.35f), Offset(w * 0.24f, h * 0.84f), Offset(w * 0.76f, h * 0.84f), w * 0.014f, StrokeCap.Round)
        // 新芽
        val bx = w / 2; val by = h * 0.84f; val k = sway * w * 0.012f
        drawLine(ink, Offset(bx, by), Offset(bx + k, h * 0.62f), w * 0.022f, StrokeCap.Round)
        val l = Path().apply { moveTo(bx + k, h * 0.66f); cubicTo(bx - w * 0.06f + k, h * 0.58f, bx - w * 0.15f + k, h * 0.58f, bx - w * 0.19f + k, h * 0.62f)
            cubicTo(bx - w * 0.15f + k, h * 0.69f, bx - w * 0.06f + k, h * 0.70f, bx + k, h * 0.66f); close() }
        val r = Path().apply { moveTo(bx + k, h * 0.63f); cubicTo(bx + w * 0.05f + k, h * 0.53f, bx + w * 0.14f + k, h * 0.51f, bx + w * 0.2f + k, h * 0.54f)
            cubicTo(bx + w * 0.16f + k, h * 0.63f, bx + w * 0.07f + k, h * 0.66f, bx + k, h * 0.63f); close() }
        drawPath(l, sage.copy(alpha = 0.85f)); drawPath(r, sage)
    }
}

/** 文字输入框：圆角浅底。 */
@Composable
fun FieldBox(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, big: Boolean = false) {
    Box(modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(18.dp), fill = Glass.field(), shadow = 0.dp).padding(horizontal = 16.dp, vertical = 13.dp)) {
        if (value.isEmpty()) Text(hint, fontSize = if (big) 16.sp else 14.sp, color = C.Faint)
        androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Clay),
            textStyle = TextStyle(fontSize = if (big) 16.sp else 14.sp, color = C.Ink, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal), modifier = Modifier.fillMaxWidth())
    }
}

/** 一个圆点小标记（今天、选中）。 */
@Composable
fun Dot(color: Color, size: Dp = 6.dp) { Box(Modifier.size(size).clip(CircleShape).background(color)) }
