package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Caches ad unit IDs and remote settings into SharedPreferences.
 * Prevents zero-ad sessions when Firebase is slow or unreachable on launch.
 */
object AdsPrefsCache {
    private const val TAG = "AdsPrefsCache"
    private const val PREFS_NAME = "ads_sdk_cache"

    private const val KEY_BANNER_ID = "cached_banner_id"
    private const val KEY_INTERSTITIAL_ID = "cached_interstitial_id"
    private const val KEY_NATIVE_ID = "cached_native_id"
    private const val KEY_APP_OPEN_ID = "cached_app_open_id"
    private const val KEY_REWARDED_ID = "cached_rewarded_id"
    private const val KEY_REWARDED_INTER_ID = "cached_rewarded_inter_id"
    private const val KEY_CLICK_THRESHOLD = "cached_click_threshold"
    private const val KEY_REWARDED_CLICK_THRESHOLD = "cached_rewarded_click_threshold"
    private const val KEY_REWARDED_INTER_CLICK_THRESHOLD = "cached_rewarded_inter_click_threshold"
    private const val KEY_APP_OPEN_CLICK_THRESHOLD = "cached_app_open_click_threshold"
    private const val KEY_APP_OPEN_RESUME_CLICK_THRESHOLD = "cached_app_open_resume_click_threshold"
    private const val KEY_IS_APP_OPEN_ON_RESUME = "cached_is_app_open_on_resume"
    private const val KEY_ADS_URL = "cached_ads_url"
    private const val KEY_MAIN_AD_IDS_JSON = "cached_main_ad_ids_json"
    private const val KEY_SUB_AD_IDS_JSON = "cached_sub_ad_ids_json"
    private const val KEY_IS_MAIN_AD_IDS = "cached_is_main_ad_ids"
    private const val KEY_IS_SUB_AD_IDS = "cached_is_sub_ad_ids"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveIds(
        context: Context,
        bannerId: String,
        interstitialId: String,
        nativeId: String,
        appOpenId: String,
        rewardedId: String,
        rewardedInterId: String = "",
        clickThreshold: Long = 1L,
        rewardedClickThreshold: Long = 1L,
        rewardedInterClickThreshold: Long = 1L,
        appOpenClickThreshold: Long = 1L,
        appOpenResumeClickThreshold: Long = 1L,
        isAppOpenOnResume: Boolean = true,
        adsUrl: String = ""
    ) {
        try {
            getPrefs(context).edit().apply {
                if (bannerId.isNotBlank()) putString(KEY_BANNER_ID, bannerId)
                if (interstitialId.isNotBlank()) putString(KEY_INTERSTITIAL_ID, interstitialId)
                if (nativeId.isNotBlank()) putString(KEY_NATIVE_ID, nativeId)
                if (appOpenId.isNotBlank()) putString(KEY_APP_OPEN_ID, appOpenId)
                if (rewardedId.isNotBlank()) putString(KEY_REWARDED_ID, rewardedId)
                if (rewardedInterId.isNotBlank()) putString(KEY_REWARDED_INTER_ID, rewardedInterId)
                putLong(KEY_CLICK_THRESHOLD, clickThreshold)
                putLong(KEY_REWARDED_CLICK_THRESHOLD, rewardedClickThreshold)
                putLong(KEY_REWARDED_INTER_CLICK_THRESHOLD, rewardedInterClickThreshold)
                putLong(KEY_APP_OPEN_CLICK_THRESHOLD, appOpenClickThreshold)
                putLong(KEY_APP_OPEN_RESUME_CLICK_THRESHOLD, appOpenResumeClickThreshold)
                putBoolean(KEY_IS_APP_OPEN_ON_RESUME, isAppOpenOnResume)
                if (adsUrl.isNotBlank()) putString(KEY_ADS_URL, adsUrl)
                apply()
            }
            Log.d(TAG, "Ad configuration cached successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache ad configuration", e)
        }
    }

    fun getBannerId(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_BANNER_ID, default) ?: default

    fun getInterstitialId(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_INTERSTITIAL_ID, default) ?: default

    fun getNativeId(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_NATIVE_ID, default) ?: default

    fun getAppOpenId(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_APP_OPEN_ID, default) ?: default

    fun getRewardedId(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_REWARDED_ID, default) ?: default

    fun getRewardedInterId(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_REWARDED_INTER_ID, default) ?: default

    fun getClickThreshold(context: Context, default: Long = 1): Long =
        getPrefs(context).getLong(KEY_CLICK_THRESHOLD, default)

    fun getRewardedClickThreshold(context: Context, default: Long = 1): Long =
        getPrefs(context).getLong(KEY_REWARDED_CLICK_THRESHOLD, default)

    fun getRewardedInterClickThreshold(context: Context, default: Long = 1): Long =
        getPrefs(context).getLong(KEY_REWARDED_INTER_CLICK_THRESHOLD, default)

    fun getAppOpenClickThreshold(context: Context, default: Long = 1): Long =
        getPrefs(context).getLong(KEY_APP_OPEN_CLICK_THRESHOLD, default)

    fun getAppOpenResumeClickThreshold(context: Context, default: Long = 1): Long =
        getPrefs(context).getLong(KEY_APP_OPEN_RESUME_CLICK_THRESHOLD, default)

    fun isAppOpenOnResume(context: Context, default: Boolean = true): Boolean =
        getPrefs(context).getBoolean(KEY_IS_APP_OPEN_ON_RESUME, default)

    fun getAdsUrl(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_ADS_URL, default) ?: default

    fun saveAdBundles(
        context: Context,
        mainAdIdsJson: String,
        subAdIdsJson: String,
        isMainAdIds: Boolean,
        isSubAdIds: Boolean
    ) {
        try {
            getPrefs(context).edit().apply {
                if (mainAdIdsJson.isNotBlank()) putString(KEY_MAIN_AD_IDS_JSON, mainAdIdsJson)
                if (subAdIdsJson.isNotBlank()) putString(KEY_SUB_AD_IDS_JSON, subAdIdsJson)
                putBoolean(KEY_IS_MAIN_AD_IDS, isMainAdIds)
                putBoolean(KEY_IS_SUB_AD_IDS, isSubAdIds)
                apply()
            }
            Log.d(TAG, "Ad bundles cached successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache ad bundles", e)
        }
    }

    fun getMainAdIdsJson(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_MAIN_AD_IDS_JSON, default) ?: default

    fun getSubAdIdsJson(context: Context, default: String = ""): String =
        getPrefs(context).getString(KEY_SUB_AD_IDS_JSON, default) ?: default

    fun isMainAdIds(context: Context, default: Boolean = true): Boolean =
        getPrefs(context).getBoolean(KEY_IS_MAIN_AD_IDS, default)

    fun isSubAdIds(context: Context, default: Boolean = false): Boolean =
        getPrefs(context).getBoolean(KEY_IS_SUB_AD_IDS, default)
}
