package com.zhishi.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * 空状态插画（build-16 起，替换原来粉色圆底 + 绿色新芽）：一笔水墨圆相（起笔重、收笔渐细，末端散成几缕飞白），
 * 里面一笔带锋的勾（待办）或三道短横（课表），右下一枚小朱印，底下一团极淡的皮肤主色晕染。
 * 墨色取皮肤的字色偏一点主色，浅色 / 深色、六套皮肤都自动配色；进场时一笔一笔写出来。
 */
enum class InkKind { TODO, TABLE }

@Composable
fun InkArt(kind: InkKind, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val ring = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        mark.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val ink = lerp(C.Ink, C.ClayDeep, 0.18f).copy(alpha = if (C.dark) 0.92f else 0.88f)
    val acc = C.Clay
    val wash = C.Clay.copy(alpha = if (C.dark) 0.14f else 0.10f)
    val seal = if (C.dark) Color(0xFFD0584C) else Color(0xFFB8322A)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        // 晕染：偏左下的一团很淡的主色
        drawCircle(Brush.radialGradient(listOf(wash, Color.Transparent), Offset(w * 0.46f, w * 0.54f), w * 0.34f), w * 0.34f, Offset(w * 0.46f, w * 0.54f))
        // 圆相主笔（到 80%），后面 3 缕飞白
        val r = ring.value
        drawPath(InkGeom.ensoPath(w, r, 0f, 0.80f, 0f, 1f), ink)
        for ((off, wk, t1) in InkGeom.HAIRS) if (r > 0.76f) drawPath(InkGeom.ensoPath(w, r, 0.76f, t1, off, wk), ink)
        val m = mark.value
        if (m > 0f) when (kind) {
            InkKind.TODO -> drawPath(InkGeom.checkPath(w, m), acc)
            InkKind.TABLE -> InkGeom.bars(w, m).forEachIndexed { i, p -> drawPath(p, if (i == 1) acc else ink) }
        }
        // 小朱印
        if (m > 0.6f) drawRoundRect(seal.copy(alpha = 0.9f * ((m - 0.6f) / 0.4f)), Offset(w * 0.80f, w * 0.80f), Size(w * 0.055f, w * 0.055f), CornerRadius(w * 0.008f))
    }
}

/** 笔画几何（纯函数）：中线 + 每点笔宽 → 填充的多边形。 */
object InkGeom {
    /** 飞白：(半径偏移, 笔宽系数, 结束位置)。 */
    val HAIRS = listOf(Triple(-0.0075f, 0.42f, 0.95f), Triple(0f, 0.40f, 1f), Triple(0.0075f, 0.36f, 0.90f))

    fun outline(pts: List<Offset>, ws: List<Float>): Path {
        val p = Path()
        if (pts.size < 2) return p
        val left = ArrayList<Offset>(pts.size); val right = ArrayList<Offset>(pts.size)
        for (i in pts.indices) {
            val a = pts[maxOf(i - 1, 0)]; val b = pts[minOf(i + 1, pts.size - 1)]
            val dx = b.x - a.x; val dy = b.y - a.y; val l = hypot(dx, dy).takeIf { it > 0f } ?: 1f
            val nx = -dy / l * ws[i] / 2; val ny = dx / l * ws[i] / 2
            left += Offset(pts[i].x + nx, pts[i].y + ny); right += Offset(pts[i].x - nx, pts[i].y - ny)
        }
        p.moveTo(left[0].x, left[0].y)
        for (q in left.drop(1)) p.lineTo(q.x, q.y)
        for (q in right.asReversed()) p.lineTo(q.x, q.y)
        p.close(); return p
    }

    /** 圆相：从右上起笔，逆时针走 318°，半径略有起伏；起笔快速压重、之后慢慢变细。 */
    fun ensoPath(w: Float, prog: Float, t0: Float, t1: Float, off: Float, wk: Float): Path {
        val n = 120; val cx = 0.5f * w; val r = 0.36f * w
        val a0 = (-62.0 * PI / 180).toFloat(); val sweep = (-318.0 * PI / 180).toFloat()
        val pts = ArrayList<Offset>(); val ws = ArrayList<Float>()
        val end = minOf(prog, t1)
        var i = (n * t0).toInt()
        while (i <= (n * end).toInt()) {
            val t = i / n.toFloat(); val a = a0 + sweep * t
            val rr = r * (1 + 0.035f * sin(t * PI.toFloat() * 1.3f) - 0.02f * t) + off * w
            pts += Offset(cx + rr * cos(a), cx + rr * sin(a))
            ws += ((minOf(t / 0.05f, 1f).pow(0.6f)) * (0.050f - 0.036f * t.pow(1.2f)) + 0.004f) * w * wk
            i++
        }
        return outline(pts, ws)
    }

    private fun cr(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
        fun k(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t)
        return Offset(k(p0.x, p1.x, p2.x, p3.x), k(p0.y, p1.y, p2.y, p3.y))
    }

    /** 带锋的勾：短短一顿往下，再一笔挑上去收成细锋。 */
    fun checkPath(w: Float, prog: Float): Path {
        val ctl = listOf(Offset(.35f, .52f), Offset(.40f, .57f), Offset(.455f, .635f), Offset(.53f, .55f), Offset(.60f, .46f), Offset(.67f, .37f))
        val pp = listOf(ctl.first()) + ctl + listOf(ctl.last())
        val segs = ctl.size - 1; val n = 60
        val pts = ArrayList<Offset>(); val ws = ArrayList<Float>()
        for (i in 0..n) {
            val tt = i / n.toFloat(); if (tt > prog) break
            val u = tt * segs; val s = minOf(u.toInt(), segs - 1); val t = u - s
            val q = cr(pp[s], pp[s + 1], pp[s + 2], pp[s + 3], t)
            pts += Offset(q.x * w, q.y * w)
            val bw = if (tt < 0.35f) 0.020f + 0.016f * sin(minOf(tt / 0.35f, 1f) * PI.toFloat() / 2) else 0.036f * (1 - (tt - 0.35f) / 0.65f).pow(0.9f) + 0.003f
            ws += bw * w
        }
        return outline(pts, ws)
    }

    /** 课表：三道略往上斜的短横，起笔重、收笔细，一道比一道晚。 */
    fun bars(w: Float, prog: Float): List<Path> {
        val rows = listOf(Triple(0.41f, 0.35f, 0.62f), Triple(0.51f, 0.38f, 0.66f), Triple(0.61f, 0.35f, 0.58f))
        return rows.mapIndexed { k, (y, x0, x1) ->
            val local = ((prog - k * 0.25f) / 0.5f).coerceIn(0f, 1f)
            val pts = ArrayList<Offset>(); val ws = ArrayList<Float>()
            val n = 30
            for (i in 0..(n * local).toInt()) {
                val t = i / n.toFloat()
                pts += Offset((x0 + (x1 - x0) * t) * w, (y - 0.025f * t + 0.006f * sin(t * PI.toFloat())) * w)
                ws += ((minOf(t / 0.12f, 1f).pow(0.6f)) * (0.034f - 0.024f * t) + 0.003f) * w
            }
            outline(pts, ws)
        }
    }
}
