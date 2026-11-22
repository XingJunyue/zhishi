package com.zhishi.timetable

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 「我的」：皮肤、课表（导入 / 学期 / 节次 / 备份）、小部件、语言、帮助与关于。 */
@Composable
fun MeScreen(onOpen: (String) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var term by remember { mutableStateOf(false) }
    var secs by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    // 测试脚本：settings:1 直接打开学期设置，settings:2 每节课时间
    LaunchedEffect(Nav.subState.value) { when (Nav.sub) { 1 -> { term = true; Nav.sub = 0 }; 2 -> { secs = true; Nav.sub = 0 } } }
    val saveFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { c.contentResolver.openOutputStream(u)?.use { it.write(Term.exportBackup(c).toByteArray()) }; true } catch (x: Exception) { false } }
            msg = if (ok) tr("已导出课表备份") else tr("导出失败")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        // 顶部：图标 + 名字 + 版本
        Row(Modifier.padding(top = 16.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(56.dp)
            Column(Modifier.padding(start = 14.dp)) {
                Text(tr("我的"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(tr("知时") + " · " + tr("版本 {0}", BuildConfig.VERSION_NAME), fontSize = 13.sp, color = C.Soft)
            }
        }

        SectionTitle(tr("皮肤"), tr("界面和桌面小部件一起换"))
        SoftCard(Modifier.fillMaxWidth(), pad = 14.dp) {
            SkinPicker()
            Spacer(Modifier.height(12.dp))
            Segments(Skins.MODES.map { tr(it) }, Skins.modeState.value) { Skins.setMode(c, it) }
        }

        SectionTitle(tr("课表"))
        SoftCard(Modifier.fillMaxWidth(), pad = 6.dp) {
            RowLink(tr("导入课表"), tr("教务、截图、Excel / CSV、日历、备份")) { Nav.open.value = "table:8" }
            RowLink(tr("学期与显示"), tr("开学日期、总周数、周末")) { term = true }
            RowLink(tr("每节课时间"), tr("上下课时间、节数")) { secs = true }
            RowLink(tr("分享课表备份"), tr("发给自己或同学，可在知时里导入")) { msg = shareBackup(c) }
            RowLink(tr("导出到文件"), tr("保存为 .json，换手机时导入")) { saveFile.launch("zhishi-timetable.json") }
        }
        msg?.let { Note(it) }

        SectionTitle(tr("小部件"))
        SoftCard(Modifier.fillMaxWidth(), pad = 6.dp) {
            RowLink(tr("桌面小部件"), tr("4×2 和 2×2，预览和一键添加")) { onOpen("widget") }
        }

        SectionTitle(tr("语言"))
        SoftCard(Modifier.fillMaxWidth(), pad = 12.dp) {
            val cur = remember { Tr.chosen(c) }
            FlowRowLite {
                LangChip(tr("跟随系统"), cur.isEmpty()) { if (cur.isNotEmpty()) Tr.set(c, "") }
                for ((code, name) in Tr.NAMES) LangChip(name, cur == code) { if (cur != code) Tr.set(c, code) }
            }
        }

        SectionTitle(tr("帮助"))
        SoftCard(Modifier.fillMaxWidth(), pad = 6.dp) {
            RowLink(tr("新手教程"), tr("再看一遍主要功能")) { Tour.show.value = "first" }
            RowLink(tr("这个版本的新功能"), null) { Tour.show.value = "new" }
            RowLink(tr("关于与更新"), tr("版本 {0}", BuildConfig.VERSION_NAME)) { onOpen("about") }
        }
        Spacer(Modifier.height(120.dp))
    }
    if (term) TermSheet(onChange = {}, onClose = { term = false })
    if (secs) SecTimesSheet(onChange = {}, onClose = { secs = false })
}

/** 把课表备份（含待办）发出去；失败返回提示文字。 */
fun shareBackup(c: android.content.Context): String? = try {
    val dir = java.io.File(c.cacheDir, "share").apply { mkdirs() }
    val f = java.io.File(dir, "zhishi-timetable.json"); f.writeText(Term.exportBackup(c))
    val uri = androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", f)
    c.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), tr("分享课表"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (x: Exception) { tr("分享失败") }

@Composable
private fun RowLink(t: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t, fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 20.sp, color = C.Faint)
    }
}

@Composable
private fun LangChip(t: String, on: Boolean, onClick: () -> Unit) {
    Text(t, fontSize = 14.sp, color = if (on) C.OnClay else C.Ink,
        modifier = Modifier.padding(4.dp)
            .glassButton(CircleShape, tint = C.Clay, fill = if (on) C.Clay.copy(alpha = 0.80f) else null, shadow = 0.dp) { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp))
}

