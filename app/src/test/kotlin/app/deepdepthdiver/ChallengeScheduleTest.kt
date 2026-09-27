package app.deepdepthdiver

import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChallengeScheduleTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        zone: TimeZone = utc,
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    private fun partsOf(millis: Long, zone: TimeZone = utc): Calendar =
        Calendar.getInstance(zone).apply { timeInMillis = millis }

    // 2024-03-11 is a Monday.
    @Test
    fun dailyBeforeTenIsLaterTheSameDay() {
        val now = at(2024, Calendar.MARCH, 11, 8)
        val next = partsOf(ChallengeSchedule.nextDaily(now, utc))
        assertEquals(11, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, next.get(Calendar.MINUTE))
    }

    @Test
    fun dailyAfterTenRollsToTomorrow() {
        val now = at(2024, Calendar.MARCH, 11, 11)
        val next = partsOf(ChallengeSchedule.nextDaily(now, utc))
        assertEquals(12, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun dailyExactlyAtTenRollsToTomorrow() {
        val now = at(2024, Calendar.MARCH, 11, 10)
        val next = partsOf(ChallengeSchedule.nextDaily(now, utc))
        assertEquals(12, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun dailyOneMinuteBeforeTenStaysToday() {
        val now = at(2024, Calendar.MARCH, 11, 9, 59)
        val next = partsOf(ChallengeSchedule.nextDaily(now, utc))
        assertEquals(11, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun dailyCrossesMonthBoundary() {
        val now = at(2024, Calendar.MARCH, 31, 12)
        val next = partsOf(ChallengeSchedule.nextDaily(now, utc))
        assertEquals(Calendar.APRIL, next.get(Calendar.MONTH))
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun dailyCrossesYearBoundary() {
        val now = at(2024, Calendar.DECEMBER, 31, 12)
        val next = partsOf(ChallengeSchedule.nextDaily(now, utc))
        assertEquals(2025, next.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, next.get(Calendar.MONTH))
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun weeklyOnMondayBeforeTenIsToday() {
        val now = at(2024, Calendar.MARCH, 11, 9)
        val next = partsOf(ChallengeSchedule.nextWeekly(now, utc))
        assertEquals(11, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun weeklyOnMondayAfterTenRollsSevenDays() {
        val now = at(2024, Calendar.MARCH, 11, 10, 1)
        val next = partsOf(ChallengeSchedule.nextWeekly(now, utc))
        assertEquals(18, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun weeklyMidweekRollsToTheComingMonday() {
        val now = at(2024, Calendar.MARCH, 13, 9) // Wednesday
        val next = partsOf(ChallengeSchedule.nextWeekly(now, utc))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertTrue(next.timeInMillis > now, "weekly reminder must be in the future")
    }

    @Test
    fun weeklyOnSundayRollsToTomorrow() {
        val now = at(2024, Calendar.MARCH, 17, 9) // Sunday
        val next = partsOf(ChallengeSchedule.nextWeekly(now, utc))
        assertEquals(18, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun triggersAreAlwaysStrictlyInTheFuture() {
        var now = at(2024, Calendar.JANUARY, 1, 0)
        repeat(400) {
            assertTrue(ChallengeSchedule.nextDaily(now, utc) > now, "daily not future at $now")
            assertTrue(ChallengeSchedule.nextWeekly(now, utc) > now, "weekly not future at $now")
            now += 7L * 24 * 60 * 60 * 1000
        }
    }

    @Test
    fun remindersAreAlwaysAtTenAmLocal() {
        val zone = TimeZone.getTimeZone("America/New_York")
        var now = at(2024, Calendar.MARCH, 1, 3, zone = zone)
        repeat(200) {
            val daily = partsOf(ChallengeSchedule.nextDaily(now, zone), zone)
            val weekly = partsOf(ChallengeSchedule.nextWeekly(now, zone), zone)
            assertEquals(10, daily.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, daily.get(Calendar.MINUTE))
            assertEquals(10, weekly.get(Calendar.HOUR_OF_DAY))
            now += 13L * 60 * 60 * 1000
        }
    }

    @Test
    fun survivesDaylightSavingTransition() {
        // US DST starts 2024-03-10; run across it in a DST-observing zone.
        val zone = TimeZone.getTimeZone("America/New_York")
        var now = at(2024, Calendar.MARCH, 8, 12, zone = zone)
        repeat(10) {
            val next = ChallengeSchedule.nextDaily(now, zone)
            assertTrue(next > now, "DST day produced a non-future trigger")
            assertEquals(10, partsOf(next, zone).get(Calendar.HOUR_OF_DAY))
            now = next
        }
    }
}
