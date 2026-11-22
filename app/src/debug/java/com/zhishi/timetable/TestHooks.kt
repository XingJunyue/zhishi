package com.zhishi.timetable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * 只在调试版里有：自动化测试用。
 *  --ez seed true   跳过引导，放几节示例课程
 *  --es wtint wall|skin / --ei wclear 0..90   小部件玻璃颜色来源 / 透明度
 *  --es wallpaper dark|light   把模拟器壁纸换成一张深色 / 浅色的渐变图（只有调试版有 SET_WALLPAPER）
 *  --ez notodos true 清空待办（截待办空状态；再 --ez seed true 会放回示例待办）
 *  --es skin <id> / --ei mode 0|1|2   换皮肤 / 深浅色（截图用）
 *  --es ocr 文件名  识别 app 专属目录里的图片，把结果打到 logcat（标签 ZhishiTest）
 */
object TestHooks {
    fun run(c: Context, i: Intent?) {
        i ?: return
        i.getStringExtra("setlang")?.let { Log.i("ZhishiTest", "lang -> $it"); Tr.set(c, it); return }
        if (i.getBooleanExtra("notodos", false)) TodoStore.save(c, emptyList())
        if (i.getBooleanExtra("seed", false)) seed(c)
        i.getStringExtra("skin")?.let { Skins.setSkin(c, Skins.byId(it)) }
        if (i.hasExtra("mode")) Skins.setMode(c, i.getIntExtra("mode", 0))
        i.getStringExtra("wtint")?.let { WidgetStyle.setTint(c, it) }
        if (i.hasExtra("wclear")) WidgetStyle.setClear(c, i.getIntExtra("wclear", WidgetStyle.CLEAR_DEFAULT))
        i.getStringExtra("wallpaper")?.let { wallpaper(c, it == "dark") }
        // 教程：--ez notour true 全部当看过（冒烟测试不被挡住）；--es tour first|new 直接弹出来截图
        if (i.getBooleanExtra("notour", false)) Tour.skipAll(c)
        i.getStringExtra("tour")?.let { Tour.show.value = it }
        // 课表网页导入：--es jwkind qz --es jwurl file:///android_asset/jwtest.html --ez jwauto true
        i.getStringExtra("jwkind")?.let { TimetableTest.webKind = it; TimetableTest.webUrl = i.getStringExtra("jwurl"); TimetableTest.autoImport = i.getBooleanExtra("jwauto", false) }
        // 教务导入页搜索框预填：--es jwq 大连
        i.getStringExtra("jwq")?.let { TimetableTest.query = it; TimetableTest.webKind = null; TimetableTest.webUrl = null }
        i.getStringExtra("ocr")?.let { name ->
            val f = File(c.getExternalFilesDir(null), name)
            Log.i("ZhishiTest", "OCR start ${f.absolutePath} exists=${f.exists()}")
            Ocr.read(c, Uri.fromFile(f)) { r, e ->
                Ocr.last.forEach { Log.i("ZhishiTest", "OCR LINE ${it.t} @${it.r.left},${it.r.top},${it.r.right},${it.r.bottom}") }
                if (r == null) Log.i("ZhishiTest", "OCR FAIL $e")
                else { Log.i("ZhishiTest", "OCR OK ${r.size}"); r.forEach { Log.i("ZhishiTest", "OCR ROW ${it.days.first()}|${it.name}|${it.from}|${it.to}") } }
            }
        }
    }

