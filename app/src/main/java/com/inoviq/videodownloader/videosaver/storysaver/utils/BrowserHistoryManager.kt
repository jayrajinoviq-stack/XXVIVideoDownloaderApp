package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

object BrowserHistoryManager {
    private const val PREFS_NAME = "xxvi_history_prefs"
    private const val KEY_HISTORY = "history_entries"
    private const val KEY_MOCK_PURGED = "mock_history_purged_v2"

    fun getHistory(context: Context): MutableList<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // One-time purge of previously hardcoded mock Instagram items
        if (!prefs.getBoolean(KEY_MOCK_PURGED, false)) {
            val existing = prefs.getString(KEY_HISTORY, null)
            if (existing != null && (existing.contains("https://www.instagram.com/?hl=en") || existing.contains("instagram.com/?hl=en"))) {
                prefs.edit().putString(KEY_HISTORY, "[]").putBoolean(KEY_MOCK_PURGED, true).apply()
            } else {
                prefs.edit().putBoolean(KEY_MOCK_PURGED, true).apply()
            }
        }

        val jsonStr = prefs.getString(KEY_HISTORY, null)
        val list = mutableListOf<HistoryEntry>()

        if (jsonStr.isNullOrBlank()) {
            return list
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val url = obj.optString("url", "")
                if (url.isNotBlank() && !url.equals("about:blank", true) && !url.equals("home", true)) {
                    list.add(
                        HistoryEntry(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            title = obj.optString("title", "Website"),
                            url = url,
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistory(context: Context, title: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.equals("about:blank", true) || cleanUrl.equals("home", true)) return
        val list = getHistory(context)
        list.removeAll { it.url.equals(cleanUrl, ignoreCase = true) }
        val finalTitle = if (title.isBlank() || title.equals("about:blank", true) || title.equals("home", true)) {
            try { android.net.Uri.parse(cleanUrl).host ?: cleanUrl } catch (_: Exception) { cleanUrl }
        } else {
            title
        }
        list.add(0, HistoryEntry(title = finalTitle, url = cleanUrl))
        saveHistory(context, list)
    }

    fun removeHistory(context: Context, entry: HistoryEntry) {
        val list = getHistory(context)
        list.removeAll { it.id == entry.id || it.url == entry.url }
        saveHistory(context, list)
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, "[]").apply()
    }

    private const val KEY_RECENTLY_USED_ENABLED = "recently_used_enabled"

    fun isRecentlyUsedEnabled(context: Context): Boolean {
        return true
    }

    fun setRecentlyUsedEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RECENTLY_USED_ENABLED, true).apply()
    }

    /**
     * Returns unique websites visited recently.
     */
    fun getRecentlyUsedWebsites24h(context: Context): List<HistoryEntry> {
        val history = getHistory(context)
        val uniqueDomains = mutableSetOf<String>()
        val result = mutableListOf<HistoryEntry>()

        for (item in history) {
            if (item.url.isBlank() || item.url.equals("about:blank", true) || item.url.equals("home", true)) continue
            val domain = extractDomain(item.url, item.title)
            if (uniqueDomains.add(domain)) {
                result.add(item)
            }
            if (result.size >= 2) break
        }

        return result
    }

    fun extractDomain(url: String, fallback: String): String {
        return try {
            val host = android.net.Uri.parse(url).host
            if (!host.isNullOrBlank()) {
                host.removePrefix("www.").removePrefix("m.")
            } else {
                fallback.lowercase()
            }
        } catch (_: Exception) {
            fallback.lowercase()
        }
    }

    private fun saveHistory(context: Context, list: List<HistoryEntry>) {
        val jsonArray = JSONArray()
        list.take(200).forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
}
