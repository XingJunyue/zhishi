package com.zhishi.timetable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 各家教务「直接点导入」的取数路径：用 node 模拟页面（test/resources/jw/fetch/harness.js，假的 fetch 按路径返回样例），
 * 跑真正的 jwScript，再交给 Jw.parse / JwOutcome。样例都是手写的，不是真实学校抓的；真实账号只验证过东大金智。
 * 本机没 node 时跳过；CI 上必须有。
 */
class JwFetchTest {
    private val dir = File(javaClass.getResource("/jw/fetch/harness.js")!!.toURI()).parentFile
    private fun res(n: String) = File(dir.parentFile, n).readText()

    private fun node(): Boolean = try {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor(20, TimeUnit.SECONDS)
    } catch (x: Exception) { false }

    /** 返回 {result, log, steps}。 */
    private fun run(kind: String, cfg: JSONObject, arg: String? = null): JSONObject {
        val has = node()
        if (System.getenv("CI").isNullOrEmpty()) assumeTrue("node not installed", has) else assertTrue("CI needs node", has)
        val js = File.createTempFile("jwscript", ".js").apply { writeText(jwScript(kind, arg, 7)); deleteOnExit() }
        val cf = File.createTempFile("cfg", ".json").apply { writeText(cfg.toString()); deleteOnExit() }
        val p = ProcessBuilder("node", File(dir, "harness.js").path, js.path, cf.path).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue("node timed out", p.waitFor(60, TimeUnit.SECONDS))
        val line = out.lines().lastOrNull { it.startsWith("{") } ?: error("no result: $out")
        val o = JSONObject(line)
        assertEquals(7, o.getJSONObject("result").optInt("token"))
        return o
    }

    private fun route(method: String, path: String, file: String? = null, text: String? = null, has: List<String> = emptyList(), hang: Boolean = false) =
        JSONObject().put("method", method).put("path", path).apply {
            if (file != null) put("file", file); if (text != null) put("text", text)
            if (has.isNotEmpty()) put("has", JSONArray(has)); if (hang) put("hang", true)
        }
    private fun cfg(url: String, home: String, vararg routes: JSONObject, frames: List<String> = emptyList(), timeout: Int? = null) =
        JSONObject().put("url", url).put("home", home).put("routes", JSONArray(routes.toList())).put("frames", JSONArray(frames)).apply { if (timeout != null) put("timeout", timeout) }

    private fun parsed(kind: String, o: JSONObject) = Jw.parse(kind, o.getJSONObject("result").toString())
    private fun outcome(o: JSONObject, n: Int): String? {
        val r = o.getJSONObject("result")
        return JwOutcome.failStep(r.optBoolean("ok"), r.optString("err"), r.optJSONObject("diag"), n)
    }

    // ---------- 正方（中国矿业大学这类新正方 V9） ----------

    private val cumt = "http://jwxt.cumt.edu.cn/jwglxt/xtgl/index_initMenu.html"

    /** 在首页（没有学年学期框）点导入：先取课表查询页读默认学年学期，cxXsKb 空时再 POST cxXsgrkb?gnmkdm=N2151（kzlx=ck、xsdm）。 */
    @Test fun zfFromHomePage() {
        val o = run("zf", cfg(cumt, "fetch/zf_home.html",
            route("GET", "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html", "fetch/zf_index.html"),
            route("POST", "/jwglxt/kbcx/xskbcx_cxXsKb.html", text = "{\"kbList\":[]}"),
            route("POST", "/jwglxt/kbcx/xskbcx_cxXsgrkb.html", "zf_kb.json", has = listOf("gnmkdm=N2151", "xnm=2026", "xqm=3", "kzlx=ck", "xsdm="))))
        val want = Jw.parse("zf", JSONObject().put("json", res("zf_kb.json")).toString())
        assertTrue(want.isNotEmpty())
        val got = parsed("zf", o)
        assertEquals(want, got)
        assertNull(outcome(o, got.size))
        assertEquals("2026-3", o.getJSONObject("result").getJSONObject("diag").optString("term"))
    }

    /** 会话过期：接口都回登录页 → 「登录未完成」，不是「解析为空」。 */
    @Test fun zfSessionExpired() {
        val o = run("zf", cfg(cumt, "fetch/zf_home.html",
            route("GET", "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html", "fetch/login.html"),
            route("POST", "/jwglxt/kbcx/xskbcx_cxXsKb.html", "fetch/login.html"),
            route("POST", "/jwglxt/kbcx/xskbcx_cxXsgrkb.html", "fetch/login.html")))
        assertEquals("login", outcome(o, parsed("zf", o).size))
    }

    // ---------- 金智（东大）：请求和以前一样，只加了步骤 / 超时 ----------

    private val neu = "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/home/index.html"
    private fun neuRoutes() = arrayOf(
        route("POST", "/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", "neu_term.json", has = listOf("CSDM=SYS", "ZCSDM=DQXNXQDM", "SFSY=1")),
        route("POST", "/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do", "neu_campus.json"),
        route("POST", "/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do", "neu_detail.json", has = listOf("XNXQDM=2026-2027-1", "XQDM=1")))

    @Test fun neuAsksCampusThenImports() {
        val o1 = run("wisedu", cfg(neu, "fetch/empty_home.html", *neuRoutes()))
        assertEquals("campus", o1.getJSONObject("result").optString("need"))
        val o2 = run("wisedu", cfg(neu, "fetch/empty_home.html", *neuRoutes()), arg = "1")
        val r = parsed("neu", o2)
        assertEquals(4, r.size)   // 和 JwTest.neuDetail 的样例一致
        assertNull(outcome(o2, r.size))
        assertEquals(listOf("page", "term", "schedule"), o2.getJSONArray("steps").let { a -> (0 until a.length()).map { a.getString(it) } })
    }

