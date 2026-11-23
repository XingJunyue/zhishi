package com.zhishi.timetable

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.net.Uri
import android.view.View
import java.time.LocalDate

/** 课程等级的标签颜色（水课 / 普通 / 重要），课表和小部件共用。 */
object LevelTag {
    val FG = intArrayOf(0xFF4E8A62.toInt(), 0xFF4F74A6.toInt(), 0xFFC46F52.toInt())
    val BG = intArrayOf(0xFFE3F1E7.toInt(), 0xFFE1EAF6.toInt(), 0xFFF8E6D8.toInt())
    /** 深色皮肤下：深底浅字。 */
    val FG_D = intArrayOf(0xFFA8D9B6.toInt(), 0xFFAFC6EC.toInt(), 0xFFF2B8A0.toInt())
    val BG_D = intArrayOf(0xFF1F3427.toInt(), 0xFF1E2A3D.toInt(), 0xFF3D2A22.toInt())
    fun fg(l: Int, dark: Boolean) = (if (dark) FG_D else FG)[l.coerceIn(0, 2)]
    fun bg(l: Int, dark: Boolean) = (if (dark) BG_D else BG)[l.coerceIn(0, 2)]
}

/**
 * 课表小部件：4×2 和 2×2。上面是正在上的课（课间就是最近的下一节），下面一条是再下一节；
 * 今天没课 / 课都上完了就是白底加颜文字。整张画成一张图，排版和预览图一致。
 */
object CourseWidgets {
    /** 今天的课排好后，此刻该显示什么（纯函数，单测）。 */
    data class Now(val main: Slot?, val live: Boolean, val next: Slot?, val total: Int, val index: Int, val doneAll: Boolean)

    fun plan(today: List<Slot>, m: Int): Now {
        val l = today.sortedBy { it.from }
        if (l.isEmpty()) return Now(null, false, null, 0, 0, false)
        val cur = l.firstOrNull { m >= it.from && m < it.to }
        if (cur != null) {
            val next = l.firstOrNull { it !== cur && it.from >= cur.from && it.from > m }
            return Now(cur, true, next, l.size, l.indexOf(cur) + 1, false)
        }
        val up = l.filter { it.from > m }
        if (up.isEmpty()) return Now(null, false, null, l.size, l.size, true)
        return Now(up[0], false, up.getOrNull(1), l.size, l.indexOf(up[0]) + 1, false)
    }

    /** 今天（按周次）要上的课。 */
    fun today(c: Context): List<Slot> {
        val w = Term.weekNow(c)
        val d = Term.weekday()
        return Term.desired(c).filter { it.kind == "course" && d in it.days && it.from < it.to && it.inWeek(w) }.sortedBy { it.from }
    }

    private val KAO = listOf("(ﾉ≧∀≦)ﾉ ♪", "ヽ(✿ﾟ▽ﾟ)ノ", "(๑˃ᴗ˂)ﻭ ✧", "٩(ˊᗜˋ*)و ♡", "(*≧ω≦)ﾉ ☆", "ヾ(≧▽≦*)o")
    private val KAO_DONE = listOf("(๑•̀ㅂ•́)و✧", "ヽ(°▽°)ノ ✿", "(ﾉ´ヮ`)ﾉ*: ･ﾟ", "o(≧▽≦)o ♪")

    private var lastSig = ""
    private var lastTodoSig = ""
    private var lastMin = -1

    /** 改课、改设置、换皮肤后调用：出错也不影响调用方。 */
    fun safeUpdate(c: Context) { try { update(c) } catch (x: Throwable) {} }

