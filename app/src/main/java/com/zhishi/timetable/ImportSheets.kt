package com.zhishi.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 课程标签（水课 / 普通 / 重要）的字色，和课块、小部件上的标签一致。 */
internal fun levelColor(l: Int) = Color(LevelTag.fg(l, C.dark))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewSheet(found: List<Slot>, shot: android.net.Uri?, onAdd: (List<Slot>) -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current
    val items = remember { mutableStateListOf<Slot>().apply { addAll(found) } }
    val off = remember { mutableStateListOf<String>() }
    var editing by remember { mutableStateOf<Slot?>(null) }
    var big by remember { mutableStateOf(false) }
    val pic = remember(shot) {
        shot?.let { u -> try {
            val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            c.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
        } catch (x: Exception) { null } }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Card) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(if (items.isEmpty()) tr("自己填课表") else tr("认出 {0} 节课", items.size), fontSize = 21.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = C.Ink)
            Text(tr("对照截图核对：点一行可以改课名、星期、节次和时间，漏掉的点「补一节」，不要的取消勾选"), fontSize = 12.sp, color = C.Faint, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (pic != null) {
                androidx.compose.foundation.Image(pic, null, contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().heightIn(max = if (big) 520.dp else 150.dp).clip(RoundedCornerShape(12.dp)).clickable { big = !big })
                Text(if (big) tr("点图片收起") else tr("点图片放大对照"), fontSize = 11.sp, color = C.Faint, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            }
            Column(Modifier.heightIn(max = if (big) 200.dp else 360.dp).verticalScroll(rememberScrollState())) {
                for (s in items.sortedWith(compareBy({ it.days.minOrNull() ?: 0 }, { it.from }))) {
                    val skip = s.id in off
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(C.Sand.copy(alpha = 0.5f)).clickable { editing = s }
                        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (skip) "○" else "●", fontSize = 16.sp, color = if (skip) C.Faint else C.Clay, modifier = Modifier.clip(CircleShape).clickable { if (skip) off.remove(s.id) else off.add(s.id) }.padding(end = 8.dp, start = 2.dp))
                        Text(daysText(s.days), fontSize = 12.sp, color = C.Faint, modifier = Modifier.width(44.dp), maxLines = 1)
                        Text(s.name, fontSize = 14.sp, color = if (skip) C.Faint else C.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${Term.clock(s.from)}–${Term.clock(s.to)}", fontSize = 12.sp, color = C.Soft)
                        Text(Slot.LEVELS[s.level], fontSize = 12.sp, color = levelColor(s.level), fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(C.Card)
                                .clickable { val i = items.indexOfFirst { it.id == s.id }; if (i >= 0) items[i] = s.copy(level = (s.level + 1) % 3) }
                                .padding(horizontal = 8.dp, vertical = 4.dp))
                        Text(" ✎", fontSize = 13.sp, color = C.Soft)
                    }
                }
            }
            Text(tr("＋ 补一节"), fontSize = 13.sp, color = C.ClayDeep, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    editing = Slot("m" + System.currentTimeMillis(), "", setOf(Term.weekday()), Term.secRange(c, 1, 2).first, Term.secRange(c, 1, 2).second)
                }.padding(vertical = 6.dp))
            if (items.isNotEmpty()) {
                Text(tr("一键设置等级："), fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 8.dp))
                FlowRowLite {
                    for (lv in 0..2) Text(tr("全部设为{0}", Slot.LEVELS[lv]), fontSize = 12.sp, color = C.Ink,
                        modifier = Modifier.padding(top = 4.dp, end = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.Sand).clickable {
                            for (i in items.indices) if (items[i].id !in off) items[i] = items[i].copy(level = lv)
                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(tr("导入 {0} 节", items.count { it.id !in off }), enabled = items.any { it.id !in off }) { onAdd(items.filter { it.id !in off }) }
        }
    }
    editing?.let { s -> CourseSheet(s, onSave = { n ->
        val i = items.indexOfFirst { it.id == s.id }
        // 同名课一起改等级
        for (j in items.indices) if (items[j].name == s.name && items[j].id != s.id && s.name.isNotEmpty()) items[j] = items[j].copy(level = n.level)
        if (i >= 0) items[i] = n else items += n
        editing = null
    }, onDelete = { items.removeAll { it.id == s.id }; editing = null }, onClose = { editing = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarPick(cals: List<Pair<Long, String>>, onPick: (Long) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = C.Card) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(tr("选课表所在的日历"), fontSize = 21.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = C.Ink)
            Text(tr("其他课表 App 可以先同步到系统日历"), fontSize = 12.sp, color = C.Faint, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (cals.isEmpty()) Text(tr("没有找到日历"), fontSize = 14.sp, color = C.Soft)
            for ((id, n) in cals) Text(n, fontSize = 15.sp, color = C.Ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(id) }.padding(vertical = 12.dp))
        }
    }
}

@Composable
internal fun Stepper(v: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Sand), verticalAlignment = Alignment.CenterVertically) {
        Text("−", fontSize = 18.sp, color = if (v > min) C.Clay else C.Faint, modifier = Modifier.clickable(enabled = v > min) { onChange(v - 1) }.padding(horizontal = 12.dp, vertical = 4.dp))
        Text("$v", fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 20.dp))
        Text("+", fontSize = 18.sp, color = if (v < max) C.Clay else C.Faint, modifier = Modifier.clickable(enabled = v < max) { onChange(v + 1) }.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}
