package com.epodbreg.stih.data

/** Settings store for tests: a plain map, so the app's real settings are never touched */
class MemoryStore : SettingsStore {
    private val values = mutableMapOf<String, Any>()

    override fun contains(key: String) = key in values
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun getString(key: String) = values[key] as? String
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putString(key: String, value: String) { values[key] = value }
}
