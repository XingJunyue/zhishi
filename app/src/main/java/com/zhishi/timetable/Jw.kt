package com.zhishi.timetable

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 教务系统课表解析：输入网页 HTML 或接口返回的 JSON 字符串，输出课程列表。
 * 全是纯函数，不碰 Android，单元测试直接喂样例就行。
 * 各家教务的接口和页面结构参考了公开资料和 NEU 教务课表导出脚本（greasyfork 573005, MIT），解析代码是自己写的。
 *
 * jw-adapters 分支新增的解析逻辑移植自（均为 MIT，见 THIRD_PARTY_NOTICES.md）：
 *  - ShiGuangSchedule/shiguang_warehouse（拾光课程表适配仓库，© 2025 星河欲转）：
 *    正方 HTML 网格/列表视图、新 URP 页面、青果「周次[节次]」、超星 sdpkkbList 接口、强智节次标签与单双周字段、连排合并
 *  - baoozak/timetable（© 2026 baoozak）：正方紧凑节次「0102」、正方备用接口 cxXsgrkb、南软 tb_kcb 表格与 <hr> 分隔、学校列表
 * 只读过、没有复制 GPL 项目的代码。
 */
data class JwCourse(
    val name: String, val day: Int, val start: Int, val end: Int,
    val weeks: Set<Int> = emptySet(), val teacher: String = "", val room: String = ""
)

object Jw {
    /** 支持的教务类型；exp = 实验性（只靠通用表格识别）。 */
    class Kind(val id: String, val name: String, val note: String, val exp: Boolean = false)
    val KINDS = listOf(
        Kind("zf", "正方教务", "jwglxt · 新版 / 旧版"),
        Kind("qz", "强智教务", "jsxsd"),
        Kind("wisedu", "金智教务", "jwapp · 新教务"),
        Kind("urpnew", "新 URP", "student/courseSelect"),
        Kind("urp", "URP（旧）", "xkAction"),
        Kind("eams", "树维 eams", "courseTableForStd"),
        Kind("kingo", "青果", "jwweb · KINGOSOFT"),
        Kind("cf", "乘方", "", true),
        Kind("nr", "南软", "", true),
        Kind("cx", "超星", "admin/pkgl · sdpkkbList"),
        Kind("umooc", "优慕课", "", true),
        Kind("generic", "其他：从当前页面识别", "找页面里最大的课表表格"),
    )

    // ---------------- 周次 ----------------

