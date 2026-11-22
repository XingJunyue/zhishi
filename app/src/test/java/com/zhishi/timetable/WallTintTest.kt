package com.zhishi.timetable

import org.junit.Assert.*
import org.junit.Test

/** 小部件「跟随壁纸」的取色、透明度换算；水墨插画的笔画几何。 */
class WallTintTest {
    private val skin = Skins.byId("chunyu")

    @Test fun darkPhotoGivesDarkClearGlass() {
        // 用户反馈那种深色照片壁纸：主色接近黑、带一点暖色
        val s = WallTint.Src(0xFF14151B.toInt(), 0xFF3A2C26.toInt(), 0xFF5A6B8C.toInt())
        assertTrue(WallTint.isDark(s))
        val p = WallTint.pal(s, skin)
        assertTrue(p.dark)
        assertTrue("深色玻璃上是浅色字", WallTint.lum(p.i(p.ink)) > 0.8f)
        assertTrue(WallTint.lum(p.i(p.bg)) < 0.03f)
    }

    @Test fun lightWallpaperGivesLightGlass() {
        val s = WallTint.Src(0xFFF4E9D8.toInt(), 0xFFCFE3F0.toInt())
        assertFalse(WallTint.isDark(s))
        val p = WallTint.pal(s, skin)
        assertFalse(p.dark)
        assertTrue("浅色玻璃上是深色字", WallTint.lum(p.i(p.ink)) < 0.03f)
    }

    @Test fun systemHintWins() {
        assertFalse(WallTint.isDark(WallTint.Src(0xFF101010.toInt(), darkText = true)))
        assertTrue(WallTint.isDark(WallTint.Src(0xFFF0F0F0.toInt(), darkText = false)))
    }

    @Test fun materialYouAccentUsed() {
        val p = WallTint.pal(WallTint.Src(0xFF202020.toInt(), accentLight = 0xFF3355AA.toInt(), accentDark = 0xFFAABBFF.toInt()), skin)
        assertEquals(0xFFAABBFF.toInt(), p.i(p.acc))
    }

    @Test fun greyWallpaperKeepsSkinAccentHue() {
        val p = WallTint.pal(WallTint.Src(0xFFEEEEEE.toInt(), 0xFFCCCCCC.toInt()), skin)
        val hs = WallTint.hsl(skin.light.i(skin.light.acc))[0]
        val hp = WallTint.hsl(p.i(p.acc))[0]
        assertEquals(hs, hp, 3f)
    }

    @Test fun hslRoundTrip() {
        for (c in listOf(0xFF3F8A62.toInt(), 0xFFC2607A.toInt(), 0xFF0A111E.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())) {
            val h = WallTint.hsl(c)
            val back = WallTint.fromHsl(h[0], h[1], h[2])
            for (sh in intArrayOf(0, 8, 16)) assertEquals(((c shr sh) and 0xFF).toFloat(), ((back shr sh) and 0xFF).toFloat(), 1.5f)
        }
    }

    @Test fun clearMapping() {
        assertEquals(0.75f, WallTint.fillAlpha(0), 0.001f)
        assertTrue("默认比 build-15 的 52% / 62% 清透得多", WallTint.fillAlpha(WidgetStyle.CLEAR_DEFAULT) < 0.30f)
        assertTrue(WallTint.fillAlpha(90) < 0.1f)
        assertEquals(WallTint.fillAlpha(90), WallTint.fillAlpha(100), 0.0001f)
        assertTrue(WallTint.plateAlpha(90) > WallTint.plateAlpha(0))
        assertTrue(WallTint.shadowAlpha(90, true) <= 1f)
    }
}
