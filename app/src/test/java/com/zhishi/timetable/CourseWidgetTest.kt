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

    // 待办小部件：过期的在前，再今天、之后、没设截止；全做完 / 一条没有分得开
    private val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    private val d0 = 20_413L
    @Test fun todoOrder() {
        val now = Todos.at(d0, 700, tz)
        val l = listOf(
            Todo("nod", "没时间", created = 1),
            Todo("later", "之后", due = Todos.at(d0 + 2, 600, tz), created = 2),
            Todo("late", "过期", due = Todos.at(d0, 600, tz), created = 3),
            Todo("today", "今天", due = Todos.at(d0, 900, tz), created = 4),
            Todo("done", "完成", done = true, doneAt = 5, created = 5),
        )
        val p = CourseWidgets.todoPlan(l, now, tz)
        assertEquals(listOf("late", "today", "later", "nod"), p.list.map { it.id })
        assertEquals(1, p.overdue); assertEquals(1, p.today); assertFalse(p.allDone)
        assertEquals(listOf(0, 1, 2, 3), p.list.map { CourseWidgets.todoState(it, now, tz) })
        assertTrue(CourseWidgets.todoPlan(listOf(l[4]), now, tz).allDone)
        assertFalse(CourseWidgets.todoPlan(emptyList(), now, tz).allDone)
    }
    @Test fun nextTick() {
        val now = Todos.at(d0, 700, tz) + 20_000L
        val midnight = Todos.at(d0 + 1, 0, tz) + 5_000L
        assertNull(CourseWidgets.nextTick(now, false, emptyList(), 700, false, emptyList(), tz))
        // 只有待办小部件：最近一条到截止就重画，没有就到 0 点
        val t = Todo("a", "x", due = Todos.at(d0, 900, tz))
        assertEquals(Todos.at(d0, 900, tz) + 1_000L, CourseWidgets.nextTick(now, false, emptyList(), 700, true, listOf(t), tz))
        assertEquals(midnight, CourseWidgets.nextTick(now, false, emptyList(), 700, true, listOf(t.copy(done = true)), tz))
        // 课表小部件今天还有课：下一分钟
        assertEquals(Todos.at(d0, 701, tz), CourseWidgets.nextTick(now, true, listOf(s("物理", 480, 760)), 700, true, listOf(t), tz))
        assertEquals(midnight, CourseWidgets.nextTick(now, true, listOf(s("物理", 480, 575)), 700, false, emptyList(), tz))
    }
}
