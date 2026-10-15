package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import java.net.URLEncoder

object SearchEngineManager {

    private const val PREF_NAME = "search_engine_prefs"
    private const val KEY_ENGINE = "selected_engine"

    val ALL_ENGINES = listOf(
        "Google",
        "Bing",
        "DuckDuckGo",
        "Yahoo Search",
        "Brave Search",
        "Ecosia"
    )

    fun getSearchEngine(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ENGINE, "Google") ?: "Google"
        // If saved engine is Startpage (which blocks WebViews with verification loops) or invalid, auto-fix to Google
        if (saved.equals("Startpage", ignoreCase = true) || !ALL_ENGINES.contains(saved)) {
            prefs.edit().putString(KEY_ENGINE, "Google").apply()
            return "Google"
        }
        return saved
    }

    fun setSearchEngine(context: Context, engineName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val targetEngine = if (engineName.equals("Startpage", ignoreCase = true) || !ALL_ENGINES.contains(engineName)) {
            "Google"
        } else {
            engineName
        }
        prefs.edit().putString(KEY_ENGINE, targetEngine).apply()
    }

    fun buildSearchUrl(context: Context, query: String): String {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim()
        }

        return when (getSearchEngine(context)) {
            "Bing" -> "https://www.bing.com/search?q=$encoded"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
            "Yahoo Search" -> "https://search.yahoo.com/search?p=$encoded"
            "Brave Search" -> "https://search.brave.com/search?q=$encoded"
            "Ecosia" -> "https://www.ecosia.org/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded" // Google default (instant, Chrome standard)
        }
    }
}
