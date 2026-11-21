package com.zhishi.timetable

/**
 * 学校 → 教务网址 + 教务类型。数据在 assets/schools.tsv，由 tools/build_schools.py 生成：
 * 来源 s = ShiGuangSchedule/shiguang_warehouse 里社区为这所学校写过的适配入口（MIT），
 *      b = baoozak/timetable 的学校列表（MIT，类型不少是猜的）。都没用真实账号验证过。
 * 纯函数，单测直接喂文本。
 */
data class School(val name: String, val kind: String, val url: String, val src: String, val note: String = "")

object Schools {
    fun parse(tsv: String): List<School> = tsv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 3 || f[0].isBlank() || f[2].isBlank()) null
            else School(f[0].trim(), f[1].trim().ifEmpty { "generic" }, f[2].trim(), f.getOrElse(3) { "b" }.trim(), f.getOrElse(4) { "" }.trim())
        }.toList()

    /**
     * 按名字搜：全名包含查询词最好；其次去掉「大学/学院」等后缀后包含；再次逐字按顺序出现（「大工」→ 大连理工大学）。
     * 同分时社区适配过的（s）排前，名字短的排前。
     */
    fun search(list: List<School>, q0: String, limit: Int = 30): List<School> {
        val q = q0.trim().lowercase().replace(" ", "")
        if (q.isEmpty()) return emptyList()
        fun score(s: School): Int {
            val n = s.name.lowercase()
            return when {
                n == q -> 0
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                s.url.lowercase().contains(q) && q.length >= 3 -> 3
                subseq(n, q) -> 4
                else -> -1
            }
        }
        return list.map { it to score(it) }.filter { it.second >= 0 }
            .sortedWith(compareBy({ it.second }, { if (it.first.src == "s") 0 else 1 }, { it.first.name.length }, { it.first.name }))
            .take(limit).map { it.first }
    }

    private fun subseq(n: String, q: String): Boolean {
        if (q.length < 2) return false
        var i = 0
        for (ch in n) if (i < q.length && ch == q[i]) i++
        return i == q.length
    }

    /** 列表里的类型 id 不认识时退回「从当前页面识别」。 */
    fun kindOf(s: School): String = if (Jw.KINDS.any { it.id == s.kind }) s.kind else "generic"
}