    /**
     * 安排下一次重画（时刻由 [nextTick] 算）：课表小部件今天还有课（上课中要显示剩余时间）就在下一分钟，没课了就到明天 0 点；
     * 待办小部件在最近一条待办到截止（变成「已过期」）或明天 0 点（「今天 / 明天」换天）重画。null = 不用再排。
     * 用不唤醒的精确闹钟：息屏时不重画，亮屏后系统补发，再接着排。
     */
    private fun schedule(c: Context, at: Long?) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(c, 11, Intent(c, WidgetTick::class.java).setAction(WidgetTick.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (at == null) { am.cancel(pi); return }
        // 不唤醒（息屏不重画，亮屏后补发）但要准：以前用 set() 是不精确的，有的系统会把它攒着拖很久，桌面上的「x 分钟后 / 还剩」就停住了
        try {
            if (TodoAlarm.exactOk(c)) am.setExact(android.app.AlarmManager.RTC, at, pi)
            else am.setWindow(android.app.AlarmManager.RTC, at, 10_000L, pi)
        } catch (x: SecurityException) {
            am.setWindow(android.app.AlarmManager.RTC, at, 10_000L, pi)
        }
    }

    /** 下一次该重画的时刻（纯函数，单测）。course / todo = 桌面上有没有这种小部件。 */
    fun nextTick(now: Long, course: Boolean, today: List<Slot>, nowMin: Int, todo: Boolean, todos: List<Todo>,
                 tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long? {
        if (!course && !todo) return null
        val midnight = Todos.at(Todos.dayOfMs(now, tz) + 1, 0, tz) + 5_000L
        var at = midnight
        if (course && today.any { it.to > nowMin }) at = minOf(at, (now / 60_000L + 1) * 60_000L)
        if (todo) todos.filter { !it.done && it.due >= 0 }.map { Todos.endOf(it, tz) }.filter { it > now }.minOrNull()?.let { at = minOf(at, it + 1_000L) }
        return at
    }

    fun update(c: Context, force: Boolean = true) {
        val m = AppWidgetManager.getInstance(c) ?: return
        val big = m.getAppWidgetIds(ComponentName(c, CourseWidget4::class.java))
        val small = m.getAppWidgetIds(ComponentName(c, CourseWidget2::class.java))
        val tBig = m.getAppWidgetIds(ComponentName(c, TodoWidget4::class.java))
        val tSmall = m.getAppWidgetIds(ComponentName(c, TodoWidget2::class.java))
        val hasCourse = big.isNotEmpty() || small.isNotEmpty()
        val hasTodo = tBig.isNotEmpty() || tSmall.isNotEmpty()
        if (!hasCourse && !hasTodo) { schedule(c, null); return }
        val list = if (hasCourse) today(c) else emptyList()
        val todos = if (hasTodo) TodoStore.all(c) else emptyList()
        val nowMs = System.currentTimeMillis()
        schedule(c, nextTick(nowMs, hasCourse, list, Term.nowMin(), hasTodo, todos))
        val sig = "${Term.nowMin()}|${Term.todayEpoch()}|${Term.desired(c).hashCode()}|${todos.hashCode()}|${todoPlan(todos, nowMs).overdue}|${Term.widgetLevel(c)}|${Tr.lang}|${Skins.palFor(c).hashCode()}|${WidgetStyle.classic(c)}|${WidgetStyle.tint(c)}|${WidgetStyle.clear(c)}|${WallTint.read(c)}|${Skins.byId(c.getSharedPreferences("look", Context.MODE_PRIVATE).getString("skin", null)).id}"
        if (!force && sig == lastSig) return
        lastSig = sig
        val all = Term.desired(c).filter { it.kind == "course" }
        // 画图用的是这个对象里的共享画笔和配色，待办列表的行在 RemoteViewsService 的 binder 线程里画，所以加锁
        if (hasCourse) synchronized(this) {
            val now = plan(list, Term.nowMin())
            for ((ids, wide) in listOf(big to true, small to false)) for (id in ids) {
                try {
                    val o = m.getAppWidgetOptions(id)
                    val v = RemoteViews(c.packageName, R.layout.widget_course)
                    v.setImageViewBitmap(R.id.canvas, draw(c, wide, o, now, all))
                    v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 7,
                        Intent(c, MainActivity::class.java).putExtra("open", "table").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    m.updateAppWidget(id, v)
                } catch (x: Exception) { android.util.Log.w("Zhishi", "课表小部件画不出来：${x.message}") }
            }
        }
        if (hasTodo) {
            val tp = todoPlan(todos, nowMs)
            // 待办小部件和分钟无关（头上只有日期）：只在待办 / 日期 / 状态 / 样式变了时重画，免得每分钟把列表滚回顶上
            val tsig = "${Term.todayEpoch()}|${todos.hashCode()}|${tp.overdue}|${tp.today}|${Term.desired(c).hashCode()}|${Tr.lang}|${Skins.palFor(c).hashCode()}|${WidgetStyle.classic(c)}|${WidgetStyle.tint(c)}|${WidgetStyle.clear(c)}|${WallTint.read(c)}|${Skins.byId(c.getSharedPreferences("look", Context.MODE_PRIVATE).getString("skin", null)).id}"
            if (force || tsig != lastTodoSig) {
                lastTodoSig = tsig
                for ((ids, wide) in listOf(tBig to true, tSmall to false)) for (id in ids) {
                    try {
                        val o = m.getAppWidgetOptions(id)
                        m.updateAppWidget(id, todoViews(c, id, wide, o, tp, all))
                    } catch (x: Exception) { android.util.Log.w("Zhishi", "待办小部件画不出来：${x.message}") }
                }
                // 列表里的每一行由 TodoWidgetService 提供：通知桌面重新取一遍
                try { m.notifyAppWidgetViewDataChanged(tBig + tSmall, R.id.list) } catch (x: Exception) { android.util.Log.w("Zhishi", "待办列表刷新失败：${x.message}") }
            }
        }
    }

    /**
     * 一个待办小部件的 RemoteViews：底图（玻璃卡 + 头部）+ 一个可以上下滑动的列表（ListView，行由 [TodoWidgetService] 画），
     * 所有安卓版本 / 桌面都支持。没有待办时不显示列表，整张底图就是颜文字空状态。
     */
    fun todoViews(c: Context, id: Int, wide: Boolean, o: Bundle?, tp: TodoNow, all: List<Slot>): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_todo)
        val empty = tp.list.isEmpty()
        val g = synchronized(this) {
            v.setImageViewBitmap(R.id.canvas, drawTodo(c, wide, o, tp, all, rows = false))
            geo(c, o, wide)
        }
        // 点空白处 / 头部：打开待办页；一条待办都没有时直接打开「添加待办」
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, if (empty) 9 else 8,
            Intent(c, MainActivity::class.java).putExtra("open", if (empty) "todo:1" else "todo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (empty) { v.setViewVisibility(R.id.list, View.GONE); return v }
        v.setViewVisibility(R.id.list, View.VISIBLE)
        val svc = Intent(c, TodoWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(TodoWidgetService.WIDE, wide)
        // data 带上小部件 id，每个小部件各用一个 factory（否则系统按 Intent 复用，4×2 和 2×2 会串）
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        v.setRemoteAdapter(R.id.list, svc)
        // 列表放在底图里头部下面那块：设计稿单位 × 缩放 × 密度 = 像素
        val px = g.k * c.resources.displayMetrics.density
        val pad = rowPad(wide)
        v.setViewPadding(R.id.list, (pad * px).toInt(), (ROW_TOP * px).toInt(), (pad * px).toInt(), (rowBottom(wide) * px).toInt())
        // 点任意一行：打开待办页（和点小部件其他地方一样）。模板要可变，系统才能把每行的 fill-in 合进去
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        v.setPendingIntentTemplate(R.id.list, PendingIntent.getActivity(c, 10,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags))
        return v
    }

    /** 每一行点下去时合进模板的 Intent。 */
    fun rowFillIn(): Intent = Intent().putExtra("open", "todo")

    // ---------------- 画图 ----------------
    // 颜色跟着当前皮肤（浅色 / 深色）走，每次画之前从皮肤取
    private var INK = 0
    private var SUB = 0
    private var ACC = 0
    private var ACC_PALE = 0
    private var ON_ACC = 0
    private var CREAM = 0
    private var FREE_BG = 0
    private var NEXT_BG = 0
    private var NEXT_FG = 0
    private var DARK = false
    private var CLASSIC = false
    /** 「跟随壁纸」生效中（液态玻璃 + 读到了壁纸颜色）：不铺皮肤插画，颜色全按壁纸。 */
    private var WALL = false
    private var CLEAR = WidgetStyle.CLEAR_DEFAULT
    private lateinit var PAL: Pal
    private fun usePal(c: Context) {
        CLASSIC = WidgetStyle.classic(c)
        CLEAR = WidgetStyle.clear(c)
        val wall = if (!CLASSIC && WidgetStyle.followWall(c)) WallTint.read(c) else null
        WALL = wall != null
        val p = if (wall != null) WallTint.pal(wall, Skins.byId(c.getSharedPreferences("look", Context.MODE_PRIVATE).getString("skin", null))) else Skins.palFor(c)
        PAL = p; DARK = p.dark
        INK = p.i(p.ink); SUB = p.i(p.soft); ACC = p.i(p.deep); ACC_PALE = p.i(p.pale); ON_ACC = p.i(p.onAcc)
        CREAM = p.i(p.bg); FREE_BG = p.i(p.card); NEXT_BG = p.i(p.sagePale); NEXT_FG = p.i(p.sageInk)
    }

    /** 小部件页的实时预览：和桌面上画法完全一样；今天没课时用一节示例课。 */
    fun preview(c: Context, wide: Boolean): Bitmap {
        val list = today(c)
        var now = plan(list, Term.nowMin())
        var all = Term.desired(c)
        if (now.main == null) {
            val m = Term.nowMin().coerceIn(30, 1380)
            val a = Slot("demo1", tr("高等数学"), setOf(Term.weekday()), m - 20, m + 25, 2, room = tr("教学楼 201"), teacher = tr("王老师"))
            val b = Slot("demo2", tr("大学英语"), setOf(Term.weekday()), m + 35, m + 80, 1, room = tr("外语楼 305"))
            all = listOf(a, b); now = plan(all, m)
        }
        return synchronized(this) { draw(c, wide, null, now, all) }
    }

    // ---------------- 待办小部件 ----------------

    /** 待办小部件此刻要显示的：没完成的待办，已过期的在前，再今天、之后、没设截止的（和待办页同一顺序）。 */
    data class TodoNow(val list: List<Todo>, val overdue: Int, val today: Int, val allDone: Boolean, val now: Long)

    fun todoPlan(all: List<Todo>, now: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): TodoNow {
        val g = Todos.group(all, now, tz)
        val l = g.overdue + g.today + g.upcoming
        return TodoNow(l, g.overdue.size, g.today.size, l.isEmpty() && g.done.isNotEmpty(), now)
    }

    /** 一条待办的状态：0 已过期、1 今天、2 之后、3 没设截止。 */
    fun todoState(t: Todo, now: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Int = when {
        t.due < 0 -> 3
        Todos.overdue(t, now, tz) -> 0
        Todos.dayOf(t, tz) == Todos.dayOfMs(now, tz) -> 1
        else -> 2
    }

    /** 小部件页的实时预览：和桌面上画法完全一样；没有待办时用几条示例。 */
    fun previewTodo(c: Context, wide: Boolean, demo: Boolean = false): Bitmap {
        val now = System.currentTimeMillis()
        var tp = todoPlan(if (demo) emptyList() else TodoStore.all(c), now)
        if (tp.list.isEmpty()) {
            val day = Todos.dayOfMs(now)
            tp = todoPlan(listOf(
                Todo("demo1", tr("交高数作业 第三章"), tr("习题 3.1–3.4"), Todos.at(day, 23 * 60 + 30), course = tr("高等数学"), created = 1),
                Todo("demo2", tr("实验报告"), "", Todos.at(day + 1, 18 * 60), created = 2),
                Todo("demo3", tr("背单词"), "", -1L, created = 3),
            ), now)
        }
        return synchronized(this) { drawTodo(c, wide, null, tp, Term.desired(c).filter { it.kind == "course" }, rows = true) }
    }

    /** 左边竖条的颜色：关联了课就用那门课的颜色，否则已过期用「重要」的暖色、其余用主色。 */
    private fun todoBar(t: Todo, state: Int, all: List<Slot>): Pair<Int, Int> {
        val s = if (t.course.isBlank()) null else all.firstOrNull { it.name.trim() == t.course.trim() }
        if (s != null) return courseToneArgb(courseColorArgb(s, all), PAL)
        return if (state == 0) LevelTag.bg(2, DARK) to LevelTag.fg(2, DARK) else ACC_PALE to ACC
    }

    /** 状态标签：文字、字色、底色。 */
    private fun todoChip(state: Int): Triple<String, Int, Int> = when (state) {
        0 -> Triple(tr("已过期"), LevelTag.fg(2, DARK), LevelTag.bg(2, DARK))
        1 -> Triple(tr("今天"), ON_ACC, ACC)
        2 -> Triple(tr("即将"), ACC, ACC_PALE)
        else -> Triple(tr("待办"), ACC, ACC_PALE)
    }

    private fun todoDue(t: Todo): String = if (t.due < 0) tr("没有截止时间") else dueText(t.due, t.allDay)

    // 待办列表的排版（设计稿单位）：头部高 ROW_TOP，列表左右留 rowPad，底下留 rowBottom；每行一块小玻璃，高 rowH，行间 rowGap
    const val ROW_TOP = 31f
    fun rowPad(wide: Boolean) = if (wide) 15f else 13f
    fun rowBottom(wide: Boolean) = if (wide) 12f else 11f
    fun rowH(wide: Boolean) = if (wide) 50f else 46f
    fun rowGap(wide: Boolean) = if (wide) 6f else 5f

    /** 待办小部件的头部：4×2 是「周一 11月23日 · 待办」+ 右边过期数 / 总数；2×2 是「待办」+ 总数。 */
    private fun todoHeader(d: D, w: Float, wide: Boolean, tp: TodoNow) {
        val red = LevelTag.fg(2, DARK)
        if (wide) {
            d.text(header() + " · " + tr("待办"), 15f, 10f, 11f, SUB, maxW = w * 0.55f)
            val right = (if (tp.overdue > 0) tr("已过期 {0} 项", tp.overdue) + " · " else "") + tr("共 {0} 项", tp.list.size)
            d.text(right, w - 15f, 10f, 11f, if (tp.overdue > 0) red else SUB, align = 2, maxW = w * 0.42f)
        } else {
            val lw = d.text(tr("待办"), 13f, 9.5f, 12f, ACC, true, maxW = w * 0.4f)
            val right = (if (tp.overdue > 0) tr("已过期 {0} 项", tp.overdue) + " · " else "") + tr("共 {0} 项", tp.list.size)
            d.text(right, w - 13f, 11f, 9.5f, if (tp.overdue > 0) red else SUB, align = 2, maxW = w - 26f - lw - 6f)
        }
    }

    /**
     * 列表里的一行：一块小玻璃（和课表小部件「下一节」那块一样），左边关联课程颜色的竖条，标题、状态标签（已过期 / 今天 / 即将），
     * 截止时间（过期是红字）、关联课程、备注。(x, y) 是左上角，宽 w，高 [rowH]。
     */
    private fun todoRow(d: D, x: Float, y: Float, w: Float, t: Todo, st: Int, all: List<Slot>, wide: Boolean) {
        val h = rowH(wide); val R = x + w
        val red = LevelTag.fg(2, DARK)
        d.glass(x + 0.6f, y + 0.6f, R - 0.6f, y + h - 0.6f, if (wide) 14f else 13f, NEXT_BG, WallTint.innerAlpha(CLEAR, DARK))
        val (_, deep) = todoBar(t, st, all)
        val (ct, cf, cb) = todoChip(st)
        if (wide) {
            d.round(x + 9f, y + 9f, x + 12.5f, y + h - 9f, 1.75f, deep)
            val cx = x + 20f
            val cr = R - 10f - d.chip(ct, R - 10f, y + 7f, 9f, cf, cb, true, right = true) - 6f
            d.text(t.title, cx, y + 6f, 13.5f, INK, true, maxW = cr - cx)
            var tx = cx; val ly = y + 29f
            d.clock(tx, ly + 0.5f, if (st == 0) red else SUB); tx += 14f
            tx += d.text(todoDue(t), tx, ly - 1f, 10.5f, if (st == 0) red else INK, maxW = R - 10f - tx) + 12f
            if (t.course.isNotBlank() && tx < R - 50f) { d.book(tx, ly + 0.5f, SUB); tx += 14f; tx += d.text(t.course, tx, ly - 1f, 10.5f, INK, maxW = R - 10f - tx) + 10f }
            if (t.note.isNotBlank() && tx < R - 40f) d.text(t.note, tx, ly - 1f, 10.5f, SUB, maxW = R - 10f - tx)
        } else {
            d.round(x + 8f, y + 8f, x + 11f, y + h - 8f, 1.5f, deep)
            val cx = x + 17f
            d.text(t.title, cx, y + 6f, 12.5f, INK, true, maxW = R - 9f - cx)
            val cw = d.chip(ct, cx, y + 25f, 8f, cf, cb, true)
            val info = listOf(todoDue(t), t.course).filter { it.isNotBlank() }.joinToString(" · ")
            d.text(info, cx + cw + 5f, y + 26f, 9.5f, if (st == 0) red else SUB, maxW = R - 9f - cx - cw - 5f)
        }
    }

    /** 桌面上列表的一行画成一张透明底的小图（宽 = 小部件宽减两边留白，高 = 一行 + 行间距；最后一行不带间距）。 */
    fun todoRowBitmap(c: Context, wide: Boolean, o: Bundle?, t: Todo, now: Long, all: List<Slot>, last: Boolean): Bitmap = synchronized(this) {
        usePal(c)
        val g = geo(c, o, wide)
        val rw = g.lw - 2 * rowPad(wide)
        val rh = rowH(wide) + (if (last) 0f else rowGap(wide))
        val s = g.scale * g.k
        val b = Bitmap.createBitmap((rw * s).toInt().coerceAtLeast(1), (rh * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        b.density = c.resources.displayMetrics.densityDpi
        val cv = Canvas(b); cv.scale(s, s)
        val d = D(cv); d.classic = CLASSIC; d.halo = haloColor()
        todoRow(d, 0f, 0f, rw, t, todoState(t, now), all, wide)
        b
    }

    private fun todoFree(d: D, w: Float, h: Float, wide: Boolean, done: Boolean) {
        val day = Term.todayEpoch().toInt()
        val kao = if (done) KAO_DONE[Math.floorMod(day, KAO_DONE.size)] else KAO[Math.floorMod(day, KAO.size)]
        val title = if (done) tr("待办都做完啦") else tr("还没有待办")
        val pink = 0xFFFF8AAA.toInt(); val yel = 0xFFFFCE5C.toInt(); val mint = 0xFF84D6BE.toInt(); val lav = 0xFFB2A0F0.toInt()
        val cx = w / 2
        if (wide) {
            d.text(header() + " · " + tr("待办"), 15f, 12f, 11f, SUB)
            d.text(kao, cx, h * 0.40f - 14f, 22f, pink, true, 1, w - 40f)
            d.text(title, cx, h * 0.65f - 12f, 18f, INK, true, 1)
            d.text(if (done) tr("辛苦啦，好好放松一下 ♡") else tr("点一下记一条待办 ♡"), cx, h * 0.82f - 7f, 11f, SUB, align = 1)
            for (s in listOf(floatArrayOf(.14f, .52f, 8f), floatArrayOf(.20f, .78f, 5f), floatArrayOf(.84f, .28f, 7f), floatArrayOf(.88f, .64f, 9f), floatArrayOf(.78f, .86f, 5f), floatArrayOf(.27f, .27f, 5f)).withIndex())
                d.spark(w * s.value[0], h * s.value[1], s.value[2], listOf(yel, mint, lav, pink, yel, pink)[s.index])
        } else {
            d.text(kao, cx, h * 0.36f - 11f, 17f, pink, true, 1, w - 24f)
            d.text(title, cx, h * 0.60f - 10f, 15f, INK, true, 1, w - 20f)
            d.text(if (done) tr("辛苦啦 ♡") else tr("点一下添加 ♡"), cx, h * 0.77f - 6f, 10f, SUB, align = 1)
            for (s in listOf(floatArrayOf(.15f, .16f, 6f), floatArrayOf(.85f, .19f, 5f), floatArrayOf(.13f, .87f, 4f), floatArrayOf(.87f, .85f, 7f)).withIndex())
                d.spark(w * s.value[0], h * s.value[1], s.value[2], listOf(yel, lav, mint, pink)[s.index])
        }
    }

    private fun draw(c: Context, wide: Boolean, o: Bundle?, now: Now, all: List<Slot>): Bitmap =
        canvasFor(c, wide, o, now.main == null) { d, lw, lh ->
            when {
                now.main == null -> freeCard(d, lw, lh, wide, now.doneAll)
                wide -> wideCard(c, d, lw, lh, now, all)
                else -> smallCard(c, d, lw, lh, now, all)
            }
        }

    /** 待办小部件：和课表小部件同一张底（样式、玻璃、透明度、皮肤 / 壁纸配色、尺寸都一样），只是内容不同。 */
    /** rows = true：把列表的前几行也画进去（小部件页的预览）；桌面上 rows = false，行由列表自己画。 */
    private fun drawTodo(c: Context, wide: Boolean, o: Bundle?, tp: TodoNow, all: List<Slot>, rows: Boolean): Bitmap =
        canvasFor(c, wide, o, tp.list.isEmpty()) { d, lw, lh ->
            if (tp.list.isEmpty()) todoFree(d, lw, lh, wide, tp.allDone)
            else {
                todoHeader(d, lw, wide, tp)
                if (rows) {
                    val L = rowPad(wide); val R = lw - rowPad(wide); val top = ROW_TOP; val bottom = lh - rowBottom(wide)
                    d.cv.save(); d.cv.clipRect(L - 2f, top, R + 2f, bottom)
                    var y = top
                    for (t in tp.list) {
                        if (y >= bottom) break
                        todoRow(d, L, y, R - L, t, todoState(t, tp.now), all, wide)
                        y += rowH(wide) + rowGap(wide)
                    }
                    d.cv.restore()
                }
            }
        }

    /** 画一张小部件：底（经典 / 液态玻璃）画好后交给 body 画内容，画布单位 = 设计稿 dp。free = 空状态（经典样式用白卡底）。 */
    private fun canvasFor(c: Context, wide: Boolean, o: Bundle?, free: Boolean, body: (D, Float, Float) -> Unit): Bitmap {
        usePal(c)
        val dm = c.resources.displayMetrics
        val g = geo(c, o, wide)
        val k = g.k; val lw = g.lw; val lh = g.lh; val scale = g.scale
        val b = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
        b.density = dm.densityDpi
        // 画布单位 = 设计稿 dp（lw × lh），铺满整张位图
        val cv = Canvas(b); cv.scale(scale * k, scale * k)
        val d = D(cv)
        d.classic = CLASSIC
        if (CLASSIC) {
            // 经典样式（改成液态玻璃之前、build-9 的样子）：实心的皮肤底色 + 淡插画，字不加任何阴影
            d.halo = 0
            d.round(0f, 0f, lw, lh, 22f, if (free) FREE_BG else CREAM)
            faintArt(c, cv, lw, lh)
        } else {
            d.halo = haloColor()
            // 液态玻璃底：很清透的一层（按透明度设置），字区再叠一层局部淡底；顶部水光、底边暗边、左上亮右下淡的亮边。不模糊。
            // 跟随皮肤时再铺一层很淡的皮肤插画；跟随壁纸时不铺（插画和壁纸会打架）
            glassCard(cv, lw, lh)
            if (!WALL) faintArt(c, cv, lw, lh)
            // 质感照导航条里选中的水滴（滑块）：spec 1.3、内发光 0.12，和里面的小玻璃块一致
            glassShine(cv, lw, lh, R_CARD, 1.4f, spec = 1.3f, glow = 0.12f)
        }
        body(d, lw, lh)
        return b
    }

    /** 小部件的尺寸：实际 dp、设计稿缩放 k、设计稿单位的宽高、位图像素和 dp → 像素比例。 */
    class Geo(val wDp: Float, val hDp: Float, val k: Float, val lw: Float, val lh: Float, val bw: Int, val bh: Int, val scale: Float)

    fun geo(c: Context, o: Bundle?, wide: Boolean): Geo {
        val dm = c.resources.displayMetrics
        val (wDp, hDp) = sizeDp(c, o, wide)
        // 设计稿：4×2 是 320×156dp，2×2 是 156×156dp；实际尺寸不同就整体等比缩放，空白处往四周铺开
        val k = minOf(wDp / (if (wide) 320f else 156f), hDp / 156f).coerceIn(0.72f, 1.35f)
        // 按真实尺寸 × 屏幕密度画（以前密度封顶 2.75，桌面再放大一次，字就糊了），一像素对一像素
        val (bw, bh, scale) = bitmapSize(wDp, hDp, dm.density, dm.widthPixels.toLong() * dm.heightPixels)
        return Geo(wDp, hDp, k, wDp / k, hDp / k, bw, bh, scale)
    }

    /** 字的细影：经典样式没有；液态玻璃只有一道 0.7px 的细影（深色玻璃暗影、浅色玻璃淡白影，玻璃越透越明显），不加模糊光晕。 */
    private fun haloColor(): Int = if (CLASSIC) 0 else withAlpha(if (DARK) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), WallTint.shadowAlpha(CLEAR, DARK))

    // 皮肤插画的淡淡一层：取插画顶部（主体在右上角）铺满卡片，透明度压低，字在左边不受影响
    private var artId = 0
    private var artBmp: Bitmap? = null
    private fun art(c: Context): Bitmap? {
        val s = Skins.byId(c.getSharedPreferences("look", Context.MODE_PRIVATE).getString("skin", null))
        val id = s.art(DARK)
        if (id == 0) return null
        if (id != artId || artBmp == null) {
            artBmp = try { android.graphics.BitmapFactory.decodeResource(c.resources, id, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } catch (x: Throwable) { null }
            artId = id
        }
        return artBmp
    }
    private fun faintArt(c: Context, cv: Canvas, lw: Float, lh: Float) {
        val a = art(c) ?: return
        val srcH = (a.width * lh / lw).toInt().coerceIn(1, a.height)
        val r = if (CLASSIC) 22f else R_CARD
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, lw, lh), r, r, Path.Direction.CW) }
        cv.save(); cv.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = if (CLASSIC) (if (DARK) 120 else 110) else (if (DARK) 40 else 36) }
        cv.drawBitmap(a, android.graphics.Rect(0, 0, a.width, srcH), RectF(0f, 0f, lw, lh), p)
        cv.restore()
    }

    private const val R_CARD = 26f

    /**
     * 卡片的玻璃底色（build-16 起）：底色（皮肤或壁纸配出来的）按「透明度」设置铺一层，默认 65% 透明 ≈ 26% 实
     * （build-15 是浅色 52%、深色 62%，在照片壁纸上像一块灰板）；字区再叠一层局部淡底（左上实 → 右下渐隐），
     * 玻璃越透这层越实一点，字照样读得清，右边和下面露出壁纸。
     */
    private fun glassCard(cv: Canvas, w: Float, h: Float) {
        val tinted = mix(PAL.i(PAL.bg), PAL.i(PAL.acc), 0.06f)
        // 透明度 90 → 100：底色走向导航条选中水滴的颜色（和小玻璃块同一个）
        val fill = mix(tinted, dropTone(), WallTint.toBar(CLEAR))
        val rect = RectF(0f, 0f, w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(fill, WallTint.fillAlpha(CLEAR, DARK)) }
        cv.drawRoundRect(rect, R_CARD, R_CARD, p)
        val pa = WallTint.plateAlpha(CLEAR)
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, 0f, w * 0.8f, h * 0.35f, intArrayOf(withAlpha(fill, pa), withAlpha(fill, pa * 0.55f), withAlpha(fill, 0f)),
                floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        cv.drawRoundRect(rect, R_CARD, R_CARD, plate)
    }

    /** 小部件实际的尺寸（dp）：API 31+ 用桌面给的确切尺寸列表，按当前横竖屏挑；否则竖屏 minWidth × maxHeight、横屏 maxWidth × minHeight。 */
    private fun sizeDp(c: Context, o: Bundle?, wide: Boolean): Pair<Float, Float> {
        val land = c.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        var wDp = 0f; var hDp = 0f
        if (o != null && android.os.Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val sizes = o.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                val pick = if (land) sizes.maxByOrNull { it.width } else sizes.minByOrNull { it.width }
                if (pick != null) { wDp = pick.width; hDp = pick.height }
            }
        }
        if (o != null && (wDp < 60f || hDp < 60f)) {
            wDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
            hDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        }
        if (wDp < 60f) wDp = if (wide) 320f else 156f
        if (hDp < 60f) hDp = 156f
        return wDp to hDp
    }

