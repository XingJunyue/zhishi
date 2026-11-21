package com.zhishi.timetable

import java.util.Calendar
import java.util.TimeZone

/**
 * 日历文件（.ics）和日历订阅链接的课表导入，主要给国外的学校用：
 * Canvas、Moodle、Brightspace、Blackboard 这类学习平台，和 Scientia、CMIS、Celcat、MyTimetable、
 * TUMonline、HISinOne、WebUntis 这类排课系统，都能给出一个日历订阅链接或 .ics 文件。
 *
 * 做的事：
 * - 展开重复规则（RRULE 每周 / 每天，EXDATE 去掉的日子，RECURRENCE-ID 挪过的单次）；
 * - 把一次次的课按「课名 + 星期 + 时间 + 教室」并回一门课，记下上课的周次；
 * - 作业截止、全天事件、取消的课不放进课表；
 * - 同一时间同一门课分几个教室（TUMonline 会这样）合成一条。
 */
object Ics {
    data class Ev(
        val uid: String, val name: String, val start: Long, val end: Long, val room: String, val teacher: String,
        val rrule: String, val exdates: Set<Long>, val recurId: Long, val allDay: Boolean, val cancelled: Boolean, val desc: String
    )

    /** 解析结果：课、截止事件数量、推断的第一周周一（epoch 天）。 */
    data class Result(val slots: List<Slot>, val deadlines: Int, val firstMonday: Long?, val maxWeek: Int)

