package com.zhishi.timetable

import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * 美国常见的选课系统导出的课程清单（Workday「View My Courses → Export to Excel」，
 * Banner / PeopleSoft 的课表列表另存的 CSV）：一行一门课，「上课时间」写成
 * 「MWF | 9:00 AM - 9:50 AM | ENG 101」或者分成 Days / Time / Location 几列。
 */
object SisSheet {
    /** .xlsx 里第一张表的所有格子（按行）；不是 xlsx 返回 null。 */
    fun xlsx(bytes: ByteArray): List<List<String>>? {
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return null
        val files = HashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (n == "xl/sharedStrings.xml" || (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml")) || n == "xl/workbook.xml")
                    files[n] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { x ->
            Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(x).map { si ->
                Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { xml(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()
        val sheet = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minByOrNull { Regex("\\d+").find(it.substringAfterLast('/'))?.value?.toIntOrNull() ?: 99 }
            ?.let { files[it] } ?: return emptyList()
        val rows = ArrayList<List<String>>()
        for (row in Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet)) {
            val cells = ArrayList<String>()
            for (cm in Regex("<c ([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1])) {
                val attrs = cm.groupValues[1]; val inner = cm.groupValues[2]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1)
                val col = ref?.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) }?.minus(1) ?: cells.size
                val type = Regex("t=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
                val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1) ?: ""
                val text = when (type) {
                    "s" -> shared.getOrNull(v.toIntOrNull() ?: -1) ?: ""
                    "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(inner).joinToString("") { xml(it.groupValues[1]) }
                    else -> xml(v)
                }
                while (cells.size < col) cells += ""
                if (col == cells.size) cells += text else if (col < cells.size) cells[col] = text
            }
            rows += cells
        }
        return rows
    }

    private fun xml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#10;", "\n").replace("&amp;", "&")

    /** 简单的 CSV（支持引号里的逗号和换行）。 */
    fun csv(text: String): List<List<String>> {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() && (',' in it || '\t' in it) } ?: ""
        val sep = if (first.count { it == '\t' } > first.count { it == ',' }) '\t' else ','
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val cell = StringBuilder(); var q = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (q) { if (ch == '"') { if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else q = false } else cell.append(ch) }
            else when (ch) {
                '"' -> q = true
                sep -> { row += cell.toString(); cell.clear() }
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = ArrayList() }
                '\r' -> {}
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    /** 「MWF」「TTh」「TR」「Mon/Wed」「Tu Th」「M W F」→ 1–7。 */
    fun days(s0: String): Set<Int> {
        val s = s0.trim()
        if (s.isEmpty()) return emptySet()
        val words = Regex("[A-Za-z]+").findAll(s).map { it.value.lowercase() }.toList()
        val full = mapOf("mon" to 1, "tue" to 2, "tues" to 2, "wed" to 3, "thu" to 4, "thur" to 4, "thurs" to 4, "fri" to 5, "sat" to 6, "sun" to 7,
            "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4, "friday" to 5, "saturday" to 6, "sunday" to 7, "tu" to 2, "th" to 4, "sa" to 6, "su" to 7)
        if (words.isNotEmpty() && words.all { it in full }) return words.map { full[it]!! }.toSet()
        // 连写的字母：M T W R(=Th) F S U，Th / Tu / Sa / Su 两个字母的先认
        val out = HashSet<Int>()
        val t = s.replace(Regex("[^A-Za-z]"), "")
        var i = 0
        while (i < t.length) {
            val two = if (i + 1 < t.length) t.substring(i, i + 2).lowercase() else ""
            when {
                two == "th" -> { out += 4; i += 2; continue }
                two == "tu" -> { out += 2; i += 2; continue }
                two == "sa" -> { out += 6; i += 2; continue }
                two == "su" -> { out += 7; i += 2; continue }
            }
            when (t[i].uppercaseChar()) { 'M' -> out += 1; 'T' -> out += 2; 'W' -> out += 3; 'R' -> out += 4; 'F' -> out += 5; 'S' -> out += 6; 'U' -> out += 7; else -> return emptySet() }
            i++
        }
        return out
    }

    private val DATE = Regex("(\\d{1,4})[/.-](\\d{1,2})[/.-](\\d{1,4})")
    /** 08/26/2026、2026-08-26、26.08.2026 → epoch 天。 */
    fun date(s: String): Long? {
        val m = DATE.find(s) ?: return null
        val g = m.groupValues.drop(1).map { it.toInt() }
        val a = g[0]; val b = g[1]; val c = g[2]
        return try {
            when {
                a > 31 -> LocalDate.of(a, b, c)                  // 2026-08-26
                c > 31 && m.value.contains('.') -> LocalDate.of(c, b, a)   // 26.08.2026
                c > 31 && a > 12 -> LocalDate.of(c, b, a)        // 26/08/2026
                c > 31 -> LocalDate.of(c, a, b)                  // 08/26/2026（美国）
                else -> LocalDate.of(2000 + c, a, b)
            }.toEpochDay()
        } catch (x: Exception) { null }
    }

    data class Meet(val name: String, val days: Set<Int>, val from: Int, val to: Int, val room: String, val teacher: String, val start: Long?, val end: Long?)

    /** 一个「上课时间」格子里的一段：「08/26/2026 - 12/11/2026 | MWF | 9:00 AM - 9:50 AM | ENG 101」，各段顺序不固定。 */
    fun pattern(line: String): Triple<Set<Int>, Pair<Int, Int>, String>? {
        val time = Ocr.rangeOf(line) ?: return null
        val segs = line.split('|', ';').map { it.trim() }.filter { it.isNotEmpty() }
        var d = emptySet<Int>(); var room = ""
        for (s in segs) {
            if (Ocr.rangeOf(s) != null) {
                // 「MWF 9:00 AM - 9:50 AM」挤在一段里
                val pre = s.substring(0, s.indexOfFirst { it.isDigit() }.coerceAtLeast(0)).trim()
                if (d.isEmpty() && pre.isNotEmpty()) d = days(pre)
                continue
            }
            if (DATE.containsMatchIn(s)) continue
            val dd = days(s)
            if (d.isEmpty() && dd.isNotEmpty() && s.length <= 24) { d = dd; continue }
            if (room.isEmpty()) room = s
        }
        if (d.isEmpty()) return null
        return Triple(d, time, room.take(30))
    }

    private fun col(head: List<String>, vararg keys: String): Int = head.indexOfFirst { h -> val l = h.lowercase(); keys.any { it in l } }

    /** 表格 → 每段上课。认不出表头返回空。 */
    fun meets(rows: List<List<String>>): List<Meet> {
        val hi = rows.take(15).indexOfFirst { r -> r.any { val l = it.lowercase(); "meeting pattern" in l || l == "days" || "days" == l.trim() || "time" == l.trim() || "start time" in l } }
        if (hi < 0) return emptyList()
        val head = rows[hi]
        val cPat = col(head, "meeting pattern")
        val cDays = head.indexOfFirst { it.trim().lowercase() in setOf("days", "day", "meeting days") }
        val cTime = head.indexOfFirst { it.trim().lowercase() in setOf("time", "times", "meeting time", "meeting times") }
        val cStartT = col(head, "start time", "begin time"); val cEndT = col(head, "end time")
        val cName = listOf("course listing", "course title", "title", "course", "class", "subject").map { k -> head.indexOfFirst { it.trim().lowercase() == k } }.firstOrNull { it >= 0 }
            ?: col(head, "course listing", "course", "title", "class")
        val cSec = col(head, "section")
        val cFmt = col(head, "instructional format", "component", "type")
        val cRoom = col(head, "location", "room", "building")
        val cTeach = col(head, "instructor", "teacher", "faculty")
        val cStart = col(head, "start date", "begin date"); val cEnd = col(head, "end date")
        val out = ArrayList<Meet>()
        for (r in rows.drop(hi + 1)) {
            fun at(i: Int) = if (i >= 0) r.getOrNull(i)?.trim() ?: "" else ""
            var name = at(cName).ifEmpty { at(cSec) }
            if (name.isEmpty()) continue
            // Workday 的课名常是「CS 101 - Intro to Computer Science」，节次「CS 101-001 - Intro…」
            name = name.replace(Regex("\\s+"), " ").take(40)
            val fmt = at(cFmt)
            if (fmt.isNotEmpty() && !name.contains(fmt, true) && fmt.length <= 12) name = "$name ($fmt)"
            val teacher = at(cTeach).lineSequence().firstOrNull()?.trim()?.take(30) ?: ""
            val sd = date(at(cStart)); val ed = date(at(cEnd))
            if (cPat >= 0) {
                for (line in at(cPat).split('\n').map { it.trim() }.filter { it.isNotEmpty() }) {
                    val p = pattern(line) ?: continue
                    val ds = DATE.findAll(line).mapNotNull { date(it.value) }.toList()
                    out += Meet(name, p.first, p.second.first, p.second.second, p.third.ifEmpty { at(cRoom).lineSequence().firstOrNull()?.trim()?.take(30) ?: "" }, teacher,
                        ds.getOrNull(0) ?: sd, ds.getOrNull(1) ?: ed)
                }
            } else {
                val d = days(at(cDays)); if (d.isEmpty()) continue
                val t = Ocr.rangeOf(at(cTime)) ?: run {
                    val a = Ocr.timeOf(at(cStartT).replace(" ", "")); val b = Ocr.timeOf(at(cEndT).replace(" ", ""))
                    if (a != null && b != null && b > a) a to b else null
                } ?: continue
                out += Meet(name, d, t.first, t.second, at(cRoom).take(30), teacher, sd, ed)
            }
        }
        return out
    }

    /** 按开学那周（没设就用最早的开始日期）换算周次，生成课表。 */
    fun slots(ms: List<Meet>, term: Long, totalWeeks: Int): Ics.Result {
        if (ms.isEmpty()) return Ics.Result(emptyList(), 0, null, 0)
        val starts = ms.mapNotNull { it.start }
        val first0 = starts.minOrNull()?.let { Term.mondayOf(it) }
        val fit = term >= 0 && (first0 == null || (first0 >= term - 7 && first0 < term + totalWeeks * 7L))
        val first = if (fit || first0 == null) term else first0
        val out = ms.flatMap { m ->
            val weeks = if (first >= 0 && m.start != null && m.end != null && m.end >= m.start)
                ((Math.floorDiv(m.start - first, 7L) + 1).toInt()..(Math.floorDiv(m.end - first, 7L) + 1).toInt()).filter { it in 1..30 }.toSet() else emptySet()
            m.days.map { d -> Slot("w${("${m.name}|$d|${m.from}").hashCode()}", m.name, setOf(d), m.from, m.to, room = m.room, teacher = m.teacher, weeks = weeks) }
        }.distinctBy { "${it.name}|${it.days}|${it.from}" }
        return Ics.Result(out.sortedWith(compareBy({ it.days.first() }, { it.from })), 0, if (fit || first0 == null) null else first, out.maxOfOrNull { it.weeks.maxOrNull() ?: 0 } ?: 0)
    }
}