    /**
     * 位图尺寸（纯函数，单测）：dp × 密度，一像素对一像素；超过 RemoteViews 的位图内存上限（屏幕像素 × 4 字节 × 1.5）的
     * 一半时才等比缩小（留一半给另一个小部件）。返回 宽、高、dp → 像素的比例。
     */
    fun bitmapSize(wDp: Float, hDp: Float, density: Float, screenPx: Long): Triple<Int, Int, Float> {
        var s = density
        val budget = (screenPx * 1.5 / 2).coerceAtLeast(200_000.0)      // 像素数
        val need = wDp * s * hDp * s
        if (need > budget) s *= kotlin.math.sqrt(budget / need).toFloat()
        return Triple((wDp * s).toInt().coerceAtLeast(1), (hDp * s).toInt().coerceAtLeast(1), s)
    }

    /**
     * 玻璃的光，照 App 底部导航条 / 选中水滴的液态玻璃画（`Glass.kt` 的 liquidGlass + GlassLens 着色器，桌面上没有着色器，用画布近似）：
     *  - 顶部柔光（浅色白 30% / 深色 16% → 0，42% 高度内）、底边暗边（深色黑 22%、浅色 7%）；
     *  - 折射带：边缘 [band] 宽的一圈，越靠边越亮（着色器里的 sheen = bend × (0.06 + 0.22 × 朝光)），上边朝光更亮；
     *  - 内发光：从边往里指数衰减（glow）；
     *  - 亮边：跟着左上的光，左上最亮、中段弱、右下有一道内反光（0.16 + 0.95 × 朝光 + 0.45 × 背光²），深色 × 0.75；
     *  - 外侧一根极淡的暗线。
     * 卡片和小玻璃块都用 spec 1.3 / glow 0.12（= 导航条里选中的水滴）。
     */
    private fun glassShine(cv: Canvas, w: Float, h: Float, r: Float, rim: Float, l: Float = 0f, t: Float = 0f, strong: Float = 1f,
                           spec: Float = 1.3f, glow: Float = 0.12f, band: Float = 0f) {
        val rect = RectF(l, t, l + w, t + h)
        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        val sp = spec * (if (DARK) 0.75f else 1f) * strong
        cv.save(); cv.clipPath(clip)
        val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, t, 0f, t + h * 0.42f,
                withAlpha(0xFFFFFFFF.toInt(), (if (DARK) 0.16f else 0.30f) * strong), 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
        }
        cv.drawRect(rect, hi)
        val lo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, t + h * 0.70f, 0f, t + h,
                0x00000000, withAlpha(if (DARK) 0xFF000000.toInt() else mix(0xFF000000.toInt(), PAL.i(PAL.acc), 0.5f), (if (DARK) 0.22f else 0.07f) * strong),
                android.graphics.Shader.TileMode.CLAMP)
        }
        cv.drawRect(rect, lo)
        // 折射带 + 内发光：一圈圈往里画，越靠边越亮；上边（朝光）亮、下边弱
        val bw = (if (band > 0f) band else minOf(w, h) / 2f * 0.62f).coerceAtMost(minOf(18f, minOf(w, h) / 2f))
        val n = 8; val step = bw / n
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = step + 0.3f }
        for (i in 0 until n) {
            val inset = (i + 0.5f) * step
            val tt = (1f - inset / bw).coerceIn(0f, 1f)
            val bend = 1f - kotlin.math.sqrt(maxOf(1f - tt * tt, 0f))
            val ig = kotlin.math.exp(-inset / maxOf(bw * 0.45f, 1f)) * glow * strong
            val top = (bend * 0.28f * sp + ig).coerceIn(0f, 1f)
            val bot = (bend * 0.06f * sp + ig).coerceIn(0f, 1f)
            ring.shader = android.graphics.LinearGradient(0f, t, 0f, t + h,
                withAlpha(-1, top), withAlpha(-1, bot), android.graphics.Shader.TileMode.CLAMP)
            val rr = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            val rad = maxOf(r - inset, 0f)
            cv.drawRoundRect(rr, rad, rad, ring)
        }
        // 亮边：描边一半在形状里；左上朝光最亮、中段弱、右下内反光
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = rim * 2f
            shader = android.graphics.LinearGradient(l, t, l + w, t + h,
                intArrayOf(withAlpha(-1, (1.11f * sp).coerceAtMost(0.95f)), withAlpha(-1, 0.16f * sp), withAlpha(-1, (0.50f * sp).coerceAtMost(0.6f))),
                floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        cv.drawRoundRect(rect, r, r, rp)
        cv.restore()
        // 外侧极淡的暗线，浅色壁纸上也看得出边
        val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = withAlpha(0xFF000000.toInt(), if (DARK) 0.30f else 0.06f) }
        cv.drawRoundRect(rect, r, r, hair)
    }

    /** 导航条选中水滴的颜色：深色白、浅色白微染主色 10%（MainActivity.BottomBar）。 */
    private fun dropTone(): Int = if (DARK) 0xFFFFFFFF.toInt() else mix(0xFFFFFFFF.toInt(), PAL.i(PAL.acc), 0.10f)
    /** 小玻璃块的底色：透明度 90 → 100 时走向水滴的颜色，拉满时和卡片底一样。 */
    private fun innerTone(col: Int): Int = mix(col, dropTone(), WallTint.toBar(CLEAR))

    private fun withAlpha(c: Int, a: Float): Int = (((c ushr 24) * a.coerceIn(0f, 1f)).toInt() shl 24) or (c and 0x00FFFFFF)
    private fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - k) + ((b shr s) and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private class D(val cv: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply { isElegantTextHeight = false }
        /** 字后面的淡光晕（玻璃很透，壁纸花的时候也读得清）。 */
        var halo = 0
        /** 经典样式：小块是实心的，不画玻璃光。 */
        var classic = false
        /** 小玻璃块（下一节、标签）：半透的底 + 顶部柔光 + 亮边。 */
        fun glass(l: Float, tp: Float, r: Float, bt: Float, rad: Float, col: Int, a: Float) {
            if (classic) { round(l, tp, r, bt, rad - 2f, col); return }
            round(l, tp, r, bt, rad, CourseWidgets.withAlpha(CourseWidgets.innerTone(col), a))
            CourseWidgets.glassShine(cv, r - l, bt - tp, rad, 0.8f, l, tp, 1f, spec = 1.3f, glow = 0.12f)
        }
        fun round(l: Float, tp: Float, r: Float, bt: Float, rad: Float, col: Int) { p.style = Paint.Style.FILL; p.color = col; cv.drawRoundRect(RectF(l, tp, r, bt), rad, rad, p) }
        /** 画一行字，(x, top) 为左上角；align 0 左、1 居中、2 右；放不下就省略号。返回宽度。 */
        fun text(s: String, x: Float, top: Float, size: Float, col: Int, bold: Boolean = false, align: Int = 0, maxW: Float = 9999f): Float {
            t.textSize = size; t.color = col; t.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (halo != 0) t.setShadowLayer(0.7f, 0f, 0.4f, halo) else t.clearShadowLayer()
            val e = TextUtils.ellipsize(s, t, maxW, TextUtils.TruncateAt.END).toString()
            val w = t.measureText(e)
            val fm = t.fontMetrics
            val base = top - fm.ascent - (fm.descent - fm.ascent - size * 1.18f) / 2
            val sx = when (align) { 1 -> x - w / 2; 2 -> x - w; else -> x }
            cv.drawText(e, sx, base, t); return w
        }
        fun width(s: String, size: Float, bold: Boolean = false): Float { t.textSize = size; t.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; return t.measureText(s) }
        fun chip(s: String, x: Float, top: Float, size: Float, fg: Int, bg: Int, bold: Boolean = false, right: Boolean = false): Float {
            val w = width(s, size, bold) + 12f; val h = size + 8f
            val l = if (right) x - w else x
            round(l, top, l + w, top + h, h / 2, if (classic) bg else CourseWidgets.withAlpha(bg, 0.85f)); text(s, l + w / 2, top + 4f - size * 0.09f, size, fg, bold, 1); return w
        }
        fun bar(l: Float, cy: Float, r: Float, h: Float, frac: Float, track: Int, fill: Int) {
            round(l, cy - h / 2, r, cy + h / 2, h / 2, track)
            if (frac > 0f) round(l, cy - h / 2, l + (r - l) * frac.coerceIn(0.04f, 1f), cy + h / 2, h / 2, fill)
        }
        fun clock(x: Float, y: Float, col: Int) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f; p.color = col; p.strokeCap = Paint.Cap.ROUND
            cv.drawCircle(x + 5f, y + 5f, 4.4f, p); cv.drawLine(x + 5f, y + 2.6f, x + 5f, y + 5.2f, p); cv.drawLine(x + 5f, y + 5.2f, x + 6.9f, y + 6.4f, p)
            p.style = Paint.Style.FILL
        }
        fun pin(x: Float, y: Float, col: Int, hole: Int) {
            p.style = Paint.Style.FILL; p.color = col
            cv.drawCircle(x + 4f, y + 4f, 4f, p)
            cv.drawPath(Path().apply { moveTo(x + 0.8f, y + 5.6f); lineTo(x + 7.2f, y + 5.6f); lineTo(x + 4f, y + 10.5f); close() }, p)
            p.color = hole; cv.drawCircle(x + 4f, y + 4f, 1.5f, p)
        }
        fun person(x: Float, y: Float, col: Int) {
            p.style = Paint.Style.FILL; p.color = col
            cv.drawCircle(x + 4.5f, y + 2.6f, 2.5f, p)
            cv.drawArc(RectF(x, y + 5.6f, x + 9f, y + 15f), 180f, 180f, true, p)
        }
        /** 小书本（待办关联的课）。 */
        fun book(x: Float, y: Float, col: Int) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f; p.color = col; p.strokeJoin = Paint.Join.ROUND
            cv.drawPath(Path().apply { moveTo(x + 5f, y + 2f); lineTo(x + 0.6f, y + 0.8f); lineTo(x + 0.6f, y + 8.6f); lineTo(x + 5f, y + 9.8f); lineTo(x + 9.4f, y + 8.6f); lineTo(x + 9.4f, y + 0.8f); close() }, p)
            cv.drawLine(x + 5f, y + 2f, x + 5f, y + 9.8f, p)
            p.style = Paint.Style.FILL
        }
        fun spark(x: Float, y: Float, r: Float, col: Int) {
            p.style = Paint.Style.FILL; p.color = col
            val q = r * 0.28f
            cv.drawPath(Path().apply { moveTo(x, y - r); lineTo(x + q, y - q); lineTo(x + r, y); lineTo(x + q, y + q); lineTo(x, y + r); lineTo(x - q, y + q); lineTo(x - r, y); lineTo(x - q, y - q); close() }, p)
        }
    }

    private fun hm(m: Int) = "%d:%02d".format(m / 60, m % 60)
    private fun header(): String {
        val t = LocalDate.ofEpochDay(Term.todayEpoch())
        return dayLabel(Term.weekday()) + " " + tr("{0}月{1}日", t.monthValue, t.dayOfMonth)
    }
    private fun secText(c: Context, s: Slot): String? {
        val st = Term.secStarts(c); val en = Term.secEnds(c)
        val (a, b) = Term.secSpan(st, en, s.from, s.to)
        if (kotlin.math.abs((st.getOrNull(a - 1) ?: -999) - s.from) > 10) return null   // 自定义时间的课不硬套节次
        return if (a == b) tr("第 {0} 节", a) else tr("{0}-{1}节", a, b)
    }
    private fun leftText(min: Int) = if (min >= 60) tr("{0} 小时 {1} 分", min / 60, min % 60) else tr("{0} 分钟", min)
    private fun untilText(min: Int) = if (min >= 60) tr("{0} 小时后", (min + 30) / 60) else tr("{0} 分钟后", min.coerceAtLeast(1))

    private fun wideCard(c: Context, d: D, w: Float, h: Float, n: Now, all: List<Slot>) {
        val s = n.main!!; val m = Term.nowMin(); val p = 15f; val L = p; val R = w - p
        val week = Term.weekNow(c)
        d.text(header() + (if (week != null) " · " + tr("第 {0} 周", week) else ""), L, 11f, 11f, SUB, maxW = w * 0.55f)
        d.text(tr("今天 {0} 节 · 第 {1} 节", n.total, n.index), R, 11f, 11f, SUB, align = 2)
        val (lightC, deepC) = courseToneArgb(courseColorArgb(s, all), PAL)
        val nextH = if (n.next != null) 46f else 0f
        val top = 33f
        val bottom = h - p - (if (n.next != null) nextH + 10f else 0f)
        d.round(L, top + 2f, L + 4f, bottom - 2f, 2f, deepC)
        val cx = L + 12f
        // 右上角的标签：上课中 / 还有多久，等级
        var cr = R
        cr -= d.chip(if (n.live) tr("上课中") else untilText(s.from - m), cr, top + 1f, 9.5f, if (n.live) ON_ACC else ACC, if (n.live) ACC else ACC_PALE, true, right = true) + 6f
        if (Term.widgetLevel(c)) cr -= d.chip(Slot.LEVELS[s.level.coerceIn(0, 2)], cr, top + 1f, 9.5f, LevelTag.fg(s.level, DARK), LevelTag.bg(s.level, DARK), right = true) + 6f
        d.text(s.name, cx, top - 2f, 17f, INK, true, maxW = cr - cx - 4f)
        // 时间 · 节次  教室  老师
        val f = 11f; var x = cx; val y = top + 25f
        d.clock(x, y + 1.5f, SUB); x += 14f
        val tt = hm(s.from) + "–" + hm(s.to) + (secText(c, s)?.let { " · $it" } ?: "")
        x += d.text(tt, x, y - 1f, f, INK) + 14f
        if (s.room.isNotBlank() && x < R - 40f) { d.pin(x + 1f, y + 1f, SUB, CREAM); x += 14f; x += d.text(s.room, x, y - 1f, f, INK, maxW = R - x - (if (s.teacher.isNotBlank()) 60f else 0f)) + 14f }
        if (s.teacher.isNotBlank() && x < R - 30f) { d.person(x, y + 1f, SUB); x += 13f; d.text(s.teacher, x, y - 1f, f, INK, maxW = R - x) }
        // 进度 / 还有多久开始
        val py = minOf(top + 49f, bottom - 3f)
        val lbl = if (n.live) tr("还剩 {0}", leftText(s.to - m)) else tr("{0} 开始", hm(s.from))
        val bl = R - d.width(lbl, 9.5f) - 8f
        val frac = if (n.live) (m - s.from).toFloat() / (s.to - s.from) else 0f
        d.bar(cx, py, bl, 5f, frac, lightC, deepC)
        d.text(lbl, R, py - 7f, 9.5f, ACC, align = 2)
        // 下一节
        n.next?.let { nx ->
            val ny = h - p - nextH
            d.glass(L, ny, R, h - p, 14f, NEXT_BG, WallTint.innerAlpha(CLEAR, DARK))
            val q = L + 11f
            val head = tr("下一节 {0}", hm(nx.from)) + (secText(c, nx)?.let { " · $it" } ?: "")
            d.text(head, q, ny + 6f, 10f, NEXT_FG, true, maxW = w * 0.55f)
            d.text(untilText(nx.from - m), R - 11f, ny + 6f, 10f, NEXT_FG, align = 2)
            val info = listOf(nx.room, nx.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            val iw = if (info.isEmpty()) 0f else minOf(d.width(info, 10.5f), (R - q) * 0.45f)
            d.text(nx.name, q, ny + 22f, 12.5f, INK, true, maxW = R - 11f - q - iw - 10f)
            if (info.isNotEmpty()) d.text(info, R - 11f, ny + 24f, 10.5f, SUB, align = 2, maxW = iw)
        }
    }

    private fun smallCard(c: Context, d: D, w: Float, h: Float, n: Now, all: List<Slot>) {
        val s = n.main!!; val m = Term.nowMin(); val p = 13f; val L = p; val R = w - p
        val (lightC, deepC) = courseToneArgb(courseColorArgb(s, all), PAL)
        d.chip(if (n.live) tr("上课中") else untilText(s.from - m), L, p - 2f, 9f, if (n.live) ON_ACC else ACC, if (n.live) ACC else ACC_PALE, true)
        if (Term.widgetLevel(c)) d.chip(Slot.LEVELS[s.level.coerceIn(0, 2)], R, p - 2f, 9f, LevelTag.fg(s.level, DARK), LevelTag.bg(s.level, DARK), right = true)
        val top = 37f
        d.round(L, top + 2f, L + 4f, top + 46f, 2f, deepC)
        val cx = L + 10f
        d.text(s.name, cx, top - 3f, 15f, INK, true, maxW = R - cx)
        d.text(hm(s.from) + "–" + hm(s.to), cx, top + 17f, 10.5f, INK, maxW = R - cx)
        val info = listOf(s.room, s.teacher).filter { it.isNotBlank() }.joinToString(" · ")
        if (info.isNotEmpty()) d.text(info, cx, top + 31f, 10.5f, SUB, maxW = R - cx)
        val py = top + 56f
        if (n.live) {
            val lbl = tr("还剩 {0}", leftText(s.to - m))
            val bl = R - d.width(lbl, 9f) - 6f
            d.bar(L, py, bl, 4f, (m - s.from).toFloat() / (s.to - s.from), lightC, deepC)
            d.text(lbl, R, py - 6.5f, 9f, ACC, align = 2)
        } else d.text(tr("{0} 开始", hm(s.from)), L, py - 6.5f, 9.5f, ACC)
        val ny = py + 9f
        val nb = h - p
        if (nb - ny < 26f) return
        d.glass(L, ny, R, nb, 13f, NEXT_BG, WallTint.innerAlpha(CLEAR, DARK))
        val nx = n.next
        if (nx == null) { d.text(tr("这是今天最后一节"), (L + R) / 2, ny + (nb - ny) / 2 - 7f, 10f, NEXT_FG, true, 1); return }
        d.text(tr("下一节 {0}", hm(nx.from)) + (if (nx.room.isNotBlank()) " · " + nx.room else ""), L + 9f, ny + 5f, 9f, NEXT_FG, true, maxW = R - L - 18f)
        d.text(nx.name, L + 9f, ny + 19f, 11.5f, INK, true, maxW = R - L - 18f)
    }

    private fun freeCard(d: D, w: Float, h: Float, wide: Boolean, done: Boolean) {
        val day = Term.todayEpoch().toInt()
        val kao = if (done) KAO_DONE[Math.floorMod(day, KAO_DONE.size)] else KAO[Math.floorMod(day, KAO.size)]
        val title = if (done) tr("今天的课上完啦") else tr("今天没课啦")
        val pink = 0xFFFF8AAA.toInt(); val yel = 0xFFFFCE5C.toInt(); val mint = 0xFF84D6BE.toInt(); val lav = 0xFFB2A0F0.toInt()
        val cx = w / 2
        if (wide) {
            d.text(header(), 15f, 12f, 11f, SUB)
            d.text(kao, cx, h * 0.40f - 14f, 22f, pink, true, 1, w - 40f)
            d.text(title, cx, h * 0.65f - 12f, 18f, INK, true, 1)
            d.text(if (done) tr("辛苦啦，好好放松一下 ♡") else tr("去做点喜欢的事吧 ♡"), cx, h * 0.82f - 7f, 11f, SUB, align = 1)
            for (s in listOf(floatArrayOf(.14f, .52f, 8f), floatArrayOf(.20f, .78f, 5f), floatArrayOf(.84f, .28f, 7f), floatArrayOf(.88f, .64f, 9f), floatArrayOf(.78f, .86f, 5f), floatArrayOf(.27f, .27f, 5f)).withIndex())
                d.spark(w * s.value[0], h * s.value[1], s.value[2], listOf(yel, mint, lav, pink, yel, pink)[s.index])
        } else {
            d.text(kao, cx, h * 0.36f - 11f, 17f, pink, true, 1, w - 24f)
            d.text(title, cx, h * 0.60f - 10f, 15f, INK, true, 1, w - 20f)
            d.text(if (done) tr("辛苦啦 ♡") else tr("休息一下吧 ♡"), cx, h * 0.77f - 6f, 10f, SUB, align = 1)
            for (s in listOf(floatArrayOf(.15f, .16f, 6f), floatArrayOf(.85f, .19f, 5f), floatArrayOf(.13f, .87f, 4f), floatArrayOf(.87f, .85f, 7f)).withIndex())
                d.spark(w * s.value[0], h * s.value[1], s.value[2], listOf(yel, lav, mint, pink)[s.index])
        }
    }
}

