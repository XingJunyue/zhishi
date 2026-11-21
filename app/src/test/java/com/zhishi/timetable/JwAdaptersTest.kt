package com.zhishi.timetable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * jw-adapters：移植自 shiguang_warehouse / baoozak/timetable（MIT）的解析逻辑，用构造的样例页面测。
 * 这些样例按公开脚本读取的页面结构写成，没有一个来自真实账号登录——真实教务可能还有出入。
 */
class JwAdaptersTest {
    private fun res(n: String) = javaClass.getResource("/jw/$n")!!.readText()
    private fun odd(a: Int, b: Int) = (a..b).filter { it % 2 == 1 }.toSet()
    private fun even(a: Int, b: Int) = (a..b).filter { it % 2 == 0 }.toSet()
    private fun html(vararg h: String) = JSONObject().put("html", JSONArray(h.toList())).toString()

    @Test fun zhengfangGrid() {
        val r = JwA.zfHtml(res("zf_grid.html"))
        assertEquals(4, r.size)
        assertEquals(JwCourse("高等数学A(一)", 1, 1, 2, (1..8).toSet() + even(10, 16), "王建国,李敏", "教1-201"), r.first { it.name.startsWith("高等数学") })
        // 一格两门课，单双周各一
        assertEquals(JwCourse("大学英语(二)", 3, 1, 2, odd(1, 15), "Smith", "外语楼305"), r.first { it.name == "大学英语(二)" })
        assertEquals(JwCourse("英语听说", 3, 1, 2, even(2, 16), "陈晓", "外语楼306"), r.first { it.name == "英语听说" })
        // 逗号周次列表、3 节连上
        assertEquals(JwCourse("程序设计基础", 2, 3, 5, setOf(3, 5, 6, 7, 9), "赵六", "机房B"), r.first { it.name == "程序设计基础" })
        // 接口没拿到（返回登录页）时，汇总入口退回页面网格
        val p = JSONObject().put("json", "<html>login</html>").put("html", JSONArray().put(res("zf_grid.html"))).toString()
        assertEquals(4, Jw.parse("zf", p).size)
    }

    @Test fun zhengfangList() {
        val r = JwA.zfHtml(res("zf_list.html"))
        assertEquals(listOf(
            JwCourse("线性代数", 1, 1, 2, (1..16).toSet(), "孙明", "理科楼101"),
            JwCourse("线性代数习题课", 1, 1, 2, even(2, 16), "孙明", "理科楼102"),
            JwCourse("中国近现代史纲要", 2, 5, 6, (1..8).toSet(), "周强,吴丽", "文科楼201")), r)
    }

    @Test fun zhengfangCompactJson() {
        val r = Jw.zfJson(res("zf_compact.json"))
        assertEquals(JwCourse("数值分析", 3, 3, 4, (1..12).toSet(), "马力", "教3-105"), r[0])
        assertEquals(JwCourse("体育(二)", 5, 7, 8, odd(1, 16), "刘洋", "体育馆"), r[1])
        assertEquals(1 to 2, Jw.sections("0102")); assertEquals(3 to 5, Jw.sections("030405"))
    }

    @Test fun qiangzhiRowLabelsOddEvenMerge() {
        val r = Jw.qz(res("qz2.html"))
        assertEquals(3, r.size)
        assertEquals(JwCourse("数据库原理", 1, 1, 2, odd(1, 16), "郑一,王二", "信息楼301"), r.first { it.name == "数据库原理" })
        assertEquals(JwCourse("数据库实验", 1, 1, 2, even(2, 16), "王二", "机房4"), r.first { it.name == "数据库实验" })
        // 行标签给出 05,06 / 07,08 节，连着两大节合成 5-8
        assertEquals(JwCourse("计算机组成", 3, 5, 8, (1..8).toSet() + (10..17).toSet(), "冯三", "信息楼205"), r.first { it.name == "计算机组成" })
        assertEquals(3 to 4, JwA.qzRowSections("第二大节 (03,04小节) 10:00-11:40"))
        assertNull(JwA.qzRowSections("第二大节 10:00-11:40"))
    }

    @Test fun urpNewPage() {
        val r = JwA.urpNewHtml(res("urpnew_page.html"))
        assertEquals(JwCourse("大学物理(上)", 1, 1, 2, (1..16).toSet(), "韩梅", "江安 一教A座201"), r.first { it.name == "大学物理(上)" })
        assertEquals(JwCourse("军事理论", 3, 1, 2, (1..8).toSet(), "赵刚,钱进", "江安 综合楼B102"), r.first { it.name == "军事理论" })
        assertEquals((1..5).toSet() + setOf(7, 8, 10, 12) + (13..17).toSet(), r.first { it.name == "线性代数" }.weeks)
        assertEquals(5 to 6, r.first { it.name == "线性代数" }.let { it.start to it.end })
        // 接口失败 → 页面
        assertEquals(3, Jw.parse("urpnew", JSONObject().put("json", "null").put("html", JSONArray().put(res("urpnew_page.html"))).toString()).size)
    }

