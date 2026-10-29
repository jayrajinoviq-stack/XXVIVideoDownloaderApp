package com.inoviq.videodownloader.videosaver.storysaver.repository

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedLanguageCode: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var selectedGender: String
        get() = prefs.getString(KEY_GENDER, "FEMALE") ?: "FEMALE"
        set(value) = prefs.edit().putString(KEY_GENDER, value).apply()

    var selectedAgeRange: String
        get() = prefs.getString(KEY_AGE, "22-31") ?: "22-31"
        set(value) = prefs.edit().putString(KEY_AGE, value).apply()

    var selectedCategory: String
        get() = prefs.getString(KEY_CATEGORY, "all") ?: "all"
        set(value) = prefs.edit().putString(KEY_CATEGORY, value).apply()

    var selectedWatchCategory: String
        get() = prefs.getString(KEY_WATCH_CATEGORY, "disney") ?: "disney"
        set(value) = prefs.edit().putString(KEY_WATCH_CATEGORY, value).apply()

    var isLanguageSelected: Boolean
        get() = prefs.getBoolean(KEY_LANGUAGE_SELECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_LANGUAGE_SELECTED, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    companion object {
        private const val PREFS_NAME = "xxvi_user_preferences"
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_LANGUAGE_SELECTED = "key_language_selected"
        private const val KEY_GENDER = "key_gender"
        private const val KEY_AGE = "key_age"
        private const val KEY_CATEGORY = "key_category"
        private const val KEY_WATCH_CATEGORY = "key_watch_category"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
    }
}
