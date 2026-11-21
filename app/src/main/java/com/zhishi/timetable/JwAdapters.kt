package com.zhishi.timetable

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 金智（东大）以外各家教务的补充解析。纯函数，不碰 Android，单测直接喂样例。
 *
 * 逻辑移植自以下 MIT 项目（署名与许可全文见仓库根目录 THIRD_PARTY_NOTICES.md）：
 *  - ShiGuangSchedule/shiguang_warehouse  © 2025 星河欲转 —— 社区在真实教务上维护的适配脚本：
 *      zhengfang_jiaowu/zhengfang_01.js（正方网格 / 列表视图）、urp_jiaowu/urp_01.js（新 URP 页面）、
 *      qingguo_jiaowu/qingguo_01.js（青果「周次[节次]」）、chaoxing_jiaowu/chaoxing.js（超星 sdpkkbList 接口与连排合并）、
 *      CSU/csu.js（强智：节次取行标签、单双周字段、两阶段合并）
 *  - baoozak/timetable  © 2026 baoozak —— utils/parsers：正方紧凑节次与备用接口、南软 tb_kcb 表格、<hr> 分隔多门课
 * 都是按思路用 Kotlin/Jsoup 重写的，不是逐行翻译；没有用任何 GPL 项目的代码。
 */
object JwA {
    private val MARK = Regex("[●★○◆☆■□▲△]")

    /** 去掉括号里的职称等：「张三（教授）」→「张三」。 */
    fun cleanTeacher(s: String) = s.replace(Regex("[(（][^)）]*[)）]"), "").replace(Regex("\\*\\s+(?=\\S)"), ",").replace("*", "")
        .replace(Regex("\\s+"), " ").trim().trim(',', '，', ' ')

    /**
     * URP 的周次：每个逗号段自己管自己的单双（「1-5,7-8,10-12周双周,13-17周」只有 10-12 是双周），
     * 和 Jw.weeks 里「末尾一个单字管全部」的读法不同，所以单独一个（拾光 urp_01.js parseWeekText）。
     */
    fun weeksPerSeg(t: String): Set<Int> = t.split(',', '，', ';', '；', '、').flatMap { seg -> Jw.weeks(seg) }.toSet()

    /**
     * 连排合并（拾光 chaoxing.js / csu.js 的阶段 1）：同名、同老师、同教室、同一天、周次完全一样，
     * 节次首尾相接的合成一段。周次不同的不合并，免得把分阶段排课并错。
     */
    fun mergeAdjacent(l: List<JwCourse>): List<JwCourse> {
        val out = ArrayList<JwCourse>()
        val groups = l.groupBy { listOf(it.name, it.teacher, it.room, it.day, it.weeks.sorted()) }
        for ((_, g) in groups) {
            val s = g.sortedBy { it.start }
            var cur = s.first()
            for (n in s.drop(1)) {
                cur = if (n.start <= cur.end + 1) cur.copy(end = maxOf(cur.end, n.end)) else { out += cur; n }
            }
            out += cur
        }
        return out.sortedWith(compareBy({ it.day }, { it.start }, { it.name }))
    }

    // ---------------- 正方：页面里的网格 / 列表视图 ----------------

    /**
     * 新版正方「学生课表查询」页面。接口拿不到（被拦、换了 gnmkdm）时用页面上已经画好的课表。
     * 网格视图：table#kbgrid_table_0，格子 td.td_wrap 的 id 是「星期-节」，每门课一个 .timetable_con。
     * 列表视图：table#kblist_table，第 n 个 tbody 是星期 n。
     */
    fun zfHtml(html: String): List<JwCourse> {
        val doc = Jsoup.parse(html)
        val out = ArrayList<JwCourse>()
        doc.getElementById("kbgrid_table_0")?.let { t ->
            for (td in t.select("td")) {
                val day = td.id().substringBefore('-').toIntOrNull()?.takeIf { it in 1..7 } ?: continue
                for (con in td.select(".timetable_con")) zfCon(con, day, null)?.let { out += it }
            }
        }
        if (out.isEmpty()) doc.getElementById("kblist_table")?.let { t ->
            t.select("tbody").forEachIndexed { day, tb ->
                if (day !in 1..7) return@forEachIndexed
                var sec: Pair<Int, Int>? = null
                for (tr in tb.select("tr")) {
                    val tds = tr.children().toList().filter { it.tagName() == "td" }
                    if (tds.isEmpty() || tr.selectFirst(".title") == null) continue
                    val body = if (tds.size > 1) { sec = Jw.sections(tds[0].text()) ?: sec; tds[1] } else tds[0]
                    val s = sec ?: continue
                    zfCon(body, day, s)?.let { out += it }
                }
            }
        }
        return out
    }