    /**
     * 「1-8周,10-16周(双)」「3、5、7周」「1-16(周)」「2-16(双周)」「1-16单」→ 周次集合。
     * 单双只管它所在的那一段。认不出返回空集（= 每周）。
     */
    fun weeks(t0: String, max: Int = 30): Set<Int> {
        val t = t0.replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',')
            .replace('～', '-').replace('~', '-').replace('—', '-').replace('–', '-').replace('至', '-').replace(' ', ',')
        val out = sortedSetOf<Int>()
        // 先按逗号切；一段里只有数字没有单双时，看看后面整串有没有统一的单双（「1-8,10-16 单」）
        val segs = t.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val tailOdd = segs.lastOrNull()?.let { isOdd(it) } ?: false
        val tailEven = segs.lastOrNull()?.let { isEven(it) } ?: false
        var pending = ArrayList<Int>()   // 只有数字的段，等后面的单双
        fun flush(odd: Boolean, even: Boolean) {
            for (w in pending) if ((!odd || w % 2 == 1) && (!even || w % 2 == 0)) out += w
            pending = ArrayList()
        }
        for (s in segs) {
            val m = Regex("(\\d+)\\s*(?:-\\s*(\\d+))?").findAll(s).toList()
            if (m.isEmpty()) { if (isOdd(s) || isEven(s)) flush(isOdd(s), isEven(s)); continue }
            val odd = isOdd(s); val even = isEven(s)
            val ws = ArrayList<Int>()
            for (x in m) {
                val a = x.groupValues[1].toInt(); val b = x.groupValues[2].toIntOrNull() ?: a
                for (w in minOf(a, b)..maxOf(a, b)) if (w in 1..max) ws += w
            }
            if (odd || even) { flush(odd, even); for (w in ws) if ((!odd || w % 2 == 1) && (!even || w % 2 == 0)) out += w }
            else if (s.contains('周')) { flush(false, false); out.addAll(ws) }
            else pending.addAll(ws)
        }
        // 剩下的：没写单双的就是每周；「1-8,10-16 单」这种末尾一个「单」管全部
        flush(false, false)
        if (out.isEmpty() && (tailOdd || tailEven)) return (1..max).filter { if (tailOdd) it % 2 == 1 else it % 2 == 0 }.toSet()
        return out
    }
    private fun isOdd(s: String) = Regex("单|單|odd").containsMatchIn(s)
    private fun isEven(s: String) = Regex("双|雙|even").containsMatchIn(s)

    /** 「0111100…」这样的位串：第 i 位是 1 表示第 i+offset 周上课。 */
    fun bits(s: String, offset: Int = 0, max: Int = 30): Set<Int> =
        s.mapIndexedNotNull { i, ch -> if (ch == '1' && i + offset in 1..max) i + offset else null }.toSet()

    /** 「第1,2节」「01-02节」「1-2」「3」→ 起止节次。 */
    fun sections(s: String): Pair<Int, Int>? {
        // 正方有时给紧凑写法「0102」「030405」：每两位一节（baoozak/timetable, MIT）
        val t = s.trim()
        if (Regex("^\\d{4,}$").matches(t) && t.length % 2 == 0) {
            val n = t.chunked(2).map { it.toInt() }.filter { it in 1..20 }
            if (n.isNotEmpty()) return n.min() to n.max()
        }
        val n = Regex("\\d+").findAll(s).map { it.value.toInt() }.filter { it in 1..20 }.toList()
        if (n.isEmpty()) return null
        return n.min() to n.max()
    }

    // ---------------- 金智（东北大学新教务）----------------

    /** getMyScheduleDetail.do 的返回 → 课程。每条明细（不同周次、教室）单独成一节。 */
    fun wisedu(json: String): List<JwCourse> {
        val j = JSONObject(json)
        val list = j.optJSONObject("datas")?.optJSONObject("getMyScheduleDetail")?.optJSONArray("arrangedList")
            ?: j.optJSONObject("datas")?.optJSONArray("arrangedList") ?: JSONArray()
        val out = ArrayList<JwCourse>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val name = o.optString("courseName").trim().takeIf { it.isNotEmpty() } ?: continue
            val day = o.optInt("dayOfWeek", 0).takeIf { it in 1..7 } ?: continue
            val a = o.optInt("beginSection", 0); val b = o.optInt("endSection", a)
            if (a <= 0) continue
            val campus = o.optString("campusName").trim()
            val place = o.optString("placeName").trim()
            val details = ArrayList<String>()
            o.optJSONArray("titleWeekTeacherClassroomDetail")?.let { d -> for (k in 0 until d.length()) d.optString(k).takeIf { it.isNotBlank() }?.let { details += it } }
            if (details.isEmpty()) o.optJSONArray("cellWeekTeacherClassroomDetail")?.let { d ->
                for (k in 0 until d.length()) { val x = d.opt(k); val t = if (x is JSONObject) x.optString("text") else x?.toString() ?: ""; if (t.isNotBlank()) details += t }
            }
            if (details.isEmpty()) {
                out += JwCourse(name, day, a, maxOf(a, b), weeks(o.optString("weeksAndTeachers", o.optString("weeks", ""))), o.optString("teacherName", "").trim(), place)
                continue
            }
            for (d0 in details) {
                val d = d0.trim()
                // 「1-16周 张三 浑南校区 文管B201」：校区前面是「周次 老师」，后面是教室
                var head = d; var room = place
                if (campus.isNotEmpty() && d.contains(campus)) {
                    head = d.substringBefore(campus).trim(); room = d.substringAfter(campus).trim().ifEmpty { place }
                }
                val wm = Regex("^((?:[\\d\\s,，、\\-~]+周?(?:\\((?:单|双)\\)|（(?:单|双)）|单|双)?)+)").find(head)
                val wText = wm?.value ?: head.substringBefore(' ')
                val teacher = head.removePrefix(wText).trim().trim(',', '，')
                out += JwCourse(name, day, a, maxOf(a, b), weeks(wText), teacher, room)
            }
        }
        return out
    }

    /** cxmrxnxq.do → (学年学期代码, 名称)。 */
    fun wiseduTerm(json: String): Pair<String, String>? {
        val r = JSONObject(json).optJSONObject("datas")?.optJSONObject("cxmrxnxq")?.optJSONArray("rows")?.optJSONObject(0) ?: return null
        return r.optString("XNXQDM").ifEmpty { return null } to r.optString("XNXQMC")
    }

    /** getMyScheduledCampus.do → [(id, 名字)]。 */
    fun wiseduCampuses(json: String): List<Pair<String, String>> {
        val a = JSONObject(json).optJSONObject("datas")?.optJSONArray("getMyScheduledCampus") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optString("id") to it.optString("name") }.filter { it.first.isNotEmpty() }
    }

    // ---------------- 正方 ----------------

    /** 正方课名后面常带的标记：★ 理论课、● 实验等。 */
    private val ZF_MARK = Regex("[●★○◆☆]")

    /** 新版正方 xskbcx_cxXsKb.html 返回的 JSON（kbList）。 */
    fun zfJson(json: String): List<JwCourse> {
        val t = json.trim()
        val a = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).let { it.optJSONArray("kbList") ?: it.optJSONObject("data")?.optJSONArray("kbList") } ?: return emptyList()
        val out = ArrayList<JwCourse>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val name = o.optString("kcmc").replace(ZF_MARK, "").trim().takeIf { it.isNotEmpty() } ?: continue
            val day = o.optString("xqj").trim().toIntOrNull()?.takeIf { it in 1..7 } ?: continue
            val (s, e) = sections(o.optString("jcs").ifBlank { o.optString("jc") }) ?: continue
            out += JwCourse(name, day, s, e, weeks(o.optString("zcd")), o.optString("xm").trim(), o.optString("cdmc").ifBlank { o.optString("cddm") }.trim())
        }
        return out
    }

    /** 旧版正方：格子里「高等数学<br>周一第1,2节{第1-16周}<br>张三<br>A101」。 */
    fun zfOld(html: String): List<JwCourse> {
        val out = ArrayList<JwCourse>()
        val doc = Jsoup.parse(html)
        for (td in doc.select("td")) {
            if (td.select("td").size > 1) continue
            val lines = lines(td)
            var i = 0
            while (i < lines.size) {
                val m = Regex("(?:周|星期)([一二三四五六日天])第([\\d,，]+)节\\{?([^}]*)\\}?").find(lines[i])
                if (m == null || i == 0) { i++; continue }
                val day = "一二三四五六日".indexOf(m.groupValues[1].replace("天", "日")) + 1
                val sec = sections(m.groupValues[2])
                if (sec == null) { i++; continue }
                val wt = m.groupValues[3]
                var w = weeks(wt.replace("第", ""))
                if (w.isNotEmpty() && Regex("单周").containsMatchIn(wt)) w = w.filter { it % 2 == 1 }.toSet()
                if (w.isNotEmpty() && Regex("双周").containsMatchIn(wt)) w = w.filter { it % 2 == 0 }.toSet()
                out += JwCourse(lines[i - 1], day, sec.first, sec.second, w, lines.getOrElse(i + 1) { "" }, lines.getOrElse(i + 2) { "" }.takeUnless { isTimeLine(it) } ?: "")
                i += 3
            }
        }
        return out.distinct()
    }
    private fun isTimeLine(s: String) = Regex("(?:周|星期)[一二三四五六日天]第").containsMatchIn(s)

    // ---------------- 强智 ----------------

    /** 强智 jsxsd 课表页：table#kbtable，格子里 div.kbcontent，多门课用一串横杠隔开。 */
    fun qz(html: String): List<JwCourse> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("kbtable") ?: doc.selectFirst("table:has(div.kbcontent)") ?: return emptyList()
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList()
        // 表头：星期几在第几列
        val head = rows.first()!!.children()
        val colDay = HashMap<Int, Int>()
        head.forEachIndexed { i, th -> Ocr.dayOf(th.text())?.let { colDay[i] = it } }
        val out = ArrayList<JwCourse>()
        var bigIdx = 0
        for (r in rows.drop(1)) {
            val cells = r.children()
            if (cells.none { it.selectFirst("div.kbcontent") != null }) { if (cells.size > 1) bigIdx++; continue }
            bigIdx++
            // 行标签（第一格）写了节次就用它，比「第几大节×2」准（拾光 CSU/csu.js）
            val rowSec = cells.firstOrNull()?.takeIf { it.selectFirst("div.kbcontent") == null }?.let { JwA.qzRowSections(it.text()) }
            cells.forEachIndexed { ci, td ->
                val div = td.selectFirst("div.kbcontent") ?: return@forEachIndexed
                val day = colDay[ci] ?: (ci).takeIf { colDay.isEmpty() && it in 1..7 } ?: return@forEachIndexed
                val chunks = div.html().split(Regex("-{5,}"))
                for (ch in chunks) {
                    val frag = Jsoup.parseBodyFragment(ch).body()
                    val name = frag.childNodes().takeWhile { !(it is Element && (it.tagName() == "br" || it.tagName() == "font")) }
                        .joinToString("") { if (it is TextNode) it.text() else (it as? Element)?.text() ?: "" }.trim()
                        .ifEmpty { lines(frag).firstOrNull() ?: "" }
                    if (name.isEmpty() || name == "&nbsp;") continue
                    val teacher = frag.selectFirst("font[title~=老师|教师]")?.text()?.trim() ?: ""
                    val wk = frag.selectFirst("font[title~=周次]")?.text()?.trim() ?: ""
                    val room = frag.selectFirst("font[title~=教室|地点]")?.text()?.trim() ?: ""
                    val sd = frag.selectFirst("font[title~=单双周]")?.text()?.trim() ?: ""
                    val sec = Regex("\\[([\\d\\-]+)节]").find(wk)?.let { sections(it.groupValues[1]) } ?: rowSec ?: ((bigIdx * 2 - 1) to (bigIdx * 2))
                    var w = weeks(wk.substringBefore('['))
                    if (w.isNotEmpty() && sd.contains("单")) w = w.filter { it % 2 == 1 }.toSet()
                    if (w.isNotEmpty() && sd.contains("双")) w = w.filter { it % 2 == 0 }.toSet()
                    out += JwCourse(name, day, sec.first, sec.second, w, JwA.cleanTeacher(teacher), room)
                }
            }
        }
        return JwA.mergeAdjacent(out)
    }

    // ---------------- 新 URP ----------------

    fun urpNew(json: String): List<JwCourse> {
        val root = JSONObject(json)
        val xk = root.optJSONArray("xkxx") ?: return emptyList()
        val out = ArrayList<JwCourse>()
        val courses = ArrayList<JSONObject>()
        for (i in 0 until xk.length()) {
            val m = xk.optJSONObject(i) ?: continue
            if (m.has("courseName")) courses += m
            else m.keys().forEach { k -> m.optJSONObject(k)?.let { courses += it } }
        }
        for (c in courses) {
            val name = c.optString("courseName").trim().takeIf { it.isNotEmpty() } ?: continue
            val teacher = c.optString("attendClassTeacher").replace("*", "").trim().trim(',', ' ')
            val tp = c.optJSONArray("timeAndPlaceList") ?: continue
            for (k in 0 until tp.length()) {
                val t = tp.optJSONObject(k) ?: continue
                val day = t.optInt("classDay", 0).takeIf { it in 1..7 } ?: continue
                val s = t.optInt("classSessions", 0); val n = t.optInt("continuingSession", 1).coerceAtLeast(1)
                if (s <= 0) continue
                val bitsStr = t.optString("classWeek")
                val w = if (bitsStr.matches(Regex("[01]{5,}"))) bits(bitsStr, 1) else weeks(t.optString("weekDescription"))
                val room = (t.optString("teachingBuildingName") + t.optString("classroomName")).trim()
                out += JwCourse(name, day, s, s + n - 1, w, teacher, room)
            }
        }
        return out
    }

    // ---------------- 树维 eams ----------------

    /** 页面源码里的 new TaskActivity(...) + index = 星期*unitCount+节 。 */
    fun eams(src: String): List<JwCourse> {
        val out = ArrayList<JwCourse>()
        val actRe = Regex("new\\s+TaskActivity\\((.*?)\\)\\s*;", RegexOption.DOT_MATCHES_ALL)
        val idxRe = Regex("index\\s*=\\s*(\\d+)\\s*\\*\\s*unitCount\\s*\\+\\s*(\\d+)")
        val acts = actRe.findAll(src).toList()
        for ((n, m) in acts.withIndex()) {
            val args = jsArgs(m.groupValues[1])
            if (args.size < 7) continue
            val endPos = if (n + 1 < acts.size) acts[n + 1].range.first else src.length
            val tail = src.substring(m.range.last, endPos)
            val idx = idxRe.findAll(tail).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
            if (idx.isEmpty()) continue
            var teacher = unq(args[1])
            if (!isQuoted(args[1])) {
                // 新版：先 var teachers = [{name:"张三"}]，再 actTeacherName.join(',')
                val before = src.substring(0, m.range.first)
                val tBlock = Regex("var\\s+teachers\\s*=\\s*\\[(.*?)];", RegexOption.DOT_MATCHES_ALL).findAll(before).lastOrNull()?.groupValues?.get(1) ?: ""
                teacher = Regex("name\\s*:\\s*[\"']([^\"']*)[\"']").findAll(tBlock).map { it.groupValues[1] }.joinToString(",")
            }
            val name = unq(args[3]).replace(Regex("\\([^()]*\\)$"), "").trim()
            val room = unq(args[5])
            if (room == "停课") continue   // 停课记录不是一节课（电子科大等会把停课单独列成一条）
            val w = bits(unq(args[6]), 0)
            for ((day0, list) in idx.groupBy { it.first }) {
                val units = list.map { it.second + 1 }.sorted()
                // 连续的节合成一段
                var a = units.first(); var prev = a
                for (u in units.drop(1) + Int.MAX_VALUE) {
                    if (u == prev + 1) { prev = u; continue }
                    out += JwCourse(name, day0 + 1, a, prev, w, teacher, room)
                    a = u; prev = u
                }
            }
        }
        return out.filter { it.day in 1..7 && it.name.isNotEmpty() }
    }
    private fun isQuoted(s: String) = s.trim().let { it.length >= 2 && (it[0] == '"' || it[0] == '\'') }
    private fun unq(s: String) = s.trim().let { if (isQuoted(it)) it.substring(1, it.length - 1) else it }
    /** 把 JS 调用的参数按逗号切开（引号里的逗号不算）。 */
    fun jsArgs(s: String): List<String> {
        val out = ArrayList<String>(); val b = StringBuilder(); var q: Char? = null; var depth = 0
        for (ch in s) {
            when {
                q != null -> { b.append(ch); if (ch == q) q = null }
                ch == '"' || ch == '\'' -> { q = ch; b.append(ch) }
                ch == '(' || ch == '[' -> { depth++; b.append(ch) }
                ch == ')' || ch == ']' -> { depth--; b.append(ch) }
                ch == ',' && depth == 0 -> { out += b.toString().trim(); b.clear() }
                else -> b.append(ch)
            }
        }
        out += b.toString().trim()
        return out
    }

    // ---------------- 通用表格 ----------------

    /**
     * 找页面里最像课表的表格：表头有星期，左边有节次。处理 rowspan/colspan。
     * 格子里按行拆：第一行课名，带「周」的是周次，带 @ 或像教室编号的是教室，剩下的短名字当老师。
     */
    fun table(html: String): List<JwCourse> {
        val doc = Jsoup.parse(html)
        var best: List<JwCourse> = emptyList()
        for (t in doc.select("table")) {
            if (t.select("table").size > 1) continue   // 只看最里层
            val r = try { parseTable(t) } catch (x: Exception) { emptyList() }
            if (r.size > best.size) best = r
        }
        return best
    }

    private fun parseTable(t: Element): List<JwCourse> {
        // 展开成网格
        val grid = ArrayList<MutableList<Element?>>()
        val rows = t.select("tr").toList().filter { it.closest("table") == t }
        for ((ri, tr) in rows.withIndex()) {
            while (grid.size <= ri) grid += ArrayList<Element?>()
            var ci = 0
            for (cell in tr.children().toList().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                while (ci < grid[ri].size && grid[ri][ci] != null) ci++
                val rs = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 30) ?: 1
                val cs = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 10) ?: 1
                for (dr in 0 until rs) for (dc in 0 until cs) {
                    val rr = ri + dr
                    while (grid.size <= rr) grid += ArrayList<Element?>()
                    while (grid[rr].size <= ci + dc) grid[rr].add(null)
                    grid[rr][ci + dc] = cell
                }
                ci += cs
            }
        }
        // 表头行：至少 5 个格子认得出星期
        val hr = grid.indexOfFirst { row -> row.count { it != null && Ocr.dayOf(it.text().take(6)) != null } >= 5 }
        if (hr < 0) return emptyList()
        val colDay = HashMap<Int, Int>()
        grid[hr].forEachIndexed { i, c -> if (c != null) Ocr.dayOf(c.text().take(6))?.let { if (it !in colDay.values) colDay[i] = it } }
        val firstDayCol = colDay.keys.minOrNull() ?: return emptyList()
        // 每一行是第几节
        val rowSec = HashMap<Int, Int>()
        var auto = 0
        for (ri in hr + 1 until grid.size) {
            val labels = grid[ri].take(firstDayCol).filterNotNull().map { it.text().trim() }
            val n = labels.reversed().firstNotNullOfOrNull { secNum(it) }
            auto = if (n != null) n else auto + 1
            rowSec[ri] = auto
        }
        val seen = HashSet<Element>()
        val out = ArrayList<JwCourse>()
        for (ri in hr + 1 until grid.size) for ((ci, day) in colDay) {
            val cell = grid[ri].getOrNull(ci) ?: continue
            if (!seen.add(cell)) continue
            val ls = lines(cell)
            if (ls.isEmpty()) continue
            // 这个格子跨了哪些行
            val spanRows = (ri until grid.size).filter { grid[it].getOrNull(ci) === cell }
            val a = rowSec[spanRows.first()] ?: continue
            var b = rowSec[spanRows.last()] ?: a
            // 一行代表一大节（2 节）时，行号是 1,2,3… 但节次标签可能写「1-2」
            val label = grid[spanRows.last()].take(firstDayCol).filterNotNull().joinToString(" ") { it.text() }
            Regex("(\\d+)\\s*[-~～—]\\s*(\\d+)").find(label)?.let { b = maxOf(b, it.groupValues[2].toInt()) }
            out += cellCourses(ls, day, a, b)
        }
        return out
    }

    private fun secNum(s: String): Int? {
        if (Regex("^\\d{1,2}\\s*[:：]\\d").containsMatchIn(s)) return null
        Regex("^第?\\s*(\\d{1,2})").find(s)?.let { return it.groupValues[1].toInt() }
        Regex("^第([一二三四五六七八九十]+)[节大]").find(s)?.let { return cnNum(it.groupValues[1]) }
        return null
    }
    private fun cnNum(s: String): Int? {
        val d = "一二三四五六七八九"
        if (s == "十") return 10
        if (s.length == 1) return d.indexOf(s[0]).takeIf { it >= 0 }?.plus(1)
        if (s.startsWith("十")) return 10 + (d.indexOf(s[1]) + 1)
        return null
    }

    /** 一个格子里的文字 → 课（可能好几门）。 */
    fun cellCourses(ls0: List<String>, day: Int, a: Int, b: Int): List<JwCourse> {
        val ls = ls0.filter { it.isNotBlank() && it != "\u00a0" }
        if (ls.isEmpty()) return emptyList()
        // 旧正方格式
        val zf = zfOld("<table><tr><td>" + ls.joinToString("<br>") { esc(it) } + "</td></tr></table>")
        if (zf.isNotEmpty()) return zf
        val groups = ArrayList<MutableList<String>>()
        var cur = ArrayList<String>()
        for (l in ls) {
            if (Regex("^-{3,}|^={3,}").containsMatchIn(l)) { if (cur.isNotEmpty()) groups += cur; cur = ArrayList(); continue }
            cur.add(l)
        }
        if (cur.isNotEmpty()) groups += cur
        val out = ArrayList<JwCourse>()
        for (g in groups) {
            val name = g.first().removePrefix("◆").trim()
            if (name.length > 40 || Regex("^[\\d\\s:：\\-]+$").matches(name)) continue
            var w: Set<Int> = emptySet(); var room = ""; var teacher = ""; var s = a; var e = b
            for (l in g.drop(1)) {
                when {
                    Regex("\\d.*(周|\\(周\\))").containsMatchIn(l) -> {
                        w = weeks(l.substringBefore('['))
                        Regex("\\[([\\d\\-]+)节]|第([\\d,\\-]+)节").find(l)?.let { m -> sections(m.value)?.let { s = it.first; e = it.second } }
                    }
                    l.startsWith("@") -> room = l.removePrefix("@").trim()
                    room.isEmpty() && Regex("[A-Za-z]?\\d{2,}|楼|室|馆|教|校区").containsMatchIn(l) -> room = l
                    teacher.isEmpty() && l.length <= 12 -> teacher = l
                }
            }
            out += JwCourse(name, day, s, e, w, teacher, room)
        }
        return out
    }
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** 元素里的文字按 <br>、块级元素分行。 */
    fun lines(e: Element): List<String> {
        val out = ArrayList<String>(); val b = StringBuilder()
        fun br() { val t = b.toString().replace('\u00a0', ' ').trim(); if (t.isNotEmpty()) out += t; b.clear() }
        fun walk(n: Node) {
            when (n) {
                is TextNode -> b.append(n.text())
                is Element -> {
                    if (n.tagName() == "br") { br(); return }
                    // <hr> 隔开同一格里的几门课（南软等，baoozak/timetable）：记成分隔行，cellCourses 按它拆
                    if (n.tagName() == "hr") { br(); out += "-----"; return }
                    if (n.attr("style").replace(" ", "").contains("display:none")) return
                    val block = n.isBlock || n.tagName() in setOf("p", "div", "li", "tr")
                    if (block) br()
                    n.childNodes().forEach { walk(it) }
                    if (block) br()
                }
            }
        }
        e.childNodes().forEach { walk(it) }
        br()
        return out
    }

    // ---------------- 汇总 ----------------

    /**
     * 按类型解析网页抓回来的东西。payload：{"kind":..,"json":..,"html":[..]}。
     * 专用解析没结果就退回通用表格。
     */
    fun parse(kind: String, payload: String): List<JwCourse> {
        val p = try { JSONObject(payload) } catch (x: Exception) { JSONObject().put("html", JSONArray().put(payload)) }
        val htmls = ArrayList<String>()
        p.optJSONArray("html")?.let { a -> for (i in 0 until a.length()) htmls += a.optString(i) }
        val json = p.optString("json", "")
        fun tryJ(f: (String) -> List<JwCourse>) = if (json.isBlank()) emptyList() else try { f(json) } catch (x: Exception) { emptyList() }
        fun tryH(f: (String) -> List<JwCourse>) = htmls.map { h -> try { f(h) } catch (x: Exception) { emptyList() } }.maxByOrNull { it.size } ?: emptyList()
        // 金智（东大）这条路和以前完全一样，不动
        if (kind == "wisedu" || kind == "neu")
            return clean(tryJ(::wisedu).ifEmpty { tryH(::qz).ifEmpty { tryH(::eams) }.ifEmpty { tryH(::table) } })
        val r = when (kind) {
            "zf" -> tryJ(::zfJson).ifEmpty { tryH(JwA::zfHtml) }.ifEmpty { tryH(::zfOld) }
            "qz" -> tryH(::qz)
            "urpnew" -> tryJ(::urpNew).ifEmpty { tryH(JwA::urpNewHtml) }
            "urp" -> tryH(JwA::urpList).ifEmpty { tryH(JwA::urpNewHtml) }
            "eams" -> tryH(::eams)
            "kingo" -> tryH(JwA::kingo)
            "cx" -> tryJ(JwA::cx)
            "nr" -> tryH(JwA::nr)
            else -> emptyList()
        }
        return clean(r.ifEmpty {
            tryH(::qz).ifEmpty { tryH(::eams) }.ifEmpty { tryH(JwA::zfHtml) }.ifEmpty { tryH(JwA::urpNewHtml) }
                .ifEmpty { tryH(JwA::urpList) }.ifEmpty { tryH(JwA::kingo) }.ifEmpty { tryH(::table) }
        })
    }

    /** 去重；同一门课同一时间同一教室老师的多条合并周次。 */
    fun clean(l: List<JwCourse>): List<JwCourse> =
        l.filter { it.name.isNotBlank() && it.day in 1..7 && it.start >= 1 && it.end >= it.start }
            .groupBy { listOf(it.name, it.day, it.start, it.end, it.teacher, it.room) }
            .map { (_, g) -> if (g.any { it.weeks.isEmpty() }) g.first().copy(weeks = emptySet()) else g.first().copy(weeks = g.flatMap { it.weeks }.toSet()) }

    /** 课程 → 课表里的课（按每节课时间换算）。节次超出现有节数时，按最后一节往后推。 */
    fun toSlots(l: List<JwCourse>, starts: List<Int>, ends: List<Int>, total: Int = 30): List<Slot> {
        val base = System.currentTimeMillis()
        return l.mapIndexed { i, c ->
            fun st(n: Int) = starts.getOrNull(n - 1) ?: ((ends.lastOrNull() ?: 1200) + 10 + (n - starts.size - 1) * 55)
            fun en(n: Int) = ends.getOrNull(n - 1) ?: (st(n) + 45)
            val from = st(c.start).coerceIn(0, 1438); val to = en(c.end).coerceIn(from + 1, 1439)
            val w = c.weeks.filter { it in 1..total }.toSet()
            Slot("j${base}_$i", c.name.take(30), setOf(c.day), from, to, 1, room = c.room.take(30), teacher = c.teacher.take(30),
                weeks = if (w.size == total) emptySet() else w)
        }
    }

    // ---------------- CSV 表格（课程名称,星期,开始节数,结束节数,老师,地点,周数）----------------

    /** 课程名称,星期,开始节数,结束节数,老师,地点,周数 。第一行是表头就跳过。 */
    fun csv(text: String): List<JwCourse> {
        val rows = csvRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return emptyList()
        val head = rows.first().map { it.trim() }
        fun col(vararg names: String, d: Int) = head.indexOfFirst { h -> names.any { h.contains(it) } }.takeIf { it >= 0 } ?: d
        val hasHead = head.any { it.contains("课程") || it.contains("星期") || it.contains("节") }
        val iName = if (hasHead) col("课程", "名称", d = 0) else 0
        val iDay = if (hasHead) col("星期", d = 1) else 1
        val iA = if (hasHead) col("开始", d = 2) else 2
        val iB = if (hasHead) col("结束", d = 3) else 3
        val iT = if (hasHead) col("老师", "教师", d = 4) else 4
        val iR = if (hasHead) col("地点", "教室", d = 5) else 5
        val iW = if (hasHead) col("周数", "周次", d = 6) else 6
        val out = ArrayList<JwCourse>()
        for (r in if (hasHead) rows.drop(1) else rows) {
            val name = r.getOrNull(iName)?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: continue
            val ds = r.getOrNull(iDay)?.trim().orEmpty()
            val day = ds.toIntOrNull()?.takeIf { it in 1..7 } ?: Ocr.dayOf(ds) ?: continue
            val a = r.getOrNull(iA)?.trim()?.toIntOrNull() ?: continue
            val b = r.getOrNull(iB)?.trim()?.toIntOrNull() ?: a
            out += JwCourse(name, day, a, maxOf(a, b), weeks(r.getOrNull(iW).orEmpty()), r.getOrNull(iT)?.trim().orEmpty(), r.getOrNull(iR)?.trim().orEmpty())
        }
        return out
    }

    fun csvRows(t: String): List<List<String>> {
        val sep = if (t.lineSequence().firstOrNull()?.let { it.count { c -> c == '\t' } > it.count { c -> c == ',' } } == true) '\t' else ','
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val f = StringBuilder(); var q = false; var i = 0
        while (i < t.length) {
            val ch = t[i]
            if (q) { if (ch == '"') { if (i + 1 < t.length && t[i + 1] == '"') { f.append('"'); i++ } else q = false } else f.append(ch) }
            else when (ch) {
                '"' -> q = true
                sep -> { row.add(f.toString()); f.clear() }
                '\r' -> {}
                '\n' -> { row.add(f.toString()); f.clear(); if (row.any { it.isNotBlank() }) rows += row; row = ArrayList() }
                else -> f.append(ch)
            }
            i++
        }
        row.add(f.toString()); if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}
