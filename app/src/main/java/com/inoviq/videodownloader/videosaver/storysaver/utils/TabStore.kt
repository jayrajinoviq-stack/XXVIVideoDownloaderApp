package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import com.inoviq.videodownloader.videosaver.storysaver.model.BrowserTab
import org.json.JSONArray
import org.json.JSONObject

object TabStore {
    private const val PREFS_NAME = "xxvi_tabs_prefs"
    private const val KEY_TABS = "saved_browser_tabs"

    fun getSavedTabs(context: Context): MutableList<BrowserTab> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TABS, null)
        val list = mutableListOf<BrowserTab>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", System.currentTimeMillis().toString())
                    val title = obj.optString("title", "Homepage")
                    val url = obj.optString("url", "")
                    val isCurrent = obj.optBoolean("isCurrent", false)
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                    list.add(
                        BrowserTab(
                            id = id,
                            title = title,
                            url = url,
                            isCurrent = isCurrent,
                            createdAt = createdAt
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            list.add(
                BrowserTab(
                    id = "1",
                    title = "Homepage",
                    url = "",
                    isCurrent = true
                )
            )
        } else if (list.none { it.isCurrent }) {
            list.first().isCurrent = true
        }

        return list
    }

    fun saveTabs(context: Context, tabs: List<BrowserTab>) {
        if (tabs.isEmpty()) return
        val jsonArray = JSONArray()
        tabs.forEach { tab ->
            val obj = JSONObject().apply {
                put("id", tab.id)
                put("title", tab.title)
                put("url", tab.url)
                put("isCurrent", tab.isCurrent)
                put("createdAt", tab.createdAt)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TABS, jsonArray.toString()).apply()
    }
}