/** 小部件样式：液态玻璃（默认）/ 经典（改版前的实心皮肤底）。存在 SharedPreferences `widget` 的 `style`。 */
object WidgetStyle {
    const val GLASS = "glass"
    const val CLASSIC = "classic"
    val ALL = listOf(GLASS to "液态玻璃", CLASSIC to "经典")
    private fun p(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)
    private fun str(c: Context, k: String): String? = try { p(c).getString(k, null) } catch (x: ClassCastException) { null }
    fun get(c: Context): String = norm(str(c, "style"))
    fun norm(v: String?): String = if (v == CLASSIC) CLASSIC else GLASS
    fun classic(c: Context) = get(c) == CLASSIC
    /** 换样式：存下来并立刻重画所有小部件。 */
    fun set(c: Context, v: String) { p(c).edit().putString("style", norm(v)).apply(); ver.value++; CourseWidgets.safeUpdate(c) }

    /** 液态玻璃的颜色来源：跟随壁纸（默认）/ 跟随皮肤。`tint` = wall | skin。经典样式不受影响，始终跟皮肤。 */
    const val WALL = "wall"
    const val SKIN = "skin"
    val TINTS = listOf(WALL to "跟随壁纸", SKIN to "跟随皮肤")
    fun tint(c: Context): String = if (str(c, "tint") == SKIN) SKIN else WALL
    fun followWall(c: Context) = tint(c) == WALL
    fun setTint(c: Context, v: String) { p(c).edit().putString("tint", if (v == SKIN) SKIN else WALL).apply(); ver.value++; CourseWidgets.safeUpdate(c) }