    private fun unfold(raw: String): List<String> {
        val out = ArrayList<String>()
        for (l in raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] += l.substring(1) else out += l
        }
        return out
    }

    private fun unescape(v: String) = v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** Outlook / Exchange 的 Windows 时区名换成常见的 IANA 名。 */
    private val WIN = mapOf(
        "W. Europe Standard Time" to "Europe/Berlin", "Romance Standard Time" to "Europe/Paris", "GMT Standard Time" to "Europe/London",
        "Central Europe Standard Time" to "Europe/Budapest", "Eastern Standard Time" to "America/New_York", "Central Standard Time" to "America/Chicago",
        "Mountain Standard Time" to "America/Denver", "Pacific Standard Time" to "America/Los_Angeles", "AUS Eastern Standard Time" to "Australia/Sydney",
        "Tokyo Standard Time" to "Asia/Tokyo", "Korea Standard Time" to "Asia/Seoul", "China Standard Time" to "Asia/Shanghai",
        "Singapore Standard Time" to "Asia/Singapore", "New Zealand Standard Time" to "Pacific/Auckland", "E. Australia Standard Time" to "Australia/Brisbane"
    )

    private fun zoneOf(prop: String, floating: TimeZone): TimeZone {
        val id = Regex("TZID=\"?([^;:\"]+)\"?").find(prop)?.groupValues?.get(1)?.trim() ?: return floating
        val z = TimeZone.getTimeZone(WIN[id] ?: id)
        // 认不出的名字 Java 会给 GMT：这时按学校所在时区（浮动时间）算
        return if (z.id == "GMT" && !id.startsWith("GMT") && !id.startsWith("UTC") && id != "Etc/UTC") floating else z
    }

    /** 20261124T090000Z / 20261124T090000 / 20261124 → 毫秒；第二个值表示是不是只有日期。 */
    private fun time(prop: String, v0: String, floating: TimeZone): Pair<Long, Boolean>? {
        val v = v0.trim()
        if (v.length < 8) return null
        val y = v.substring(0, 4).toIntOrNull() ?: return null
        val mo = v.substring(4, 6).toIntOrNull() ?: return null
        val d = v.substring(6, 8).toIntOrNull() ?: return null
        val dateOnly = v.length == 8 || "VALUE=DATE" in prop && "DATE-TIME" !in prop
        val utc = v.endsWith("Z")
        val k = Calendar.getInstance(if (utc) TimeZone.getTimeZone("UTC") else zoneOf(prop, floating))
        k.clear()
        if (dateOnly) k.set(y, mo - 1, d) else {
            if (v.length < 13) return null
            k.set(y, mo - 1, d, v.substring(9, 11).toInt(), v.substring(11, 13).toInt(), if (v.length >= 15) v.substring(13, 15).toIntOrNull() ?: 0 else 0)
        }
        return k.timeInMillis to dateOnly
    }

    /** PT1H30M / P1D 这类时长 → 毫秒。 */
    private fun duration(v: String): Long {
        val m = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").find(v) ?: return 0
        val g = m.groupValues.drop(1).map { it.toLongOrNull() ?: 0L }
        return (((g[0] * 7 + g[1]) * 24 + g[2]) * 60 + g[3]) * 60_000L + g[4] * 1000L
    }

    private val TEACH = Regex("(?im)^\\s*(?:lecturers?|instructors?|teachers?|staff|tutors?|professors?|dozent(?:in|en)?|lehrende|lehrperson|vortragende|docent(?:en)?|enseignant(?:e|s)?|profesor(?:a|es)?|講師|担当(?:教員)?|교수|담당교수)\\s*[:：]\\s*(.+)$")

    fun events(raw: String, floating: TimeZone = TimeZone.getDefault()): List<Ev> {
        if (!raw.contains("BEGIN:VCALENDAR", ignoreCase = true) && !raw.contains("BEGIN:VEVENT", ignoreCase = true)) return emptyList()
        val out = ArrayList<Ev>()
        var inEv = false; var depth = 0
        var uid = ""; var name = ""; var st: Pair<Long, Boolean>? = null; var en: Long = 0; var dur = 0L
        var room = ""; var teacher = ""; var rrule = ""; val ex = HashSet<Long>(); var rid = 0L; var cancel = false; var desc = ""
        for (l in unfold(raw)) {
            val line = l.trimEnd()
            if (line.equals("BEGIN:VEVENT", true)) {
                inEv = true; depth = 0; uid = ""; name = ""; st = null; en = 0; dur = 0; room = ""; teacher = ""; rrule = ""; ex.clear(); rid = 0; cancel = false; desc = ""
                continue
            }
            if (!inEv) continue
            if (line.startsWith("BEGIN:", true)) { depth++; continue }     // VALARM 之类
            if (line.startsWith("END:", true) && !line.equals("END:VEVENT", true)) { depth--; continue }
            if (depth > 0) continue
            if (line.equals("END:VEVENT", true)) {
                inEv = false
                val s = st ?: continue
                val e = if (en > 0) en else if (dur > 0) s.first + dur else s.first
                out += Ev(uid, name, s.first, e, room, teacher, rrule, ex.toSet(), rid, s.second, cancel, desc)
                continue
            }
            val i = line.indexOf(':'); if (i < 0) continue
            val prop = line.substring(0, i); val key = prop.substringBefore(';').uppercase(); val v = line.substring(i + 1)
            when (key) {
                "UID" -> uid = v.trim()
                "SUMMARY" -> name = unescape(v).replace('\n', ' ').trim()
                "DTSTART" -> st = time(prop, v, floating)
                "DTEND" -> en = time(prop, v, floating)?.first ?: 0
                "DURATION" -> dur = duration(v)
                "LOCATION" -> room = unescape(v).replace('\n', ' ').trim()
                "DESCRIPTION" -> { desc = unescape(v); TEACH.find(desc)?.let { teacher = it.groupValues[1].trim() } }
                "ORGANIZER" -> if (teacher.isEmpty()) Regex("CN=\"?([^;:\"]+)").find(prop)?.let { teacher = it.groupValues[1].trim() }
                "RRULE" -> rrule = v.trim()
                "EXDATE" -> v.split(',').forEach { t -> time(prop, t, floating)?.let { ex += it.first } }
                "RECURRENCE-ID" -> rid = time(prop, v, floating)?.first ?: 0
                "STATUS" -> cancel = v.trim().equals("CANCELLED", true)
            }
        }
        return out
    }

    private val BYDAY = mapOf("MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY, "WE" to Calendar.WEDNESDAY, "TH" to Calendar.THURSDAY,
        "FR" to Calendar.FRIDAY, "SA" to Calendar.SATURDAY, "SU" to Calendar.SUNDAY)

    /** 把一条事件展开成每一次的开始时间（只支持课表常见的 WEEKLY / DAILY，最多一年）。 */
    fun expand(e: Ev, tz: TimeZone): List<Long> {
        if (e.rrule.isEmpty()) return listOf(e.start)
        val p = e.rrule.split(';').mapNotNull { val kv = it.split('='); if (kv.size == 2) kv[0].uppercase() to kv[1] else null }.toMap()
        val freq = p["FREQ"]?.uppercase() ?: return listOf(e.start)
        val interval = (p["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val count = p["COUNT"]?.toIntOrNull()
        val until = p["UNTIL"]?.let { time(if (it.endsWith("Z")) "" else "VALUE=DATE-TIME", it, tz)?.let { (ms, dateOnly) -> if (dateOnly) ms + 86_400_000L - 1 else ms } }
        val limit = minOf(until ?: Long.MAX_VALUE, e.start + 366L * 86_400_000L)
        val base = Calendar.getInstance(tz).apply { timeInMillis = e.start }
        val days = p["BYDAY"]?.split(',')?.mapNotNull { BYDAY[it.takeLast(2).uppercase()] }?.toSet()
        val out = ArrayList<Long>()
        if (freq == "WEEKLY") {
            val wd = days ?: setOf(base.get(Calendar.DAY_OF_WEEK))
            // 从开始那周的周一往后，一周周地走
            // 从开始那周的周一往后，一周周地走（不用 Calendar 的周字段，避免受地区「一周从周几开始」影响）
            val back = (base.get(Calendar.DAY_OF_WEEK) + 5) % 7      // 周一 = 0
            val wk = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
            val order = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
            var n = 0
            loop@ while (n < 60) {
                for ((off, d) in order.withIndex()) {
                    if (d !in wd) continue
                    val k = (wk.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) }
                    if (k.timeInMillis < e.start) continue
                    if (k.timeInMillis > limit) break@loop
                    out += k.timeInMillis
                    if (count != null && out.size >= count) break@loop
                }
                wk.add(Calendar.DAY_OF_YEAR, 7 * interval); n++
            }
        } else if (freq == "DAILY") {
            val k = base.clone() as Calendar
            var n = 0
            while (k.timeInMillis <= limit && n < 400) {
                if (days == null || k.get(Calendar.DAY_OF_WEEK) in days) { out += k.timeInMillis; if (count != null && out.size >= count) break }
                k.add(Calendar.DAY_OF_YEAR, interval); n++
            }
        } else return listOf(e.start)
        return out.filter { it !in e.exdates }
    }

    /** 课名里多余的东西：Canvas 末尾的「[CS 101-001]」留着当课程代码，挪到前面去；取消标记去掉。 */
    fun cleanName(s: String): String {
        var t = s.trim()
        Regex("\\s*\\[([^\\]]{2,30})]\\s*$").find(t)?.let { m ->
            val code = m.groupValues[1].trim(); val rest = t.substring(0, m.range.first).trim()
            t = if (rest.isEmpty()) code else if (rest.contains(code.substringBefore('-').trim(), true)) rest else "$code $rest"
        }
        t = t.replace(Regex("(?i)^\\s*(cancel+ed|abgesagt|entfällt|annulé)\\s*[:：-]\\s*"), "")
        return t.replace(Regex("\\s+"), " ").take(40)
    }

    private fun isCancelledName(s: String) = Regex("(?i)\\b(cancel+ed|abgesagt|entfällt|annulé|cancelado)\\b").containsMatchIn(s)

    /**
     * 解析整个日历。term = 已设的开学那周周一（epoch 天，-1 表示没设）：
     * 课程的日子落在这学期里就按它算周次，否则按最早那节课所在的周当第 1 周。
     */
    fun parse(raw: String, term: Long = -1, totalWeeks: Int = 20, tz: TimeZone = TimeZone.getDefault()): Result {
        val evs = events(raw, tz)
        // 挪过的单次：从原来的系列里去掉那一次
        val moved = evs.filter { it.recurId > 0 }.groupBy { it.uid }.mapValues { e -> e.value.map { it.recurId }.toSet() }
        var deadlines = 0
        data class Occ(val name: String, val day: Long, val dow: Int, val from: Int, val to: Int, val room: String, val teacher: String)
        val occ = ArrayList<Occ>()
        val seen = HashSet<String>()
        for (e in evs) {
            if (e.cancelled || isCancelledName(e.name)) continue
            val len = e.end - e.start
            // 截止日期（作业、测验）：只有一个时刻、全天，或者 Canvas 的作业
            if (e.allDay || len < 15 * 60_000L || e.uid.startsWith("event-assignment")) { if (e.name.isNotBlank()) deadlines++; continue }
            if (len > 6 * 3600_000L || e.name.isBlank()) continue
            val starts = if (e.recurId > 0) listOf(e.start) else expand(e, tz).filter { it !in (moved[e.uid] ?: emptySet()) }
            for (s in starts) {
                val k = Calendar.getInstance(tz).apply { timeInMillis = s }
                val ke = Calendar.getInstance(tz).apply { timeInMillis = s + len }
                val from = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE)
                var to = ke.get(Calendar.HOUR_OF_DAY) * 60 + ke.get(Calendar.MINUTE)
                if (to <= from) to = minOf(from + (len / 60_000L).toInt(), 24 * 60 - 1)
                val day = Math.floorDiv(s + tz.getOffset(s), 86_400_000L)
                val dow = k.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }
                val name = cleanName(e.name)
                // 同一门课同一时刻在几个教室：只留一条，教室合起来
                val dk = "$name|$day|$from"
                if (!seen.add(dk)) {
                    val i = occ.indexOfFirst { it.name == name && it.day == day && it.from == from }
                    if (i >= 0 && e.room.isNotBlank() && e.room !in occ[i].room) occ[i] = occ[i].copy(room = (occ[i].room + " / " + e.room).take(40))
                    continue
                }
                occ += Occ(name, day, dow, from, to, e.room, e.teacher)
            }
        }
        if (occ.isEmpty()) return Result(emptyList(), deadlines, null, 0)
        // 第 1 周：已设的学期能装下大部分课就用它，否则用最早那节课的那周
        val minMon = Term.mondayOf(occ.minOf { it.day })
        val fit = term >= 0 && occ.count { val w = ((it.day - term) / 7 + 1).toInt(); it.day >= term && w <= maxOf(totalWeeks, 30) } * 2 >= occ.size
        val first = if (fit) term else minMon
        fun week(d: Long) = ((d - first) / 7 + 1).toInt()
        val groups = occ.filter { it.day >= first }.groupBy { "${it.name}|${it.dow}|${it.from}|${it.to}" }
        val slots = groups.values.map { g ->
            val o = g.first()
            val room = g.groupingBy { it.room }.eachCount().maxByOrNull { it.value }?.key ?: ""
            val teacher = g.firstOrNull { it.teacher.isNotBlank() }?.teacher ?: ""
            val weeks = g.map { week(it.day) }.filter { it in 1..30 }.toSet()
            Slot("i${("${o.name}|${o.dow}|${o.from}").hashCode()}", o.name, setOf(o.dow), o.from, o.to,
                room = room.take(30), teacher = teacher.take(30), weeks = weeks)
        }.filter { it.weeks.isNotEmpty() }
        // 只出现过一次、又和每周的课撞名撞点的（调课、补课）也保留：周次会说明是哪一周
        return Result(slots.sortedWith(compareBy({ it.days.first() }, { it.from })), deadlines, if (fit) null else first, slots.maxOfOrNull { it.weeks.maxOrNull() ?: 0 } ?: 0)
    }

    /** 订阅链接：webcal:// 换成 https://，去掉两头的空白和引号。 */
    fun normalizeUrl(u0: String): String {
        var u = u0.trim().trim('"', '\'', '<', '>')
        if (u.startsWith("webcal://", true)) u = "https://" + u.substring(9)
        if (u.startsWith("webcals://", true)) u = "https://" + u.substring(10)
        if (!u.contains("://")) u = "https://$u"
        return u
    }

    /** 从链接认出是哪个系统（只用来给提示）。 */
    fun sourceOf(u: String): String? {
        val l = u.lowercase()
        return when {
            "/feeds/calendars/user_" in l -> "Canvas"
            "export_execute.php" in l -> "Moodle"
            "feedou=" in l || "/d2l/" in l -> "Brightspace"
            "blackboard" in l || "/webapps/calendar" in l -> "Blackboard"
            "ical" in l && ("tumonline" in l || "campusonline" in l || "co/" in l && "ptoken" in l) -> "TUMonline / CAMPUSonline"
            "hisinone" in l || "qisserver" in l -> "HISinOne"
            "calendar.php?client_id=" in l || "ilias" in l -> "ILIAS"
            "stud.ip" in l || "studip" in l -> "Stud.IP"
            "webuntis" in l || "untis" in l -> "WebUntis"
            "mytimetable" in l -> "MyTimetable"
            "celcat" in l -> "Celcat"
            "cmis" in l -> "CMIS"
            "scientia" in l || "syllabus" in l || "publish" in l && "timetable" in l -> "Syllabus+"
            "outlook" in l || "office365" in l -> "Outlook"
            "calendar.google.com" in l -> "Google Calendar"
            else -> null
        }
    }

    /** 下载订阅链接的内容；不是日历（多半是登录页、链接过期）就抛出原因。 */
    fun fetch(url: String): String {
        var u = normalizeUrl(url)
        repeat(5) {
            val con = java.net.URL(u).openConnection() as java.net.HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 12_000; con.readTimeout = 20_000
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Zhishi")
            con.setRequestProperty("Accept", "text/calendar, text/plain, */*")
            val rc = con.responseCode
            if (rc in 300..399) { u = java.net.URL(java.net.URL(u), con.getHeaderField("Location") ?: throw Exception("redirect")).toString(); return@repeat }
            if (rc == 401 || rc == 403 || rc == 404 || rc == 410) throw LinkDead(rc)
            if (rc != 200) throw Exception("HTTP $rc")
            val bytes = con.inputStream.use { it.readBytes() }
            val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.ISO_8859_1)
            val cs = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(con.contentType ?: "")?.groupValues?.get(1)
            val text = try { String(bytes, if (cs != null) charset(cs) else Charsets.UTF_8) } catch (x: Exception) { String(bytes, Charsets.UTF_8) }
            if (!head.contains("BEGIN:VCALENDAR", true)) throw NotCalendar()
            return text
        }
        throw Exception("too many redirects")
    }

    class LinkDead(val code: Int) : Exception("HTTP $code")
    class NotCalendar : Exception("not a calendar")
}
