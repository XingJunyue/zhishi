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

/** 截止时间的说法：今天 8:00 / 明天 8:00 / 11月23日 8:00。 */
fun dueText(due: Long): String {
    if (due < 0) return ""
    val day = Todos.dayOf(Todo("", "x", due = due)) ?: return ""
    val t = Term.clock(Todos.minuteOf(due))
    val today = Term.todayEpoch()
    val d = LocalDate.ofEpochDay(day)
    val date = when (day - today) {
        0L -> tr("今天"); 1L -> tr("明天")
        else -> if (Tr.cjk) tr("{0}月{1}日", d.monthValue, d.dayOfMonth) else "${d.month.getDisplayName(java.time.format.TextStyle.SHORT, Tr.locale())} ${d.dayOfMonth}"
    }
    return "$date $t"
}

@Composable
fun TodoScreen() {
    val c = LocalContext.current
    val ver = TodoStore.ver.value
    val all = remember(ver) { TodoStore.all(c) }
    var tick by remember { mutableStateOf(0) }
    Every(60_000) { tick++ }
    val now = remember(tick) { System.currentTimeMillis() }
    val todayEnd = remember(tick) { Todos.at(Term.todayEpoch() + 1, 0) }
    val g = remember(all, todayEnd) { Todos.group(all, todayEnd) }
    var edit by remember { mutableStateOf<Pair<Todo, Boolean>?>(null) }
    fun newTodo() = Todo("t" + System.currentTimeMillis(), "", created = System.currentTimeMillis())
    // 冒烟测试：todo:1 打开新建，todo:2 打开第一条
    LaunchedEffect(Nav.subState.value) {
        when (Nav.sub) {
            1 -> { edit = newTodo() to true; Nav.sub = 0 }
            2 -> { (g.today + g.upcoming + g.done).firstOrNull()?.let { edit = it to false }; Nav.sub = 0 }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("待办"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(if (g.today.isEmpty() && g.upcoming.isEmpty()) tr("都做完啦") else tr("今天 {0} 项 · 之后 {1} 项", g.today.size, g.upcoming.size),
                    fontSize = 14.sp, color = C.Soft, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(42.dp).liquidGlass(CircleShape).clickable { edit = newTodo() to true },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, tr("添加待办"), tint = C.Ink, modifier = Modifier.size(22.dp)) }
        }
        if (all.isEmpty()) {
            Column(Modifier.padding(22.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(24.dp)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                RainArt(size = 110.dp)
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
                val sub = listOfNotNull(dueText(t.due).ifEmpty { null }, t.note.ifBlank { null }).joinToString(" · ")
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

/** 新建 / 编辑待办。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoSheet(start: Todo, isNew: Boolean, onSave: (Todo) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current
    var t by remember { mutableStateOf(start) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Long?>(null) }   // 选好的日期，等选时间
    var askDel by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val courses = remember { Term.desired(c).filter { it.kind == "course" }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct() }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) { t = t.copy(remind = false); hint = tr("没有通知权限，提醒发不出来。可以在系统设置里打开。") }
    }
    GlassSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) tr("添加待办") else tr("编辑待办"), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.weight(1f))
                val ok = t.title.isNotBlank()
                Text(tr("保存"), fontSize = 15.sp, color = if (ok) C.OnClay else C.Faint, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (ok) C.Clay else C.Sand).clickable(enabled = ok) {
                        onSave(t.copy(title = t.title.trim(), note = t.note.trim(), remind = t.remind && t.due >= 0))
                    }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            FieldBox(t.title, tr("要做什么"), { t = t.copy(title = it.take(60)) }, big = true)
            Spacer(Modifier.height(8.dp))
            FieldBox(t.note, tr("备注（选填）"), { t = t.copy(note = it.take(120)) })

            Text(tr("截止时间"), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(top = 18.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.due < 0) tr("不设") else dueText(t.due), fontSize = 14.sp, color = if (t.due < 0) C.Soft else C.ClayDeep, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (t.due < 0) C.Sand else C.ClayPale).clickable { pickDate = true }.padding(horizontal = 14.dp, vertical = 9.dp))
                if (t.due >= 0) Text(tr("清除"), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp))
                    .clickable { t = t.copy(due = -1L, remind = false) }.padding(8.dp))
            }
            if (t.due >= 0) Toggle(tr("到点提醒"), tr("截止时间到了发一条通知"), t.remind) { on ->
                t = t.copy(remind = on); hint = null
                if (on && Build.VERSION.SDK_INT >= 33 && !TodoAlarm.canNotify(c)) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            hint?.let { Note(it, warm = true) }

            Text(tr("关联课程"), fontSize = 13.sp, color = C.Soft, modifier = Modifier.padding(top = 16.dp))
            FlowRowLite {
                CourseChip(tr("不关联"), t.course.isBlank()) { t = t.copy(course = "") }
                for (n in courses) CourseChip(n, t.course == n) { t = t.copy(course = n) }
                if (t.course.isNotBlank() && t.course !in courses) CourseChip(t.course, true) {}
            }
            if (courses.isEmpty()) Text(tr("课表里还没有课"), fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 4.dp))

            if (!isNew) {
                Spacer(Modifier.height(14.dp))
                Text(if (t.done) tr("标为未完成") else tr("标为已完成"), fontSize = 14.sp, color = C.ClayDeep, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.ClayPale.copy(alpha = 0.7f))
                        .clickable { onSave(t.copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else 0L)) }.padding(vertical = 12.dp))
                Text(if (askDel) tr("再点一次确认删除") else tr("删除这条待办"), fontSize = 14.sp, color = if (askDel) C.ClayDeep else C.Soft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Sand.copy(alpha = 0.6f))
                        .clickable { if (askDel) onDelete() else askDel = true }.padding(vertical = 12.dp))
            }
        }
    }
    if (pickDate) TermDateDialog(tr("截止日期"), tr("选好日期后再选时间"),
        initial = Todos.dayOf(t) ?: Term.todayEpoch(), onPick = { d -> pickDate = false; pickTime = d }, onClose = { pickDate = false })
    pickTime?.let { day ->
        val init = if (t.due >= 0) Todos.minuteOf(t.due) else 22 * 60
        val st = rememberTimePickerState(init / 60, init % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { pickTime = null }, containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp, shape = DialogShape, modifier = dialogGlass(),
            title = { Text(tr("截止时间"), color = C.Ink, fontSize = 16.sp) },
            text = { TimePicker(st, colors = TimePickerDefaults.colors(selectorColor = C.Clay, timeSelectorSelectedContainerColor = C.ClayPale,
                timeSelectorSelectedContentColor = C.ClayDeep, clockDialColor = C.Sand)) },
            confirmButton = { TextButton({ t = t.copy(due = Todos.at(day, st.hour * 60 + st.minute)); pickTime = null }) { Text(tr("确定"), color = C.ClayDeep, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton({ pickTime = null }) { Text(tr("取消"), color = C.Soft) } })
    }
}

@Composable
private fun CourseChip(t: String, on: Boolean, onClick: () -> Unit) {
    Text(t, fontSize = 13.sp, color = if (on) C.OnClay else C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp, end = 8.dp).widthIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.Clay else C.Sand)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp))
}
