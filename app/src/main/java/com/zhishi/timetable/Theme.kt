package com.zhishi.timetable

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 皮肤：每套一份浅色、一份深色配色（ARGB）。App 界面和桌面小部件共用同一份，换皮肤后小部件立刻重画。
 * 名字用中文当键，六种语言在 assets/i18n 下的 json 里。
 */
class Pal(
    val bg: Long, val card: Long, val ink: Long, val soft: Long, val faint: Long,
    val acc: Long, val deep: Long, val pale: Long, val track: Long, val sand: Long,
    val onAcc: Long, val sage: Long, val sagePale: Long, val sageInk: Long, val dark: Boolean,
) {
    fun i(v: Long) = v.toInt()
}

/**
 * 一套皮肤：名字、一句话简介（poem）、浅色 / 深色配色，和对应的整屏插画背景（res/drawable-nodpi/skin_<id>_<light|dark>.webp）
 * 及圆形缩略图（skin_<id>_<light|dark>_thumb.webp）。
 */
class Skin(val id: String, val name: String, val poem: String, val light: Pal, val dark: Pal) {
    fun art(dark: Boolean): Int = ART["${id}_${if (dark) "dark" else "light"}"] ?: 0
    fun thumb(dark: Boolean): Int = ART["${id}_${if (dark) "dark" else "light"}_thumb"] ?: 0
    companion object {
        /** 资源 id 表：用 R 常量，混淆和资源压缩都能看到引用。 */
        private val ART: Map<String, Int> by lazy { mapOf(
            "chunyu_light" to R.drawable.skin_chunyu_light, "chunyu_dark" to R.drawable.skin_chunyu_dark,
            "xuanzhi_light" to R.drawable.skin_xuanzhi_light, "xuanzhi_dark" to R.drawable.skin_xuanzhi_dark,
            "qingci_light" to R.drawable.skin_qingci_light, "qingci_dark" to R.drawable.skin_qingci_dark,
            "mushan_light" to R.drawable.skin_mushan_light, "mushan_dark" to R.drawable.skin_mushan_dark,
            "yehang_light" to R.drawable.skin_yehang_light, "yehang_dark" to R.drawable.skin_yehang_dark,
            "ying_light" to R.drawable.skin_ying_light, "ying_dark" to R.drawable.skin_ying_dark,
            "chunyu_light_thumb" to R.drawable.skin_chunyu_light_thumb, "chunyu_dark_thumb" to R.drawable.skin_chunyu_dark_thumb,
            "xuanzhi_light_thumb" to R.drawable.skin_xuanzhi_light_thumb, "xuanzhi_dark_thumb" to R.drawable.skin_xuanzhi_dark_thumb,
            "qingci_light_thumb" to R.drawable.skin_qingci_light_thumb, "qingci_dark_thumb" to R.drawable.skin_qingci_dark_thumb,
            "mushan_light_thumb" to R.drawable.skin_mushan_light_thumb, "mushan_dark_thumb" to R.drawable.skin_mushan_dark_thumb,
            "yehang_light_thumb" to R.drawable.skin_yehang_light_thumb, "yehang_dark_thumb" to R.drawable.skin_yehang_dark_thumb,
            "ying_light_thumb" to R.drawable.skin_ying_light_thumb, "ying_dark_thumb" to R.drawable.skin_ying_dark_thumb,
        ) }
    }
}

private val SAGE_L = Triple(0xFF6E9A55, 0xFFE8F1DF, 0xFF4F7A36)
private val SAGE_D = Triple(0xFF9CC47A, 0xFF22301A, 0xFFB5D99A)

private fun lp(bg: Long, card: Long, ink: Long, soft: Long, faint: Long, acc: Long, deep: Long, pale: Long, track: Long, sand: Long, onAcc: Long = 0xFFFFFFFF) =
    Pal(bg, card, ink, soft, faint, acc, deep, pale, track, sand, onAcc, SAGE_L.first, SAGE_L.second, SAGE_L.third, false)
