package com.zhishi.timetable

import org.junit.Assert.*
import org.junit.Test

class CourseWidgetTest {
    private fun s(n: String, f: Int, t: Int) = Slot(n, n, setOf(1), f, t)
    private val day = listOf(s("英语", 840, 935), s("高数", 610, 705), s("物理", 480, 575))

    @Test fun empty() { val n = CourseWidgets.plan(emptyList(), 600); assertNull(n.main); assertFalse(n.doneAll) }
    @Test fun inClass() {
        val n = CourseWidgets.plan(day, 650)
        assertEquals("高数", n.main?.name); assertTrue(n.live); assertEquals("英语", n.next?.name); assertEquals(2, n.index); assertEquals(3, n.total)
    }
    @Test fun between() {
        val n = CourseWidgets.plan(day, 720)
        assertEquals("英语", n.main?.name); assertFalse(n.live); assertNull(n.next)
    }
    @Test fun before() { val n = CourseWidgets.plan(day, 400); assertEquals("物理", n.main?.name); assertEquals("高数", n.next?.name) }
    @Test fun done() { val n = CourseWidgets.plan(day, 1000); assertNull(n.main); assertTrue(n.doneAll) }

    // 小部件位图：按真实 dp × 密度画（以前密度封顶 2.75，Xiaomi 15 这种 3.0+ 的屏幕上会被放大发糊）
    @Test fun bitmapAtRealDensity() {
        val (w, h, k) = CourseWidgets.bitmapSize(320f, 156f, 3.0f, 1200L * 2670)
        assertEquals(960, w); assertEquals(468, h); assertEquals(3.0f, k, 0.0001f)
        // 超过内存预算（屏幕像素 × 1.5 的一半）才等比缩小
        val (w2, h2, k2) = CourseWidgets.bitmapSize(2000f, 2000f, 3.5f, 1080L * 2400)
        assertTrue(w2.toLong() * h2 <= 1080L * 2400 * 3 / 4 + 4000); assertTrue(k2 < 3.5f); assertEquals(w2, h2)
    }
    @Test fun styleNorm() {
        assertEquals("glass", WidgetStyle.norm(null)); assertEquals("glass", WidgetStyle.norm("x")); assertEquals("classic", WidgetStyle.norm("classic"))
    }
}