    /** 一门课的块：.title 是课名，各个 <p> 按标签认「节/周」「地点」「教师」。 */
    private fun zfCon(con: Element, day: Int, sec0: Pair<Int, Int>?): JwCourse? {
        val name = con.select(".title").text().replace(MARK, "").trim().takeIf { it.isNotEmpty() } ?: return null
        var sec = sec0; var w: Set<Int> = emptySet(); var room = ""; var teacher = ""
        // 列表视图一个 <p> 里并排几个 <font>（周数：/上课地点：/教师 ：），拆开各算一项
        val lab = Regex("^\\s*(周数|上课地点|教师|节/周|课程学时|考核方式)")
        val ps = con.select("p").flatMap { p -> p.select("font").let { fs -> if (fs.size > 1 && fs.count { lab.containsMatchIn(it.text()) } >= 2) fs else listOf(p) } }
        for ((i, p) in ps.withIndex()) {
            val label = p.select("[title]").joinToString(" ") { it.attr("title") }
            val t = p.text().replace('\u00a0', ' ').trim()
            val m = Regex("\\((\\d+)\\s*-\\s*(\\d+)节\\)").find(t)
            when {
                m != null -> { sec = m.groupValues[1].toInt() to m.groupValues[2].toInt(); w = Jw.weeks(t.substringAfter("节)")) }
                t.startsWith("周数") || label.contains("周") && !label.contains("节") -> w = Jw.weeks(t.substringAfter('：').substringAfter(':'))
                label.contains("地点") || t.startsWith("上课地点") -> room = t.substringAfter('：').substringAfter(':').trim().split(Regex("\\s+")).last()
                label.contains("教师") || t.startsWith("教师") -> teacher = cleanTeacher(t.substringAfter('：').substringAfter(':'))
                // 没有标签时按拾光脚本的位置：0 节/周，1 地点，2 教师
                i == 1 && room.isEmpty() -> room = t.split(Regex("\\s+")).last()
                i == 2 && teacher.isEmpty() -> teacher = cleanTeacher(t)
            }
        }
        val s = sec ?: return null
        return JwCourse(name, day, s.first, s.second, w, teacher, room)
    }

    // ---------------- 强智：节次取行标签 ----------------

    /** 「第一大节 (01,02小节) 08:00-09:40」→ 1..2；只有时间、没节次就返回 null。 */
    fun qzRowSections(label: String): Pair<Int, Int>? {
        val t = label.replace(Regex("\\d{1,2}[:：]\\d{2}"), " ")
        if (!Regex("\\d").containsMatchIn(t)) return null
        return Jw.sections(t)
    }

    // ---------------- 新 URP：页面 ----------------

    /**
     * 新 URP「本学期课表」页面（接口失败时用）：td 的 id 是「星期_节」，每门课一个 .class_div，
     * 里面依次是 p：课名、[教师]、周次、节次、地点（拾光 urp_01.js 的两种排法都认）。
     */
    fun urpNewHtml(html: String): List<JwCourse> {
        val out = ArrayList<JwCourse>()
        for (td in Jsoup.parse(html).select("td[id]")) {
            val parts = td.id().split('_')
            if (parts.size != 2) continue
            val day = parts[0].toIntOrNull()?.takeIf { it in 1..7 } ?: continue
            val row = parts[1].toIntOrNull()
            for (div in td.select(".class_div")) {
                val ps = div.select("p").map { it.text().replace('\u00a0', ' ').trim() }
                if (ps.size < 4) continue
                val name = ps[0].takeIf { it.isNotEmpty() } ?: continue
                var teacher = ""; var w: Set<Int> = emptySet(); var sec: Pair<Int, Int>? = null; var room = ""
                for (x in ps.drop(1)) {
                    when {
                        Regex("周").containsMatchIn(x) && Regex("\\d").containsMatchIn(x) && w.isEmpty() -> w = weeksPerSeg(x)
                        Regex("^\\d+\\s*(?:-\\s*\\d+)?\\s*节?$").matches(x) && sec == null -> sec = Jw.sections(x)
                        teacher.isEmpty() && w.isEmpty() && !Regex("\\d").containsMatchIn(x) && x.length < 20 -> teacher = cleanTeacher(x)
                        room.isEmpty() && (w.isNotEmpty() || sec != null) -> room = x
                    }
                }
                val s = sec ?: row?.let { it to it } ?: continue
                out += JwCourse(name, day, s.first, s.second, w, teacher, room)
            }
        }
        return out
    }

