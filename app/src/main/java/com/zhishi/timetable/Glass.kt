package com.zhishi.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（照 iOS 26 Liquid Glass 的样子）：清透、带水光，不模糊。
 *  - 很透的底：浅色白 ~16%、深色白 ~10%，再按皮肤主色轻轻染一点，后面的插画背景清清楚楚透出来；
 *  - 一圈细亮边：左上亮（白 ~65%）→ 右下淡（~10%）；
 *  - 顶部一层柔光：白 ~26% 往下 42% 高度内淡到 0；
 *  - 底边一点点折射暗边；
 *  - 很轻的投影（只画在形状外面，透明底下面不会发脏）。
 * level：0 = 最清透（卡片、按钮、课块），越大越实（弹窗 / 底部弹出 = 1，~98.5% 不透明，要压住后面复杂内容时用）；导航条用 Glass.bar()。
 * 内容会按形状裁剪（点击水波纹也是），所以调用处不用再 clip。
 */
object Glass {
    const val CLEAR = 0f
    const val BAR = 0.55f
    const val THICK = 1f

    /** 某一档玻璃的底色。tint = 染色（默认皮肤主色）。 */
    fun fill(level: Float, tint: Color = C.Clay): Color {
        val dark = C.dark
        // build-14：卡片再清透一点（浅色 16% → 12%、深色 10% → 8%），插画透得更清楚
        val clear = lerp(Color.White, tint, if (dark) 0.22f else 0.10f).copy(alpha = if (dark) 0.08f else 0.12f)
        if (level <= 0f) return clear
        // 厚玻璃 ~98.5%：实测 94% 时后面列表的深色字在一整片干净的底上还有 ~8 级灰度的残影，97% 时 ~5 级，仍看得出；
        // 98.5% 再配上 GlassSheet 加重的遮罩，残影 ≤2 级，肉眼看不出。玻璃感靠亮边、顶部水光和底边暗边。
        val solid = lerp(C.Card, tint, if (dark) 0.06f else 0.04f).copy(alpha = 0.985f)
        return lerp(clear, solid, level.coerceIn(0f, 1f))
    }

    /**
     * 底部导航条的底：直接从皮肤卡片色出发（不和白色清透层混，深色下不会发灰），
     * 深色 ~90%、浅色 ~80% 不透明，后面的课表格子只隐约可见，没选中的字也读得清。
     */
    fun bar(tint: Color = C.Clay): Color {
        val dark = C.dark
        return lerp(C.Card, tint, if (dark) 0.08f else 0.05f).copy(alpha = if (dark) 0.90f else 0.80f)
    }

    /** 输入框的底：比厚玻璃再略深一点的皮肤色，几乎不透（后面什么都透不过来），和底部弹出区分开。 */
    fun field(): Color {
        val dark = C.dark
        return lerp(C.Card, C.Sand, if (dark) 0.85f else 0.65f).copy(alpha = 0.96f)
    }
}

/**
 * 液态玻璃的「后面」：把一块内容（插画背景 / 整页）录进一个 GraphicsLayer，玻璃再把它当透镜后面的东西重新画一遍。
 * API 33+ 才有 AGSL（RuntimeShader），更低的版本不录。
 */
class GlassSource {
    var layer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
    var coords: androidx.compose.ui.layout.LayoutCoordinates? = null
}

/** 只有插画背景（页面里的按钮、胶囊用：它们本身在页面里，不能折射整页，否则自己画自己）。 */
val LocalGlassArt = androidx.compose.runtime.staticCompositionLocalOf<GlassSource?> { null }
/** 插画 + 整页内容（底部导航条用：它浮在页面上面）。 */
val LocalGlassPage = androidx.compose.runtime.staticCompositionLocalOf<GlassSource?> { null }

@Composable
fun rememberGlassSource(): GlassSource {
    val src = androidx.compose.runtime.remember { GlassSource() }
    if (GlassLens.supported) src.layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    return src
}

/** 录下这块内容给玻璃用（照常画出来）。 */
fun Modifier.glassSource(src: GlassSource): Modifier = this
    .onGloballyPositioned { src.coords = it }
    .drawWithContent {
        val l = src.layer
        if (l == null) { drawContent(); return@drawWithContent }
        l.record { this@drawWithContent.drawContent() }
        drawLayer(l)
    }

