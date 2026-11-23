package com.zhishi.timetable

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** 待办的暖色点（日历上和课的点区分开）。 */
val TodoDot = Color(0xFFE8956A)

/** 截止时间的说法：昨天 8:00 / 今天 22:00 / 明天 全天 / 周三 8:00 / 11月30日 8:00（不是今年就带年份）。 */
fun dueText(due: Long, allDay: Boolean = false): String {
    if (due < 0) return ""
    val day = Todos.dayOfMs(due)
    val today = Term.todayEpoch()
    val d = LocalDate.ofEpochDay(day)
    val diff = day - today
    val date = when {
        diff == -1L -> tr("昨天")
        diff == 0L -> tr("今天")
        diff == 1L -> tr("明天")
        diff in 2L..6L -> dayLabel(Math.floorMod(day + 3, 7L).toInt() + 1)
        else -> {
            val sameYear = d.year == LocalDate.ofEpochDay(today).year
            if (Tr.cjk) (if (sameYear) "" else "${d.year}/") + tr("{0}月{1}日", d.monthValue, d.dayOfMonth)
            else "${d.month.getDisplayName(java.time.format.TextStyle.SHORT, Tr.locale())} ${d.dayOfMonth}" + (if (sameYear) "" else ", ${d.year}")
        }
    }
    return if (allDay) "$date ${tr("全天")}" else "$date ${Term.clock(Todos.minuteOf(due))}"
}

