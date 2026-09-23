package com.depthdiver

import com.badlogic.gdx.Preferences

/** Minimal in-memory [Preferences] used to isolate persistence in headless JVM tests. */
class TestPreferences(private val name: String = "test") : Preferences {

    private val store = HashMap<String, Any>()

    private fun put(key: String, value: Any): TestPreferences {
        store[key] = value
        return this
    }

    override fun putBoolean(key: String, value: Boolean) = put(key, value)
    override fun putInteger(key: String, value: Int) = put(key, value)
    override fun putLong(key: String, value: Long) = put(key, value)
    override fun putFloat(key: String, value: Float) = put(key, value)
    override fun putString(key: String, value: String?) = put(key, value.orEmpty())
    override fun put(values: Map<String, Any?>): Preferences {
        values.forEach { (k, v) -> put(k, v!!) }
        return this
    }

    override fun getBoolean(key: String): Boolean = getBoolean(key, false)
    override fun getInteger(key: String): Int = getInteger(key, 0)
    override fun getLong(key: String): Long = getLong(key, 0L)
    override fun getFloat(key: String): Float = getFloat(key, 0f)
    override fun getString(key: String): String = getString(key, "")

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        store[key] as? Boolean ?: defValue

    override fun getInteger(key: String, defValue: Int): Int =
        (store[key] as? Number)?.toInt() ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (store[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (store[key] as? Number)?.toFloat() ?: defValue

    override fun getString(key: String, defValue: String): String =
        store[key] as? String ?: defValue

    override fun get(): Map<String, Any?> = store

    override fun contains(key: String): Boolean = store.containsKey(key)

    override fun clear() {
        store.clear()
    }

    override fun remove(key: String) {
        store.remove(key)
    }

    override fun flush() {
        // in-memory only
    }

    override fun toString(): String = "TestPreferences($name)$store"
}