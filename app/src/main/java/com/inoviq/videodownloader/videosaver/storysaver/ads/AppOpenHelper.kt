package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdEvent
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdState
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdType
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

/**
 * Helper for managing App Open Ads.
 * Follows Google's official AppOpenExample patterns:
 * - 4-hour ad freshness verification
 * - isShowingAd guard
 * - Splash activity suppression
 * - FullscreenAdLock coordination
 */
class AppOpenHelper(private val context: Context) {

    private var appOpenAd: AppOpenAd? = null
    var state: AdState = AdState.Idle
        private set

    private var loadTime: Long = 0L
    private var isUsingFallback = false
    private var loadedUnitId: String = ""
    var isSplashActive = false

    private val pendingShowCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun isAdReady(): Boolean = isAdAvailable()

    /**
     * Loads the App Open ad.
     */
    fun loadAd(
        primaryAdUnitId: String,
        fallbackAdUnitId: String = "",
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (state is AdState.Loading || (isAdAvailable() && wasLoadTimeLessThanNHoursAgo(4))) {
            Log.d(TAG, "App Open ad already loading or cached fresh.")
            if (isAdAvailable()) onEvent?.invoke(AdEvent.Loaded(AdType.APP_OPEN))
            return
        }

        val effectivePrimaryId = if (primaryAdUnitId.isNotBlank()) primaryAdUnitId else fallbackAdUnitId
        val effectiveFallbackId = if (primaryAdUnitId.isNotBlank()) fallbackAdUnitId else ""

        if (effectivePrimaryId.isBlank()) {
            Log.w(TAG, "Cannot load App Open ad: Ad unit ID is blank.")
            return
        }

        state = AdState.Loading
        isUsingFallback = (primaryAdUnitId.isBlank() && fallbackAdUnitId.isNotBlank())
        requestAppOpenAd(effectivePrimaryId, effectiveFallbackId, onEvent)
    }

    private fun requestAppOpenAd(
        unitId: String,
        fallbackUnitId: String,
        onEvent: ((AdEvent) -> Unit)?
    ) {
        val request = AdRequestBuilder.buildRequest(unitId)

        // ==========================================
        // [GOOGLE OFFICIAL PATTERN - KEPT UNTOUCHED]
        // ==========================================
        AppOpenAd.load(
            context,
            unitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open ad loaded for unit: $unitId")
                    appOpenAd = ad
                    loadedUnitId = unitId
                    loadTime = Date().time
                    state = AdState.Ready
                    onEvent?.invoke(AdEvent.Loaded(AdType.APP_OPEN))
                    dispatchPendingShow(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "App Open ad failed to load ($unitId): ${loadAdError.message}")
                    appOpenAd = null

                    if (!isUsingFallback) {
                        // Main Ad Failed -> log fail_main_app_open
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logMainAdFailed(
                            adFormat = "app_open",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        if (fallbackUnitId.isNotBlank()) {
                            Log.d(TAG, "Attempting fallback App Open unit: $fallbackUnitId")
                            isUsingFallback = true
                            requestAppOpenAd(fallbackUnitId, "", onEvent)
                        } else {
                            state = AdState.Failed(loadAdError.message)
                            onEvent?.invoke(
                                AdEvent.Failed(
                                    AdType.APP_OPEN,
                                    loadAdError.code,
                                    loadAdError.message
                                )
                            )
                            dispatchPendingShow(false)
                        }
                    } else {
                        // Sub / Fallback Failed -> log fail_sub_app_open
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logSubAdFailed(
                            adFormat = "app_open",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        state = AdState.Failed(loadAdError.message)
                        onEvent?.invoke(
                            AdEvent.Failed(
                                AdType.APP_OPEN,
                                loadAdError.code,
                                loadAdError.message
                            )
                        )
                        dispatchPendingShow(false)
                        // Request control: Stop, do not call again
                        Log.w(TAG, "Both main and fallback App Open failed. Request control active, stopping retry.")
                    }
                }
            }
        )
    }

    private fun dispatchPendingShow(success: Boolean) {
        val callbacks = synchronized(pendingShowCallbacks) {
            val list = ArrayList(pendingShowCallbacks)
            pendingShowCallbacks.clear()
            list
        }
        callbacks.forEach { it.invoke(success) }
    }

