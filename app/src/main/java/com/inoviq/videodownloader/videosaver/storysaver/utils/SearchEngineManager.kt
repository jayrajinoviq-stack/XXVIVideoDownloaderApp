package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import java.net.URLEncoder

object SearchEngineManager {

    private const val PREF_NAME = "search_engine_prefs"
    private const val KEY_ENGINE = "selected_engine"

    val ALL_ENGINES = listOf(
        "Google",
        "Bing",
        "Brave Search",
        "DuckDuckGo",
        "Startpage",
        "Ecosia",
        "Yahoo Search",
        "Kagi"
    )

    fun getSearchEngine(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ENGINE, "Google") ?: "Google"
    }

    fun setSearchEngine(context: Context, engineName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ENGINE, engineName).apply()
    }

    fun buildSearchUrl(context: Context, query: String): String {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim()
        }

        return when (getSearchEngine(context)) {
            "Bing" -> "https://www.bing.com/search?q=$encoded"
            "Brave Search" -> "https://search.brave.com/search?q=$encoded"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
            "Startpage" -> "https://www.startpage.com/sp/search?query=$encoded"
            "Ecosia" -> "https://www.ecosia.org/search?q=$encoded"
            "Yahoo Search" -> "https://search.yahoo.com/search?p=$encoded"
            "Kagi" -> "https://kagi.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded" // Google default
        }
    }
}
