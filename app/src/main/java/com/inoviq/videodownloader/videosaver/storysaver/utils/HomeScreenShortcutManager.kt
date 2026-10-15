package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import com.inoviq.videodownloader.videosaver.storysaver.R
import org.json.JSONArray
import org.json.JSONObject

data class HomeScreenShortcut(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val url: String,
    val iconType: String = "custom"
)

object HomeScreenShortcutManager {
    const val MAX_SHORTCUTS = 8
    private const val PREFS_NAME = "xxvi_home_shortcuts_prefs"
    private const val KEY_SHORTCUTS = "home_shortcuts_entries"

    /**
     * Retrieves the current list of shortcuts from SharedPreferences.
     * If first run, seeds with the 5 default platform shortcuts (Facebook, Instagram, TikTok, WhatsApp, Twitter).
     */
    fun getShortcuts(context: Context): MutableList<HomeScreenShortcut> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SHORTCUTS, null)
        val list = mutableListOf<HomeScreenShortcut>()

        if (jsonStr.isNullOrBlank()) {
            // Seed the 5 default items on initial download/open
            list.add(HomeScreenShortcut(id = "facebook", title = "Facebook", url = "https://m.facebook.com/", iconType = "facebook"))
            list.add(HomeScreenShortcut(id = "instagram", title = "Instagram", url = "https://www.instagram.com/", iconType = "instagram"))
            list.add(HomeScreenShortcut(id = "tiktok", title = "TikTok", url = "https://www.tiktok.com/", iconType = "tiktok"))
            list.add(HomeScreenShortcut(id = "whatsapp", title = "WhatsApp", url = "https://web.whatsapp.com/", iconType = "whatsapp"))
            list.add(HomeScreenShortcut(id = "twitter", title = "Twitter", url = "https://twitter.com/", iconType = "twitter"))
            saveShortcuts(context, list)
            return list
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", System.currentTimeMillis().toString())
                val title = obj.optString("title", "Website")
                val url = obj.optString("url", "")
                val iconType = obj.optString("iconType", "custom")
                if (url.isNotBlank()) {
                    list.add(HomeScreenShortcut(id, title, url, iconType))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    /**
     * Persists shortcuts list to SharedPreferences.
     */
    fun saveShortcuts(context: Context, list: List<HomeScreenShortcut>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("iconType", item.iconType)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SHORTCUTS, jsonArray.toString()).apply()
    }

    /**
     * Adds a new website shortcut. Returns false if MAX_SHORTCUTS (8) is already reached.
     */
    fun addShortcut(context: Context, title: String, url: String): Boolean {
        val list = getShortcuts(context)
        if (list.size >= MAX_SHORTCUTS) return false

        val trimmedUrl = url.trim()
        val finalUrl = when {
            trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
            else -> "https://$trimmedUrl"
        }
        val targetTitle = if (title.isBlank()) {
            deriveTitle(finalUrl)
        } else {
            title.trim()
        }

        val iconType = detectIconType(finalUrl, targetTitle)
        val newShortcut = HomeScreenShortcut(
            id = System.currentTimeMillis().toString(),
            title = targetTitle,
            url = finalUrl,
            iconType = iconType
        )

        list.add(newShortcut)
        saveShortcuts(context, list)
        return true
    }

    /**
     * Removes a shortcut by its ID. Can remove any shortcut from the list.
     */
    fun removeShortcut(context: Context, id: String): Boolean {
        val list = getShortcuts(context)
        val removed = list.removeAll { it.id == id }
        if (removed) {
            saveShortcuts(context, list)
        }
        return removed
    }

    private fun detectIconType(url: String, title: String): String {
        val lowerUrl = url.lowercase()
        val lowerTitle = title.lowercase()
        return when {
            lowerUrl.contains("facebook") || lowerTitle.contains("facebook") -> "facebook"
            lowerUrl.contains("instagram") || lowerTitle.contains("instagram") -> "instagram"
            lowerUrl.contains("tiktok") || lowerTitle.contains("tiktok") -> "tiktok"
            lowerUrl.contains("whatsapp") || lowerTitle.contains("whatsapp") -> "whatsapp"
            lowerUrl.contains("twitter") || lowerUrl.contains("x.com") || lowerTitle.contains("twitter") -> "twitter"
            lowerUrl.contains("youtube") || lowerTitle.contains("youtube") -> "youtube"
            lowerUrl.contains("google") || lowerTitle.contains("google") -> "google"
            else -> "custom"
        }
    }

    fun resolveIconRes(shortcut: HomeScreenShortcut): Int {
        val lowerUrl = shortcut.url.lowercase()
        val lowerTitle = shortcut.title.lowercase()
        val iconType = shortcut.iconType.lowercase()

        return when {
            iconType == "facebook" || lowerUrl.contains("facebook") || lowerTitle.contains("facebook") -> R.drawable.img_logos_facebook
            iconType == "instagram" || lowerUrl.contains("instagram") || lowerTitle.contains("instagram") -> R.drawable.img_logo_instagram
            iconType == "tiktok" || lowerUrl.contains("tiktok") || lowerTitle.contains("tiktok") -> R.drawable.img_logos_tiktok
            iconType == "whatsapp" || lowerUrl.contains("whatsapp") || lowerTitle.contains("whatsapp") -> R.drawable.img_logos_whatsapp
            iconType == "twitter" || lowerUrl.contains("twitter") || lowerUrl.contains("x.com") || lowerTitle.contains("twitter") -> R.drawable.img_logo_twitter_x
            iconType == "youtube" || lowerUrl.contains("youtube") || lowerTitle.contains("youtube") -> R.drawable.ic_logo_youtube
            iconType == "google" || lowerUrl.contains("google") || lowerTitle.contains("google") -> R.drawable.ic_logo_google
            else -> R.drawable.ic_globe
        }
    }

    private fun deriveTitle(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: url
            host.removePrefix("www.").substringBefore(".")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } catch (_: Exception) {
            "Website"
        }
    }
}
