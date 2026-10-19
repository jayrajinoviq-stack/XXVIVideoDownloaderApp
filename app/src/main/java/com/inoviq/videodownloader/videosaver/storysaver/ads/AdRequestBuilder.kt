package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.admanager.AdManagerAdRequest

enum class AdNetworkType {
    ADMOB,
    AD_MANAGER // (AdX / Google Ad Manager)
}

/**
 * Automatically detects whether an ad unit is AdMob (e.g. ca-app-pub-...) or
 * AdX / Google Ad Manager (e.g. /23357075583/...) and constructs the matching request.
 */
object AdRequestBuilder {
    private const val TAG = "AdRequestBuilder"

    fun getNetworkType(adUnitId: String): AdNetworkType {
        val trimmed = adUnitId.trim()
        return if (trimmed.startsWith("/")) {
            AdNetworkType.AD_MANAGER
        } else {
            AdNetworkType.ADMOB
        }
    }

    fun isAdManager(adUnitId: String): Boolean = getNetworkType(adUnitId) == AdNetworkType.AD_MANAGER

    fun isAdMob(adUnitId: String): Boolean = !isAdManager(adUnitId)

    fun getNetworkName(adUnitId: String): String =
        if (isAdManager(adUnitId)) "AdX (Google Ad Manager)" else "AdMob"

    fun buildRequest(adUnitId: String): AdRequest {
        val type = getNetworkType(adUnitId)
        Log.d(TAG, "Building request for '$adUnitId' (Network: $type)")
        return if (type == AdNetworkType.AD_MANAGER) {
            AdManagerAdRequest.Builder().build()
        } else {
            AdRequest.Builder().build()
        }
    }
}
