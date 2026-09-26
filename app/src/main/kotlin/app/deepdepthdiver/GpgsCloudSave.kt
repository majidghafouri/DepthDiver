package app.deepdepthdiver

import android.app.Activity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.depthdiver.CloudSaveService

/**
 * Stub implementation for Google Play Games Services cloud save (Saved Games / Snapshots).
 * 
 * TODO: When the GPGS dependency is available (play-services-games:23.1.0),
 * uncomment the full implementation below and add the dependency to app/build.gradle.kts.
 * 
 * The CloudSaveService interface in core is designed as a cloud seam - this class
 * implements it and can be swapped in via CloudSave.setCustomImpl().
 */
class GpgsCloudSave(
    private val activity: Activity,
) : CloudSaveService {

    override fun snapshot(): ByteArray = LocalCloudSave.snapshot()
    override fun restore(bytes: ByteArray): Boolean = LocalCloudSave.restore(bytes)
    override fun isAvailable(): Boolean = LocalCloudSave.isAvailable()
    override fun upload() = LocalCloudSave.upload()
    override fun download() = LocalCloudSave.download()
    override fun syncNow() = LocalCloudSave.syncNow()

    /**
     * Local-only placeholder implementation.
     * When GPGS is enabled, this will be replaced with Saved Games (Snapshots) logic.
     */
    private object LocalCloudSave : CloudSaveService {
        override fun snapshot(): ByteArray = ByteArray(0)
        override fun restore(bytes: ByteArray): Boolean = false
        override fun isAvailable(): Boolean = false
        override fun upload() {}
        override fun download() {}
        override fun syncNow() {}
    }
}