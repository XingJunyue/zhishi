package com.zhishi.timetable

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 老版本 / 被改坏的存储不能让启动崩（build-17 崩溃排查后加的回归测试）。 */
class RobustnessTest {
    @Test fun badSlotFieldsAreTolerated() {
        val s = Slot.of(JSONObject("""{"n":"坏字段","d":["一",3,9,0],"f":"abc","t":5000,"w":[1,"x",99],"l":9}"""))
        assertEquals(setOf(3), s.days)
        assertEquals(setOf(1), s.weeks)
        assertTrue(s.from in 0..1439 && s.to in 0..1439)
        assertTrue(s.level in 0..2)
        assertTrue(s.id.isNotBlank())
    }

    @Test fun badTodosAreSkipped() {
        val l = Todos.parse("""[{"id":"o1","t":"老待办","due":1,"re":true}, "x", null, 42, {"id":"o2","t":"坏的","due":"明天","re":"yes","rb":-30}]""")
        assertEquals(listOf("o1", "o2"), l.map { it.id })
        assertEquals(-1L, l[1].due)
        assertEquals(0, l[1].before)
        assertTrue(Todos.parse("不是 json").isEmpty())
        // 没日期却开着提醒、全天：算提醒时刻不能崩
        assertNull(Todos.remindAt(Todo("z", "z", due = -1L, remind = true, allDay = true, before = 1440)))
    }

    @Test fun widgetPlanEdgeStates() {
        val d = 1
        fun sl(f: Int, t: Int) = Slot("x$f", "课", setOf(d), f, t)
        // 都上完 / 第一节前 / 课间 / 空 / 零长度
        assertTrue(CourseWidgets.plan(listOf(sl(480, 525), sl(540, 585)), 700).doneAll)
        assertEquals(480, CourseWidgets.plan(listOf(sl(480, 525)), 300).main!!.from)
        assertFalse(CourseWidgets.plan(listOf(sl(480, 525), sl(600, 645)), 550).live)
        assertNull(CourseWidgets.plan(emptyList(), 600).main)
        CourseWidgets.plan(listOf(sl(600, 600), sl(600, 601)), 600)
    }

    /** build-15/16 闪退的根因：inline 布局（Row / Column / Box …）内容里 return@Row 提前返回，
     *  在 Compose 编译器 1.5.x + 运行时 1.7 下组栈错位，首帧 ArrayIndexOutOfBoundsException（IntStack.peek2）。源码里不许再出现。 */
    @Test fun noEarlyReturnFromInlineLayouts() {
        val bad = Regex("""return@(Row|Column|Box|BoxWithConstraints|FlowRow|FlowColumn|Layout)\b""")
        val root = java.io.File("src/main/java"); assertTrue(root.isDirectory)
        val hits = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (bad.containsMatchIn(l)) "${f.name}:${i + 1}: ${l.trim()}" else null }
        }.toList()
        assertTrue("inline 布局里不要提前 return：\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
