package com.zhishi.timetable

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

/** 待办：存储往返、分组（今天 / 即将 / 已完成）、按天归类、备份里带上待办；日历页按天取课。 */
class TodoTest {
    private val tz = TimeZone.getTimeZone("Asia/Shanghai")
    private val day = 20_413L                       // 2025-11-21 之类的某一天（epoch 天）
    private fun at(d: Long, m: Int) = Todos.at(d, m, tz)

    @Test fun jsonRoundTrip() {
        val l = listOf(
            Todo("a", "交作业", "第三章", at(day, 22 * 60), true, "高等数学", created = 5),
            Todo("b", "借书", created = 6),
            Todo("c", "体测", due = at(day - 1, 600), done = true, doneAt = 99, created = 1),
        )
        val back = Todos.parse(Todos.toJson(l).toString())
        assertEquals(l, back)
        // 坏数据不崩、空标题 / 空 id 的丢掉
        assertTrue(Todos.parse("not json").isEmpty())
        assertEquals(1, Todos.parse("""[{"id":"x","t":"ok"},{"id":"","t":"no"},{"id":"y","t":""}]""").size)
    }

    @Test fun grouping() {
        val todayEnd = at(day + 1, 0)
        val l = listOf(
            Todo("later", "下周", due = at(day + 5, 480), created = 1),
            Todo("tonight", "今晚", due = at(day, 22 * 60), created = 2),
            Todo("overdue", "昨天的", due = at(day - 1, 600), created = 3),
            Todo("nodue2", "没日期 2", created = 9),
            Todo("nodue1", "没日期 1", created = 4),
            Todo("tomorrow", "明天", due = at(day + 1, 480), created = 5),
            Todo("done1", "早完成", done = true, doneAt = 10, created = 6),
            Todo("done2", "晚完成", due = at(day, 600), done = true, doneAt = 20, created = 7),
        )
        val g = Todos.group(l, todayEnd)
        assertEquals(listOf("overdue", "tonight"), g.today.map { it.id })
        assertEquals(listOf("tomorrow", "later", "nodue1", "nodue2"), g.upcoming.map { it.id })
        assertEquals(listOf("done2", "done1"), g.done.map { it.id })
        assertTrue(Todos.overdue(l[2], at(day, 700)))
        assertFalse(Todos.overdue(l[1], at(day, 700)))
        assertFalse(Todos.overdue(l[7], at(day + 3, 0)))   // 完成的不算过期
    }

    @Test fun byDay() {
        val l = listOf(Todo("a", "a", due = at(day, 0)), Todo("b", "b", due = at(day, 23 * 60 + 59)), Todo("c", "c", due = at(day + 1, 0)), Todo("d", "d"))
        assertEquals(day, Todos.dayOf(l[0], tz)); assertEquals(day, Todos.dayOf(l[1], tz)); assertEquals(day + 1, Todos.dayOf(l[2], tz))
        assertNull(Todos.dayOf(l[3], tz))
        assertEquals(listOf("a", "b"), Todos.on(l, day, tz).map { it.id })
        assertEquals(22 * 60 + 5, Todos.minuteOf(at(day, 22 * 60 + 5), tz), )
    }

    @Test fun backupCarriesTodos() {
        val todos = listOf(Todo("a", "交作业", due = at(day, 600), course = "高等数学", created = 1))
        val j = Term.backupJson(emptyList(), listOf(480), listOf(525), 45, 20000L, 18, todos)
        val b = Term.parseBackup(j)!!
        assertEquals(todos, b.todos)
        // 旧备份（没有 todos 字段）：null，恢复时不动现有待办
        val old = Term.parseBackup(Term.backupJson(emptyList(), listOf(480), listOf(525), 45, 20000L, 18))!!
        assertNull(old.todos)
    }

    @Test fun classesOnADay() {
        val term = Term.mondayOf(day)           // 这周是第 1 周
        val wd = Math.floorMod(day + 3, 7L).toInt() + 1
        val every = Slot("1", "每周", setOf(wd), 480, 580)
        val odd = Slot("2", "单周", setOf(wd), 600, 700, weeks = setOf(1, 3))
        val even = Slot("3", "双周", setOf(wd), 800, 900, weeks = setOf(2))
        val other = Slot("4", "别的天", setOf(wd % 7 + 1), 480, 580)
        val all = listOf(even, odd, every, other)
        assertEquals(listOf("1", "2"), Term.classesOn(all, term, 16, day).map { it.id })
        assertEquals(listOf("1", "3"), Term.classesOn(all, term, 16, day + 7).map { it.id })
        assertTrue(Term.classesOn(all, term, 16, day - 7).isEmpty())      // 开学前
        assertTrue(Term.classesOn(all, term, 1, day + 7).isEmpty())       // 学期结束后
        assertEquals(listOf("1", "2", "3"), Term.classesOn(all, -1L, 16, day).map { it.id })   // 没设开学日期：每周都上
    }

    @Test fun whatsNewHasRedesign() {
        val r = Tour.WHATS_NEW.first { it.id == "redesign" }
        val txt = r.pages.joinToString { it.title + it.body }
        assertTrue(txt.contains("待办") && txt.contains("日历"))
    }

    @Test fun whatsNewNewestIsGlass() {
        assertEquals("glass", Tour.WHATS_NEW.first().id)
        val txt = Tour.WHATS_NEW.first().pages.joinToString { it.title + it.body }
        assertTrue(txt.contains("液态玻璃") && txt.contains("导入"))
        assertEquals(Tour.WHATS_NEW.size, Tour.WHATS_NEW.map { it.id }.toSet().size)
    }
}
