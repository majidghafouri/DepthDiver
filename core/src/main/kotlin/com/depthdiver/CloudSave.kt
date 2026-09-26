package com.depthdiver

/**
 * Cloud save interface for cross-device profile synchronization.
 * 
 * The game stores all persistent state in the Profile object (Preferences-backed).
 * This interface provides a cloud seam to sync that state across devices.
 * 
 * Implementations should:
 * - Serialize the full Profile state to a byte array
 * - Upload to cloud when online
 * - Download and merge on app start / resume
 * - Handle conflicts (last-write-wins or user choice)
 */
interface CloudSaveService {

    /** Serialize current Profile state to a byte array for upload. */
    fun snapshot(): ByteArray

    /** Restore Profile state from a downloaded snapshot. Returns true if applied. */
    fun restore(bytes: ByteArray): Boolean

    /** Whether a cloud save is available for the current user. */
    fun isAvailable(): Boolean

    /** Upload the current snapshot to cloud. */
    fun upload()

    /** Download latest snapshot from cloud. */
    fun download()

    /** Called when the user explicitly requests a sync (e.g., from settings). */
    fun syncNow()
}

object CloudSave {

    @Volatile
    private var customImpl: CloudSaveService? = null

    fun instance(): CloudSaveService = customImpl ?: LocalCloudSave

    fun setCustomImpl(impl: CloudSaveService?) {
        customImpl = impl
    }

    /** Local-only implementation that just logs (no-op for cloud). */
    private object LocalCloudSave : CloudSaveService {
        override fun snapshot(): ByteArray = ByteArray(0)
        override fun restore(bytes: ByteArray): Boolean = false
        override fun isAvailable(): Boolean = false
        override fun upload() {}
        override fun download() {}
        override fun syncNow() {}
    }
}