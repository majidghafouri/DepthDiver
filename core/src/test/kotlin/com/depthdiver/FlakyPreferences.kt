package com.depthdiver

import com.badlogic.gdx.Preferences

/**
 * Mimics libGDX [com.badlogic.gdx.backends.android.AndroidPreferences] semantics
 * that made the persistence bug possible: `put*` values land in a per-wrapper
 * pending edit and only become visible after [flush] — which is a **no-op when
 * the wrapper has no pending edit**. So a caller that does `prefs.putX(...)`
 * then `prefs.flush()` across *two freshly created wrappers* silently drops
 * the write. With the retained-instance fix the write survives; without it the
 * regression test below fails.
 */
class FlakyPreferences : Preferences {

    private val committed = HashMap<String, Any?>()
    private var pending: MutableMap<String, Any>? = null

    override fun putBoolean(key: String, val_: Boolean): Preferences {
        pending().put(key, val_)
        return this
    }

    override fun putInteger(key: String, `val`: Int): Preferences {
        pending().put(key, `val`)
        return this
    }

    override fun putLong(key: String, `val`: Long): Preferences {
        pending().put(key, `val`)
        return this
    }

    override fun putFloat(key: String, `val`: Float): Preferences {
        pending().put(key, `val`)
        return this
    }

    override fun putString(key: String, val_: String?): Preferences {
        pending().put(key, val_.orEmpty())
        return this
    }

    override fun put(values: Map<String, Any?>): Preferences {
        values.forEach { (k, v) -> if (v != null) pending().put(k, v) }
        return this
    }

    private fun pending(): MutableMap<String, Any> {
        if (pending == null) pending = HashMap()
        return pending!!
    }

    override fun getBoolean(key: String): Boolean = getBoolean(key, false)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = committed[key] as? Boolean ?: defValue
    override fun getInteger(key: String): Int = getInteger(key, 0)
    override fun getInteger(key: String, defValue: Int): Int = committed[key] as? Int ?: defValue
    override fun getLong(key: String): Long = getLong(key, 0L)
    override fun getLong(key: String, defValue: Long): Long = committed[key] as? Long ?: defValue
    override fun getFloat(key: String): Float = getFloat(key, 0f)
    override fun getFloat(key: String, defValue: Float): Float = committed[key] as? Float ?: defValue
    override fun getString(key: String): String = getString(key, "")
    override fun getString(key: String, defValue: String): String = committed[key] as? String ?: defValue

    override fun get(): Map<String, Any?> = committed

    override fun contains(key: String): Boolean = committed.containsKey(key)

    override fun clear() {
        pending().clear()
        committed.clear()
    }

    override fun flush() {
        pending?.let { committed.putAll(it) }
        pending = null
    }

    override fun remove(key: String) {
        pending().remove(key)
    }
}