package com.ega.terminallauncher.alias

import android.content.Context
import android.content.SharedPreferences

interface AliasRepository {
    fun getAll(): Map<String, String>
    fun get(name: String): String?
    fun put(name: String, target: String)
    fun remove(name: String): Boolean
    fun clear()
}

class InMemoryAliasRepository : AliasRepository {
    private val map = mutableMapOf<String, String>()

    override fun getAll(): Map<String, String> = synchronized(map) { map.toMap() }

    override fun get(name: String): String? = synchronized(map) { map[name.lowercase()] }

    override fun put(name: String, target: String) {
        synchronized(map) {
            map[name.lowercase()] = target
        }
    }

    override fun remove(name: String): Boolean = synchronized(map) {
        map.remove(name.lowercase()) != null
    }

    override fun clear() {
        synchronized(map) {
            map.clear()
        }
    }
}

class SharedPrefsAliasRepository(context: Context) : AliasRepository {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getAll(): Map<String, String> {
        val all = prefs.all
        val result = mutableMapOf<String, String>()
        for ((k, v) in all) {
            if (v is String) {
                result[k.lowercase()] = v
            }
        }
        return result
    }

    override fun get(name: String): String? {
        return prefs.getString(name.lowercase(), null)
    }

    override fun put(name: String, target: String) {
        prefs.edit().putString(name.lowercase(), target).apply()
    }

    override fun remove(name: String): Boolean {
        val key = name.lowercase()
        return if (prefs.contains(key)) {
            prefs.edit().remove(key).apply()
            true
        } else {
            false
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "ash_launcher_alias"
    }
}