    /** 玻璃透明度 0..100（%），默认 65（build-21 起上限 100，拉满 = 导航条那么透）。 */
    const val CLEAR_DEFAULT = 65
    fun clear(c: Context): Int = (try { p(c).getInt("clear", CLEAR_DEFAULT) } catch (x: ClassCastException) { CLEAR_DEFAULT }).coerceIn(0, WallTint.MAX)
    /** 改透明度：马上存、预览马上变；redraw = false 时先不重画桌面（拖动滑块中途），松手后再重画。 */
    fun setClear(c: Context, v: Int, redraw: Boolean = true) { p(c).edit().putInt("clear", v.coerceIn(0, WallTint.MAX)).apply(); ver.value++; if (redraw) CourseWidgets.safeUpdate(c) }
    val ver = androidx.compose.runtime.mutableStateOf(0)
}

/** 到点重画小部件；改时间、改时区、更新安装后也重画。 */
class WidgetTick : android.content.BroadcastReceiver() {
    companion object { const val ACTION = "com.zhishi.timetable.WIDGET_TICK" }
    // 广播里抛异常会直接杀掉整个进程（App 开着时就是「闪退」），所以这里必须兜住
    override fun onReceive(c: Context, i: Intent) { try { CourseWidgets.update(c, force = i.action != ACTION) } catch (x: Throwable) { android.util.Log.w("Zhishi", "小部件重画失败", x) } }
}

