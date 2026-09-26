package app.deepdepthdiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.badlogic.gdx.Gdx
import com.depthdiver.Challenge
import com.depthdiver.Profile
import com.depthdiver.Strings
import java.util.Calendar

class ChallengeNotificationManager(private val context: Context) {

    private val nm: AndroidNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
    private val CHANNEL_ID = "depthdiver_challenges"
    private val CHANNEL_NAME = "Depth Diver Challenges"
    private val NOTIFICATION_ID_DAILY = 1
    private val NOTIFICATION_ID_WEEKLY = 2

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Daily and weekly challenge notifications"
            nm.createNotificationChannel(channel)
        }
    }

    /** Schedule daily challenge notification for next occurrence. */
    fun scheduleDailyChallenge() {
        val next = nextDailyTime()
        val delay = next - System.currentTimeMillis()
        if (delay > 0) {
            scheduleNotification(delay, NOTIFICATION_ID_DAILY, getDailyContent())
        }
    }

    /** Schedule weekly challenge notification (e.g., every Monday 10:00). */
    fun scheduleWeeklyChallenge() {
        val next = nextWeeklyTime()
        val delay = next - System.currentTimeMillis()
        if (delay > 0) {
            scheduleNotification(delay, NOTIFICATION_ID_WEEKLY, getWeeklyContent())
        }
    }

    private fun nextDailyTime(): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    private fun nextWeeklyTime(): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun getDailyContent(): Pair<String, String> {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        val active = Challenge.activeFor(day)
        return Pair(
            "${Strings.t("dailyChallenge")} Available!",
            "${active.summary(Profile.bestDepth(), Profile.bestRunPearls(), Profile.bestScore())} - ${Strings.t("reward")} ${Challenge.REWARD} ${Strings.t("pearls")}"
        )
    }

    private fun getWeeklyContent(): Pair<String, String> {
        return Pair(
            "${Strings.t("weeklyChallenge")} Active!",
            "${Strings.t("weeklyDesc")} - ${Strings.t("reward")} 200 ${Strings.t("pearls")}"
        )
    }

    private fun scheduleNotification(delayMs: Long, id: Int, content: Pair<String, String>) {
        val intent = Intent(context, AndroidLauncher::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(content.first)
            .setContentText(content.second)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        Handler(Looper.getMainLooper()).postDelayed({
            nm.notify(id, builder.build())
        }, delayMs)
    }

    fun cancelAll() {
        nm.cancelAll()
    }
}