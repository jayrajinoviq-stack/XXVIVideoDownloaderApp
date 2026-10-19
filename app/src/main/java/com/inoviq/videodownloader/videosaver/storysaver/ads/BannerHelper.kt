package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.view.WindowMetrics
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdEvent
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdType
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

import com.google.android.gms.ads.BaseAdView
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView

/**
 * Helper for managing Banner Ads.
 * Follows Google's official BannerExample implementation with:
 * - Anchored Adaptive Banner size (+15-30% CPM increase)
 * - Safe lifecycle disposal
 * - Two-tier fallback support (AdMob & Google Ad Manager / AdX)
 */
class BannerHelper {

    private var baseAdView: BaseAdView? = null
    private var isUsingFallback = false

    /**
     * Loads and attaches an adaptive banner ad into the specified container.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        primaryAdUnitId: String,
        fallbackAdUnitId: String = "",
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) return

        val effectivePrimaryId = if (primaryAdUnitId.isNotBlank()) primaryAdUnitId else fallbackAdUnitId
        val effectiveFallbackId = if (primaryAdUnitId.isNotBlank()) fallbackAdUnitId else ""

        if (effectivePrimaryId.isBlank()) {
            Log.w(TAG, "Cannot load banner: Ad unit ID is blank.")
            onEvent?.invoke(AdEvent.Failed(AdType.BANNER, -1, "Ad unit ID is blank"))
            return
        }

        destroy()
        isUsingFallback = (primaryAdUnitId.isBlank() && fallbackAdUnitId.isNotBlank())
        requestBanner(activity, container, effectivePrimaryId, effectiveFallbackId, onEvent)
    }

    private fun requestBanner(
        activity: Activity,
        container: ViewGroup,
        unitId: String,
        fallbackUnitId: String,
        onEvent: ((AdEvent) -> Unit)?
    ) {
        val adaptiveSize = getAdaptiveAdSize(activity)
        val isAdX = AdRequestBuilder.isAdManager(unitId)
        Log.d(TAG, "Requesting banner: $unitId (isAdX=$isAdX)")

        val view: BaseAdView = if (isAdX) {
            AdManagerAdView(activity).apply {
                adUnitId = unitId
                setAdSizes(adaptiveSize)
            }
        } else {
            AdView(activity).apply {
                adUnitId = unitId
                setAdSize(adaptiveSize)
            }
        }
        this.baseAdView = view

        container.visibility = android.view.View.VISIBLE
        container.removeAllViews()
        container.addView(view)

        var hasLoggedImpression = false

        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "Banner loaded for unit: $unitId")
                if (!hasLoggedImpression) {
                    hasLoggedImpression = true
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                        adFormat = "banner",
                        adUnitId = unitId,
                        isSub = isUsingFallback
                    )
                }
                onEvent?.invoke(AdEvent.Loaded(AdType.BANNER))
            }

            override fun onAdImpression() {
                Log.d(TAG, "Banner impression confirmed for unit: $unitId")
                if (!hasLoggedImpression) {
                    hasLoggedImpression = true
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                        adFormat = "banner",
                        adUnitId = unitId,
                        isSub = isUsingFallback
                    )
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "Banner failed to load ($unitId): ${loadAdError.message}")
                if (!isUsingFallback) {
                    // Main banner failed -> log fail_main_banner
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logMainAdFailed(
                        adFormat = "banner",
                        adUnitId = unitId,
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                    if (fallbackUnitId.isNotBlank()) {
                        Log.d(TAG, "Attempting fallback banner unit: $fallbackUnitId")
                        isUsingFallback = true
                        requestBanner(activity, container, fallbackUnitId, "", onEvent)
                    } else {
                        onEvent?.invoke(
                            AdEvent.Failed(
                                AdType.BANNER,
                                loadAdError.code,
                                loadAdError.message
                            )
                        )
                    }
                } else {
                    // Sub banner failed -> log fail_sub_banner
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logSubAdFailed(
                        adFormat = "banner",
                        adUnitId = unitId,
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                    onEvent?.invoke(
                        AdEvent.Failed(
                            AdType.BANNER,
                            loadAdError.code,
                            loadAdError.message
                        )
                    )
                    // Request control: Stop, do not call again
                    Log.w(TAG, "Both main and fallback banner failed. Request control active, stopping retry.")
                }
            }

            override fun onAdClicked() {
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                    adFormat = "banner",
                    adUnitId = unitId,
                    isSub = isUsingFallback
                )
                onEvent?.invoke(AdEvent.Clicked(AdType.BANNER))
            }

            override fun onAdOpened() {
                onEvent?.invoke(AdEvent.Showed(AdType.BANNER))
            }
        }

        if (view is AdManagerAdView) {
            view.loadAd(AdManagerAdRequest.Builder().build())
        } else if (view is AdView) {
            view.loadAd(AdRequest.Builder().build())
        }
    }

    /**
     * Calculates the Google Recommended Anchored Adaptive Banner size.
     */
    @Suppress("DEPRECATION")
    private fun getAdaptiveAdSize(activity: Activity): AdSize {
        val adWidthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }

        val density = activity.resources.displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    /**
     * Safely destroys the AdView to prevent Activity memory leaks.
     */
    fun destroy() {
        baseAdView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
            baseAdView = null
            Log.d(TAG, "Banner adView destroyed.")
        }
    }

    companion object {
        private const val TAG = "BannerHelper"
    }
}
