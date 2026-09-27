package app.deepdepthdiver

import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.depthdiver.R
import com.depthdiver.Challenge
import com.depthdiver.Profile

/**
 * SharedPreferences mirror of the player's record stats.
 *
 * The reminder receiver runs in a cold process where libGDX is not
 * initialised, so `Profile` is unreachable. The running app mirrors the three
 * numbers it needs into plain SharedPreferences after `Gdx.app` exists.
 */
object ChallengeProgress {

    private const val PREFS = "depthdiver-notifications"
    private const val KEY_ASKED = "askedPostNotifications"
    private const val KEY_HAS_SNAPSHOT = "hasProgressSnapshot"
    private const val KEY_BEST_DEPTH = "bestDepth"
    private const val KEY_BEST_PEARLS = "bestRunPearls"
    private const val KEY_BEST_SCORE = "bestScore"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    fun wasPermissionAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASKED, false)

    /** Call only once libGDX has published `Gdx.app`. */
    fun syncFromProfile(context: Context) {
        runCatching {
            prefs(context).edit()
                .putBoolean(KEY_HAS_SNAPSHOT, true)
                .putInt(KEY_BEST_DEPTH, Profile.bestDepth().toInt())
                .putInt(KEY_BEST_PEARLS, Profile.bestRunPearls())
                .putInt(KEY_BEST_SCORE, Profile.bestScore())
                .apply()
        }
    }

    fun hasSnapshot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_SNAPSHOT, false)

    fun bestDepth(context: Context): Int = prefs(context).getInt(KEY_BEST_DEPTH, 0)

    fun bestPearls(context: Context): Int = prefs(context).getInt(KEY_BEST_PEARLS, 0)

    fun bestScore(context: Context): Int = prefs(context).getInt(KEY_BEST_SCORE, 0)
}

/**
 * Delivers a challenge reminder when its alarm fires, then arms the next one so
 * the cycle continues without the player ever launching the game.
 */
class ChallengeReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isDaily = action == ChallengeAlarms.ACTION_DAILY
        val isWeekly = action == ChallengeAlarms.ACTION_WEEKLY
        if (!isDaily && !isWeekly) return

        val active = Challenge.activeFor(currentUtcDay())

        val notificationId = if (isDaily) NOTIFICATION_ID_DAILY else NOTIFICATION_ID_WEEKLY
        val titleRes =
            if (isDaily) R.string.notification_daily_title else R.string.notification_weekly_title

        val body = buildString {
            append(objectiveText(context, active))
            append('\n')
            append(context.getString(R.string.notification_reward, Challenge.REWARD))
            if (ChallengeProgress.hasSnapshot(context)) {
                append('\n')
                append(
                    context.getString(
                        R.string.notification_progress,
                        ChallengeProgress.bestDepth(context),
                        ChallengeProgress.bestPearls(context),
                        ChallengeProgress.bestScore(context),
                    )
                )
            }
        }

        val launch = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, AndroidLauncher::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ChallengeAlarms.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(titleRes))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()

        val nm =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        nm.notify(notificationId, notification)

        if (isDaily) ChallengeAlarms.armDaily(context) else ChallengeAlarms.armWeekly(context)
    }

    private fun objectiveText(context: Context, active: Challenge.Active): String =
        when (active.kind) {
            Challenge.Kind.Depth ->
                context.getString(R.string.notification_objective_depth, active.target)

            Challenge.Kind.Pearls ->
                context.getString(R.string.notification_objective_pearls, active.target)

            Challenge.Kind.Score ->
                context.getString(R.string.notification_objective_score, active.target)
        }

    private companion object {
        const val NOTIFICATION_ID_DAILY = 1
        const val NOTIFICATION_ID_WEEKLY = 2

        fun currentUtcDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()
    }
}

/**
 * Alarms do not survive a reboot or an app update, so the reminders have to be
 * re-armed when either happens.
 */
class ChallengeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON -> ChallengeAlarms.armAll(context)
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
