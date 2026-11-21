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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（照 iOS 26 Liquid Glass 的样子）：清透、带水光，不模糊。
 *  - 很透的底：浅色白 ~16%、深色白 ~10%，再按皮肤主色轻轻染一点，后面的插画背景清清楚楚透出来；
 *  - 一圈细亮边：左上亮（白 ~65%）→ 右下淡（~10%）；
 *  - 顶部一层柔光：白 ~26% 往下 42% 高度内淡到 0；
 *  - 底边一点点折射暗边；
 *  - 很轻的投影（只画在形状外面，透明底下面不会发脏）。
 * level：0 = 最清透（卡片、按钮、课块），越大越实（导航条 ~0.35，弹窗 / 底部弹出 = 1，要压住后面复杂内容时用）。
 * 内容会按形状裁剪（点击水波纹也是），所以调用处不用再 clip。
 */
object Glass {
    const val CLEAR = 0f
    const val BAR = 0.35f
    const val THICK = 1f

    /** 某一档玻璃的底色。tint = 染色（默认皮肤主色）。 */
    fun fill(level: Float, tint: Color = C.Clay): Color {
        val dark = C.dark
        val clear = lerp(Color.White, tint, if (dark) 0.22f else 0.10f).copy(alpha = if (dark) 0.10f else 0.16f)
        if (level <= 0f) return clear
        val solid = lerp(C.Card, tint, if (dark) 0.06f else 0.04f).copy(alpha = if (dark) 0.80f else 0.82f)
        return lerp(clear, solid, level.coerceIn(0f, 1f))
    }
}

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(22.dp),
    level: Float = Glass.CLEAR,
    tint: Color? = null,
    fill: Color? = null,
    shadow: Dp = 6.dp,
    rim: Dp = 1.dp,
): Modifier = composed {
    val dark = C.dark
    val t = tint ?: C.Clay
    val base = fill ?: Glass.fill(level, t)
    // 越实的玻璃，顶部柔光越淡（免得盖住字）
    val hiA = (if (dark) 0.16f else 0.30f) * (1f - 0.55f * level.coerceIn(0f, 1f))
    val rimTop = if (dark) 0.42f else 0.75f
    val rimMid = if (dark) 0.10f else 0.22f
    val rimEnd = if (dark) 0.06f else 0.12f
    val edge = if (dark) Color.Black.copy(alpha = 0.22f) else lerp(Color.Black, t, 0.5f).copy(alpha = 0.07f)
    val hair = if (dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.06f)
    val shadowArgb = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f).toArgb()
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
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
        onDrawWithContent {
            // 投影：只画在形状外面
            if (paint != null) clipPath(path, ClipOp.Difference) {
                drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
            }
            drawPath(path, base)
            drawPath(path, highlight)
            drawPath(path, bottom)
            clipPath(path) { this@onDrawWithContent.drawContent() }
            // 外侧一根极淡的暗线，让亮边在浅色背景上也立得住
            drawPath(path, hair, style = Stroke(1f))
            // 亮边：描边一半在形状里（裁掉外面一半）
            if (rimPx > 0f) clipPath(path) { drawPath(path, rimBrush, style = Stroke(rimPx * 2f)) }
        }
    }
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
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().liquidGlass(SheetShape, Glass.THICK, shadow = 0.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 10.dp).size(36.dp, 4.dp)
                .background(C.Faint.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
            content()
        }
    }
}


/** 对话框（AlertDialog / DatePickerDialog）套的玻璃：配合 containerColor = Transparent、tonalElevation = 0。 */
@Composable
fun dialogGlass(): Modifier = Modifier.liquidGlass(DialogShape, Glass.THICK, shadow = 0.dp)
