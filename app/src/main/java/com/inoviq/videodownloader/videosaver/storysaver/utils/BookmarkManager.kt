package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkEntry(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val url: String
)

object BookmarkManager {
    private const val PREFS_NAME = "xxvi_bookmark_prefs"
    private const val KEY_BOOKMARKS = "bookmark_entries"

    fun getBookmarks(context: Context): MutableList<BookmarkEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_BOOKMARKS, null)
        val list = mutableListOf<BookmarkEntry>()

        if (jsonStr == null) {
            // First run: seed default bookmarks
            list.add(BookmarkEntry(title = "Instagram", url = "https://www.instagram.com/"))
            list.add(BookmarkEntry(title = "Facebook", url = "https://m.facebook.com/"))
            list.add(BookmarkEntry(title = "WhatsApp", url = "https://web.whatsapp.com/"))
            saveBookmarks(context, list)
            return list
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title", "Bookmark")
                val url = obj.optString("url", "")
                // Disallow home screen / empty bookmarks
                if (url.isNotBlank() && !url.equals("about:blank", true) && !url.equals("home", true) && !title.equals("Homepage", true)) {
                    list.add(
                        BookmarkEntry(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            title = title,
                            url = url
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addBookmark(context: Context, title: String, url: String): Boolean {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() ||
            trimmedUrl.equals("about:blank", ignoreCase = true) ||
            trimmedUrl.equals("home", ignoreCase = true) ||
            title.equals("Homepage", ignoreCase = true)) {
            return false
        }
        val list = getBookmarks(context)
        if (list.none { it.url.equals(trimmedUrl, ignoreCase = true) }) {
            list.add(0, BookmarkEntry(title = title, url = trimmedUrl))
            saveBookmarks(context, list)
            return true
        }
        return false
    }

    fun removeBookmark(context: Context, entry: BookmarkEntry) {
        val list = getBookmarks(context)
        list.removeAll { it.id == entry.id || it.url == entry.url }
        saveBookmarks(context, list)
    }

    private fun saveBookmarks(context: Context, list: List<BookmarkEntry>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BOOKMARKS, jsonArray.toString()).apply()
    }
}