/**
 * 透镜着色器（AGSL）：
 *  - 边缘折射：离边越近，往里取样越多（圆弧形的透镜剖面），中间是平的，后面的东西清清楚楚、不模糊；
 *  - 边缘一点色散（红蓝分开取样）；
 *  - 亮边跟着光的方向（左上）走，对面有一道弱一点的内反光；
 *  - 边缘一圈淡淡的内发光，可选中心放大（导航条上的水滴）；
 *  - cover = 0 时只画边缘那一圈（中间透明，真正的背景照常显示），= 1 时整块都画。
 */
object GlassLens {
    val supported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 33

    const val SRC = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float strength;
uniform float chroma;
uniform float cover;
uniform float2 light;
uniform float spec;
uniform float glow;
uniform float mag;
uniform float4 tint;
uniform float px;

float sdRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 xy) {
    float2 hs = size * 0.5;
    float2 p = xy - hs;
    float r = min(radius, min(hs.x, hs.y));
    float d = sdRR(p, hs, r);
    float inside = max(-d, 0.0);
    float e = 0.5;
    float2 g = float2(sdRR(p + float2(e, 0.0), hs, r) - sdRR(p - float2(e, 0.0), hs, r),
                      sdRR(p + float2(0.0, e), hs, r) - sdRR(p - float2(0.0, e), hs, r));
    float gl = length(g);
    float2 n = float2(0.0, -1.0);
    if (gl > 0.0001) { n = g / gl; }
    float t = clamp(1.0 - inside / band, 0.0, 1.0);
    float bend = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    float2 shift = -n * bend * strength;
    float2 c = hs + p * (1.0 - mag) + shift;
    float4 m = float4(content.eval(c));
    float4 col = m;
    if (chroma > 0.0) {
        float2 ca = shift * chroma;
        col = float4(float4(content.eval(c + ca)).r, m.g, float4(content.eval(c - ca)).b, m.a);
    }
    col.rgb = mix(col.rgb, tint.rgb * col.a, tint.a);
    float rimLine = 1.0 - smoothstep(0.0, 1.5 * px, inside);
    float lit = max(dot(n, light), 0.0);
    float back = max(-dot(n, light), 0.0);
    float s = rimLine * (0.16 + 0.95 * pow(lit, 1.5) + 0.45 * back * back) * spec;
    float sheen = bend * (0.06 + 0.22 * lit) * spec;
    float ig = exp(-inside / max(band * 0.45, 1.0)) * glow;
    float add = clamp(s + sheen + ig, 0.0, 1.0);
    col.rgb = col.rgb + (float3(col.a, col.a, col.a) - col.rgb) * add;
    float vis = max(cover, smoothstep(0.0, 0.85, t));
    float aa = smoothstep(0.0, px, inside);
    return half4(col * (vis * aa));
}
"""

    /** 编译失败（某些 GPU / 模拟器）就记下来，以后都走老画法。 */
    @Volatile var broken = false

    fun make(): Any? {
        if (!supported || broken) return null
        return try { android.graphics.RuntimeShader(SRC) } catch (x: Throwable) {
            broken = true; android.util.Log.w("ZhishiGlass", "lens shader failed: ${x.message}"); null
        }
    }
}

/** 透镜参数。 */
class LensSpec(
    val cover: Boolean = false,        // 整块都画（导航条、水滴）还是只画边缘一圈（页面里的按钮）
    val band: Dp = 0.dp,               // 折射带宽，0 = 按尺寸自动
    val strength: Float = 0.6f,        // 边缘位移 = 折射带宽 × strength
    val chroma: Float = 0.3f,
    val mag: Float = 0f,
    val spec: Float = 1f,
    val glow: Float = 0.10f,
    val tint: Color = Color.Transparent,
)

private val LIGHT = Offset(-0.45f, -0.89f)

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(22.dp),
    level: Float = Glass.CLEAR,
    tint: Color? = null,
    fill: Color? = null,
    shadow: Dp = 6.dp,
    rim: Dp = 1.dp,
    lens: LensSpec? = null,
    source: GlassSource? = null,
    press: Float = 0f,
): Modifier = composed {
    val dark = C.dark
    val t = tint ?: C.Clay
    val base = fill ?: Glass.fill(level, t)
    val pr = press.coerceIn(0f, 1.2f)
    // 越实的玻璃，顶部柔光越淡（免得盖住字）；按下去时更亮
    val hiA = ((if (dark) 0.16f else 0.30f) * (1f - 0.55f * level.coerceIn(0f, 1f)) + 0.12f * pr).coerceAtMost(0.6f)
    val rimTop = ((if (dark) 0.42f else 0.75f) + 0.25f * pr).coerceAtMost(1f)
    val rimMid = if (dark) 0.10f else 0.22f
    val rimEnd = if (dark) 0.06f else 0.12f
    val edge = if (dark) Color.Black.copy(alpha = 0.22f) else lerp(Color.Black, t, 0.5f).copy(alpha = 0.07f)
    val hair = if (dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.06f)
    val shadowArgb = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f).toArgb()
    val glowA = (if (dark) 0.10f else 0.16f) * pr

    // ---- 透镜（API 33+，有可折射的背景，且和背景在同一个窗口里）----
    val src = if (lens != null) (source ?: LocalGlassArt.current) else null
    val shader = if (src?.layer != null) androidx.compose.runtime.remember { GlassLens.make() } else null
    val lensLayer = if (shader != null) androidx.compose.ui.graphics.rememberGraphicsLayer() else null
    val pos = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    val track = if (shader != null && src != null) Modifier.onGloballyPositioned { me ->
        val sc = src.coords
        pos.value = if (sc != null && sc.isAttached && me.isAttached && sc.findRootCoordinates() === me.findRootCoordinates())
            sc.localPositionOf(me, Offset.Zero) else null
    } else Modifier

    this.then(track).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val radius = when (outline) {
            is androidx.compose.ui.graphics.Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
            else -> 0f
        }
        val sPx = shadow.toPx()
        val rimPx = rim.toPx()
        val paint = if (sPx > 0f) android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            setShadowLayer(sPx, 0f, sPx * 0.45f, shadowArgb)
        } else null
        val highlight = Brush.verticalGradient(0f to Color.White.copy(alpha = hiA), 0.42f to Color.Transparent, startY = 0f, endY = size.height)
        val bottom = Brush.verticalGradient(0.70f to Color.Transparent, 1f to edge, startY = 0f, endY = size.height)
        val rimBrush = Brush.linearGradient(
            0f to Color.White.copy(alpha = rimTop), 0.45f to Color.White.copy(alpha = rimMid), 1f to Color.White.copy(alpha = rimEnd),
            start = Offset.Zero, end = Offset(size.width, size.height))
        val glowBrush = if (glowA > 0f) Brush.radialGradient(listOf(Color.White.copy(alpha = glowA), Color.White.copy(alpha = glowA * 0.35f)),
            center = Offset(size.width / 2, size.height * 0.35f), radius = maxOf(size.width, size.height) * 0.7f) else null
        val half = minOf(size.width, size.height) / 2f
        val bandPx = if (lens != null && lens.band > 0.dp) lens.band.toPx().coerceAtMost(half) else (half * 0.62f).coerceAtMost(18.dp.toPx())
        onDrawWithContent {
            // 投影：只画在形状外面
            if (paint != null) clipPath(path, ClipOp.Difference) {
                drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
            }
            // 整块透镜（导航条、水滴）：先画透镜，再盖很淡的底色；只画边缘的（按钮）：先画底色，透镜的折射边压在上面
            if (lens?.cover != true) drawPath(path, base)
            val at = pos.value
            val srcLayer = src?.layer
            var lensed = false
            if (shader != null && lensLayer != null && srcLayer != null && at != null && lens != null && size.minDimension > 1f &&
                android.os.Build.VERSION.SDK_INT >= 33) {
                try {
                    val sh = shader as android.graphics.RuntimeShader
                    sh.setFloatUniform("size", size.width, size.height)
                    sh.setFloatUniform("radius", radius)
                    sh.setFloatUniform("band", bandPx)
                    sh.setFloatUniform("strength", bandPx * lens.strength * (1f + 0.25f * pr))
                    sh.setFloatUniform("chroma", lens.chroma)
                    sh.setFloatUniform("cover", if (lens.cover) 1f else 0f)
                    sh.setFloatUniform("light", LIGHT.x, LIGHT.y)
                    sh.setFloatUniform("spec", lens.spec * (if (dark) 0.75f else 1f) * (1f + 0.4f * pr))
                    sh.setFloatUniform("glow", lens.glow + 0.10f * pr)
                    sh.setFloatUniform("mag", lens.mag + 0.04f * pr)
                    sh.setFloatUniform("tint", lens.tint.red, lens.tint.green, lens.tint.blue, lens.tint.alpha)
                    sh.setFloatUniform("px", density)
                    lensLayer.clip = true
                    lensLayer.record(size = androidx.compose.ui.unit.IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                        translate(-at.x, -at.y) { drawLayer(srcLayer) }
                    }
                    lensLayer.renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content").asComposeRenderEffect()
                    clipPath(path) { drawLayer(lensLayer) }
                    lensed = true
                } catch (x: Throwable) {
                    GlassLens.broken = true
                    android.util.Log.w("ZhishiGlass", "lens draw failed: ${x.message}")
                }
            }
            if (lens?.cover == true) drawPath(path, base)
            drawPath(path, highlight)
            drawPath(path, bottom)
            if (glowBrush != null) drawPath(path, glowBrush)
            clipPath(path) { this@onDrawWithContent.drawContent() }
            // 外侧一根极淡的暗线，让亮边在浅色背景上也立得住
            drawPath(path, hair, style = Stroke(1f))
            // 亮边：描边一半在形状里（裁掉外面一半）；有透镜时着色器已经画了跟光走的亮边，这里淡一点
            if (rimPx > 0f) clipPath(path) { drawPath(path, rimBrush, style = Stroke(rimPx * 2f), alpha = if (lensed) 0.55f else 1f) }
        }
    }
}

/**
 * 液态玻璃按钮：透镜折射 + 亮边 + 按下去像水滴一样鼓起来、变亮，松手时弹回来轻轻晃一下（弹簧）。
 * 没有涟漪（水滴本身就是反馈）。
 */
fun Modifier.glassButton(
    shape: Shape = CircleShape,
    level: Float = Glass.CLEAR,
    tint: Color? = null,
    fill: Color? = null,
    shadow: Dp = 4.dp,
    enabled: Boolean = true,
    lens: LensSpec = LensSpec(),
    onClick: () -> Unit,
): Modifier = composed {
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val p by androidx.compose.animation.core.animateFloatAsState(if (pressed && enabled) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 420f), label = "drop")
    this.graphicsLayer {
        // 按下：横向多鼓一点（水滴被按扁），松手回弹时 p 会略小于 0，形成一下轻微的回弹
        scaleX = 1f + 0.075f * p; scaleY = 1f + 0.05f * p
    }.liquidGlass(shape, level, tint, fill, shadow, lens = lens, press = p.coerceAtLeast(0f))
        .clickable(interactionSource = src, indication = null, enabled = enabled) { onClick() }
}

/** 一块玻璃面板。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    level: Float = Glass.CLEAR,
    tint: Color? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liquidGlass(shape, level, tint), contentAlignment = contentAlignment, content = content)
}

/** 底部弹出、对话框的形状和底色（实一些的玻璃，后面是课表格子时字也看得清）。 */
val SheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
val DialogShape = RoundedCornerShape(30.dp)

/**
 * 玻璃底部弹出：ModalBottomSheet 自己的底色改透明，整块内容画成一块「厚」玻璃（后面多半是课表格子，太透字会花），
 * 带亮边和顶部水光，自己画拖动条。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    sheetState: androidx.compose.material3.SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState, shape = SheetShape,
        containerColor = Color.Transparent, contentColor = C.Ink, tonalElevation = 0.dp, dragHandle = null,
        // 遮罩加重一点（默认黑 32%）：弹出层下面的列表先被压暗，再透过 97% 的玻璃就看不出字了
        scrimColor = Color.Black.copy(alpha = if (C.dark) 0.58f else 0.45f),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().liquidGlass(SheetShape, Glass.THICK, shadow = 0.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 10.dp).size(36.dp, 4.dp)
                .background(C.Faint.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
            content()
        }
    }
}


/** 对话框（AlertDialog / DatePickerDialog）套的玻璃：配合 containerColor = Transparent、tonalElevation = 0。厚玻璃 ~98.5%，后面多半还压着一层底部弹出。 */
@Composable
fun dialogGlass(): Modifier = Modifier.liquidGlass(DialogShape, Glass.THICK, shadow = 0.dp)