class CourseWidget4 : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = CourseWidgets.safeUpdate(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) = CourseWidgets.safeUpdate(c)
}

class CourseWidget2 : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = CourseWidgets.safeUpdate(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) = CourseWidgets.safeUpdate(c)
}

/** 待办小部件 4×2 / 2×2：和课表小部件同一张底和刷新（CourseWidgets.update 一起重画），里面是可以滑动的全部待办（TodoWidgetService）。 */
class TodoWidget4 : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = CourseWidgets.safeUpdate(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) = CourseWidgets.safeUpdate(c)
}

/**
 * 待办小部件里可以上下滑动的列表：每行是一张画好的小图（和小部件同一套画法），点一行打开待办页。
 * 用 RemoteViewsService + ListView，Android 8（minSdk 26）到最新版本、各家桌面都支持。
 */
class TodoWidgetService : RemoteViewsService() {
    companion object { const val WIDE = "wide" }
    override fun onGetViewFactory(i: Intent): RemoteViewsFactory =
        TodoRows(applicationContext, i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID), i.getBooleanExtra(WIDE, true))
}

class TodoRows(private val c: Context, private val id: Int, private val wide: Boolean) : RemoteViewsService.RemoteViewsFactory {
    private var tp: CourseWidgets.TodoNow? = null
    private var all: List<Slot> = emptyList()
    private var opts: Bundle? = null
    override fun onCreate() {}
    /** 桌面收到 notifyAppWidgetViewDataChanged 后在这里重新读待办（binder 线程，可以做重活）。 */
    override fun onDataSetChanged() {
        try {
            tp = CourseWidgets.todoPlan(TodoStore.all(c), System.currentTimeMillis())
            all = Term.desired(c).filter { it.kind == "course" }
            opts = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) null else AppWidgetManager.getInstance(c)?.getAppWidgetOptions(id)
        } catch (x: Throwable) { tp = null; android.util.Log.w("Zhishi", "待办列表读不出来：${x.message}") }
    }
    override fun getCount(): Int = tp?.list?.size ?: 0
    override fun getViewAt(pos: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_todo_row)
        val p = tp ?: return v
        val t = p.list.getOrNull(pos) ?: return v
        try { v.setImageViewBitmap(R.id.row, CourseWidgets.todoRowBitmap(c, wide, opts, t, p.now, all, pos == p.list.size - 1)) }
        catch (x: Throwable) { android.util.Log.w("Zhishi", "待办列表这一行画不出来：${x.message}") }
        v.setOnClickFillInIntent(R.id.row_root, CourseWidgets.rowFillIn())
        return v
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(pos: Int): Long = pos.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}

class TodoWidget2 : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = CourseWidgets.safeUpdate(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) = CourseWidgets.safeUpdate(c)
}