    /**
     * Google's official 4-hour freshness check.
     */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /**
     * Shows the App Open ad if available and appropriate.
     */
    fun showIfAvailable(
        activity: Activity,
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (isAdAvailable()) {
            showAdDirectly(activity, onEvent)
        } else {
            Log.d(TAG, "App Open ad not available.")
            onEvent?.invoke(AdEvent.NotReady(AdType.APP_OPEN))
        }
    }

    /**
     * Shows the App Open ad directly.
     */
    fun showAdDirectly(
        activity: Activity,
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) {
            onEvent?.invoke(AdEvent.Skipped(AdType.APP_OPEN, "Activity is finishing or destroyed"))
            return
        }

        val ad = appOpenAd
        if (ad == null || !wasLoadTimeLessThanNHoursAgo(4)) {
            appOpenAd = null
            state = AdState.Idle
            onEvent?.invoke(AdEvent.NotReady(AdType.APP_OPEN))
            return
        }

        if (!FullscreenAdLock.acquire(TAG)) {
            Log.w(TAG, "Cannot show App Open: Fullscreen lock is already held.")
            onEvent?.invoke(AdEvent.Skipped(AdType.APP_OPEN, "Fullscreen lock active"))
            return
        }

        // ==========================================
        // [GOOGLE OFFICIAL PATTERN - KEPT UNTOUCHED]
        // ==========================================
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open ad showed fullscreen content.")
                state = AdState.Showing
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                    adFormat = "app_open",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                onEvent?.invoke(AdEvent.Showed(AdType.APP_OPEN))
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open ad dismissed.")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                appOpenAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent?.invoke(AdEvent.Dismissed(AdType.APP_OPEN))
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "App Open ad failed to show: ${adError.message}")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                appOpenAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent?.invoke(AdEvent.Failed(AdType.APP_OPEN, adError.code, adError.message))
            }

            override fun onAdClicked() {
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                    adFormat = "app_open",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                onEvent?.invoke(AdEvent.Clicked(AdType.APP_OPEN))
            }
        }

        ad.show(activity)
    }

    /**
     * Loads the App Open ad if not already ready, displaying a loader dialog.
     * If already preloaded, shows immediately without delay.
     * Prevents duplicate/spam requests by attaching to any active in-flight request.
     * Includes a 6-second safety watchdog timeout.
     */
    fun loadAndShow(
        activity: Activity,
        primaryAdUnitId: String,
        fallbackAdUnitId: String = "",
        onShowLoader: () -> Unit,
        onHideLoader: () -> Unit,
        onEvent: (AdEvent) -> Unit
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) {
            onEvent(AdEvent.Skipped(AdType.APP_OPEN, "Activity finishing or destroyed"))
            return
        }

        // Case 1: Already loaded and fresh -> Show immediately!
        if (isAdReady()) {
            Log.d(TAG, "App Open ad already preloaded. Showing directly without loader.")
            showAdDirectly(activity, onEvent)
            return
        }

        // Case 2: Not ready -> Show loader and fetch
        Log.d(TAG, "App Open ad not ready yet. Showing loader and fetching...")
        onShowLoader()

        var hasFinished = false

        // 6-second safety timeout
        val timeoutTask = Runnable {
            if (!hasFinished) {
                hasFinished = true
                Log.w(TAG, "App Open ad load timeout (6s). Dismissing loader.")
                onHideLoader()
                onEvent(AdEvent.Skipped(AdType.APP_OPEN, "Ad load timeout"))
            }
        }
        timeoutHandler.postDelayed(timeoutTask, 6000L)

        val showAction: (Boolean) -> Unit = { isSuccess ->
            if (!hasFinished) {
                hasFinished = true
                timeoutHandler.removeCallbacks(timeoutTask)
                onHideLoader()
                if (isSuccess && AdPreCondition.canShowOnActivity(activity)) {
                    showAdDirectly(activity, onEvent)
                } else {
                    onEvent(AdEvent.Failed(AdType.APP_OPEN, -1, "Ad load failed or timed out"))
                }
            }
        }

        synchronized(pendingShowCallbacks) {
            pendingShowCallbacks.add(showAction)
        }

        // Single in-flight request lock: Only trigger network request if not already loading
        if (state !is AdState.Loading) {
            loadAd(primaryAdUnitId, fallbackAdUnitId)
        }
    }

    companion object {
        private const val TAG = "AppOpenHelper"
    }
}
