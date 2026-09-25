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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()
        config.useImmersiveMode = true
        val instance = DepthDiverGame()
        game = instance
        initialize(instance, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { dispatchBack() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
        }
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
}