/** 皮肤选择：每套一个圆形的插画缩略图（深浅色跟当前模式），下面是名字；选中的套一圈主色。下面一行是这套皮肤的一句话。 */
@Composable
fun SkinPicker() {
    val c = LocalContext.current
    val cur = Skins.skinState.value
    val dark = C.dark
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (s in Skins.ALL) {
            val on = s.id == cur.id
            val p = if (dark) s.dark else s.light
            val ring by animateColorAsState(if (on) Color(p.acc) else Color.Transparent, label = "ring")
            Column(Modifier.width(52.dp).clip(RoundedCornerShape(16.dp)).clickable { Skins.setSkin(c, s) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(50.dp).border(2.dp, ring, CircleShape).padding(3.5.dp).clip(CircleShape).background(Color(p.bg))) {
                    val r = s.thumb(dark)
                    if (r != 0) Image(androidx.compose.ui.res.painterResource(r), tr(s.name), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Text(tr(s.name), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) C.Ink else C.Soft,
                    maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
    Text(tr(cur.name) + " · " + tr(cur.poem), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

/** 小部件页：两种课表小部件的样子，和「添加到桌面」。 */
@Composable
fun WidgetScreen() {
    val c = LocalContext.current
    val am = remember { AppWidgetManager.getInstance(c) }
    val canPin = remember { am?.isRequestPinAppWidgetSupported == true }
    var tip by remember { mutableStateOf<String?>(null) }
    fun pin(cls: Class<*>) {
        val ok = try { am?.requestPinAppWidget(ComponentName(c, cls), null, null) == true } catch (x: Exception) { false }
        if (!ok) tip = tr("这个桌面不支持一键添加：长按桌面空白处 → 小部件 → 找到「知时」")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(tr("上面是正在上的课（课间就是最近的下一节），下面是再下一节；今天没课就是一张白卡。"),
            fontSize = 13.sp, color = C.Soft, lineHeight = 20.sp, modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp))
        // 小部件样式：液态玻璃 / 经典（改版前的实心皮肤底），换了马上重画桌面上的小部件
        val sv = WidgetStyle.ver.value
        val style = remember(sv) { WidgetStyle.get(c) }
        SectionTitle(tr("小部件样式"), tr("经典样式始终跟着皮肤换色"))
        Segments(WidgetStyle.ALL.map { tr(it.second) }, WidgetStyle.ALL.indexOfFirst { it.first == style }.coerceAtLeast(0)) { i -> WidgetStyle.set(c, WidgetStyle.ALL[i].first) }
        if (style == WidgetStyle.GLASS) {
            // 液态玻璃：颜色跟随壁纸 / 皮肤，透明度可调（拖动时预览跟着变，松手才写入并重画桌面上的小部件）
            val tint = remember(sv) { WidgetStyle.tint(c) }
            SectionTitle(tr("玻璃颜色"), tr("跟随壁纸：按壁纸的主色自动选深浅、色调和文字颜色"))
            Segments(WidgetStyle.TINTS.map { tr(it.second) }, WidgetStyle.TINTS.indexOfFirst { it.first == tint }.coerceAtLeast(0)) { i -> WidgetStyle.setTint(c, WidgetStyle.TINTS[i].first) }
            if (tint == WidgetStyle.WALL && remember(sv) { WallTint.read(c) } == null)
                Note(tr("这台手机没有提供壁纸颜色（Android 8.1 以下或动态壁纸），先按皮肤配色。"))
            val clear = remember(sv) { WidgetStyle.clear(c) }
            var moved by remember { mutableStateOf(0) }
            LaunchedEffect(moved) { if (moved > 0) { kotlinx.coroutines.delay(400); CourseWidgets.safeUpdate(c) } }
            Box(Modifier.padding(top = 10.dp)) { StepSlider(tr("透明度"), clear, 0..90, 5, "%") { if (it != clear) { WidgetStyle.setClear(c, it, redraw = false); moved++ } } }
            Note(tr("桌面小部件看不到身后壁纸的像素，做不了真正的折射；这里是按壁纸配色，让玻璃和壁纸更搭。"))
        }
        WidgetCard(tr("课表 4×2"), true, canPin) { pin(CourseWidget4::class.java) }
        WidgetCard(tr("课表 2×2"), false, canPin) { pin(CourseWidget2::class.java) }
        tip?.let { Note(it) }
        Note(tr("小部件上的「重要 / 普通 / 水课」标签可以在「设置 → 学期与显示」里关掉。"))
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun WidgetCard(title: String, wide: Boolean, canPin: Boolean, onPin: () -> Unit) {
    val c = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    Every(30_000) { tick++ }
    val bmp = remember(tick, Skins.skinState.value, C.dark, WidgetStyle.ver.value, WallTint.ver.value) { try { CourseWidgets.preview(c, wide).asImageBitmap() } catch (x: Exception) { null } }
    // 跟随壁纸时：预览底下垫一块按壁纸主色做的渐变（App 读不到壁纸本身，只能用它的颜色模拟）
    val wall = remember(WidgetStyle.ver.value, WallTint.ver.value) {
        if (!WidgetStyle.classic(c) && WidgetStyle.followWall(c)) WallTint.read(c) else null
    }
    SoftCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.weight(1f))
            if (canPin) Text(tr("添加到桌面"), fontSize = 13.sp, color = C.OnClay, fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = null),
                modifier = Modifier.glassButton(CircleShape, tint = C.Clay, fill = C.Clay.copy(alpha = 0.80f), shadow = 0.dp) { onPin() }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        // 小部件本身是透明的玻璃：直接放在插画背景上预览，和放在桌面壁纸上一样
        val under = if (wall == null) Modifier else Modifier.clip(RoundedCornerShape(20.dp)).background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(wall.primary), Color(wall.secondary ?: wall.primary), Color(wall.tertiary ?: wall.secondary ?: wall.primary))))
        Box(Modifier.fillMaxWidth().padding(top = 14.dp).then(under).padding(if (wall == null) 4.dp else 12.dp), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(if (wide) 1f else 0.5f).aspectRatio(if (wide) 320f / 156f else 1f))
        }
    }
}
