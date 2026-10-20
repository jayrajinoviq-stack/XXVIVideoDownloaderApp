package com.design.home.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.design.home.R
import com.design.home.helpers.WeatherManager

class SmartTopWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
        WeatherManager.refreshWeather(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WEATHER) {
            WeatherManager.refreshWeather(context, force = true)
        }
    }

    companion object {
        const val ACTION_REFRESH_WEATHER = "com.design.home.widgets.ACTION_REFRESH_WEATHER"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, SmartTopWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (widgetId in widgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (_: Exception) {
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_smart_top)

                // Apply cached / current weather
                val weather = WeatherManager.getCachedWeather(context)
                views.setTextViewText(R.id.widget_weather_temp, "${weather.temp}°")
                views.setTextViewText(R.id.widget_weather_desc, weather.description)
                views.setImageViewResource(R.id.widget_weather_icon, weather.iconRes)

                appWidgetManager.updateAppWidget(widgetId, views)
            } catch (_: Exception) {
            }
        }
    }
}
