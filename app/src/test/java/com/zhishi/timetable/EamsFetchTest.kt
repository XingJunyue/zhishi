package com.zhishi.timetable

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 树维 eams（电子科大）：点「导入课表」时不靠用户打开课表页，脚本自己请求
 * courseTableForStd.action → courseTableForStd!courseTable.action，再解析 new TaskActivity(...)。
 *
 * 抓取那段是网页里跑的 JS，这里用 node 模拟页面（假的 fetch 按网址返回 test/resources/jw/eams_uestc/ 里的样例）跑真正的 jwScript("eams")，
 * 再把结果交给 Jw.parse。样例是照树维页面结构手写的，不是真实账号抓的页面。
 * 本机没装 node 时跳过；CI（环境变量 CI 有值）上必须有 node，否则失败。
 */
class EamsFetchTest {
    private val dir = File(javaClass.getResource("/jw/eams_uestc/harness.js")!!.toURI()).parentFile

    private fun node(): Boolean = try {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS)
    } catch (x: Exception) { false }

    private fun run(std: String, mode: String): JSONObject {
        val has = node()
        if (System.getenv("CI").isNullOrEmpty()) assumeTrue("node not installed", has) else assertTrue("CI needs node", has)
        val js = File.createTempFile("eams", ".js").apply { writeText(jwScript("eams", null)); deleteOnExit() }
        val p = ProcessBuilder("node", File(dir, "harness.js").path, js.path, File(dir, "home.html").path, File(dir, std).path, mode)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue("node timed out", p.waitFor(60, TimeUnit.SECONDS))
        val line = out.lines().lastOrNull { it.startsWith("{") } ?: error("no result from script: $out")
        return JSONObject(line)
    }

    private fun check(o: JSONObject) {
        val r = o.getJSONObject("result")
        assertTrue(r.toString().take(300), r.optBoolean("ok"))
        // 请求了课表接口，参数齐全
        val log = o.getJSONArray("log")
        val posts = (0 until log.length()).map { log.getJSONObject(it) }.filter { it.getString("url").endsWith("/eams/courseTableForStd!courseTable.action") }
        assertTrue("no courseTable request: $log", posts.isNotEmpty())
        val body = posts.last().getString("body")
        for (k in listOf("ignoreHead=1", "setting.kind=std", "ids=987654", "semester.id=")) assertTrue("$k in $body", body.contains(k))
        // 解析
        val cs = Jw.parse("eams", r.toString())
        assertEquals(cs.toString(), 2, cs.size)
        val phy = cs.first { it.name == "大学物理Ⅱ" }
        assertEquals(1, phy.day); assertEquals(1 to 2, phy.start to phy.end); assertEquals((1..16).toSet(), phy.weeks)
        assertEquals("王老师", phy.teacher); assertEquals("清水河校区品学楼B-209", phy.room)
        val prog = cs.first { it.name == "程序设计基础" }
        assertEquals(3, prog.day); assertEquals(5 to 7, prog.start to prog.end)
        assertEquals((1..15 step 2).toSet(), prog.weeks); assertEquals("李老师,赵老师", prog.teacher)
        assertFalse("停课 is not a class", cs.any { it.room == "停课" })
    }

    /** 课表入口页有 ids 和学期 id：首页是空架子也能导入。 */
    @Test fun fetchFromSkeletonHome() {
        val o = run("std.html", "strict")
        check(o)
        assertEquals("523", o.getJSONObject("result").getJSONObject("diag").optString("term"))
    }

    /** 页面上读不到学期 id：电子科大按日期推学期 id 再请求。 */
    @Test fun fetchWithoutSemesterOnPage() = check(run("std_nosem.html", "anysem"))

    /** 直接给课表接口的返回也能解析（不经过抓取）。 */
    @Test fun parseTableResponse() {
        val t = File(dir, "table.html").readText()
        val cs = Jw.parse("eams", JSONObject().put("html", org.json.JSONArray().put(t)).toString())
        assertEquals(2, cs.size)
    }

    @Test fun userAgentLooksLikeChrome() {
        val wv = "Mozilla/5.0 (Linux; Android 14; Pixel 6 Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36"
        val m = jwUserAgent(wv, false)
        assertEquals("Mozilla/5.0 (Linux; Android 14; Pixel 6 Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36", m)
        assertFalse(m.contains("wv")); assertFalse(m.contains("Version/"))
        val d = jwUserAgent(wv, true)
        assertTrue(d.contains("Windows NT 10.0")); assertTrue(d.contains("Chrome/120.0.6099.230")); assertFalse(d.contains("Mobile"))
        assertTrue(jwWebScheme("https")); assertTrue(jwWebScheme("HTTP")); assertFalse(jwWebScheme("weixin")); assertFalse(jwWebScheme("intent")); assertFalse(jwWebScheme(null as String?))
    }

    /** 教务导入页不默认东北大学：空搜索不出东大，搜到才出。 */
    @Test fun noNeuByDefault() {
        assertFalse(neuHit("")); assertFalse(neuHit("  "))
        assertTrue(neuHit("东北大学")); assertTrue(neuHit("东北")); assertTrue(neuHit("neu"))
        assertFalse(neuHit("东北大学秦皇岛分校")); assertFalse(neuHit("电子科技大学")); assertFalse(neuHit("大学"))
    }
}
