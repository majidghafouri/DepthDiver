package app.deepdepthdiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.depthdiver.R

/**
 * Arms the daily/weekly challenge reminders as real OS alarms.
 *
 * These used to be armed with `Handler.postDelayed`, which cannot work for a
 * ~24h delay: the process is long dead before the handler fires, and nothing is
 * registered with AlarmManager at all.
 */
object ChallengeAlarms {

    const val ACTION_DAILY = "app.depthdiver.CHALLENGE_DAILY"
    const val ACTION_WEEKLY = "app.depthdiver.CHALLENGE_WEEKLY"
    const val CHANNEL_ID = "depthdiver_challenges"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"

    private const val REQUEST_DAILY = 4202
    private const val REQUEST_WEEKLY = 4203

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            AndroidNotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = context.getString(R.string.notification_channel_description)
        nm.createNotificationChannel(channel)
    }

    /** Arm (or re-arm) both reminders. Safe to call on every launch. */
    fun armAll(context: Context) {
        armDaily(context)
        armWeekly(context)
    }

    fun armDaily(context: Context) {
        val triggerAt = ChallengeSchedule.nextDaily(System.currentTimeMillis())
        setAlarm(context, ACTION_DAILY, triggerAt, REQUEST_DAILY)
    }

    fun armWeekly(context: Context) {
        val triggerAt = ChallengeSchedule.nextWeekly(System.currentTimeMillis())
        setAlarm(context, ACTION_WEEKLY, triggerAt, REQUEST_WEEKLY)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, ACTION_DAILY, REQUEST_DAILY))
        am.cancel(pendingIntent(context, ACTION_WEEKLY, REQUEST_WEEKLY))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        nm.cancelAll()
    }

    private fun setAlarm(context: Context, action: String, triggerAt: Long, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAndAllowWhileIdle survives Doze and needs no exact-alarm permission,
        // which matters because USE_EXACT_ALARM is restricted by Play policy and
        // a daily reminder gains nothing from being pinned to the second.
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context, action, requestCode),
        )
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ChallengeReminderReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