    /** 接口卡住不返回：脚本自己超时报错，标出卡在哪一步（以前会一直转圈）。 */
    @Test fun neuHangTimesOut() {
        val o = run("wisedu", cfg(neu, "fetch/empty_home.html", route("POST", "/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do", hang = true), timeout = 300))
        val r = o.getJSONObject("result")
        assertFalse(r.optBoolean("ok"))
        assertEquals(r.toString(), "timeout", outcome(o, 0))
        assertEquals("term", r.getJSONObject("diag").optString("step"))
    }

    // ---------- 强智 / 新 URP / 旧 URP / 超星 ----------

    @Test fun qzFetchesList() {
        val o = run("qz", cfg("http://jw.example.edu.cn/jsxsd/framework/xsMain.jsp", "fetch/empty_home.html",
            route("GET", "/jsxsd/xskb/xskb_list.do", "qz.html")))
        assertEquals(4, parsed("qz", o).size)
    }

    @Test fun urpNewFetchesCallback() {
        val o = run("urpnew", cfg("http://jw.example.edu.cn/student/index", "fetch/empty_home.html",
            route("POST", "/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback", "urpnew.json")))
        val want = Jw.parse("urpnew", JSONObject().put("json", res("urpnew.json")).toString())
        assertTrue(want.isNotEmpty()); assertEquals(want, parsed("urpnew", o))
    }

    @Test fun urpOldFetchesXkAction() {
        val o = run("urp", cfg("http://jw.example.edu.cn/menu/s_main.jsp", "fetch/empty_home.html",
            route("GET", "/xkAction.do", "urp_list.html", has = listOf("actionType=6"))))
        assertEquals(3, parsed("urp", o).size)
    }

    @Test fun cxReadsIdsThenList() {
        val o = run("cx", cfg("http://jw.example.edu.cn/admin/index", "fetch/empty_home.html",
            route("GET", "/admin/pkgl/xskb/queryKbForXsd", "fetch/cx_page.html"),
            route("GET", "/admin/xsd/pkgl/xskb/sdpkkbList", "cx.json", has = listOf("xhid=A1B2C3", "xqdm=01", "xnxq=2026-2027-1"))))
        val want = Jw.parse("cx", JSONObject().put("json", res("cx.json")).toString())
        assertTrue(want.isNotEmpty()); assertEquals(want, parsed("cx", o))
    }

    // ---------- 读当前页面里嵌的 frame ----------

    @Test fun kingoInsideFrame() {
        val o = run("kingo", cfg("http://jw.example.edu.cn/jwweb/MAINFRM.aspx", "fetch/frameset.html", frames = listOf("kingo.html")))
        assertEquals(4, parsed("kingo", o).size)
    }

    @Test fun genericTableInsideFrame() {
        val o = run("generic", cfg("http://jw.example.edu.cn/index.jsp", "fetch/frameset.html", frames = listOf("table.html")))
        assertEquals(2, parsed("generic", o).size)
    }

    /** 什么都没拿到：「没找到课表」。 */
    @Test fun nothingFound() {
        val o = run("qz", cfg("http://jw.example.edu.cn/jsxsd/framework/xsMain.jsp", "fetch/empty_home.html"))
        assertEquals("notfound", outcome(o, parsed("qz", o).size))
    }

    // ---------- 结果判断与诊断信息（不跑 node） ----------

    @Test fun outcomeAndDiagnostics() {
        val d = JSONObject().put("step", "schedule").put("http", 500).put("login", false).put("found", true)
            .put("tried", JSONArray(listOf("POST /jwglxt/kbcx/xskbcx_cxXsgrkb.html", "GET /student/2023123456/info")))
        assertEquals("empty", JwOutcome.failStep(true, "", d, 0))
        assertNull(JwOutcome.failStep(true, "", d, 3))
        assertEquals("network", JwOutcome.failStep(false, "HTTP 500", d, 0))
        assertEquals("timeout", JwOutcome.failStep(false, "timeout /x", d, 0))
        assertEquals("login", JwOutcome.failStep(false, "Unexpected token < in JSON at position 0", d, 0))
        assertEquals("nojwapp", JwOutcome.failStep(false, "jwapp", null, 0))
        assertEquals("login", JwOutcome.failStep(true, "", JSONObject().put("loginPage", true), 0))
        assertEquals("notfound", JwOutcome.failStep(true, "", JSONObject(), 0))
        val t = JwOutcome.diagText("1.96 preview", "zf", "jwxt.cumt.edu.cn", "schedule", "empty", "HTTP 500 http://x/y?xh=2023123456&pwd=abc", d, 2, true,
            JwOutcome.uaLabel("Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.1 Mobile Safari/537.36", false), "2026-11-19 22:00 +0800")
        assertTrue(t, t.contains("jwxt.cumt.edu.cn")); assertTrue(t.contains("HTTP: 500")); assertTrue(t.contains("xskbcx_cxXsgrkb.html"))
        assertTrue(t.contains("手机版 Chrome 120"))
        // 不带查询参数、长数字（学号）、密码
        assertFalse(t, t.contains("2023123456")); assertFalse(t.contains("pwd")); assertFalse(t.contains("Pixel"))
    }
}