private fun dp(bg: Long, card: Long, ink: Long, soft: Long, faint: Long, acc: Long, deep: Long, pale: Long, track: Long, sand: Long, onAcc: Long) =
    Pal(bg, card, ink, soft, faint, acc, deep, pale, track, sand, onAcc, SAGE_D.first, SAGE_D.second, SAGE_D.third, true)

object Skins {
    val ALL = listOf(
        Skin("chunyu", "春雨", "清新如春，细雨润心",
            lp(0xFFF1F6F1, 0xFFFFFFFE, 0xFF1E2B25, 0xFF56685E, 0xFF8C9A92, 0xFF3F8A62, 0xFF2C6B4A, 0xFFDCEEE1, 0xFFE0E9E2, 0xFFEAF1EB),
            dp(0xFF0F1813, 0xFF17221C, 0xFFE2ECE5, 0xFFA2B4A8, 0xFF73857A, 0xFF8CCBA4, 0xFFA9DDBC, 0xFF1C3427, 0xFF25312A, 0xFF1D2922, 0xFF0D2618)),
        Skin("xuanzhi", "宣纸", "书卷墨香，文心相伴",
            lp(0xFFF6F1E7, 0xFFFFFCF6, 0xFF2B2622, 0xFF6E645B, 0xFF9E9488, 0xFFB0503A, 0xFF8C3C2A, 0xFFF5E2D8, 0xFFEBE3D5, 0xFFF0E9DC),
            dp(0xFF191613, 0xFF221F1B, 0xFFEDE6DA, 0xFFB8AE9F, 0xFF857B6E, 0xFFE38E74, 0xFFF0AA94, 0xFF3A2620, 0xFF322C26, 0xFF29241F, 0xFF2A120B)),
        Skin("qingci", "青瓷", "古韵流光，淡雅宁静",
            lp(0xFFEDF4F1, 0xFFFBFEFC, 0xFF1D2E2A, 0xFF557069, 0xFF89A099, 0xFF4A9180, 0xFF337363, 0xFFD9EDE6, 0xFFDAE7E2, 0xFFE5F0EB),
            dp(0xFF0E1715, 0xFF16221F, 0xFFDDEDE7, 0xFF9DBAB1, 0xFF6B8981, 0xFF8ED3C0, 0xFFADE3D4, 0xFF1A3730, 0xFF22342F, 0xFF1A2A26, 0xFF0B2620)),
        Skin("mushan", "暮山", "暮色山河，静谧沉稳",
            lp(0xFFEFEDF7, 0xFFFFFEFF, 0xFF262537, 0xFF615E7A, 0xFF9693AE, 0xFF5F5CA6, 0xFF474489, 0xFFE4E2F6, 0xFFE2E0EF, 0xFFEAE9F4),
            dp(0xFF15142A, 0xFF1E1D36, 0xFFE6E5F3, 0xFFADABC8, 0xFF7B7996, 0xFFADA9EC, 0xFFC7C4F4, 0xFF2B2A4E, 0xFF2A2943, 0xFF201F38, 0xFF181638)),
        Skin("yehang", "夜航", "星海航行，向梦而行",
            lp(0xFFECEEF9, 0xFFFFFFFF, 0xFF1D2238, 0xFF59607E, 0xFF8E94B0, 0xFF4B5CB0, 0xFF35438C, 0xFFDFE3F8, 0xFFDFE2F1, 0xFFE8EAF6),
            dp(0xFF0A111E, 0xFF121B2C, 0xFFE2E8F3, 0xFF9EABC2, 0xFF6A7791, 0xFF7FA8E8, 0xFFA3C2F2, 0xFF1A2A47, 0xFF1F2A40, 0xFF162136, 0xFF0A1630)),
        Skin("ying", "樱", "樱花烂漫，与你相遇",
            lp(0xFFFBF3F4, 0xFFFFFDFD, 0xFF33262A, 0xFF7A6268, 0xFFAD959B, 0xFFC2607A, 0xFFA0465F, 0xFFF8E0E6, 0xFFF0E2E5, 0xFFF6EAED),
            dp(0xFF1A1215, 0xFF241A1E, 0xFFF1E5E8, 0xFFC3A9B0, 0xFF8E747B, 0xFFEE9AB0, 0xFFF5B8C7, 0xFF3F2430, 0xFF34262B, 0xFF2B1F24, 0xFF3A1220)),
    )
    val MODES = listOf("跟随系统", "浅色", "深色")

