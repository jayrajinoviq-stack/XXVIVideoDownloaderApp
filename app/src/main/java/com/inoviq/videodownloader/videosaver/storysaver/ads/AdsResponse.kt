package com.inoviq.videodownloader.videosaver.storysaver.ads

import android.content.Context
import android.util.Log
import com.inoviq.videodownloader.videosaver.storysaver.BuildConfig
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.AdNetworkType
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.AdRequestBuilder
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object AdsResponse {
    private const val TAG = "AdsResponse"
    const val KEY_IS_SHOW_ADS = "isShowAdsURL"
    const val KEY_ADS_URL = "AdsURL"
    const val KEY_CLICK_THRESHOLD = "clickCount"
    var isShowAdsURL: Boolean = false
    var isNewAdsID: Boolean = false
    var isChildDirected: Boolean = false
    var ADS_URL: String = ""
    var ADS_URL_LIST: List<String> = emptyList()
    var AdsImgClickURl: String = ""
    var CLICK_THRESHOLD: Long = 3L
    var REWARDED_CLICK_THRESHOLD: Long = 1L
    var REWARDED_INTERSTITIAL_CLICK_THRESHOLD: Long = 1L
    var APP_OPEN_CLICK_THRESHOLD: Long = 1L
    var APP_OPEN_RESUME_CLICK_THRESHOLD: Long = 1L
    var isAppOpenOnResume: Boolean = true

    // Launcher Specific Controls
    var isLauncherAdsShow: Boolean = BuildConfig.DEBUG
    var LAUNCHER_CLICK_THRESHOLD: Long = 2L
    
    var isLauncherWebAdShow: Boolean = false

    const val KEY_SHOW_ONBOARDING_EVERY_TIME = "ShowOnboardingEveryTime"
    var ShowOnboardingEveryTime: Boolean = false

    // Fixed URLs (Not from Firebase)

    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_FALLBACK_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/8691691433"
    const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_REWARDED_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/5354046379"

    var BANNER_ID: String = if (BuildConfig.DEBUG) TEST_BANNER_ID else ""
    var FALLBACK_BANNER_ID: String = ""

    var INTERSTITIAL_ID: String = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_ID else ""
    var FALLBACK_INTERSTITIAL_ID: String = if (BuildConfig.DEBUG) TEST_FALLBACK_INTERSTITIAL_ID else ""

    var NATIVE_ID: String = if (BuildConfig.DEBUG) TEST_NATIVE_ID else ""
    var FALLBACK_NATIVE_ID: String = ""

    var APP_OPEN_ID: String = if (BuildConfig.DEBUG) TEST_APP_OPEN_ID else ""
    var FALLBACK_APP_OPEN_ID: String = ""

    var REWARDED_ID: String = if (BuildConfig.DEBUG) TEST_REWARDED_ID else ""
    var FALLBACK_REWARDED_ID: String = ""

    var REWARDED_INTERSTITIAL_ID: String = if (BuildConfig.DEBUG) TEST_REWARDED_INTERSTITIAL_ID else ""
    var FALLBACK_REWARDED_INTERSTITIAL_ID: String = ""

    // Remote Web Native Image URLs (Firebase JSON)
    var AdsLargeImgList: List<String> = emptyList()
    var AdsSmallImgList: List<String> = emptyList()

    // Local Drawables for Web Ads Fallback
    val AdsLargeDrawableList: List<Int> = listOf(
        R.drawable.ad_large_img_1,
    )

    val AdsSmallDrawableList: List<Int> = listOf(
        R.drawable.ad_small_img_1,
    )

    fun initialize(context: Context? = null, isDebug: Boolean = BuildConfig.DEBUG) {
        Log.d(TAG, "initialize() called, isDebug = $isDebug")
        if (isDebug) {
            loadDebugAds()
        } else {
            loadReleaseAds(context)
        }
    }


    fun loadDebugAds() {
        Log.d(TAG, "loadDebugAds: Populating default test IDs for manual control")
        isShowAdsURL = true
        isNewAdsID = false
        CLICK_THRESHOLD = 1L
        REWARDED_CLICK_THRESHOLD = 5L
        REWARDED_INTERSTITIAL_CLICK_THRESHOLD = 5L
        APP_OPEN_CLICK_THRESHOLD = 1L
        APP_OPEN_RESUME_CLICK_THRESHOLD = 1L
        isAppOpenOnResume = true
        isLauncherAdsShow = true

        LAUNCHER_CLICK_THRESHOLD = 2L

        isLauncherWebAdShow = false
        ShowOnboardingEveryTime = true

        BANNER_ID = TEST_BANNER_ID
        FALLBACK_BANNER_ID = ""

        INTERSTITIAL_ID = TEST_INTERSTITIAL_ID
        FALLBACK_INTERSTITIAL_ID = TEST_FALLBACK_INTERSTITIAL_ID

        NATIVE_ID = TEST_NATIVE_ID
        FALLBACK_NATIVE_ID = ""

        APP_OPEN_ID = TEST_APP_OPEN_ID
        FALLBACK_APP_OPEN_ID = ""

        REWARDED_ID = TEST_REWARDED_ID
        FALLBACK_REWARDED_ID = "" 

        REWARDED_INTERSTITIAL_ID = TEST_REWARDED_INTERSTITIAL_ID
        FALLBACK_REWARDED_INTERSTITIAL_ID = ""

        val remoteUrl = RemoteConfigHelper.getString(KEY_ADS_URL)
        val parsedRemote = parseAdsUrlList(remoteUrl)

        ADS_URL_LIST = if (parsedRemote.size >= 2) {
            parsedRemote
        } else if (parsedRemote.size == 1) {
            listOf(parsedRemote[0], "https://www.google.com/")
        } else {
            listOf("https://5128.fflivegame.com/", "https://www.google.com/")
        }


        ADS_URL = ADS_URL_LIST.first()

        AdsImgClickURl = ADS_URL

        parseWebNativeAds()
    }

    data class AdIdsBundle(
        val bannerAd: String = "",
        val appOpenAd: String = "",
        val interstitialAd: String = "",
        val nativeAd: String = "",
        val rewardAd: String = "",
        val rewardInterAd: String = ""
    )

    private fun parseAdIdsJson(jsonStr: String): AdIdsBundle {
        if (jsonStr.isBlank()) return AdIdsBundle()
        return try {
            val json = Gson().fromJson(jsonStr, JsonObject::class.java)
            AdIdsBundle(
                bannerAd = json.get("BannerAd")?.asString ?: json.get("bannerAd")?.asString ?: "",
                appOpenAd = json.get("AppOpenAd")?.asString ?: json.get("appOpenAd")?.asString ?: "",
                interstitialAd = json.get("InterstialAd")?.asString ?: json.get("InterstitialAd")?.asString ?: json.get("interstitialAd")?.asString ?: "",
                nativeAd = json.get("NativeAd")?.asString ?: json.get("nativeAd")?.asString ?: "",
                rewardAd = json.get("RewardAd")?.asString ?: json.get("rewardAd")?.asString ?: "",
                rewardInterAd = json.get("RewardInterAd")?.asString ?: json.get("rewardInterAd")?.asString ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AdIds JSON: $jsonStr", e)
            AdIdsBundle()
        }
    }

    fun loadReleaseAds(context: Context? = null) {
        Log.d(TAG, "loadReleaseAds: Fetching real IDs and conditions from Firebase")

        val isMain = getRemoteBoolean("isMainAdIds", "is_main_ad_ids", default = true)
        val isSub = getRemoteBoolean("isSubAdIds", "is_sub_ad_ids", default = true)
        isShowAdsURL = getRemoteBoolean(KEY_IS_SHOW_ADS, "is_show_ads_url", default = false)

        val mainAdJson = getRemoteString("MainAdIds", "main_ad_ids")
        val subAdJson = getRemoteString("SubAdIds", "sub_ad_ids")

        val mainAds = parseAdIdsJson(mainAdJson)
        val subAds = parseAdIdsJson(subAdJson)

        Log.d(TAG, "Firebase Flags: isMain=$isMain, isSub=$isSub, isShowAdsURL=$isShowAdsURL")
        Log.d(TAG, "Parsed MainAds: $mainAds")
        Log.d(TAG, "Parsed SubAds: $subAds")

        when {
            isMain && isSub -> {
                BANNER_ID = mainAds.bannerAd.ifBlank { subAds.bannerAd }
                FALLBACK_BANNER_ID = if (BANNER_ID == mainAds.bannerAd) subAds.bannerAd else ""

                APP_OPEN_ID = mainAds.appOpenAd.ifBlank { subAds.appOpenAd }
                FALLBACK_APP_OPEN_ID = if (APP_OPEN_ID == mainAds.appOpenAd) subAds.appOpenAd else ""

                INTERSTITIAL_ID = mainAds.interstitialAd.ifBlank { subAds.interstitialAd }
                FALLBACK_INTERSTITIAL_ID = if (INTERSTITIAL_ID == mainAds.interstitialAd) subAds.interstitialAd else ""

                NATIVE_ID = mainAds.nativeAd.ifBlank { subAds.nativeAd }
                FALLBACK_NATIVE_ID = if (NATIVE_ID == mainAds.nativeAd) subAds.nativeAd else ""

                REWARDED_ID = mainAds.rewardAd.ifBlank { subAds.rewardAd }
                FALLBACK_REWARDED_ID = if (REWARDED_ID == mainAds.rewardAd) subAds.rewardAd else ""

                REWARDED_INTERSTITIAL_ID = mainAds.rewardInterAd.ifBlank { subAds.rewardInterAd }
                FALLBACK_REWARDED_INTERSTITIAL_ID = if (REWARDED_INTERSTITIAL_ID == mainAds.rewardInterAd) subAds.rewardInterAd else ""
            }

            !isMain && isSub -> {
                BANNER_ID = subAds.bannerAd
                FALLBACK_BANNER_ID = ""

                APP_OPEN_ID = subAds.appOpenAd
                FALLBACK_APP_OPEN_ID = ""

                INTERSTITIAL_ID = subAds.interstitialAd
                FALLBACK_INTERSTITIAL_ID = ""

                NATIVE_ID = subAds.nativeAd
                FALLBACK_NATIVE_ID = ""

                REWARDED_ID = subAds.rewardAd
                FALLBACK_REWARDED_ID = ""

                REWARDED_INTERSTITIAL_ID = subAds.rewardInterAd
                FALLBACK_REWARDED_INTERSTITIAL_ID = ""
            }

            // Case 3: isMain true, isSub false -> Show Main ads only (if fail, show nothing)
            isMain && !isSub -> {
                BANNER_ID = mainAds.bannerAd
                FALLBACK_BANNER_ID = ""

                APP_OPEN_ID = mainAds.appOpenAd
                FALLBACK_APP_OPEN_ID = ""

                INTERSTITIAL_ID = mainAds.interstitialAd
                FALLBACK_INTERSTITIAL_ID = ""

                NATIVE_ID = mainAds.nativeAd
                FALLBACK_NATIVE_ID = ""

                REWARDED_ID = mainAds.rewardAd
                FALLBACK_REWARDED_ID = ""

                REWARDED_INTERSTITIAL_ID = mainAds.rewardInterAd
                FALLBACK_REWARDED_INTERSTITIAL_ID = ""
            }

            // Case 4: isMain false, isSub false -> No AdMob/AdX ads. If isShowAdsURL true, web ads will show
            else -> {
                BANNER_ID = ""
                FALLBACK_BANNER_ID = ""

                APP_OPEN_ID = ""
                FALLBACK_APP_OPEN_ID = ""

                INTERSTITIAL_ID = ""
                FALLBACK_INTERSTITIAL_ID = ""

                NATIVE_ID = ""
                FALLBACK_NATIVE_ID = ""

                REWARDED_ID = ""
                FALLBACK_REWARDED_ID = ""

                REWARDED_INTERSTITIAL_ID = ""
                FALLBACK_REWARDED_INTERSTITIAL_ID = ""
            }
        }

        // Target URL
        val remoteUrl = getRemoteString(KEY_ADS_URL, "ads_url")
        val parsedRemote = parseAdsUrlList(remoteUrl)
        ADS_URL_LIST = if (parsedRemote.isNotEmpty()) {
            parsedRemote
        } else if (remoteUrl.isNotBlank()) {
            listOf(remoteUrl)
        } else {
            listOf("https://5128.fflivegame.com/")
        }

        ADS_URL = ADS_URL_LIST.firstOrNull() ?: "https://5128.fflivegame.com/"
        val rawClickUrl = getRemoteString("AdsImgClickURl", "ads_img_click_url")
        AdsImgClickURl = if (rawClickUrl.isNotBlank()) rawClickUrl else ADS_URL

        // Counts & Thresholds
        val rawThreshold = getRemoteLong(KEY_CLICK_THRESHOLD, "click_threshold", "interstitial_click_count", default = 1L)
        CLICK_THRESHOLD = if (rawThreshold <= 0L) 1L else rawThreshold

        val rawRewardedThreshold = getRemoteLong("rewarded_click_count", "rewarded_count", default = 1L)
        REWARDED_CLICK_THRESHOLD = if (rawRewardedThreshold <= 0L) 1L else rawRewardedThreshold

        val rawRewardedInterThreshold = getRemoteLong("rewarded_inter_click_count", default = 2L)
        REWARDED_INTERSTITIAL_CLICK_THRESHOLD = if (rawRewardedInterThreshold <= 0L) 2L else rawRewardedInterThreshold

        val rawAppOpenThreshold = getRemoteLong("app_open_click_count", default = 1L)
        APP_OPEN_CLICK_THRESHOLD = if (rawAppOpenThreshold <= 0L) 1L else rawAppOpenThreshold

        val rawAppOpenResumeThreshold = getRemoteLong("app_open_resume_count", default = 1L)
        APP_OPEN_RESUME_CLICK_THRESHOLD = if (rawAppOpenResumeThreshold <= 0L) 1L else rawAppOpenResumeThreshold

        isAppOpenOnResume = getRemoteBoolean("is_app_open_on_resume", default = true)

        // Launcher Specific Controls from Firebase Remote Config
        isLauncherAdsShow = getRemoteBoolean("isLauncherAdsShow", "is_launcher_ads_show", default = false)
        val rawLauncherThreshold = getRemoteLong("launcherClickCount", "launcher_click_count", default = 2L)
        LAUNCHER_CLICK_THRESHOLD = if (rawLauncherThreshold <= 0L) 1L else rawLauncherThreshold
        isLauncherWebAdShow = getRemoteBoolean("isLauncherWebAdShow", "is_launcher_web_ad_show", default = false)
        ShowOnboardingEveryTime = getRemoteBoolean(KEY_SHOW_ONBOARDING_EVERY_TIME, "showOnboardingEveryTime", default = false)


        parseWebNativeAds()

        Log.d(TAG, "Release Ads Final Setup: Interstitial='$INTERSTITIAL_ID' (fallback='$FALLBACK_INTERSTITIAL_ID'), AppOpen='$APP_OPEN_ID' (fallback='$FALLBACK_APP_OPEN_ID'), Native='$NATIVE_ID', Banner='$BANNER_ID', isShowAdsURL=$isShowAdsURL, clickThreshold=$CLICK_THRESHOLD")
    }

    private fun getRemoteString(vararg keys: String): String {
        for (key in keys) {
            val value = RemoteConfigHelper.getString(key)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun getRemoteBoolean(vararg keys: String, default: Boolean): Boolean {
        for (key in keys) {
            val value = RemoteConfigHelper.getString(key)
            if (value.isNotBlank()) {
                return value.equals("true", ignoreCase = true) || RemoteConfigHelper.getBoolean(key, default)
            }
        }
        return RemoteConfigHelper.getBoolean(keys.firstOrNull() ?: "", default)
    }

    private fun getRemoteLong(vararg keys: String, default: Long): Long {
        for (key in keys) {
            val value = RemoteConfigHelper.getString(key)
            if (value.isNotBlank()) {
                value.toLongOrNull()?.let { return it }
            }
        }
        return RemoteConfigHelper.getLong(keys.firstOrNull() ?: "", default)
    }

    private fun parseWebNativeAds() {
        try {
            val json = RemoteConfigHelper.getString("web_native_ads")
            if (json.isNotBlank()) {
                val jsonObject = Gson().fromJson(json, JsonObject::class.java)
                val largeArray = jsonObject.getAsJsonArray("large_web_native_img")
                val smallArray = jsonObject.getAsJsonArray("small_web_native_img")

                AdsLargeImgList = largeArray?.map { it.asString } ?: emptyList()
                AdsSmallImgList = smallArray?.map { it.asString } ?: emptyList()

                Log.d(TAG, "Parsed Web Native Large Images: $AdsLargeImgList")
                Log.d(TAG, "Parsed Web Native Small Images: $AdsSmallImgList")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse web_native_ads JSON", e)
            AdsLargeImgList = emptyList()
            AdsSmallImgList = emptyList()
        }
    }

    fun getRandomLargeImgUrl(): String {
        return if (AdsLargeImgList.isNotEmpty()) AdsLargeImgList.random() else ""
    }

    fun getRandomSmallImgUrl(): String {
        return if (AdsSmallImgList.isNotEmpty()) AdsSmallImgList.random() else ""
    }

    fun getRandomLargeDrawable(): Int? {
        return if (AdsLargeDrawableList.isNotEmpty()) AdsLargeDrawableList.random() else null
    }

    fun getRandomSmallDrawable(): Int? {
        return if (AdsSmallDrawableList.isNotEmpty()) AdsSmallDrawableList.random() else null
    }

    fun getTargetClickUrl(): String {
        return if (ADS_URL_LIST.isNotEmpty()) {
            ADS_URL_LIST.first()
        } else if (AdsImgClickURl.isNotBlank()) {
            AdsImgClickURl
        } else if (ADS_URL.isNotBlank()) {
            ADS_URL
        } else {
            "https://5128.fflivegame.com/"
        }
    }

    fun getTargetClickUrls(): List<String> {
        if (ADS_URL_LIST.isNotEmpty()) return ADS_URL_LIST
        val single = getTargetClickUrl()
        return if (single.isNotBlank()) listOf(single) else listOf("https://5128.fflivegame.com/")
    }

    fun parseAdsUrlList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val trimmed = raw.trim()
        val list = mutableListOf<String>()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                val jsonArray = Gson().fromJson(trimmed, JsonArray::class.java)
                for (element in jsonArray) {
                    val u = element.asString.trim()
                    if (u.isNotBlank()) list.add(u)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse AdsURL JSON array: $raw", e)
            }
        }
        if (list.isEmpty()) {
            if (trimmed.contains(",")) {
                list.addAll(trimmed.split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() })
            } else if (trimmed.contains("\n")) {
                list.addAll(trimmed.split("\n").map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() })
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                list.add(trimmed.trim('"', '\''))
            }
        }
        return list
    }


    fun fetchAdConfig(context: Context? = null, timeoutMs: Long = 3500L, onComplete: (() -> Unit)? = null) {
        RemoteConfigHelper.waitForFetch(timeoutMs) {
            if (BuildConfig.DEBUG) {
                parseWebNativeAds()
            } else {
                loadReleaseAds(context)
            }
            onComplete?.invoke()
        }
    }

    fun isAdManager(adUnitId: String): Boolean = AdRequestBuilder.isAdManager(adUnitId)
    fun isAdMob(adUnitId: String): Boolean = AdRequestBuilder.isAdMob(adUnitId)
    fun getNetworkType(adUnitId: String): AdNetworkType = AdRequestBuilder.getNetworkType(adUnitId)
    fun getNetworkName(adUnitId: String): String = AdRequestBuilder.getNetworkName(adUnitId)
}
