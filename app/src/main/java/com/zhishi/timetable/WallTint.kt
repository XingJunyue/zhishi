package com.zhishi.timetable

import android.content.Context

/**
 * 小部件「跟随壁纸」：读系统给出的壁纸主色（WallpaperManager.getWallpaperColors，Android 8.1+；
 * Android 12+ 再加 Material You 的强调色），自动决定玻璃是深是浅、带什么色调、字用深色还是浅色。
 *
 * 说明：桌面小部件拿不到身后壁纸的像素（大多数桌面也不给），所以做不了真正的折射，只能「按壁纸配色」。
 * 取色逻辑是纯函数（[Src] → [Pal]），有单测 WallTintTest。
 */
object WallTint {
    /** 壁纸给出的颜色。darkText = 系统提示「壁纸上适合深色字」（API 31+ 才有，没有就按亮度猜）；accent* = Material You 强调色。 */
    data class Src(
        val primary: Int, val secondary: Int? = null, val tertiary: Int? = null,
        val darkText: Boolean? = null, val accentLight: Int? = null, val accentDark: Int? = null,
    )

    // ---------- 纯函数 ----------
    fun lum(c: Int): Float {
        fun ch(v: Int): Float { val s = v / 255f; return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
        return 0.2126f * ch((c shr 16) and 0xFF) + 0.7152f * ch((c shr 8) and 0xFF) + 0.0722f * ch(c and 0xFF)
    }

    /** 壁纸整体偏暗吗：优先用系统的提示，否则按主色 60% + 次色 25% + 第三色 15% 的亮度。 */
    fun isDark(s: Src): Boolean {
        s.darkText?.let { return !it }
        var w = 0.6f; var l = lum(s.primary) * 0.6f
        s.secondary?.let { l += lum(it) * 0.25f; w += 0.25f }
        s.tertiary?.let { l += lum(it) * 0.15f; w += 0.15f }
        return l / w < 0.20f
    }

    /** RGB → HSL（h 0..360，s、l 0..1）。 */
    fun hsl(c: Int): FloatArray {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val l = (mx + mn) / 2f
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        var h = when (mx) { r -> (g - b) / d + (if (g < b) 6f else 0f); g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
        h *= 60f
        return floatArrayOf(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val ss = s.coerceIn(0f, 1f); val ll = l.coerceIn(0f, 1f)
        val q = if (ll < 0.5f) ll * (1 + ss) else ll + ss - ll * ss
        val p = 2 * ll - q
        fun hue(t0: Float): Float { var t = t0; if (t < 0) t += 1f; if (t > 1) t -= 1f
            return when { t < 1f / 6 -> p + (q - p) * 6 * t; t < 0.5f -> q; t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6; else -> p } }
        val hh = (((h % 360f) + 360f) % 360f) / 360f
        val r = (hue(hh + 1f / 3) * 255 + 0.5f).toInt(); val g = (hue(hh) * 255 + 0.5f).toInt(); val b = (hue(hh - 1f / 3) * 255 + 0.5f).toInt()
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    private fun L(v: Int): Long = v.toLong() and 0xFFFFFFFFL

    /**
     * 按壁纸配出一份小部件用的 [Pal]：深浅跟壁纸，底色 / 字带壁纸主色的色调；强调色优先 Material You，
     * 否则取最鲜艳的那个壁纸色；壁纸几乎是黑白灰时，强调色用皮肤 [skin] 的。
     */
    fun pal(s: Src, skin: Skin): Pal {
        val dark = isDark(s)
        val base = if (dark) skin.dark else skin.light
        val cols = listOfNotNull(s.primary, s.secondary, s.tertiary)
        val p = hsl(s.primary)
        val h = p[0]; val ts = minOf(p[1], 0.30f)               // 底色只带一点色调
        val vivid = cols.maxByOrNull { hsl(it)[1] * (1f - kotlin.math.abs(hsl(it)[2] - 0.5f)) }!!
        val vh = hsl(vivid)
        val grey = vh[1] < 0.10f
        val accHue: Float; val accSat: Float
        if (grey) { val a = hsl(base.i(base.acc)); accHue = a[0]; accSat = a[1].coerceIn(0.30f, 0.60f) }
        else { accHue = vh[0]; accSat = vh[1].coerceIn(0.35f, 0.62f) }
        val second = cols.firstOrNull { it != vivid && hsl(it)[1] >= 0.10f }?.let { hsl(it)[0] } ?: (accHue + 35f)
        val mu = if (dark) s.accentDark else s.accentLight
        return if (dark) Pal(
            bg = L(fromHsl(h, ts * 0.8f, 0.11f)), card = L(fromHsl(h, ts * 0.7f, 0.16f)),
            ink = L(fromHsl(h, 0.12f, 0.96f)), soft = L(fromHsl(h, 0.10f, 0.80f)), faint = L(fromHsl(h, 0.08f, 0.60f)),
            acc = L(mu ?: fromHsl(accHue, accSat, 0.74f)), deep = L(fromHsl(accHue, accSat, 0.82f)), pale = L(fromHsl(accHue, accSat * 0.6f, 0.24f)),
            track = L(fromHsl(h, 0.10f, 0.26f)), sand = L(fromHsl(h, 0.10f, 0.20f)), onAcc = L(fromHsl(accHue, 0.45f, 0.12f)),
            sage = L(fromHsl(second, 0.40f, 0.70f)), sagePale = L(fromHsl(second, 0.25f, 0.22f)), sageInk = L(fromHsl(second, 0.45f, 0.84f)), dark = true,
        ) else Pal(
            bg = L(fromHsl(h, ts, 0.95f)), card = L(fromHsl(h, ts * 0.6f, 0.985f)),
            ink = L(fromHsl(h, 0.20f, 0.11f)), soft = L(fromHsl(h, 0.14f, 0.30f)), faint = L(fromHsl(h, 0.10f, 0.52f)),
            acc = L(mu ?: fromHsl(accHue, accSat, 0.40f)), deep = L(fromHsl(accHue, accSat, 0.32f)), pale = L(fromHsl(accHue, accSat * 0.8f, 0.90f)),
            track = L(fromHsl(h, 0.14f, 0.87f)), sand = L(fromHsl(h, 0.14f, 0.92f)), onAcc = 0xFFFFFFFFL,
            sage = L(fromHsl(second, 0.40f, 0.45f)), sagePale = L(fromHsl(second, 0.35f, 0.90f)), sageInk = L(fromHsl(second, 0.45f, 0.28f)), dark = false,
        )
    }

    /**
     * 透明度 0..100%（build-21 起上限 100，原来 90）。0–90 和原来完全一样：0% ≈ 75% 实，默认 65% ≈ 26% 实，90% ≈ 7.5% 实。
     * 90 → 100 往 App 底部导航条里选中的玻璃水滴（滑块）靠：拉满时卡片底和里面的小玻璃块都 = 水滴的底
     * （深色白 10%、浅色白微染主色 30%，见 MainActivity.BottomBar），字区淡底完全去掉。用户 2026-11-22 定的：卡片和小块保持一致，都以滑块为基准。
     */
    const val MAX = 100
    /** 90 → 100 这一段走到哪了（0..1）。 */
    fun toBar(clear: Int): Float = ((clear.coerceIn(0, MAX) - 90) / 10f).coerceIn(0f, 1f)
    fun barFill(dark: Boolean): Float = if (dark) 0.10f else 0.30f
    fun fillAlpha(clear: Int, dark: Boolean = false): Float {
        val c = clear.coerceIn(0, MAX)
        val base = 0.75f * (1f - minOf(c, 90) / 100f)
        return base + (barFill(dark) - base) * toBar(c)
    }
    /** 字区那层局部淡底：0–90 玻璃越透这层越实一点，保证字读得清（左 → 右渐隐）；90 → 100 淡出到 0（导航条没有这层）。 */
    fun plateAlpha(clear: Int): Float = (0.08f + 0.30f * minOf(clear.coerceIn(0, MAX), 90) / 100f) * (1f - toBar(clear))
    /** 字的细影强度（0..1）：玻璃越透越明显，但始终是 0.7px 的细影，不是模糊光晕。 */
    fun shadowAlpha(clear: Int, dark: Boolean): Float =
        ((if (dark) 0.40f else 0.30f) + (if (dark) 0.40f else 0.45f) * clear.coerceIn(0, MAX) / 100f).coerceAtMost(1f)
    /** 小玻璃块（下一节）的不透明度：0–90 原样（深 55%、浅 50%），90 → 100 走到和卡片底一样的水滴 10% / 30%。 */
    fun innerAlpha(clear: Int, dark: Boolean): Float {
        val a = if (dark) 0.55f else 0.50f
        return a + (barFill(dark) - a) * toBar(clear)
    }

    // ---------- 读系统 ----------
    /** 现在的壁纸颜色；拿不到（Android 8.0、动态壁纸不给色、出错）就是 null，退回跟随皮肤。 */
    fun read(c: Context): Src? {
        if (android.os.Build.VERSION.SDK_INT < 27) return null
        return try {
            val wm = android.app.WallpaperManager.getInstance(c) ?: return null
            val w = wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM) ?: return null
            var darkText: Boolean? = null; var al: Int? = null; var ad: Int? = null
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                darkText = (w.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                al = c.getColor(android.R.color.system_accent1_600); ad = c.getColor(android.R.color.system_accent1_200)
            }
            Src(w.primaryColor.toArgb(), w.secondaryColor?.toArgb(), w.tertiaryColor?.toArgb(), darkText, al, ad)
        } catch (x: Throwable) { null }
    }

    /** 壁纸颜色变了：重画小部件（只在 App 进程活着时能收到；每次小部件重画也会重新读）。 */
    @Volatile private var listening = false
    val ver = androidx.compose.runtime.mutableStateOf(0)
    fun listen(c: Context) {
        if (listening || android.os.Build.VERSION.SDK_INT < 27) return
        try {
            val app = c.applicationContext
            android.app.WallpaperManager.getInstance(app)?.addOnColorsChangedListener({ _, which ->
                if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0) { ver.value++; CourseWidgets.refresh(app) }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            listening = true
        } catch (x: Throwable) {}
    }
}
