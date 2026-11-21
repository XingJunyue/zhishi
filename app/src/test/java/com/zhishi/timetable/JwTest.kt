package com.zhishi.timetable

import org.junit.Assert.*
import org.junit.Test

/** 教务导入：周次字符串、各家教务样例 → 课程。样例在 test/resources/jw/。 */
class JwTest {
    private fun res(n: String) = javaClass.getResource("/jw/$n")!!.readText()
    private fun odd(a: Int, b: Int) = (a..b).filter { it % 2 == 1 }.toSet()
    private fun even(a: Int, b: Int) = (a..b).filter { it % 2 == 0 }.toSet()

    @Test fun weeksBasic() {
        assertEquals((1..16).toSet(), Jw.weeks("1-16周"))
        assertEquals((1..16).toSet(), Jw.weeks("1-16"))
        assertEquals(setOf(3, 5, 7), Jw.weeks("3、5、7周"))
        assertEquals(setOf(3, 5, 7), Jw.weeks("3,5,7"))
        assertEquals((1..16).toSet(), Jw.weeks("1-16(周)"))
        assertEquals(emptySet<Int>(), Jw.weeks(""))
    }

    @Test fun weeksOddEvenPerSegment() {
        assertEquals((1..8).toSet() + even(10, 16), Jw.weeks("1-8周,10-16周(双)"))
        assertEquals(odd(1, 16), Jw.weeks("1-16周(单)"))
        assertEquals(odd(1, 16), Jw.weeks("1-16单"))
        assertEquals(even(2, 16), Jw.weeks("2-16(双周)"))
        assertEquals(odd(1, 8) + odd(10, 16), Jw.weeks("1-8,10-16 单"))
        assertEquals(setOf(1, 2, 3, 9), Jw.weeks("1～3周，9周"))
    }

    @Test fun bitsAndSections() {
        assertEquals(setOf(1, 2, 3), Jw.bits("01110000", 0))
        assertEquals(setOf(1, 2, 3), Jw.bits("11100000", 1))
        assertEquals(1 to 2, Jw.sections("第1,2节"))
        assertEquals(1 to 2, Jw.sections("01-02节"))
        assertEquals(5 to 7, Jw.sections("5-7"))
    }

    @Test fun neuWisedu() {
        assertEquals("2026-2027-1" to "2026-2027学年第一学期", Jw.wiseduTerm(res("neu_term.json")))
        assertEquals(listOf("1" to "浑南校区", "2" to "南湖校区"), Jw.wiseduCampuses(res("neu_campus.json")))
        val r = Jw.wisedu(res("neu_detail.json"))
        assertEquals(4, r.size)
        val math = r.first { it.name.startsWith("高等数学") }
        assertEquals(JwCourse("高等数学①(一)", 1, 1, 2, (1..16).toSet(), "刘思妤", "教103"), math)
        val eng = r.filter { it.name == "大学英语(一)" }
        assertEquals(2, eng.size)
        assertEquals((1..8).toSet() + even(10, 16), eng[0].weeks)
        assertEquals("宋岩", eng[0].teacher); assertEquals("机401-语音室", eng[0].room)
        assertEquals(setOf(9), eng[1].weeks); assertEquals("文管B201", eng[1].room)
        val chem = r.first { it.name == "无机化学(1)" }
        assertEquals(setOf(3, 5, 7), chem.weeks); assertEquals("徐君莉,谢宏伟", chem.teacher); assertEquals("逸310", chem.room)
        assertEquals(5 to 6, chem.start to chem.end); assertEquals(5, chem.day)
        // 走汇总入口也一样
        assertEquals(4, Jw.parse("neu", org.json.JSONObject().put("json", res("neu_detail.json")).toString()).size)
    }

    @Test fun zhengfang() {
        val r = Jw.zfJson(res("zf_kb.json"))
        assertEquals(listOf(
            JwCourse("数据结构", 2, 1, 2, odd(1, 16), "王老师", "信息楼A201"),
            JwCourse("操作系统", 4, 5, 7, (1..8).toSet() + (10..12).toSet(), "李老师", "B305")), r)
        val old = Jw.zfOld(res("zf_old.html"))
        assertEquals(2, old.size)
        assertEquals(JwCourse("电路分析", 1, 1, 2, (1..16).toSet(), "韩老师", "实验楼302"), old[0])
        assertEquals(even(2, 16), old.first { it.name == "模拟电子" }.weeks)
        assertEquals(4, old.first { it.name == "模拟电子" }.day)
    }

