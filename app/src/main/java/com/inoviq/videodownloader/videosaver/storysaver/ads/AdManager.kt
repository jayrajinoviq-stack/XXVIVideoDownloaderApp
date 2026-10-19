package com.inoviq.videodownloader.videosaver.storysaver.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdEvent

/**
 * Standard Ads Facade providing backwards-compatible, rock-solid ad calls across the app.
 * Directs all operations to [AdsManager].
 */
object AdManager {
    private const val TAG = "AdManager"

    fun init(context: Context, isDebug: Boolean = true, onInitialized: (() -> Unit)? = null) {
        AdsManager.init(context, isDebug, onInitialized)
    }

    fun preloadAll(context: Context) {
        AdsManager.preloadAll(context)
    }

    /**
     * Shows an Interstitial ad respecting count threshold with loader dialog.
     */
    fun showInterstitial(
        activity: Activity,
        onComplete: () -> Unit
    ) {
        AdsManager.onInterstitialUserAction(activity, onComplete)
    }

    /**
     * Shows an Interstitial ad directly without count skipping.
     */
    fun showInterstitialDirect(
        activity: Activity,
        onComplete: () -> Unit
    ) {
        AdsManager.loadAndShowInterstitial(
            activity = activity,
            checkCount = false
        ) { event ->
            if (event is AdEvent.Dismissed || event is AdEvent.Failed || event is AdEvent.Skipped) {
                onComplete()
            }
        }
    }

    /**
     * Shows App Open ad with watchdog safety timeout to ensure onComplete is never delayed.
     */
    fun showAppOpenAd(
        activity: Activity,
        onComplete: () -> Unit
    ) {
        var hasCompleted = false
        val finishOnce = {
            if (!hasCompleted) {
                hasCompleted = true
                onComplete()
            }
        }

        // Safety watchdog: ensure user is never blocked on splash screen for more than 5 seconds
        val safetyHandler = Handler(Looper.getMainLooper())
        val safetyRunnable = Runnable {
            Log.w(TAG, "showAppOpenAd safety timeout reached, continuing to app.")
            com.inoviq.videodownloader.videosaver.storysaver.ads.internal.FullscreenAdLock.release("showAppOpenAd_timeout")
            finishOnce()
        }
        safetyHandler.postDelayed(safetyRunnable, 5000L)

        AdsManager.loadAndShowAppOpen(
            activity = activity,
            checkCount = false
        ) { event ->
            Log.d(TAG, "showAppOpenAd event: $event")
            when (event) {
                is AdEvent.Showed -> {
                    Log.d(TAG, "showAppOpenAd: Ad is SHOWING. Cancelling safety watchdog so splash waits for user to click Continue.")
                    safetyHandler.removeCallbacks(safetyRunnable)
                }
                is AdEvent.Dismissed -> {
                    safetyHandler.removeCallbacks(safetyRunnable)
                    com.inoviq.videodownloader.videosaver.storysaver.ads.internal.FullscreenAdLock.release("showAppOpenAd_dismissed")
                    finishOnce()
                }
                is AdEvent.Failed, is AdEvent.Skipped, is AdEvent.NotReady -> {
                    safetyHandler.removeCallbacks(safetyRunnable)
                    com.inoviq.videodownloader.videosaver.storysaver.ads.internal.FullscreenAdLock.release("showAppOpenAd_finished")
                    finishOnce()
                }
                else -> {
                    // Ad is loading or requested, wait for Showed, Dismissed, or Failed
                }
            }
        }
    }

    /**
     * Loads and displays Native Small ad into container.
     */
    fun loadSmallNativeAd(
        activity: Activity,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner? = null,
        adUnitKey: String = "DEFAULT_SMALL_NATIVE",
        onFinished: (() -> Unit)? = null
    ) {
        AdsManager.showNativeSmall(
            activity = activity,
            container = container,
            shimmerLayout = null,
            onEvent = { event ->
                if (event is AdEvent.Showed || event is AdEvent.Failed || event is AdEvent.Skipped) {
                    onFinished?.invoke()
                }
            }
        )
    }

    /**
     * Loads and displays Native Large ad into container.
     */
    fun loadLargeNativeAd(
        activity: Activity,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner? = null,
        adUnitKey: String = "DEFAULT_LARGE_NATIVE",
        onFinished: (() -> Unit)? = null
    ) {
        AdsManager.showNativeLarge(
            activity = activity,
            container = container,
            shimmerLayout = null,
            onEvent = { event ->
                if (event is AdEvent.Showed || event is AdEvent.Failed || event is AdEvent.Skipped) {
                    onFinished?.invoke()
                }
            }
        )
    }

    /**
     * Loads Anchored Adaptive Banner ad into container.
     */
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        onFinished: (() -> Unit)? = null
    ) {
        AdsManager.loadBanner(activity, container) {
            onFinished?.invoke()
        }
    }

    fun destroyBanner() {
        AdsManager.destroyBanner()
    }

    fun destroyNative() {
        AdsManager.destroyNative()
    }
}
