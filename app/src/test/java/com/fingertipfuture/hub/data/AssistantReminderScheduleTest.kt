package com.fingertipfuture.hub.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReminderScheduleTest {

    @Test
    fun dailyReminderUsesTodayWhenTimeIsStillAhead() {
        val now = time(2026, Calendar.JANUARY, 5, 10, 0)

        val trigger = nextReminderTriggerAtMillis(reminder(11, 30), now)

        assertEquals(90 * 60_000L, trigger!! - now)
    }

    @Test
    fun dailyReminderRollsToTomorrowAfterTimePassed() {
        val now = time(2026, Calendar.JANUARY, 5, 10, 0)

        val trigger = nextReminderTriggerAtMillis(reminder(9, 30), now)!!
        val next = Calendar.getInstance().apply { timeInMillis = trigger }

        assertEquals(Calendar.TUESDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, next.get(Calendar.MINUTE))
    }

    @Test
    fun oneTimeReminderInThePastDoesNotSchedule() {
        val now = time(2026, Calendar.JANUARY, 5, 10, 0)

        val trigger = nextReminderTriggerAtMillis(
            reminder(9, 0).copy(
                repeat = ReminderRepeat.ONCE,
                scheduledDate = "2026-01-05",
            ),
            now,
        )

        assertNull(trigger)
    }

    @Test
    fun weeklyReminderFindsSelectedWeekday() {
        val mondayMorning = time(2026, Calendar.JANUARY, 5, 7, 0)

        val trigger = nextReminderTriggerAtMillis(
            reminder(8, 0).copy(
                repeat = ReminderRepeat.WEEKLY,
                weekday = 3,
                weekdays = listOf(3),
            ),
            mondayMorning,
        )!!
        val next = Calendar.getInstance().apply { timeInMillis = trigger }

        assertEquals(Calendar.WEDNESDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun weeklyReminderUsesTheEarliestSelectedWeekday() {
        val mondayMorning = time(2026, Calendar.JANUARY, 5, 7, 0)

        val trigger = nextReminderTriggerAtMillis(
            reminder(8, 0).copy(
                repeat = ReminderRepeat.WEEKLY,
                weekdays = listOf(3, 5),
            ),
            mondayMorning,
        )!!
        val next = Calendar.getInstance().apply { timeInMillis = trigger }

        assertEquals(Calendar.WEDNESDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun legacyWeekdayReminderStillSkipsWeekend() {
        val fridayAfterReminderTime = time(2026, Calendar.JANUARY, 9, 12, 0)

        val trigger = nextReminderTriggerAtMillis(
            reminder(8, 0).copy(repeat = ReminderRepeat.WEEKDAYS),
            fridayAfterReminderTime,
        )!!
        val next = Calendar.getInstance().apply { timeInMillis = trigger }

        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertTrue(trigger - fridayAfterReminderTime > 24 * 60 * 60_000L)
    }

    private fun reminder(hour: Int, minute: Int) = AssistantReminder(
        id = "rem-test",
        assistantId = "default",
        title = "测试",
        prompt = "测试任务",
        hour = hour,
        minute = minute,
    )

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