@Composable
fun TodoScreen() {
    val c = LocalContext.current
    val ver = TodoStore.ver.value
    val all = remember(ver) { TodoStore.all(c) }
    var tick by remember { mutableStateOf(0) }
    Every(60_000) { tick++ }
    val now = remember(tick) { System.currentTimeMillis() }
    val g = remember(all, now) { Todos.group(all, now) }
    var edit by remember { mutableStateOf<Pair<Todo, Boolean>?>(null) }
    fun newTodo() = Todo("t" + System.currentTimeMillis(), "", created = System.currentTimeMillis())
    // 冒烟测试：todo:1 打开新建，todo:2 打开第一条
    LaunchedEffect(Nav.subState.value) {
        when (Nav.sub) {
            1 -> { edit = newTodo() to true; Nav.sub = 0 }
            2 -> { (g.overdue + g.today + g.upcoming + g.done).firstOrNull()?.let { edit = it to false }; Nav.sub = 0 }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("待办"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(if (g.overdue.isEmpty() && g.today.isEmpty() && g.upcoming.isEmpty()) tr("都做完啦")
                    else (if (g.overdue.isNotEmpty()) tr("已过期 {0} 项", g.overdue.size) + " · " else "") + tr("今天 {0} 项 · 之后 {1} 项", g.today.size, g.upcoming.size),
                    fontSize = 14.sp, color = C.Soft, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(42.dp).glassButton(CircleShape) { edit = newTodo() to true },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, tr("添加待办"), tint = C.Ink, modifier = Modifier.size(22.dp)) }
        }
        if (all.isEmpty()) {
            Column(Modifier.padding(22.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(24.dp)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                InkArt(InkKind.TODO, size = 116.dp)
                Text(tr("还没有待办"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(top = 4.dp))
                Text(tr("记下作业、实验报告、考试，可以关联到某门课，到点提醒你。"), fontSize = 13.sp, color = C.Soft, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                PrimaryButton(tr("添加待办")) { edit = newTodo() to true }
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
            fun section(title: String, l: List<Todo>) {
                if (l.isEmpty()) return
                item(key = "h$title") {
                    Row(Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        Text("  ${l.size}", fontSize = 13.sp, color = C.Faint)
                    }
                }
                items(l, key = { it.id }) { t -> TodoRow(t, now, onToggle = { TodoStore.toggle(c, t) }, onOpen = { edit = t to false }, onDelete = { TodoStore.remove(c, t.id) }) }
            }
            section(tr("已过期"), g.overdue)
            section(tr("今天"), g.today)
            section(tr("即将"), g.upcoming)
            section(tr("已完成"), g.done)
        }
    }
    edit?.let { (t, isNew) ->
        key(t.id) { TodoSheet(t, isNew, onSave = { TodoStore.put(c, it); edit = null }, onDelete = { TodoStore.remove(c, t.id); edit = null }, onClose = { edit = null }) }
    }
}

/** 一条待办：左边圆形勾选框，右滑 / 左滑删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoRow(t: Todo, now: Long, onToggle: () -> Unit, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    val body = @Composable {
        val late = Todos.overdue(t, now)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).liquidGlass(RoundedCornerShape(18.dp))
            .clickable { onOpen() }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val ring by animateColorAsState(if (t.done) C.Clay else C.Faint, label = "ring")
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (t.done) C.Clay else Color.Transparent).border(1.8.dp, ring, CircleShape).clickable { onToggle() },
                contentAlignment = Alignment.Center) { if (t.done) Text("✓", fontSize = 13.sp, color = C.OnClay, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(t.title, fontSize = 15.sp, color = if (t.done) C.Faint else C.Ink, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null)
                val sub = listOfNotNull(dueText(t.due, t.allDay).ifEmpty { null }, t.note.ifBlank { null }).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    if (late) Text(tr("已过期"), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFD9785A)).padding(horizontal = 5.dp, vertical = 1.dp))
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = if (late) Color(0xFFC2603F) else C.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (t.remind && t.due >= 0 && !t.done) Icon(Icons.Outlined.Notifications, null, tint = C.Faint, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
            if (t.course.isNotBlank()) Text(t.course, fontSize = 11.sp, color = C.ClayDeep, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 96.dp).clip(RoundedCornerShape(8.dp)).background(C.ClayPale.copy(alpha = 0.85f)).padding(horizontal = 7.dp, vertical = 3.dp))
        }
    }
    if (onDelete == null) { body(); return }
    val st = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v != SwipeToDismissBoxValue.Settled) { onDelete(); true } else false })
    SwipeToDismissBox(st, backgroundContent = {
        // 只在真的滑动时才画红底：卡片是半透明的，不然红色会透出来
        if (st.dismissDirection != SwipeToDismissBoxValue.Settled) Box(Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFD9785A).copy(alpha = 0.85f)).padding(horizontal = 20.dp),
            contentAlignment = if (st.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
            Text(tr("删除"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }) { body() }
}

/** 保存前整理：去掉首尾空白，没设时间就关掉提醒和提前量。 */
private fun tidy(t: Todo) = t.copy(title = t.title.trim(), note = t.note.trim(),
    remind = t.remind && t.due >= 0, before = if (t.remind && t.due >= 0) t.before else 0, allDay = t.allDay && t.due >= 0)

/** 新建 / 编辑待办。截止时间：快捷时间 + 日期 + 时 / 分滚轮（精确到分钟）或全天；提醒可提前；可以设成关联课程的下一次上课。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoSheet(start: Todo, isNew: Boolean, onSave: (Todo) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current
    var t by remember { mutableStateOf(start) }
    var pickDate by remember { mutableStateOf(false) }
    var askDel by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    Every(30_000) { tick++ }
    val now = remember(tick, t) { System.currentTimeMillis() }
    val all = remember { Term.desired(c) }
    val courses = remember { all.filter { it.kind == "course" }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct() }
    val nextClass = remember(t.course, tick) { Todos.nextClass(all, Term.termStart(c), Term.totalWeeks(c), t.course, System.currentTimeMillis()) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) { t = t.copy(remind = false); hint = tr("没有通知权限，提醒发不出来。可以在系统设置里打开。") }
    }
    GlassSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) tr("添加待办") else tr("编辑待办"), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.weight(1f))
                val ok = t.title.isNotBlank()
                Text(tr("保存"), fontSize = 15.sp, color = if (ok) C.OnClay else C.Faint, fontWeight = FontWeight.SemiBold,
                    style = LocalTextStyle.current.copy(shadow = null),
                    modifier = Modifier.glassButton(RoundedCornerShape(12.dp), tint = C.Clay, fill = if (ok) C.Clay.copy(alpha = 0.92f) else C.Sand, shadow = 0.dp, enabled = ok) {
                        onSave(tidy(t))
                    }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            FieldBox(t.title, tr("要做什么"), { t = t.copy(title = it.take(60)) }, big = true)
            Spacer(Modifier.height(8.dp))
            FieldBox(t.note, tr("备注（选填）"), { t = t.copy(note = it.take(120)) })

            Text(tr("关联课程"), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(top = 16.dp))
            FlowRowLite {
                CourseChip(tr("不关联"), t.course.isBlank()) { t = t.copy(course = "") }
                for (n in courses) CourseChip(n, t.course == n) { t = t.copy(course = n) }
                if (t.course.isNotBlank() && t.course !in courses) CourseChip(t.course, true) {}
            }
            if (courses.isEmpty()) Text(tr("课表里还没有课"), fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 4.dp))

            // ----- 截止时间 -----
            Text(tr("截止时间"), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(top = 18.dp))
            FlowRowLite {
                CourseChip(tr("不设"), t.due < 0) { t = Todos.cleared(t); hint = null }
                CourseChip(tr("1 小时后"), false) { t = Todos.preset(t, Todos.Preset.IN_1H, System.currentTimeMillis()) }
                CourseChip(tr("今晚"), false) { t = Todos.preset(t, Todos.Preset.TONIGHT, System.currentTimeMillis()) }
                CourseChip(tr("今天"), false) { t = Todos.preset(t, Todos.Preset.TODAY, System.currentTimeMillis()) }
                CourseChip(tr("明天"), false) { t = Todos.preset(t, Todos.Preset.TOMORROW, System.currentTimeMillis()) }
                CourseChip(tr("下周一"), false) { t = Todos.preset(t, Todos.Preset.NEXT_WEEK, System.currentTimeMillis()) }
                if (nextClass != null) CourseChip(tr("下次上课前"), t.due == nextClass && !t.allDay) { t = t.copy(due = nextClass, allDay = false) }
            }
            if (t.due >= 0) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dueText(t.due, t.allDay), fontSize = 15.sp, color = C.ClayDeep, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.glassButton(RoundedCornerShape(12.dp), tint = C.Clay, fill = C.ClayPale.copy(alpha = 0.9f), shadow = 0.dp) { pickDate = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp))
                    Spacer(Modifier.weight(1f))
                    Text(tr("全天"), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(end = 6.dp))
                    Switch(t.allDay, { on -> t = Todos.withAllDay(t, on, System.currentTimeMillis()) },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Clay, checkedThumbColor = C.OnClay, uncheckedTrackColor = C.Track, uncheckedBorderColor = C.Track, uncheckedThumbColor = C.Faint))
                }
                if (!t.allDay) {
                    val m = Todos.minuteOf(t.due)
                    TimeWheels(m, Modifier.padding(top = 8.dp)) { nm -> if (nm != Todos.minuteOf(t.due)) t = Todos.withMinute(t, nm, System.currentTimeMillis()) }
                }
                if (!t.done && Todos.endOf(t) <= now) Note(tr("这个时间已经过去了：保存后会显示为已过期，也不会再提醒。"), warm = true)
                Toggle(tr("提醒"), if (t.allDay) tr("全天的待办从当天 8:00 算起") else tr("到点或提前发一条通知"), t.remind) { on ->
                    t = t.copy(remind = on); hint = null
                    if (on && Build.VERSION.SDK_INT >= 33 && !TodoAlarm.canNotify(c)) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (t.remind) FlowRowLite {
                    for (b in Todos.BEFORE) CourseChip(beforeText(b, t.allDay), t.before == b) { t = t.copy(before = b) }
                }
                if (t.remind && !t.done) {
                    val at = Todos.alarmAt(t, now)
                    val r = Todos.remindAt(t)
                    if (at != null && r != null && r <= now) Text(tr("提醒时间已经过了，保存后马上提醒一次"), fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 6.dp))
                    else if (at != null) Text(tr("将在 {0} 提醒", dueText(at)), fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 6.dp))
                }
                if (t.remind) {
                    Text(tr("收不到提醒？在系统设置里给知时打开「自启动」，省电策略选「无限制」。小米、华为、OPPO 等手机默认会拦后台提醒。"),
                        fontSize = 12.sp, color = C.Faint, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    FlowRowLite {
                        CourseChip(tr("1 分钟后发测试通知"), false) {
                            if (Build.VERSION.SDK_INT >= 33 && !TodoAlarm.canNotify(c)) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { TodoAlarm.test(c); hint = tr("1 分钟后会发一条测试通知。可以先锁屏或回到桌面，看它能不能弹出来。") }
                        }
                        CourseChip(tr("通知与自启动设置"), false) {
                            try {
                                c.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", c.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (x: Exception) {}
                        }
                    }
                }
            }
            hint?.let { Note(it, warm = true) }

            if (!isNew) {
                Spacer(Modifier.height(14.dp))
                val ok = t.title.isNotBlank()
                Text(if (t.done) tr("标为未完成") else tr("标为已完成"), fontSize = 14.sp, color = if (ok) C.ClayDeep else C.Faint, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.ClayPale.copy(alpha = 0.7f))
                        .clickable(enabled = ok) { onSave(tidy(t).copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else 0L)) }.padding(vertical = 12.dp))
                Text(if (askDel) tr("再点一次确认删除") else tr("删除这条待办"), fontSize = 14.sp, color = if (askDel) C.ClayDeep else C.Soft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Sand.copy(alpha = 0.6f))
                        .clickable { if (askDel) onDelete() else askDel = true }.padding(vertical = 12.dp))
            }
        }
    }
    if (pickDate) TermDateDialog(tr("截止日期"), tr("时间不变，只换日期"),
        initial = Todos.dayOf(t) ?: Term.todayEpoch(), onPick = { d -> pickDate = false; t = Todos.withDay(t, d, System.currentTimeMillis()) }, onClose = { pickDate = false })
}

/** 提醒提前量的说法。 */
fun beforeText(min: Int, allDay: Boolean): String = when {
    min == 0 -> if (allDay) tr("当天 8:00") else tr("准时")
    min == 1440 -> tr("提前 1 天")
    min == 60 -> tr("提前 1 小时")
    else -> tr("提前 {0} 分钟", min)
}

/** 时 : 分 两个滚轮，精确到分钟；停下来才回调。 */
@Composable
fun TimeWheels(minute: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val cb = rememberUpdatedState(onChange)
    val h = minute / 60; val m = minute % 60
    Box(modifier.fillMaxWidth().height(WHEEL_H * 3), contentAlignment = Alignment.Center) {
        // 中间选中的那一行：一条玻璃
        Box(Modifier.fillMaxWidth().height(WHEEL_H).liquidGlass(RoundedCornerShape(14.dp), fill = C.ClayPale.copy(alpha = 0.55f), shadow = 0.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Wheel(24, h, { "%02d".format(it) }, Modifier.weight(1f)) { cb.value(it * 60 + (minute % 60)) }
            Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ClayDeep)
            Wheel(60, m, { "%02d".format(it) }, Modifier.weight(1f)) { cb.value((minute / 60) * 60 + it) }
        }
    }
}

private val WHEEL_H = 40.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, value: Int, label: (Int) -> String, modifier: Modifier, onChange: (Int) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = value)
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = state)
    val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { WHEEL_H.toPx() }
    val centered by remember { derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, count - 1) } }
    val cb = rememberUpdatedState(onChange)
    // 停下来时回调
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving -> if (!moving) cb.value(centered) }
    }
    // 外面改了（快捷时间）：滚过去
    LaunchedEffect(value) { if (!state.isScrollInProgress && centered != value) state.scrollToItem(value.coerceIn(0, count - 1)) }
    LazyColumn(modifier.height(WHEEL_H * 3), state = state, flingBehavior = fling, horizontalAlignment = Alignment.CenterHorizontally) {
        item { Spacer(Modifier.height(WHEEL_H)) }
        items(count) { i ->
            val on = i == centered
            Box(Modifier.height(WHEEL_H).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(label(i), fontSize = if (on) 22.sp else 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) C.Ink else C.Faint.copy(alpha = 0.7f))
            }
        }
        item { Spacer(Modifier.height(WHEEL_H)) }
    }
}

@Composable
private fun CourseChip(t: String, on: Boolean, onClick: () -> Unit) {
    Text(t, fontSize = 13.sp, color = if (on) C.OnClay else C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = if (on) LocalTextStyle.current.copy(shadow = null) else LocalTextStyle.current,
        modifier = Modifier.padding(top = 8.dp, end = 8.dp).widthIn(max = 200.dp)
            .glassButton(RoundedCornerShape(12.dp), tint = C.Clay, fill = if (on) C.Clay.copy(alpha = 0.92f) else C.Sand.copy(alpha = 0.9f), shadow = 0.dp) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp))
}
