package com.zhishi.timetable

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class IntlImportTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    /** 排课系统常见：一条每周重复的课，期中放假那周去掉；另有一次挪了时间。 */
    @Test fun weeklySeriesWithExdateAndMove() {
        val ics = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:abc
SUMMARY:Analysis I (MA0001) VO
DTSTART;TZID=Europe/Berlin:20261019T101500
DTEND;TZID=Europe/Berlin:20261019T114500
RRULE:FREQ=WEEKLY;UNTIL=20261207T235959Z
EXDATE;TZID=Europe/Berlin:20261102T101500
LOCATION:MI HS 1
DESCRIPTION:Dozent: Prof. Dr. Müller\nWöchentlich
END:VEVENT
BEGIN:VEVENT
UID:abc
RECURRENCE-ID;TZID=Europe/Berlin:20261116T101500
SUMMARY:Analysis I (MA0001) VO
DTSTART;TZID=Europe/Berlin:20261117T101500
DTEND;TZID=Europe/Berlin:20261117T114500
LOCATION:MI HS 1
END:VEVENT
END:VCALENDAR"""
        val r = Ics.parse(ics, -1, 20, berlin)
        assertEquals(day(2026, 10, 19), r.firstMonday)
        val mon = r.slots.first { 1 in it.days }
        assertEquals(615 to 705, mon.from to mon.to)
        assertEquals("MI HS 1", mon.room)
        assertEquals("Prof. Dr. Müller", mon.teacher)
        // 10/19 起 8 周（到 12/7），去掉 11/2（第 3 周）和挪走的 11/16（第 5 周）
        assertEquals(setOf(1, 2, 4, 6, 7, 8), mon.weeks)
        val tue = r.slots.first { 2 in it.days }
        assertEquals(setOf(5), tue.weeks)
    }

    /** Canvas：每次课一条、时间是 UTC，作业截止 DTSTART = DTEND，全天事件只有日期。 */
    @Test fun canvasFeed() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val sb = StringBuilder("BEGIN:VCALENDAR\n")
        for (w in 0 until 3) {
            val d = LocalDate.of(2026, 9, 1).plusDays(7L * w)   // 周二
            val ds = "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)
            sb.append("BEGIN:VEVENT\nUID:event-calendar-event-$w\nSUMMARY:Lecture [CS 101-001]\nDTSTART:${ds}T130000Z\nDTEND:${ds}T141500Z\nLOCATION:ENG 105\nEND:VEVENT\n")
        }
        sb.append("BEGIN:VEVENT\nUID:event-assignment-9\nSUMMARY:HW 1 [CS 101-001]\nDTSTART:20260905T035900Z\nDTEND:20260905T035900Z\nEND:VEVENT\n")
        sb.append("BEGIN:VEVENT\nUID:event-calendar-event-99\nSUMMARY:Labor Day\nDTSTART;VALUE=DATE:20260907\nEND:VEVENT\n")
        sb.append("END:VCALENDAR\n")
        val r = Ics.parse(sb.toString(), -1, 20, ny)
        assertEquals(2, r.deadlines)
        assertEquals(1, r.slots.size)
        val s = r.slots[0]
        assertEquals("CS 101-001 Lecture", s.name)
        assertEquals(setOf(2), s.days)
        assertEquals(9 * 60 to 10 * 60 + 15, s.from to s.to)   // 13:00Z = 9:00 EDT
        assertEquals(setOf(1, 2, 3), s.weeks)
    }

    @Test fun sameTimeTwoRoomsMerged() {
        val ics = """BEGIN:VCALENDAR
BEGIN:VEVENT
UID:1
SUMMARY:Physik
DTSTART;TZID=Europe/Berlin:20261020T080000
DTEND;TZID=Europe/Berlin:20261020T100000
LOCATION:HS 1
END:VEVENT
BEGIN:VEVENT
UID:2
SUMMARY:Physik
DTSTART;TZID=Europe/Berlin:20261020T080000
DTEND;TZID=Europe/Berlin:20261020T100000
LOCATION:HS 2
END:VEVENT
END:VCALENDAR"""
        val r = Ics.parse(ics, -1, 20, berlin)
        assertEquals(1, r.slots.size); assertEquals("HS 1 / HS 2", r.slots[0].room)
    }

    @Test fun keepsExistingTermWhenItFits() {
        val term = day(2026, 10, 12)
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:x\nSUMMARY:Algebra\nDTSTART;TZID=Europe/Berlin:20261021T090000\nDTEND;TZID=Europe/Berlin:20261021T103000\nEND:VEVENT\nEND:VCALENDAR"
        val r = Ics.parse(ics, term, 20, berlin)
        assertNull(r.firstMonday); assertEquals(setOf(2), r.slots[0].weeks)
    }

    @Test fun urls() {
        assertEquals("https://x.instructure.com/feeds/calendars/user_abc.ics", Ics.normalizeUrl(" webcal://x.instructure.com/feeds/calendars/user_abc.ics "))
        assertEquals("Canvas", Ics.sourceOf("https://x.instructure.com/feeds/calendars/user_abc.ics"))
        assertEquals("Moodle", Ics.sourceOf("https://moodle.uni.edu/calendar/export_execute.php?userid=1&authtoken=z"))
    }

    @Test fun sisDays() {
        assertEquals(setOf(1, 3, 5), SisSheet.days("MWF"))
        assertEquals(setOf(2, 4), SisSheet.days("TTh"))
        assertEquals(setOf(2, 4), SisSheet.days("TR"))
        assertEquals(setOf(1, 3), SisSheet.days("Mon/Wed"))
        assertEquals(setOf(2, 4), SisSheet.days("Tu Th"))
        assertTrue(SisSheet.days("ENG 105").isEmpty())
    }

    /** Workday「View My Courses」另存的 CSV。 */
    @Test fun workdayCsv() {
        val csv = "My Enrolled Courses\n" +
            "Course Listing,Credits,Instructional Format,Meeting Patterns,Instructor,Start Date,End Date\n" +
            "\"CS 101 - Intro to Programming\",4,Lecture,\"MWF | 9:00 AM - 9:50 AM | ENG 105\",Ada Lovelace,08/24/2026,12/11/2026\n" +
            "\"MATH 221 - Calculus\",4,Lecture,\"TTh | 1:00 PM - 2:15 PM | SCI 210\nF | 10:00 AM - 10:50 AM | SCI 101\",Alan Turing,08/24/2026,12/11/2026\n"
        val ms = SisSheet.meets(SisSheet.csv(csv))
        assertEquals(3, ms.size)
        val r = SisSheet.slots(ms, -1, 20)
        assertEquals(day(2026, 8, 24), r.firstMonday)
        val cs = r.slots.filter { it.name.startsWith("CS 101") }
        assertEquals(setOf(1, 3, 5), cs.flatMap { it.days }.toSet())
        assertEquals(540 to 590, cs[0].from to cs[0].to)
        assertEquals("ENG 105", cs[0].room)
        assertEquals("Ada Lovelace", cs[0].teacher)
        assertEquals((1..16).toSet(), cs[0].weeks)
        val fri = r.slots.first { it.name.startsWith("MATH") && 5 in it.days }
        assertEquals(600 to 650, fri.from to fri.to); assertEquals("SCI 101", fri.room)
        val tth = r.slots.filter { it.name.startsWith("MATH") && it.from == 13 * 60 }
        assertEquals(setOf(2, 4), tth.flatMap { it.days }.toSet())
    }

    @Test fun bannerColumns() {
        val csv = "Course,Title,Days,Time,Location,Instructor\nCHEM 101,General Chemistry,MW,10:00 am-11:15 am,CHEM 120,Marie Curie\n"
        val ms = SisSheet.meets(SisSheet.csv(csv))
        assertEquals(1, ms.size); assertEquals(setOf(1, 3), ms[0].days); assertEquals(600 to 675, ms[0].from to ms[0].to)
    }
}
