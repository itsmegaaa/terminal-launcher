package com.ega.terminallauncher.mru

import android.content.Context
import android.content.SharedPreferences

interface MruRepository {
    fun get(query: String): String?
    fun put(query: String, packageName: String)
    fun removePackage(packageName: String)
    fun clear()
}

class InMemoryMruRepository : MruRepository {
    private val map = mutableMapOf<String, String>()

    override fun get(query: String): String? {
        val key = query.trim().removeSuffix("?").lowercase()
        return map[key]
    }

    override fun put(query: String, packageName: String) {
        val key = query.trim().removeSuffix("?").lowercase()
        map[key] = packageName
    }

    override fun removePackage(packageName: String) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value == packageName) {
                iterator.remove()
            }
        }
    }

    override fun clear() {
        map.clear()
    }
}

class SharedPrefsMruRepository(context: Context) : MruRepository {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ash_launcher_mru", Context.MODE_PRIVATE)

    override fun get(query: String): String? {
        val key = query.trim().removeSuffix("?").lowercase()
        return prefs.getString(key, null)
    }

    override fun put(query: String, packageName: String) {
        val key = query.trim().removeSuffix("?").lowercase()
        prefs.edit().putString(key, packageName).apply()
    }

    override fun removePackage(packageName: String) {
        val all = prefs.all
        val editor = prefs.edit()
        var changed = false
        for ((key, value) in all) {
            if (value == packageName) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
