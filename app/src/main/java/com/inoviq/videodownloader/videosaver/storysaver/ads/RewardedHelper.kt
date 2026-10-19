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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Helper for managing Rewarded Ads.
 * Follows Google's official RewardedExample pattern.
 * Rewarded ads yield the highest CPM ($8-$25).
 */
class RewardedHelper(private val context: Context) {

    private var rewardedAd: RewardedAd? = null
    var state: AdState = AdState.Idle
        private set

    private var isUsingFallback = false
    private var loadedUnitId: String = ""
    private val pendingShowCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun isAdReady(): Boolean = rewardedAd != null

    /**
     * Loads a Rewarded ad.
     */
    fun loadAd(
        primaryAdUnitId: String,
        fallbackAdUnitId: String = "",
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (state is AdState.Loading || (state is AdState.Ready && rewardedAd != null)) {
            Log.d(TAG, "Rewarded ad already loading or ready.")
            if (state is AdState.Ready) onEvent?.invoke(AdEvent.Loaded(AdType.REWARDED))
            return
        }

        val effectivePrimaryId = if (primaryAdUnitId.isNotBlank()) primaryAdUnitId else fallbackAdUnitId
        val effectiveFallbackId = if (primaryAdUnitId.isNotBlank()) fallbackAdUnitId else ""

        if (effectivePrimaryId.isBlank()) {
            Log.w(TAG, "Cannot load rewarded ad: Ad unit ID is blank.")
            return
        }

        state = AdState.Loading
        isUsingFallback = (primaryAdUnitId.isBlank() && fallbackAdUnitId.isNotBlank())
        requestRewardedAd(effectivePrimaryId, effectiveFallbackId, onEvent)
    }

    private fun requestRewardedAd(
        unitId: String,
        fallbackUnitId: String,
        onEvent: ((AdEvent) -> Unit)?
    ) {
        val request = AdRequestBuilder.buildRequest(unitId)

        // ==========================================
        // [GOOGLE OFFICIAL PATTERN - KEPT UNTOUCHED]
        // ==========================================
        RewardedAd.load(
            context,
            unitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded for unit: $unitId")
                    rewardedAd = ad
                    loadedUnitId = unitId
                    state = AdState.Ready
                    onEvent?.invoke(AdEvent.Loaded(AdType.REWARDED))
                    dispatchPendingShow(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load ($unitId): ${loadAdError.message}")
                    rewardedAd = null

                    if (!isUsingFallback) {
                        // Main Ad Failed -> log fail_main_rewarded
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logMainAdFailed(
                            adFormat = "rewarded",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        if (fallbackUnitId.isNotBlank()) {
                            Log.d(TAG, "Attempting fallback rewarded unit: $fallbackUnitId")
                            isUsingFallback = true
                            requestRewardedAd(fallbackUnitId, "", onEvent)
                        } else {
                            state = AdState.Failed(loadAdError.message)
                            onEvent?.invoke(
                                AdEvent.Failed(
                                    AdType.REWARDED,
                                    loadAdError.code,
                                    loadAdError.message
                                )
                            )
                            dispatchPendingShow(false)
                        }
                    } else {
                        // Sub / Fallback Failed -> log fail_sub_rewarded
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logSubAdFailed(
                            adFormat = "rewarded",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        state = AdState.Failed(loadAdError.message)
                        onEvent?.invoke(
                            AdEvent.Failed(
                                AdType.REWARDED,
                                loadAdError.code,
                                loadAdError.message
                            )
                        )
                        dispatchPendingShow(false)
                        // Request control: Stop, do not call again
                        Log.w(TAG, "Both main and fallback rewarded ad failed. Request control active, stopping retry.")
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
     * Shows the rewarded ad if available.
     */
    fun showIfReady(
        activity: Activity,
        onEvent: (AdEvent) -> Unit
    ) {
        if (isAdReady()) {
            showAdDirectly(activity, onEvent)
        } else {
            Log.w(TAG, "Rewarded ad not ready.")
            onEvent(AdEvent.NotReady(AdType.REWARDED))
        }
    }

    /**
     * Shows the rewarded ad directly.
     */
    fun showAdDirectly(
        activity: Activity,
        onEvent: (AdEvent) -> Unit
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) {
            onEvent(AdEvent.Skipped(AdType.REWARDED, "Activity is finishing or destroyed"))
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            state = AdState.Idle
            onEvent(AdEvent.NotReady(AdType.REWARDED))
            return
        }

        if (!FullscreenAdLock.acquire(TAG)) {
            Log.w(TAG, "Fullscreen lock active.")
            onEvent(AdEvent.Skipped(AdType.REWARDED, "Another fullscreen ad is displaying"))
            return
        }

        // ==========================================
        // [GOOGLE OFFICIAL PATTERN - KEPT UNTOUCHED]
        // ==========================================
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed fullscreen content.")
                state = AdState.Showing
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                    adFormat = "rewarded",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                onEvent(AdEvent.Showed(AdType.REWARDED))
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed.")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                rewardedAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent(AdEvent.Dismissed(AdType.REWARDED))
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                rewardedAd = null
                state = AdState.Idle
                FullscreenAdLock.release(TAG)
                onEvent(AdEvent.Failed(AdType.REWARDED, adError.code, adError.message))
            }

            override fun onAdClicked() {
                com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                    adFormat = "rewarded",
                    adUnitId = loadedUnitId.ifEmpty { ad.adUnitId },
                    isSub = isUsingFallback
                )
                com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                onEvent(AdEvent.Clicked(AdType.REWARDED))
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onEvent(AdEvent.RewardEarned(rewardItem.type, rewardItem.amount))
        }
    }

    /**
     * Loads the rewarded ad if not already ready, displaying a loader dialog.
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
            onEvent(AdEvent.Skipped(AdType.REWARDED, "Activity finishing or destroyed"))
            return
        }

        // Case 1: Already loaded and ready -> Show immediately!
        if (isAdReady()) {
            Log.d(TAG, "Rewarded ad already preloaded. Showing directly without loader.")
            showAdDirectly(activity, onEvent)
            return
        }

        // Case 2: Not ready -> Show loader and fetch
        Log.d(TAG, "Rewarded ad not ready yet. Showing loader and fetching...")
        onShowLoader()

        var hasFinished = false

        // 6-second safety timeout
        val timeoutTask = Runnable {
            if (!hasFinished) {
                hasFinished = true
                Log.w(TAG, "Rewarded ad load timeout (6s). Dismissing loader.")
                onHideLoader()
                onEvent(AdEvent.Skipped(AdType.REWARDED, "Ad load timeout"))
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
                    onEvent(AdEvent.Failed(AdType.REWARDED, -1, "Ad load failed or timed out"))
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
        private const val TAG = "RewardedHelper"
    }
}