    private fun seed(c: Context) {
        Tour.skipAll(c)
        if (Term.desired(c).isEmpty()) Term.setDesired(c, listOf(
            Slot("t1", "大学英语(一)", setOf(1), 480, 580, 1, room = "机401-语音室", teacher = "宋岩"),
            Slot("t2", "高等数学①(一)", setOf(1), 600, 700, 2, room = "教210", teacher = "刘思妤"),
            Slot("t3", "高等数学①(一)", setOf(3), 480, 580, 2, room = "教103", teacher = "刘思妤"),
            Slot("t4", "工程制图基础", setOf(2), 600, 700, 1, room = "大成111", teacher = "赵群超"),
            Slot("t5", "无机化学(1)", setOf(1), 840, 940, 1, room = "逸102", teacher = "徐君莉,谢宏伟"),
            Slot("t6", "无机化学(1)", setOf(4), 840, 940, 1, room = "逸310", teacher = "徐君莉,谢宏伟", weeks = (1..16).toSet()),
            Slot("t7", "大学生心理与健康教育(一)", setOf(2), 960, 1060, 0, room = "大成305", teacher = "刘洋", weeks = (1..8).toSet()),
            Slot("t8", "习近平新时代中国特色社会主义思想概论", setOf(1), 1140, 1240, 1, room = "教305", teacher = "王老师"),
            Slot("t9", "习近平新时代中国特色社会主义思想概论", setOf(5), 960, 1060, 1, room = "教305", weeks = (1..11).toSet()),
            Slot("t10", "大学物理实验", setOf(4), 600, 700, 1, room = "实验楼", weeks = setOf(1, 3, 5, 7, 9, 11, 13, 15)),
            Slot("t11", "形势与政策", setOf(5), 1140, 1240, 0, room = "大成101", weeks = setOf(2, 4, 6, 8, 10, 12)),
            Slot("t12", "体育(一)", setOf(3), 840, 940, 1, room = "体育馆", teacher = "孙老师"),
            // 今天正在上的一节（冒烟截图里能看到「正在上」和小部件的上课中）
            Slot("t13", "线性代数", setOf(Term.weekday()), (Term.nowMin() - 20).coerceIn(0, 1380), (Term.nowMin() + 40).coerceIn(60, 1439), 2, room = "教201", teacher = "周老师")))
        if (Term.termStart(c) < 0) Term.setWeekNow(c, 12)
        // 示例待办：今天、明天、下周、没日期、一条已完成（冒烟截图待办页、日历页）
        if (TodoStore.all(c).isEmpty()) {
            val t = Term.todayEpoch(); val now = System.currentTimeMillis()
            TodoStore.save(c, listOf(
                Todo("d1", "交高数作业 第三章", "习题 3.1–3.4", Todos.at(t, 22 * 60), true, "高等数学①(一)", created = now - 5000),
                Todo("d2", "无机化学实验报告", "", Todos.at(t + 1, 18 * 60), false, "无机化学(1)", created = now - 4000),
                Todo("d3", "大学英语 Unit 5 单词测验", "", Todos.at(t + 4, 8 * 60), false, "大学英语(一)", created = now - 3000),
                Todo("d4", "借图书馆的工程制图参考书", "", -1L, false, "", created = now - 2000),
                Todo("d5", "体育课交体测表", "", Todos.at(t - 1, 12 * 60), false, "体育(一)", done = true, doneAt = now - 1000, created = now - 6000),
            ))
        }
        // 皮肤：--es skin qingci --ei mode 2
        Log.i("ZhishiTest", "seeded")
    }

    /** 一张像照片的渐变壁纸：深色 = 夜里的深蓝灰 + 一团暖色光；浅色 = 米白到浅蓝。 */
    private fun wallpaper(c: Context, dark: Boolean) {
        try {
            val w = 1080; val h = 2400
            val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val (a, z, blob) = if (dark) Triple(0xFF0B0D14.toInt(), 0xFF1C2232.toInt(), 0xFF7A5442.toInt()) else Triple(0xFFF4E9D8.toInt(), 0xFFCFE3F0.toInt(), 0xFFF2C6B4.toInt())
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), a, z, android.graphics.Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = android.graphics.RadialGradient(w * 0.5f, h * 0.42f, w * 0.55f, blob, blob and 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
            cv.drawCircle(w * 0.5f, h * 0.42f, w * 0.55f, p)
            android.app.WallpaperManager.getInstance(c).setBitmap(b, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
            Log.i("ZhishiTest", "wallpaper ${if (dark) "dark" else "light"} -> ${WallTint.read(c)}")
        } catch (x: Throwable) { Log.w("ZhishiTest", "wallpaper failed: $x") }
    }
}
