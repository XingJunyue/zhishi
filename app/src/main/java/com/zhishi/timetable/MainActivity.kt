package com.zhishi.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 外部要求打开某一页（小部件点进来、测试脚本）："table:1" = 课表页的第 1 个动作。 */
object Nav {
    val open = mutableStateOf<String?>(null)
    val subState = mutableStateOf(0)
    var sub: Int get() = subState.value; set(v) { subState.value = v }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        Skins.init(this)
        super.onCreate(b)
        if (BuildConfig.DEBUG) TestHooks.run(this, intent)
        Tour.init(this)
        TodoAlarm.rescheduleAll(this)
        setContent {
            val dark = C.dark
            val bg = C.Cream.toArgb()
            LaunchedEffect(dark, bg) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb()) else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb()) else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                )
            }
            ZhishiTheme { Box(Modifier.fillMaxSize().background(C.Cream)) { App(); TourHost() } }
        }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        if (BuildConfig.DEBUG) TestHooks.run(this, i)
        i.getStringExtra("open")?.let { Nav.open.value = it }
    }

    override fun onResume() {
        super.onResume()
        Skins.sysDarkState.value = Skins.sysDark(this)
        CourseWidgets.safeUpdate(this)
    }
}

/** 底部四个标签页；widget / about 是从「我的」点进去的二级页。settings 是「我的」的旧名字（测试脚本、教程还在用）。 */
private val TABS = listOf("table", "todo", "calendar", "me")
private val SCREENS = TABS + listOf("widget", "about", "settings")
private fun norm(s: String?) = when (s) { "settings" -> "me"; in SCREENS -> s; else -> null }

@Composable
fun App() {
    val ctx = LocalContext.current
    val openExtra = remember { (ctx as? android.app.Activity)?.intent?.getStringExtra("open") }
    var screen by remember {
        mutableStateOf(openExtra?.let { Nav.sub = it.substringAfter(':', "0").toIntOrNull() ?: 0; norm(it.substringBefore(':')) } ?: "table")
    }
    val req = Nav.open.value
    LaunchedEffect(req) {
        if (req != null) {
            Nav.sub = req.substringAfter(':', "0").toIntOrNull() ?: 0
            screen = norm(req.substringBefore(':')) ?: "table"
            Nav.open.value = null
        }
    }
    val sub = screen == "widget" || screen == "about"
    BackHandler(enabled = sub) { screen = "me" }
    BackHandler(enabled = !sub && screen != "table") { screen = "table" }
    if (Updater.enabled()) UpdatePrompt()
    LaunchedEffect(Unit) { Tour.check(ctx) }
    Box(Modifier.fillMaxSize()) {
        Backdrop()
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "screen"
        ) { s ->
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (s) {
                    "table" -> TimetableScreen()
                    "todo" -> TodoScreen()
                    "calendar" -> CalendarScreen()
                    "me" -> MeScreen(onOpen = { screen = it })
                    "widget" -> Paged({ screen = "me" }, tr("小部件")) { WidgetScreen() }
                    "about" -> Paged({ screen = "me" }, tr("关于")) { AboutScreen() }
                }
            }
        }
        if (!sub) BottomBar(screen, Modifier.align(Alignment.BottomCenter)) { screen = it }
    }
}

/** 底部毛玻璃导航条：课表 / 待办 / 日历 / 我的，选中的那个用皮肤主色的实心图标。 */
@Composable
private fun BottomBar(cur: String, m: Modifier, go: (String) -> Unit) {
    val items = listOf(
        Triple("table", tr("课表"), Icons.Filled.Home to Icons.Outlined.Home),
        Triple("todo", tr("待办"), Icons.Filled.CheckCircle to Icons.Outlined.CheckCircle),
        Triple("calendar", tr("日历"), Icons.Filled.DateRange to Icons.Outlined.DateRange),
        Triple("me", tr("我的"), Icons.Filled.AccountCircle to Icons.Outlined.AccountCircle),
    )
    val shape = RoundedCornerShape(28.dp)
    Row(
        m.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().clip(shape)
            .background(C.GlassStrong).border(1.dp, C.GlassEdge, shape).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((id, label, icons) in items) {
            val on = id == cur
            val tint by animateColorAsState(if (on) C.ClayDeep else C.Faint, label = "tab")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { go(id) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (on) icons.first else icons.second, null, tint = tint, modifier = Modifier.size(24.dp))
                Text(label, fontSize = 11.sp, color = tint, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** 二级页面：左上返回 + 粗体大标题 + 内容。 */
@Composable
fun Paged(onBack: () -> Unit, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, top = 12.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(start = 14.dp))
        }
        content()
    }
}
