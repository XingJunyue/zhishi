package com.zhishi.timetable

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 课表里的一节课。
 * level：课程标签，0 水课、1 普通、2 重要——只是显示用的标签（课块和小部件上标出来），不做任何限制。
 * ssid / kind 只为兼容旧备份保留，kind 恒为 "course"。
 */
data class Slot(
    val id: String, val name: String, val days: Set<Int>, val from: Int, val to: Int,
    val level: Int = 1, val ssid: String = "", val kind: String = "course",
    /** 教室、老师；weeks 为空 = 每周都上；color -1 = 按课名自动配色。 */
    val room: String = "", val teacher: String = "", val weeks: Set<Int> = emptySet(), val color: Int = -1,
    val note: String = "", val credit: String = ""
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("n", name).put("d", JSONArray(days.toList())).put("f", from).put("t", to)
        .put("l", level).put("s", ssid).put("k", kind).put("r", room).put("te", teacher).put("w", JSONArray(weeks.sorted())).put("co", color)
        .put("no", note).put("cr", credit)

    /** 这周上不上（week 为 null 表示没设开学日期，按每周都上算）。 */
    fun inWeek(week: Int?) = week == null || weeks.isEmpty() || week in weeks

    companion object {
        fun of(j: JSONObject): Slot {
            val d = j.optJSONArray("d") ?: JSONArray()
            val w = j.optJSONArray("w") ?: JSONArray()
            return Slot(j.optString("id"), j.optString("n"), (0 until d.length()).map { d.getInt(it) }.toSet(),
                j.optInt("f"), j.optInt("t"), j.optInt("l", 1), j.optString("s", ""), j.optString("k", "course"),
                j.optString("r", ""), j.optString("te", ""), (0 until w.length()).map { w.getInt(it) }.toSet(), j.optInt("co", -1),
                j.optString("no", ""), j.optString("cr", ""))
        }
        val LEVELS = listOf(tr("水课"), tr("普通"), tr("重要"))

        /** 「1-16周」「3,5,7」「1-8,10-16 单」这类写法 → 周次集合；空串 = 每周。 */
        fun parseWeeks(t0: String, max: Int = 30): Set<Int> {
            val t = t0.replace('，', ',').replace('、', ',').replace('～', '-').replace('~', '-').replace('—', '-').replace('–', '-')
            val odd = Regex("单|單|odd|impair|ungerade|impar|奇").containsMatchIn(t)
            val even = Regex("双|雙|even|pair(?!e)|gerade(?!r)|par\\b|偶").containsMatchIn(t) && !Regex("ungerade|impair|impar").containsMatchIn(t)
            val out = sortedSetOf<Int>()
            for (m in Regex("(\\d+)\\s*(?:-\\s*(\\d+))?").findAll(t)) {
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toIntOrNull() ?: a
                for (w in minOf(a, b)..maxOf(a, b)) if (w in 1..max) out += w
            }
            val r = out.filter { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }.toSet()
            if (r.isEmpty() && (odd || even)) return (1..max).filter { if (odd) it % 2 == 1 else it % 2 == 0 }.toSet()
            return r
        }
        /** 周次集合 → 简短文字：每周 / 1-16周 / 1-15周(单) / 3,5,9周 */
        fun weeksText(w: Set<Int>): String {
            if (w.isEmpty()) return tr("每周")
            val l = w.sorted()
            val step = if (l.size >= 2 && l.zipWithNext().all { (a, b) -> b - a == 2 }) 2 else if (l.zipWithNext().all { (a, b) -> b - a == 1 }) 1 else 0
            val body = when {
                l.size == 1 -> "${l[0]}"
                step == 1 -> "${l.first()}-${l.last()}"
                step == 2 -> "${l.first()}-${l.last()}"
                else -> {
                    val parts = ArrayList<String>(); var i = 0
                    while (i < l.size) { var j = i; while (j + 1 < l.size && l[j + 1] == l[j] + 1) j++; parts += if (j > i) "${l[i]}-${l[j]}" else "${l[i]}"; i = j + 1 }
                    parts.joinToString(",")
                }
            }
            return tr("{0}周", body) + if (step == 2 && l.size >= 2) (if (l.first() % 2 == 1) tr("(单)") else tr("(双)")) else ""
        }
    }
}

