package com.design.home.helpers

import android.content.Context

object RecentAppsManager {
    private const val PREFS_NAME = "recent_apps_prefs"
    private const val KEY_RECENT = "recent_apps_list"
    private const val PREFIX_COUNT = "launch_count_"
    private const val PREFIX_TIMESTAMP = "last_timestamp_"
    const val MAX_RECENT = 8

    val DEFAULT_POPULAR_PACKAGES = listOf(
        "com.android.chrome",
        "com.google.android.youtube",
        "com.whatsapp",
        "com.instagram.android",
        "org.telegram.messenger",
        "com.google.android.apps.photos",
        "com.google.android.gm",
        "com.google.android.apps.maps",
        "com.android.vending",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.android.settings",
        "com.android.camera",
        "com.google.android.GoogleCamera"
    )

    fun recordAppUsed(context: Context, packageName: String, activityName: String) {
        if (packageName.isBlank()) return
        val identifier = "$packageName/$activityName"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Update MRU order
        val current = getRecentIdentifiers(context).toMutableList()
        current.remove(identifier)
        current.add(0, identifier)
        while (current.size > MAX_RECENT) {
            current.removeAt(current.size - 1)
        }

        // 2. Increment launch count & update timestamp
        val currentCount = prefs.getInt("$PREFIX_COUNT$identifier", 0)
        prefs.edit()
            .putString(KEY_RECENT, current.joinToString(","))
            .putInt("$PREFIX_COUNT$identifier", currentCount + 1)
            .putLong("$PREFIX_TIMESTAMP$identifier", System.currentTimeMillis())
            .apply()
    }

    fun getRecentIdentifiers(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    fun getFrequentIdentifiers(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all
            .filterKeys { it.startsWith(PREFIX_COUNT) }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix(PREFIX_COUNT)
                val count = (value as? Int) ?: 0
                if (count > 0 && id.isNotBlank()) id to count else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
