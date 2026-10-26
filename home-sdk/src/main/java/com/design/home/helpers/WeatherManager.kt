package com.design.home.helpers

import android.content.Context
import com.design.commons.helpers.ensureBackgroundThread
import com.design.home.R
import com.design.home.widgets.SmartTopWidgetProvider
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class WeatherData(
    val temp: Int,
    val description: String,
    val iconRes: Int
)

object WeatherManager {
    private const val PREFS_NAME = "smart_widget_weather_prefs"
    private const val KEY_TEMP = "temp"
    private const val KEY_DESC = "description"
    private const val KEY_ICON = "icon_res"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val MIN_UPDATE_INTERVAL_MS = 20 * 60 * 1000L // 20 minutes

    fun getCachedWeather(context: Context): WeatherData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val temp = prefs.getInt(KEY_TEMP, 30)
        val desc = prefs.getString(KEY_DESC, "Clear Sky") ?: "Clear Sky"
        val icon = prefs.getInt(KEY_ICON, R.drawable.ic_weather_sun)
        return WeatherData(temp, desc, icon)
    }

    fun refreshWeather(context: Context, force: Boolean = false, callback: ((Boolean) -> Unit)? = null) {
        ensureBackgroundThread {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
            val now = System.currentTimeMillis()

            if (!force && (now - lastUpdate) < MIN_UPDATE_INTERVAL_MS) {
                callback?.invoke(true)
                return@ensureBackgroundThread
            }

            val lat = 28.6139
            val lon = 77.2090

            var success = false
            var conn: HttpURLConnection? = null
            try {
                val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").roundToInt()
                    val weatherCode = current.getInt("weather_code")
                    val (desc, icon) = getWeatherDetails(weatherCode)

                    prefs.edit()
                        .putInt(KEY_TEMP, temp)
                        .putString(KEY_DESC, desc)
                        .putInt(KEY_ICON, icon)
                        .putLong(KEY_LAST_UPDATE, now)
                        .apply()

                    SmartTopWidgetProvider.updateAllWidgets(context)
                    success = true
                }
            } catch (_: Exception) {
                success = false
            } finally {
                conn?.disconnect()
            }

            callback?.invoke(success)
        }
    }

    private fun getWeatherDetails(code: Int): Pair<String, Int> {
        return when (code) {
            0 -> Pair("Clear Sky", R.drawable.ic_weather_sun)
            1 -> Pair("Mainly Clear", R.drawable.ic_weather_sun)
            2 -> Pair("Partly Cloudy", R.drawable.ic_weather_partly_cloudy)
            3 -> Pair("Overcast", R.drawable.ic_weather_cloud)
            45, 48 -> Pair("Foggy", R.drawable.ic_weather_cloud)
            51, 53, 55 -> Pair("Drizzle", R.drawable.ic_weather_rain)
            56, 57 -> Pair("Freezing Drizzle", R.drawable.ic_weather_snow)
            61, 63, 65 -> Pair("Rain", R.drawable.ic_weather_rain)
            66, 67 -> Pair("Freezing Rain", R.drawable.ic_weather_snow)
            71, 73, 75, 77 -> Pair("Snow", R.drawable.ic_weather_snow)
            80, 81, 82 -> Pair("Rain Showers", R.drawable.ic_weather_rain)
            85, 86 -> Pair("Snow Showers", R.drawable.ic_weather_snow)
            95, 96, 99 -> Pair("Thunderstorm", R.drawable.ic_weather_thunder)
            else -> Pair("Clear Sky", R.drawable.ic_weather_sun)
        }
    }
}