    // ---------------- 旧 URP：选课结果列表 ----------------

    /**
     * 旧版 URP（xkAction.do?actionType=6「本学期课表」下方的列表）：
     * 表头有「课程名」「星期」「节次」，同一门课的多个时间段用 rowspan 合并前面的列。
     * 按表头名字找列，「节数」给了就用 节次+节数-1 当结束节。
     */
    fun urpList(html: String): List<JwCourse> {
        val doc = Jsoup.parse(html)
        var best = emptyList<JwCourse>()
        for (t in doc.select("table")) {
            if (t.select("table").size > 1) continue
            val g = grid(t)
            val hr = g.indexOfFirst { r -> r.any { it?.text()?.trim() == "星期" } && r.any { it?.text()?.contains("课程名") == true } }
            if (hr < 0) continue
            val head = g[hr].map { it?.text()?.trim().orEmpty() }
            fun col(vararg n: String) = head.indexOfFirst { h -> n.any { h == it } }.takeIf { it >= 0 }
                ?: head.indexOfFirst { h -> n.any { h.contains(it) } }
            val cName = col("课程名"); val cDay = col("星期"); val cSec = col("节次"); val cN = col("节数")
            val cWeek = col("周次"); val cT = col("教师", "任课教师"); val cB = col("教学楼"); val cR = col("教室")
            if (cName < 0 || cDay < 0 || cSec < 0) continue
            val out = ArrayList<JwCourse>()
            for (r in g.drop(hr + 1)) {
                fun v(i: Int) = if (i < 0) "" else r.getOrNull(i)?.text()?.replace('\u00a0', ' ')?.trim().orEmpty()
                val name = v(cName).takeIf { it.isNotEmpty() } ?: continue
                val ds = v(cDay)
                val day = ds.toIntOrNull()?.takeIf { it in 1..7 } ?: Ocr.dayOf(ds) ?: Ocr.dayOf("星期$ds") ?: continue
                val sec = Jw.sections(v(cSec)) ?: continue
                val n = v(cN).toIntOrNull()
                val end = if (n != null && n > 0 && sec.first == sec.second) sec.first + n - 1 else sec.second
                val room = (v(cB) + " " + v(cR)).trim().let { if (v(cB).isNotEmpty() && v(cR).startsWith(v(cB))) v(cR) else it }
                out += JwCourse(name.replace(MARK, "").trim(), day, sec.first, end, weeksPerSeg(v(cWeek)), cleanTeacher(v(cT)), room)
            }
            if (out.size > best.size) best = out
        }
        return best
    }

    // ---------------- 青果 ----------------

