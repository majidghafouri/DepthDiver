package app.deepdepthdiver

import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.depthdiver.DepthDiverGame

class AndroidLauncher : AndroidApplication() {

    private var game: DepthDiverGame? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var notificationManager: ChallengeNotificationManager? = null

    // Kept in SharedPreferences rather than a field: an Activity field is lost
    // on process death, which would re-prompt the player on every cold start.
    private val notificationPermissionAsked: Boolean
        get() = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ASKED_NOTIFICATIONS, false)

    private fun markNotificationPermissionAsked() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ASKED_NOTIFICATIONS, true)
            .apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()
        config.useImmersiveMode = true
        val instance = DepthDiverGame()
        game = instance

        // TODO: Initialize GPGS leaderboard when play-services-games dependency is available
        // val leaderboardId = getString(R.string.gpgs_leaderboard_id)
        // val gpgsLeaderboard = GpgsLeaderboard(this, leaderboardId)
        // Leaderboard.setCustomImpl(gpgsLeaderboard)

        // TODO: Initialize GPGS cloud save when play-services-games dependency is available
        // val cloudSave = GpgsCloudSave(this)
        // CloudSave.setCustomImpl(cloudSave)

        initialize(instance, config)

        // Notifications read profile state through libGDX Preferences, so this
        // must run after initialize() has published Gdx.app.
        scheduleChallengeNotifications()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { dispatchBack() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
        }
    }

    /**
     * Android 13+ requires POST_NOTIFICATIONS to be granted at runtime. Without
     * asking, a fresh install never has it, so every daily/weekly reminder was
     * silently dropped by the permission check.
     */
    private fun scheduleChallengeNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            if (notificationPermissionAsked) {
                // Already asked once and still denied: respect that and stop
                // prompting instead of nagging on every launch.
                return
            }
            markNotificationPermissionAsked()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        startNotificationReminders()
    }

    private fun startNotificationReminders() {
        if (!hasNotificationPermission()) return
        runCatching {
            notificationManager = ChallengeNotificationManager(this)
            notificationManager?.scheduleDailyChallenge()
            notificationManager?.scheduleWeeklyChallenge()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            // Only arm the reminders once the player has actually granted it.
            startNotificationReminders()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @Deprecated("Handled by OnBackInvokedCallback where predictive back is active")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!dispatchBack()) super.onBackPressed()
    }

    private fun dispatchBack(): Boolean {
        val handled = game?.handleSystemBack() == true
        if (!handled) finish()
        return handled
    }

    override fun onDestroy() {
        backCallback?.let { callback ->
            runCatching { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback) }
            backCallback = null
        }
        game = null
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 4201
        const val PREFS = "depthdiver-notifications"
        const val KEY_ASKED_NOTIFICATIONS = "askedPostNotifications"
    }
}
