package com.zhishi.timetable

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * 待办：标题、备注（选填）、截止时间（选填）、关联的课（选填，按课名记）、到点提醒、完成。
 * 只存在本机（SharedPreferences `todo` 的 `items`），课表备份 / 导出里一起带上。
 * due / doneAt / created 都是毫秒时间戳；due = -1 表示没设截止时间。
 */
data class Todo(
    val id: String, val title: String, val note: String = "", val due: Long = -1L, val remind: Boolean = false,
    val course: String = "", val done: Boolean = false, val doneAt: Long = 0L, val created: Long = 0L,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("t", title).put("no", note).put("due", due).put("re", remind)
        .put("co", course).put("dn", done).put("da", doneAt).put("cr", created)

    companion object {
        fun of(j: JSONObject) = Todo(j.optString("id"), j.optString("t"), j.optString("no", ""), j.optLong("due", -1L),
            j.optBoolean("re", false), j.optString("co", ""), j.optBoolean("dn", false), j.optLong("da", 0L), j.optLong("cr", 0L))
    }
}

/** 纯逻辑（单测用）：序列化、分组、按天归类。 */
object Todos {
    class Groups(val today: List<Todo>, val upcoming: List<Todo>, val done: List<Todo>)

    fun toJson(l: List<Todo>): JSONArray = JSONArray().also { a -> l.forEach { a.put(it.json()) } }
    fun fromJson(a: JSONArray?): List<Todo> = if (a == null) emptyList() else
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Todo.of(it) }?.takeIf { it.id.isNotBlank() && it.title.isNotBlank() } }
    fun parse(raw: String?): List<Todo> = try { fromJson(JSONArray(raw ?: "[]")) } catch (x: Exception) { emptyList() }

    /**
     * 今天 = 没完成、截止时间在今天结束之前（过期的也在这里，排在最前）；
     * 即将 = 没完成、截止在明天以后，或者没设截止（排在有日期的后面，按创建时间）；
     * 已完成 = 最近完成的在前。
     */
    fun group(all: List<Todo>, todayEnd: Long): Groups {
        val open = all.filter { !it.done }
        val today = open.filter { it.due in 0 until todayEnd }.sortedBy { it.due }
        val later = open.filter { it.due >= todayEnd }.sortedBy { it.due } + open.filter { it.due < 0 }.sortedBy { it.created }
        val done = all.filter { it.done }.sortedByDescending { it.doneAt }
        return Groups(today, later, done)
    }

    fun overdue(t: Todo, now: Long) = !t.done && t.due in 0 until now

    /** 截止时间落在哪一天（本地时区的 epoch 天）；没设返回 null。 */
    fun dayOf(t: Todo, tz: TimeZone = TimeZone.getDefault()): Long? =
        if (t.due < 0) null else Math.floorDiv(t.due + tz.getOffset(t.due), 86_400_000L)

    /** 某一天要交的待办（日历页用）。 */
    fun on(all: List<Todo>, day: Long, tz: TimeZone = TimeZone.getDefault()) = all.filter { dayOf(it, tz) == day }.sortedWith(compareBy({ it.done }, { it.due }))

    /** epoch 天 + 分钟 → 毫秒（本地时区）。 */
    fun at(day: Long, minute: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val k = Calendar.getInstance(tz); k.clear()
        val d = java.time.LocalDate.ofEpochDay(day)
        k.set(d.year, d.monthValue - 1, d.dayOfMonth, minute / 60, minute % 60)
        return k.timeInMillis
    }
    fun minuteOf(ms: Long, tz: TimeZone = TimeZone.getDefault()): Int = Calendar.getInstance(tz).apply { timeInMillis = ms }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}

/** 存储：改了就通知界面（Compose 读 ver）并重排提醒。 */
object TodoStore {
    val ver = mutableStateOf(0)
    private fun p(c: Context) = c.getSharedPreferences("todo", Context.MODE_PRIVATE)
    fun all(c: Context): List<Todo> = Todos.parse(p(c).getString("items", "[]"))
    fun save(c: Context, l: List<Todo>) { p(c).edit().putString("items", Todos.toJson(l).toString()).apply(); ver.value++ }
    fun put(c: Context, t: Todo) {
        val l = all(c); save(c, if (l.any { it.id == t.id }) l.map { if (it.id == t.id) t else it } else l + t)
        TodoAlarm.sync(c, t)
    }
    fun remove(c: Context, id: String) { save(c, all(c).filter { it.id != id }); TodoAlarm.cancel(c, id) }
    fun toggle(c: Context, t: Todo) = put(c, t.copy(done = !t.done, doneAt = if (!t.done) System.currentTimeMillis() else 0L))
    /** 从备份恢复：整体替换。 */
    fun replaceAll(c: Context, l: List<Todo>) { all(c).forEach { TodoAlarm.cancel(c, it.id) }; save(c, l); TodoAlarm.rescheduleAll(c) }
}

/** 到点提醒：有精确闹钟权限就用精确的，没有就用不精确的（系统可能晚几分钟）。 */
object TodoAlarm {
    const val CHANNEL = "todo"
    private fun pi(c: Context, id: String, create: Boolean): PendingIntent? =
        PendingIntent.getBroadcast(c, id.hashCode(), Intent(c, TodoReminder::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE))

    fun sync(c: Context, t: Todo) {
        if (!t.remind || t.done || t.due <= System.currentTimeMillis()) { cancel(c, t.id); return }
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val p = pi(c, t.id, true) ?: return
        try {
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.due, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.due, p)
        } catch (x: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.due, p)
        }
    }
    fun cancel(c: Context, id: String) {
        val p = pi(c, id, false) ?: return
        c.getSystemService(AlarmManager::class.java)?.cancel(p); p.cancel()
    }
    fun rescheduleAll(c: Context) = try { TodoStore.all(c).forEach { sync(c, it) } } catch (x: Exception) {}

    fun canNotify(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("待办提醒"), NotificationManager.IMPORTANCE_HIGH))
    }
}

/** 闹钟到了：发通知；开机 / 更新后：把提醒重新排上。 */
class TodoReminder : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> { TodoAlarm.rescheduleAll(c); return }
        }
        val id = i.getStringExtra("id") ?: return
        val t = TodoStore.all(c).firstOrNull { it.id == id } ?: return
        if (t.done || !TodoAlarm.canNotify(c)) return
        TodoAlarm.channel(c)
        val open = PendingIntent.getActivity(c, id.hashCode(), Intent(c, MainActivity::class.java).putExtra("open", "todo")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = listOf(t.course, t.note).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { tr("到截止时间了") }
        val n = android.app.Notification.Builder(c, TodoAlarm.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_todo).setContentTitle(t.title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        try { c.getSystemService(NotificationManager::class.java)?.notify(id.hashCode(), n) } catch (x: SecurityException) {}
    }
}