    @Test fun urpOldList() {
        val r = JwA.urpList(res("urp_list.html"))
        assertEquals(listOf(
            JwCourse("操作系统", 2, 3, 4, (1..16).toSet(), "杨帆,钱多", "第二教学楼 201"),
            JwCourse("操作系统", 4, 1, 2, odd(1, 15), "杨帆,钱多", "第二教学楼 305"),
            JwCourse("编译原理", 5, 7, 9, even(2, 16), "许诺", "实验楼B105")), r)
        assertEquals(3, Jw.parse("urp", html(res("urp_list.html"))).size)
    }

    @Test fun qingguo() {
        val r = JwA.kingo(res("kingo.html"))
        assertEquals(4, r.size)
        assertEquals(JwCourse("概率论与数理统计", 1, 1, 2, (1..16).toSet(), "何平", "教学楼A305"), r.first { it.name.startsWith("概率论") })
        assertEquals(JwCourse("电工学", 3, 1, 2, (1..8).toSet() + (10..12).toSet(), "严谨", "工科楼101"), r.first { it.name == "电工学" })
        assertEquals(JwCourse("金工实习", 3, 1, 2, (13..16).toSet(), "孔武", "工程训练中心"), r.first { it.name == "金工实习" })
        assertEquals(JwCourse("形势与政策", 2, 3, 4, setOf(2, 4, 6, 8), "", "报告厅"), r.first { it.name == "形势与政策" })
        assertEquals(4, Jw.parse("kingo", html(res("kingo.html"))).size)
    }

    @Test fun chaoxing() {
        val r = JwA.cx(res("cx.json"))
        assertEquals(listOf(
            JwCourse("高级语言程序设计", 1, 1, 3, (1..8).toSet(), "林森", "实训楼502"),
            JwCourse("高级语言程序设计", 1, 4, 4, setOf(9, 10), "林森", "实训楼502"),
            JwCourse("大学语文", 4, 5, 6, even(2, 12), "苏文,白雪", "")), r)
        assertEquals("2026-2027-1", JwA.cxTerm(2026, 11))
        assertEquals("2026-2027-1", JwA.cxTerm(2027, 1))
        assertEquals("2026-2027-2", JwA.cxTerm(2027, 3))
    }

    @Test fun nanruanHrSeparated() {
        val r = JwA.nr(res("nr.html"))
        assertEquals(3, r.size)
        assertEquals(JwCourse("机械原理", 2, 1, 2, (1..12).toSet(), "高远", "工程馆203"), r.first { it.name == "机械原理" })
        assertEquals(JwCourse("机械原理实验", 2, 1, 2, (13..16).toSet(), "高远", "实验中心101"), r.first { it.name == "机械原理实验" })
        assertEquals(JwCourse("大学化学", 1, 3, 4, (1..16).toSet(), "田甜", "化学楼110"), r.first { it.name == "大学化学" })
    }

    @Test fun mergeAdjacentKeepsDifferentWeeksApart() {
        val a = JwCourse("A", 1, 1, 2, setOf(1, 2), "t", "r")
        assertEquals(listOf(a.copy(end = 4)), JwA.mergeAdjacent(listOf(a, a.copy(start = 3, end = 4))))
        assertEquals(2, JwA.mergeAdjacent(listOf(a, a.copy(start = 3, end = 4, weeks = setOf(3)))).size)
    }

    // ---------------- 学校列表 ----------------

    private fun schoolsFile(): String = listOf("src/main/assets/schools.tsv", "app/src/main/assets/schools.tsv").map { File(it) }.first { it.exists() }.readText()

    @Test fun schoolListParsesAndSearches() {
        val all = Schools.parse(schoolsFile())
        assertTrue("school list too small: ${all.size}", all.size > 1500)
        val ids = Jw.KINDS.map { it.id }.toSet()
        assertTrue(all.all { it.kind in ids })
        assertTrue(all.all { it.url.startsWith("http") })
        assertTrue(all.none { it.name == "东北大学" })   // 东大有内置入口，不在列表里重复
        assertEquals("大连理工大学", Schools.search(all, "大连理工")[0].name)
        val nefu = Schools.search(all, "东北林业大学")[0]
        assertEquals("qz", nefu.kind); assertEquals("s", nefu.src)
        assertTrue(Schools.search(all, "北邮").any { it.name == "北京邮电大学" })
        assertTrue(Schools.search(all, "  ").isEmpty())
        assertTrue(Schools.search(all, "大学", 30).size <= 30)
    }

    @Test fun schoolTsvFormat() {
        val l = Schools.parse("# head\n甲大学\tzf\thttps://a.edu.cn/jwglxt\ts\t入口\n乙学院\tweird\thttps://b.edu.cn\tb\n坏行\n")
        assertEquals(2, l.size)
        assertEquals(School("甲大学", "zf", "https://a.edu.cn/jwglxt", "s", "入口"), l[0])
        assertEquals("generic", Schools.kindOf(l[1]))
    }
}
