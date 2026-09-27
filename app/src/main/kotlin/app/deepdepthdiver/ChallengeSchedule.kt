package app.deepdepthdiver

import java.util.Calendar
import java.util.TimeZone

/**
 * Trigger-time maths for the daily and weekly challenge reminders.
 *
 * Kept free of Android and AlarmManager so the rollover behaviour (which is
 * where off-by-one-day bugs live) can be unit tested.
 */
object ChallengeSchedule {

    const val HOUR_OF_DAY = 10
    const val MINUTE = 0
    const val SECOND = 0
    const val MILLISECOND = 0

    /** First 10:00 strictly after [nowMillis]. */
    fun nextDaily(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        nextOccurrence(nowMillis, zone, weekly = false)

    /** First Monday 10:00 strictly after [nowMillis]. */
    fun nextWeekly(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        nextOccurrence(nowMillis, zone, weekly = true)

    private fun nextOccurrence(nowMillis: Long, zone: TimeZone, weekly: Boolean): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, SECOND)
            set(Calendar.MILLISECOND, MILLISECOND)
        }

        if (weekly) {
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }

        // The reminder is for "the next one", so an occurrence that has already
        // started today (or is happening right now) rolls forward.
        if (cal.timeInMillis <= nowMillis) {
            cal.add(if (weekly) Calendar.WEEK_OF_YEAR else Calendar.DAY_OF_MONTH, 1)
        }

        // Daylight-saving transitions can make the wall-clock arithmetic land
        // back on the current instant; make sure we always return a future time.
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }

        return cal.timeInMillis
    }
}