    /**
     * 青果（KINGOSOFT jwweb）课表：找含「星期一」又有「[」的表，每行最后 7 格是周一到周日；
     * 格子里一行「1-16[1-2]」（周次[节次]），它上面两行是课名、老师，下面一行是教室（拾光 qingguo_01.js）。
     */
    fun kingo(html: String): List<JwCourse> {
        val doc = Jsoup.parse(html)
        val t = doc.select("table").toList().filter { it.select("table").size == 1 }
            .firstOrNull { it.text().contains("星期一") && it.text().contains("[") } ?: return emptyList()
        val pat = Regex("^([\\d\\-,，、\\s单双周()（）]+?)\\s*\\[(\\d+)\\s*-\\s*(\\d+)节?]")
        val out = ArrayList<JwCourse>()
        for (tr in t.select("tr")) {
            if (tr.closest("table") !== t) continue
            val cells = tr.children().toList().filter { it.tagName() == "td" || it.tagName() == "th" }
            if (cells.size < 7) continue
            cells.forEachIndexed { ci, cell ->
                val dist = cells.size - 1 - ci
                if (dist > 6) return@forEachIndexed
                val day = 7 - dist
                val ls = Jw.lines(cell).map { it.trim() }.filter { it.isNotEmpty() }
                ls.forEachIndexed { i, l ->
                    val m = pat.find(l) ?: return@forEachIndexed
                    val prevIsPat = { k: Int -> k >= 0 && pat.containsMatchIn(ls[k]) }
                    // 课名、老师在上面；上面紧挨着是上一门的教室时，说明这门课没写老师
                    val name: String; val teacher: String
                    if (i >= 2 && !prevIsPat(i - 1) && !prevIsPat(i - 2) && !(i >= 3 && prevIsPat(i - 3))) { name = ls[i - 2]; teacher = ls[i - 1] }
                    else if (i >= 1 && !prevIsPat(i - 1)) { name = ls[i - 1]; teacher = "" }
                    else return@forEachIndexed
                    val room = ls.getOrNull(i + 1)?.takeUnless { pat.containsMatchIn(it) } ?: ""
                    out += JwCourse(name.replace(Regex("\\s"), ""), day, m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                        Jw.weeks(m.groupValues[1]), cleanTeacher(teacher), room.replace(Regex("\\s"), ""))
                }
            }
        }
        // 拾光脚本的「矩阵合并」：同一门课按周展开节次再重新切段 —— 这里用周次相同的连排合并即可
        return mergeAdjacent(out)
    }

    // ---------------- 超星 ----------------

    /**
     * 超星教务 /admin/xsd/pkgl/xskb/sdpkkbList 的返回：{"ret":0,"data":[...]}，每条只是一节课：
     * kcmc（可能带 <a>）、tmc 老师、croommc 教室、xingqi 星期、djc 第几节、zcstr「1,2,3」。连着的节合并（拾光 chaoxing.js）。
     */
    fun cx(json: String): List<JwCourse> {
        val j = JSONObject(json)
        val a = j.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<JwCourse>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val name = text(o.optString("kcmc")).takeIf { it.isNotEmpty() } ?: continue
            val day = o.optString("xingqi").trim().toIntOrNull()?.takeIf { it in 1..7 } ?: continue
            val sec = o.optString("djc").trim().toIntOrNull()?.takeIf { it in 1..20 } ?: continue
            val w = o.optString("zcstr").split(',', '，').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..30 }.toSet()
            if (w.isEmpty()) continue
            out += JwCourse(name, day, sec, sec, w, cleanTeacher(text(o.optString("tmc"))), text(o.optString("croommc")))
        }
        return mergeAdjacent(out)
    }
    private fun text(s: String) = if (s.contains('<')) Jsoup.parseBodyFragment(s).text().trim() else s.trim()

    // ---------------- 南软 ----------------

    /** 南软：table.tb_kcb（没有就退回 #kbtable），多门课用 <hr> 隔开；格子交给通用的格子识别。 */
    fun nr(html: String): List<JwCourse> {
        val doc = Jsoup.parse(html)
        val t = doc.selectFirst("table.tb_kcb") ?: doc.getElementById("kbtable") ?: return emptyList()
        return Jw.table("<html><body>" + t.outerHtml() + "</body></html>")
    }

    // ---------------- 工具 ----------------

    /** 表格展开成网格（处理 rowspan/colspan），只看这张表自己的行。 */
    fun grid(t: Element): List<List<Element?>> {
        val g = ArrayList<MutableList<Element?>>()
        val rows = t.select("tr").toList().filter { it.closest("table") === t }
        for ((ri, tr) in rows.withIndex()) {
            while (g.size <= ri) g += ArrayList<Element?>()
            var ci = 0
            for (cell in tr.children().toList().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                while (ci < g[ri].size && g[ri][ci] != null) ci++
                val rs = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 60) ?: 1
                val cs = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 20) ?: 1
                for (dr in 0 until rs) for (dc in 0 until cs) {
                    val rr = ri + dr
                    while (g.size <= rr) g += ArrayList<Element?>()
                    while (g[rr].size <= ci + dc) g[rr].add(null)
                    g[rr][ci + dc] = cell
                }
                ci += cs
            }
        }
        return g
    }

    /** 超星的学年学期：页面下拉框没有时按日期猜，8 月到次年 1 月算第一学期。 */
    fun cxTerm(year: Int, month: Int): String =
        if (month >= 8) "$year-${year + 1}-1" else if (month <= 1) "${year - 1}-$year-1" else "${year - 1}-$year-2"
}
