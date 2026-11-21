package com.zhishi.timetable

import org.junit.Assert.*
import org.junit.Test

/** 国外课表：左边是钟点（8:00、9 AM…），格子里常写着 9:00–10:50。 */
class OcrClockTest {
    private fun sec(a: Int, b: Int) = 480 + (a - 1) * 55 to 480 + (b - 1) * 55 + 45
    private fun l(t: String, x: Int, y: Int, w: Int = 80, h: Int = 24) = Ocr.L(t, Ocr.R(x - w / 2, y, x + w / 2, y + h))

    @Test fun clockWords() {
        assertEquals(8 * 60, Ocr.timeOf("8:00"))
        assertEquals(13 * 60, Ocr.timeOf("1 PM"))
        assertEquals(9 * 60, Ocr.timeOf("9am"))
        assertEquals(14 * 60 + 15, Ocr.timeOf("14.15"))
        assertEquals(12 * 60, Ocr.timeOf("12 p.m."))
        assertNull(Ocr.timeOf("3"))
        assertEquals(9 * 60 to 10 * 60 + 50, Ocr.rangeOf("9:00 - 10:50"))
        assertEquals(13 * 60 to 14 * 60 + 50, Ocr.rangeOf("1:00–2:50pm"))
        assertEquals(14 * 60 + 15 to 15 * 60 + 45, Ocr.rangeOf("14.15–15.45"))
    }

    /** 每小时 60 像素，8:00 在 y=100。 */
    private fun grid(labels: (Int) -> String): MutableList<Ocr.L> {
        val g = ArrayList<Ocr.L>()
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday").forEachIndexed { i, d -> g += l(d, 200 + i * 120, 40) }
        for (h in 8..18) g += l(labels(h), 60, 100 + (h - 8) * 60 - 12, 40)
        return g
    }

    @Test fun byTimeAxis() {
        val g = grid { "$it:00" }
        // 周二 10:00 开始的一格，字在上半截
        g += l("CS 101", 320, 222); g += l("Room 2.14", 320, 250)
        // 周四写明了时间
        g += l("Calculus II", 560, 342); g += l("13:00-14:50", 560, 370)
        val r = Ocr.parse(g, 900, ::sec).associateBy { it.name }
        assertEquals(setOf(2), r["CS 101"]!!.days)
        assertEquals(600, r["CS 101"]!!.from)
        assertTrue(r["CS 101"]!!.to >= 650)
        assertEquals(setOf(4), r["Calculus II"]!!.days)
        assertEquals(13 * 60 to 14 * 60 + 50, r["Calculus II"]!!.from to r["Calculus II"]!!.to)
    }

    @Test fun amPmAxis() {
        val g = grid { h -> if (h < 12) "$h AM" else if (h == 12) "12 PM" else "${h - 12} PM" }
        g += l("Biology", 440, 402)
        val r = Ocr.parse(g, 900, ::sec)
        assertEquals(1, r.size); assertEquals(setOf(3), r[0].days); assertEquals(13 * 60, r[0].from)
    }
}
