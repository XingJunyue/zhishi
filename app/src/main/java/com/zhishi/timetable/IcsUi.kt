package com.zhishi.timetable

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 各系统里日历订阅链接在哪（菜单名保持学校网站上的原文，多半是英文 / 德文）。 */
val ICS_WHERE = listOf(
    "Canvas" to "Calendar → Calendar Feed",
    "Moodle" to "Calendar → Import or export calendars → Export calendar → Get calendar URL",
    "Brightspace (D2L)" to "Calendar → Subscribe",
    "Blackboard" to "Calendar → Settings → Share Calendar",
    "Syllabus+ / CMIS / Celcat / MyTimetable" to "My timetable → Subscribe / Add to calendar / iCal feed",
    "TUMonline / CAMPUSonline" to "Kalender → Veröffentlichen / Export → Link kopieren",
    "HISinOne" to "Mein Stundenplan → iCalendar-Export",
    "WebUntis" to "Mein Stundenplan → Teilen / iCal",
    "Workday / Banner / PeopleSoft" to "Download .ics, or Export to Excel → CSV, or take a screenshot"
)

private fun prefs(c: Context) = c.getSharedPreferences("ics", Context.MODE_PRIVATE)
fun lastIcsUrl(c: Context) = prefs(c).getString("url", "") ?: ""
fun rememberIcsUrl(c: Context, u: String) = prefs(c).edit().putString("url", u).apply()

/** 贴日历订阅链接（https / webcal）导入课表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcsLinkSheet(onGo: (String) -> Unit, onClose: () -> Unit) {
    val c = androidx.compose.ui.platform.LocalContext.current
    val clip = LocalClipboardManager.current
    var url by remember { mutableStateOf(lastIcsUrl(c).ifEmpty { clip.getText()?.text?.trim()?.takeIf { Regex("^(https?|webcals?)://", RegexOption.IGNORE_CASE).containsMatchIn(it) && it.length < 600 } ?: "" }) }
    var help by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Card) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(tr("日历订阅链接"), fontSize = 21.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = C.Ink)
            Text(tr("学校的课表或学习平台（Canvas、Moodle 等）里复制「日历订阅 / iCal」链接，贴在这里。只读取，不会改你的日历。"),
                fontSize = 13.sp, color = C.Soft, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Sand).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (url.isEmpty()) Text("https://… / webcal://…", fontSize = 14.sp, color = C.Faint)
                BasicTextField(url, { url = it.trim() }, singleLine = false, maxLines = 4, textStyle = TextStyle(fontSize = 13.sp, color = C.Ink), modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.padding(top = 6.dp)) {
                Text(tr("粘贴"), fontSize = 13.sp, color = C.ClayDeep, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { clip.getText()?.text?.let { url = it.trim() } }.padding(vertical = 6.dp, horizontal = 2.dp))
                Spacer(Modifier.width(18.dp))
                Text(if (help) tr("收起") else tr("链接在哪找？"), fontSize = 13.sp, color = C.ClayDeep, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { help = !help }.padding(vertical = 6.dp, horizontal = 2.dp))
            }
            Ics.sourceOf(url)?.let { Text(tr("认出来了：{0}", it), fontSize = 12.sp, color = C.SageInk, modifier = Modifier.padding(top = 2.dp)) }
            if (help) Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Sand).padding(12.dp)) {
                for ((sys, path) in ICS_WHERE) {
                    Text(sys, fontSize = 13.sp, color = C.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(path, fontSize = 12.sp, color = C.Soft, lineHeight = 17.sp)
                }
                Text(tr("学习平台的链接里多是作业截止，知时只导入有上下课时间的课，截止日期会跳过。"), fontSize = 12.sp, color = C.Faint, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(tr("导入课表"), enabled = url.length > 8) { rememberIcsUrl(c, url); onGo(url) }
        }
    }
}