    @Test fun qiangzhi() {
        val r = Jw.qz(res("qz.html")).sortedBy { it.name }
        assertEquals(4, r.size)
        val la = r.first { it.name == "线性代数" }
        assertEquals(JwCourse("线性代数", 1, 1, 2, (1..16).toSet(), "赵老师", "C101"), la)
        assertEquals(even(2, 16), r.first { it.name == "大学物理" }.weeks)
        assertEquals(odd(1, 15), r.first { it.name == "体育" }.weeks)
        assertEquals(3, r.first { it.name == "体育" }.day)
        val mk = r.first { it.name == "马克思主义原理" }
        assertEquals(2, mk.day); assertEquals(3 to 4, mk.start to mk.end)   // 第二大节没写节次，按大节推
    }

    @Test fun urpNew() {
        val r = Jw.urpNew(res("urpnew.json")).sortedBy { it.day }
        assertEquals(listOf(
            JwCourse("概率论", 3, 3, 4, (1..8).toSet(), "吴老师", "一教101"),
            JwCourse("编译原理", 5, 7, 9, even(2, 16), "郑老师", "二教205")), r)
    }

    @Test fun shuweiEams() {
        val r = Jw.eams(res("eams.html"))
        assertEquals(2, r.size)
        assertEquals(JwCourse("离散数学", 2, 3, 4, (1..16).toSet(), "冯老师", "理科楼301"), r[0])
        assertEquals(JwCourse("计算机网络", 5, 7, 9, setOf(2, 4, 6, 8, 10, 12, 14, 16), "陈老师,褚老师", "文科楼B12"), r[1])
    }

    @Test fun genericTable() {
        val r = Jw.table(res("table.html"))
        assertEquals(2, r.size)
        assertEquals(JwCourse("软件工程", 1, 1, 2, (1..16).toSet(), "何老师", "F401"), r.first { it.name == "软件工程" })
        val ja = r.first { it.name == "日语" }
        assertEquals(3, ja.day); assertEquals(3 to 4, ja.start to ja.end); assertEquals("外语楼207", ja.room); assertEquals((2..8).toSet(), ja.weeks)
        // 其他类型（青果等）走通用识别
        assertEquals(2, Jw.parse("kingo", org.json.JSONObject().put("html", org.json.JSONArray().put(res("table.html"))).toString()).size)
        // 强智页面用通用类型也能认出（专用解析优先）
        assertEquals(4, Jw.parse("generic", org.json.JSONObject().put("html", org.json.JSONArray().put(res("qz.html"))).toString()).size)
    }

    @Test fun csvTable() {
        val r = Jw.csv(res("courses.csv"))
        assertEquals(3, r.size)
        assertEquals(JwCourse("高等数学", 1, 1, 2, (1..16).toSet(), "张三", "教103"), r[0])
        assertEquals("大学物理, 实验", r[1].name); assertEquals(odd(1, 16), r[1].weeks); assertEquals(5 to 6, r[1].start to r[1].end)
        assertEquals(5, r[2].day); assertEquals(setOf(3, 5, 7), r[2].weeks)
    }

    @Test fun cleanMergesWeeks() {
        val a = JwCourse("A", 1, 1, 2, setOf(1, 2), "t", "r"); val b = a.copy(weeks = setOf(5))
        assertEquals(listOf(a.copy(weeks = setOf(1, 2, 5))), Jw.clean(listOf(a, b)))
    }

    @Test fun toSlotsUsesSectionTimes() {
        val starts = listOf(480, 535, 600, 655); val ends = listOf(525, 580, 645, 700)
        val s = Jw.toSlots(listOf(JwCourse("A", 2, 2, 3, (1..20).toSet(), "t", "r"), JwCourse("B", 1, 4, 5, setOf(1))), starts, ends, 20)
        assertEquals(535 to 645, s[0].from to s[0].to)
        assertTrue(s[0].weeks.isEmpty())          // 全部周 = 每周
        assertEquals(setOf(2), s[0].days)
        assertEquals(655, s[1].from); assertTrue(s[1].to > 700)   // 超出节数往后推
    }

    @Test fun backupRoundTrip() {
        val l = listOf(Slot("a", "高数", setOf(1), 480, 570, 2, room = "教103", weeks = setOf(1, 3)))
        val j = Term.backupJson(l, listOf(480, 535), listOf(525, 580), 45, 20000L, 18)
        val b = Term.parseBackup(j)!!
        assertEquals(l, b.courses); assertEquals(listOf(480, 535), b.starts); assertEquals(listOf(525, 580), b.ends)
        assertEquals(20000L, b.term); assertEquals(18, b.weeks)
    }
}
