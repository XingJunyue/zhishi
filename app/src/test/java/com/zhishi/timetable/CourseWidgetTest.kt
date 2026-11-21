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
}
