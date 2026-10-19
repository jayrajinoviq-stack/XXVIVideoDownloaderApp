package com.inoviq.videodownloader.videosaver.storysaver.ads.model

import android.util.Log
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.AdNetworkType
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.AdRequestBuilder
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Data bundle representing all ad formats for a network tier (Main or Sub).
 * Automatically parses JSON from Firebase Remote Config (e.g. MainAdIds or SubAdIds).
 *
 * Supported formats:
 * - BannerAd
 * - AppOpenAd
 * - InterstialAd / InterstitialAd
 * - NativeAd
 * - RewardAd / RewardedAd
 * - RewardInterAd / RewardedInterstitialAd
 */
data class AdIdBundle(
    val bannerAd: String = "",
    val appOpenAd: String = "",
    val interstitialAd: String = "",
    val nativeAd: String = "",
    val rewardAd: String = "",
    val rewardInterAd: String = ""
) {
    fun isEmpty(): Boolean =
        bannerAd.isBlank() && appOpenAd.isBlank() && interstitialAd.isBlank() &&
        nativeAd.isBlank() && rewardAd.isBlank() && rewardInterAd.isBlank()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun getBannerNetwork(): AdNetworkType = AdRequestBuilder.getNetworkType(bannerAd)
    fun getAppOpenNetwork(): AdNetworkType = AdRequestBuilder.getNetworkType(appOpenAd)
    fun getInterstitialNetwork(): AdNetworkType = AdRequestBuilder.getNetworkType(interstitialAd)
    fun getNativeNetwork(): AdNetworkType = AdRequestBuilder.getNetworkType(nativeAd)
    fun getRewardNetwork(): AdNetworkType = AdRequestBuilder.getNetworkType(rewardAd)
    fun getRewardInterNetwork(): AdNetworkType = AdRequestBuilder.getNetworkType(rewardInterAd)

    companion object {
        private const val TAG = "AdIdBundle"
        val EMPTY = AdIdBundle()
        private val gson = Gson()

        /**
         * Parses a JSON string containing ad unit mappings.
         * Tolerates user-provided typos such as "InterstialAd" alongside "InterstitialAd".
         */
        fun fromJson(jsonString: String?): AdIdBundle {
            if (jsonString.isNullOrBlank()) return EMPTY
            return try {
                val jsonObject = gson.fromJson(jsonString.trim(), JsonObject::class.java) ?: return EMPTY

                fun optStringAny(vararg keys: String): String {
                    for (key in keys) {
                        if (jsonObject.has(key)) {
                            val element = jsonObject.get(key)
                            if (element != null && !element.isJsonNull) {
                                val s = element.asString.trim()
                                if (s.isNotBlank()) return s
                            }
                        }
                    }
                    return ""
                }

                AdIdBundle(
                    bannerAd = optStringAny("BannerAd", "bannerAd", "banner_ad", "Banner", "banner"),
                    appOpenAd = optStringAny("AppOpenAd", "appOpenAd", "app_open_ad", "AppOpen", "app_open"),
                    interstitialAd = optStringAny(
                        "InterstialAd",
                        "InterstitialAd",
                        "interstialAd",
                        "interstitialAd",
                        "interstitial_ad",
                        "Interstitial",
                        "interstitial"
                    ),
                    nativeAd = optStringAny("NativeAd", "nativeAd", "native_ad", "Native", "native"),
                    rewardAd = optStringAny(
                        "RewardAd",
                        "rewardAd",
                        "reward_ad",
                        "RewardedAd",
                        "rewardedAd",
                        "rewarded_ad",
                        "Reward",
                        "reward"
                    ),
                    rewardInterAd = optStringAny(
                        "RewardInterAd",
                        "rewardInterAd",
                        "rewarded_inter_ad",
                        "RewardedInterstitialAd",
                        "rewarded_interstitial_ad",
                        "RewardedInterAd"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse AdIdBundle JSON: $jsonString", e)
                EMPTY
            }
        }
    }
}