    private fun p(c: Context) = c.getSharedPreferences("look", Context.MODE_PRIVATE)

    /** Compose 界面读这两个状态，改了马上重组。 */
    val skinState = mutableStateOf(ALL[0])
    val modeState = mutableStateOf(0)
    val sysDarkState = mutableStateOf(false)

    fun init(c: Context) {
        skinState.value = byId(p(c).getString("skin", null))
        modeState.value = p(c).getInt("mode", 0).coerceIn(0, 2)
        sysDarkState.value = sysDark(c)
    }
    fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: ALL[0]
    fun sysDark(c: Context) = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun setSkin(c: Context, s: Skin) { p(c).edit().putString("skin", s.id).apply(); skinState.value = s; CourseWidgets.safeUpdate(c) }
    fun setMode(c: Context, m: Int) { p(c).edit().putInt("mode", m).apply(); modeState.value = m; CourseWidgets.safeUpdate(c) }

    /** 当前是不是深色（mode：0 跟随系统、1 浅色、2 深色）。 */
    fun darkFor(mode: Int, sys: Boolean) = when (mode) { 1 -> false; 2 -> true; else -> sys }

    /** 小部件用：不依赖 Compose，直接读存储和系统深色设置。 */
    fun palFor(c: Context): Pal {
        val s = byId(p(c).getString("skin", null))
        return if (darkFor(p(c).getInt("mode", 0), sysDark(c))) s.dark else s.light
    }
}

/** 界面配色：随皮肤和深浅色变。名字沿用原来的写法（Clay = 主色）。 */
object C {
    private val pal: Pal get() { val s = Skins.skinState.value; return if (dark) s.dark else s.light }
    val dark: Boolean get() = Skins.darkFor(Skins.modeState.value, Skins.sysDarkState.value)
    val Cream get() = Color(pal.bg)
    val Card get() = Color(pal.card)
    val Ink get() = Color(pal.ink)
    val Soft get() = Color(pal.soft)
    val Faint get() = Color(pal.faint)
    val Clay get() = Color(pal.acc)
    val ClayDeep get() = Color(pal.deep)
    val ClayPale get() = Color(pal.pale)
    val Track get() = Color(pal.track)
    val Sand get() = Color(pal.sand)
    val OnClay get() = Color(pal.onAcc)
    val Sage get() = Color(pal.sage)
    val SagePale get() = Color(pal.sagePale)
    val SageInk get() = Color(pal.sageInk)
    /** 液态玻璃的底（清透，插画背景透出来）；要亮边和水光用 Modifier.liquidGlass（Glass.kt）。 */
    val Glass get() = com.zhishi.timetable.Glass.fill(com.zhishi.timetable.Glass.CLEAR)
    /** 稍实一点的玻璃（导航条一档）。 */
    val GlassStrong get() = com.zhishi.timetable.Glass.fill(com.zhishi.timetable.Glass.BAR)
    /** 玻璃的细亮边。 */
    val GlassEdge get() = Color.White.copy(alpha = if (dark) 0.16f else 0.6f)
}

/** 课块配色（浅底, 深字）：深色模式下换成深底浅字，色相不变。 */
fun courseTone(pair: Pair<Color, Color>, dark: Boolean, card: Color): Pair<Color, Color> {
    if (!dark) return pair
    val (lightC, deepC) = pair
    // 深色：低饱和的深底（像蒙了一层雾的玻璃）+ 浅字
    return androidx.compose.ui.graphics.lerp(card, deepC, 0.42f) to androidx.compose.ui.graphics.lerp(lightC, Color.White, 0.25f)
}
fun courseToneArgb(pair: Pair<Int, Int>, pal: Pal): Pair<Int, Int> =
    if (!pal.dark) pair else courseTone(Color(pair.first) to Color(pair.second), true, Color(pal.card)).let { it.first.toArgb() to it.second.toArgb() }
