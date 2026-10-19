package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdEvent
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdState
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdType
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Helper for managing Interstitial Ads.
 * Follows Google's official InterstitialExample patterns with:
 * - Count-based display threshold (0 = every time, 1 = skip 1, etc.)
 * - Two-tier failover (Primary -> Fallback)
 * - Ad freshness checks (1-hour expiry)
 * - FullscreenAdLock protection
 * - Load-And-Show with loader dialog (never skips, waits for ad then shows)
 */
class InterstitialHelper(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    var state: AdState = AdState.Idle
        private set

    private var loadTimeMs: Long = 0L
    private val AD_EXPIRY_MS = 3_600_000L // 1 hour (Google AdMob standard)

    private val adLoader = AdLoader()
    private var isUsingFallback = false
    private var loadedUnitId: String = ""
    private var clickCounter: Long = 0L

    // Callbacks waiting for in-flight ad to complete for loadAndShow
    private val pendingShowCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * Checks if an ad is ready to show right now.
     */
    fun isAdReady(): Boolean = interstitialAd != null && isFresh()

    /**
     * Loads the interstitial ad. Tries primary unit first; if it fails, falls back.
     * Skips directly to fallback if primary unit ID is blank.
     */
    fun loadAd(
        primaryAdUnitId: String,
        fallbackAdUnitId: String = "",
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (state is AdState.Loading || (state is AdState.Ready && isFresh())) {
            Log.d(TAG, "Interstitial ad already ready or loading.")
            return
        }

        val effectivePrimaryId = if (primaryAdUnitId.isNotBlank()) primaryAdUnitId else fallbackAdUnitId
        val effectiveFallbackId = if (primaryAdUnitId.isNotBlank()) fallbackAdUnitId else ""

        if (effectivePrimaryId.isBlank()) {
            Log.w(TAG, "Cannot load interstitial: Ad unit ID is blank.")
            state = AdState.Failed("Ad unit ID is blank")
            onEvent?.invoke(AdEvent.Failed(AdType.INTERSTITIAL, -1, "Ad unit ID is blank"))
            return
        }

        state = AdState.Loading
        isUsingFallback = (primaryAdUnitId.isBlank() && fallbackAdUnitId.isNotBlank())
        requestAd(effectivePrimaryId, effectiveFallbackId, onEvent)
    }

    private fun requestAd(
        unitId: String,
        fallbackUnitId: String,
        onEvent: ((AdEvent) -> Unit)?
    ) {
        val adRequest = AdRequestBuilder.buildRequest(unitId)

        // ==========================================
        // [GOOGLE OFFICIAL PATTERN - KEPT UNTOUCHED]
        // ==========================================
        InterstitialAd.load(
            context,
            unitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial loaded for unit: $unitId")
                    interstitialAd = ad
                    loadedUnitId = unitId
                    loadTimeMs = System.currentTimeMillis()
                    state = AdState.Ready
                    adLoader.reset()
                    onEvent?.invoke(AdEvent.Loaded(AdType.INTERSTITIAL))
                    dispatchPendingShow(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Interstitial failed to load ($unitId): ${loadAdError.message}")
                    interstitialAd = null

                    if (!isUsingFallback) {
                        // 1. Log specific main failure event
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logMainAdFailed(
                            adFormat = "interstitial",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )

                        // 2. Try fallback sub ad if available
                        if (fallbackUnitId.isNotBlank()) {
                            Log.d(TAG, "Attempting fallback interstitial unit: $fallbackUnitId")
                            isUsingFallback = true
                            requestAd(fallbackUnitId, "", onEvent)
                        } else {
                            state = AdState.Failed(loadAdError.message)
                            onEvent?.invoke(
                                AdEvent.Failed(
                                    AdType.INTERSTITIAL,
                                    loadAdError.code,
                                    loadAdError.message
                                )
                            )
                            dispatchPendingShow(false)
                        }
                    } else {
                        // Sub / Fallback failed -> Log specific sub failure event
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logSubAdFailed(
                            adFormat = "interstitial",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        state = AdState.Failed(loadAdError.message)
                        onEvent?.invoke(
                            AdEvent.Failed(
                                AdType.INTERSTITIAL,
                                loadAdError.code,
                                loadAdError.message
                            )
                        )
                        dispatchPendingShow(false)

                        // Request Control: Both main & sub failed -> Do NOT call again!
                        Log.w(TAG, "Both main and fallback interstitial failed. Request control active, stopping retry.")
                    }
                }
            }
        )
    }

    /**
     * Checks if the cached interstitial ad is still valid.
     */
    fun isFresh(): Boolean = AdPreCondition.isAdFresh(loadTimeMs, AD_EXPIRY_MS)

    /**
     * Evaluates skip counter and displays the interstitial ad if ready.
     * @param threshold 0 = show every time; 1 = skip 1, show on 2nd; 2 = skip 2, show on 3rd; etc.
     */
    fun showIfReady(
        activity: Activity,
        threshold: Long,
        onEvent: (AdEvent) -> Unit
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) {
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Activity finishing or destroyed"))
            return
        }

        // ==========================================
        // [CUSTOM EXTENSION: Skip-Count Logic]
        // ==========================================
        if (threshold > 0) {
            clickCounter++
            if (clickCounter < threshold) {
                Log.d(TAG, "Interstitial skipped by counter ($clickCounter/$threshold)")
                onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Counter ($clickCounter/$threshold)"))
                return
            }
            // Threshold reached -> reset and show
            clickCounter = 0
        }

        // Expiration check
        if (!isFresh()) {
            Log.w(TAG, "Cached interstitial is stale. Discarding and reloading.")
            interstitialAd = null
            state = AdState.Idle
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Ad expired"))
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "Interstitial not ready to show.")
            onEvent(AdEvent.NotReady(AdType.INTERSTITIAL))
            return
        }

        if (!FullscreenAdLock.acquire(TAG)) {
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Another fullscreen ad is displaying"))
            return
        }

        // ==========================================
        // [GOOGLE OFFICIAL PATTERN - KEPT UNTOUCHED]
        // ==========================================
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial showed fullscreen content.")
                state = AdState.Showing
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                    adFormat = "interstitial",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                onEvent(AdEvent.Showed(AdType.INTERSTITIAL))
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed.")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                // Set reference to null to prevent displaying twice
                interstitialAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent(AdEvent.Dismissed(AdType.INTERSTITIAL))
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial failed to show: ${adError.message}")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                // Set reference to null to prevent displaying twice
                interstitialAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent(
                    AdEvent.Failed(
                        AdType.INTERSTITIAL,
                        adError.code,
                        adError.message
                    )
                )
            }

            override fun onAdClicked() {
                Log.d(TAG, "Interstitial clicked.")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                    adFormat = "interstitial",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                onEvent(AdEvent.Clicked(AdType.INTERSTITIAL))
            }
        }

        ad.show(activity)
    }

    /**
     * Shows the interstitial ad directly without evaluating the skip counter.
     */
    fun showAdDirectly(activity: Activity, onEvent: (AdEvent) -> Unit) {
        if (!AdPreCondition.canShowOnActivity(activity)) {
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Activity finishing or destroyed"))
            return
        }

        val ad = interstitialAd
        if (ad == null || !isFresh()) {
            interstitialAd = null
            state = AdState.Idle
            onEvent(AdEvent.NotReady(AdType.INTERSTITIAL))
            return
        }

        if (!FullscreenAdLock.acquire(TAG)) {
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Another fullscreen ad is displaying"))
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial showed fullscreen content.")
                state = AdState.Showing
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                    adFormat = "interstitial",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                onEvent(AdEvent.Showed(AdType.INTERSTITIAL))
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed.")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                interstitialAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent(AdEvent.Dismissed(AdType.INTERSTITIAL))
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial failed to show: ${adError.message}")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                interstitialAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent(AdEvent.Failed(AdType.INTERSTITIAL, adError.code, adError.message))
            }

            override fun onAdClicked() {
                Log.d(TAG, "Interstitial clicked.")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                    adFormat = "interstitial",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                onEvent(AdEvent.Clicked(AdType.INTERSTITIAL))
            }
        }

        ad.show(activity)
    }

    /**
     * Loads the ad if not already ready, displaying a loader dialog.
     * If already preloaded, shows immediately without delay.
     * Prevents duplicate/spam requests by attaching to any active in-flight request.
     * Includes a 6-second safety timeout.
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
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Activity finishing or destroyed"))
            return
        }

        // Case 1: Already loaded and fresh -> Show immediately!
        if (isAdReady()) {
            Log.d(TAG, "Ad already preloaded & fresh. Showing directly without loader.")
            showAdDirectly(activity, onEvent)
            return
        }

        // Case 2: Not ready -> Show loader and fetch
        Log.d(TAG, "Ad not ready yet. Showing loader and fetching...")
        onShowLoader()

        var hasFinished = false

        // 6-second safety timeout
        val timeoutTask = Runnable {
            if (!hasFinished) {
                hasFinished = true
                Log.w(TAG, "Ad load timeout (6s). Dismissing loader.")
                onHideLoader()
                onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Ad load timeout"))
            }
        }
        this.timeoutRunnable = timeoutTask
        timeoutHandler.postDelayed(timeoutTask, 6000L)

        val showAction: (Boolean) -> Unit = { isSuccess ->
            if (!hasFinished) {
                hasFinished = true
                timeoutHandler.removeCallbacks(timeoutTask)
                onHideLoader()
                if (isSuccess && AdPreCondition.canShowOnActivity(activity)) {
                    showAdDirectly(activity, onEvent)
                } else {
                    onEvent(AdEvent.Failed(AdType.INTERSTITIAL, -1, "Ad load failed or timed out"))
                }
            }
        }

        synchronized(pendingShowCallbacks) {
            pendingShowCallbacks.add(showAction)
        }

        // Only trigger network request if not already loading
        if (state !is AdState.Loading) {
            loadAd(primaryAdUnitId, fallbackAdUnitId)
        }
    }

    private fun dispatchPendingShow(success: Boolean) {
        val callbacks = synchronized(pendingShowCallbacks) {
            val list = ArrayList(pendingShowCallbacks)
            pendingShowCallbacks.clear()
            list
        }
        callbacks.forEach { it.invoke(success) }
    }

    companion object {
        private const val TAG = "InterstitialHelper"
    }
}
