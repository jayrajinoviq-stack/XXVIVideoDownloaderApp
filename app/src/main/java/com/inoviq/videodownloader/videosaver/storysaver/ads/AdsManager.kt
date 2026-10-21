package com.inoviq.videodownloader.videosaver.storysaver.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.facebook.shimmer.ShimmerFrameLayout
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.AdPreCondition
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.AppOpenHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.BannerHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.FullscreenAdLock
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.InterstitialHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.NativeHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.NetworkMonitor
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.RewardedHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.RewardedInterstitialHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.WebNativeHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdEvent
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdType
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.inoviq.videodownloader.videosaver.storysaver.databinding.AdUnifiedLargeBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.AdUnifiedSmallBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object AdsManager {
    private const val TAG = "AdsManager"

    private val isInitialized = AtomicBoolean(false)
    private var defaultLoadingDialog: AlertDialog? = null
    private var interstitialClickCount: Long = 0L
    private var rewardedClickCount: Long = 0L
    private var rewardedInterClickCount: Long = 0L
    private var appOpenClickCount: Long = 0L
    private var appOpenResumeCount: Long = 0L
    var skipNextAppOpen: Boolean = false
    var enableResumeAppOpen: Boolean = true
    private var isFirstAppStart: Boolean = true
    private var currentActivity: WeakReference<Activity>? = null

    fun getInterstitialClickCount(): Long = interstitialClickCount
    fun getRewardedClickCount(): Long = rewardedClickCount
    fun getRewardedInterClickCount(): Long = rewardedInterClickCount
    fun getAppOpenClickCount(): Long = appOpenClickCount
    fun getAppOpenResumeCount(): Long = appOpenResumeCount

    fun resetAllCounters() {
        interstitialClickCount = 0L
        rewardedClickCount = 0L
        rewardedInterClickCount = 0L
        appOpenClickCount = 0L
        appOpenResumeCount = 0L
    }

    private fun showDefaultLoader(activity: Activity) {
        if (!AdPreCondition.canShowOnActivity(activity)) return
        try {
            dismissDefaultLoader()
            val progressBar = ProgressBar(activity).apply {
                setPadding(40, 40, 40, 40)
            }
            defaultLoadingDialog = AlertDialog.Builder(activity)
                .setTitle("Loading Ad...")
                .setMessage("Please wait while the ad prepares to show.")
                .setView(progressBar)
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show default loader", e)
        }
    }

    private fun dismissDefaultLoader() {
        try {
            defaultLoadingDialog?.dismiss()
            defaultLoadingDialog = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss default loader", e)
        }
    }

    private lateinit var interstitialHelper: InterstitialHelper
    private lateinit var bannerHelper: BannerHelper
    private lateinit var nativeHelper: NativeHelper
    private lateinit var appOpenHelper: AppOpenHelper
    private lateinit var rewardedHelper: RewardedHelper
    private lateinit var rewardedInterstitialHelper: RewardedInterstitialHelper
    private lateinit var networkMonitor: NetworkMonitor

    /**
     * Initializes the Ads SDK and underlying format helpers.
     */
    fun init(context: Context, isDebug: Boolean = true, onInitialized: (() -> Unit)? = null) {
        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "AdsManager already initialized.")
            onInitialized?.invoke()
            return
        }

        val appContext = context.applicationContext

        // Initialize Analytics tracking
        AnalyticsHelper.init(appContext)

        // Initialize helper instances
        interstitialHelper = InterstitialHelper(appContext)
        bannerHelper = BannerHelper()
        nativeHelper = NativeHelper(appContext)
        appOpenHelper = AppOpenHelper(appContext)
        rewardedHelper = RewardedHelper(appContext)
        rewardedInterstitialHelper = RewardedInterstitialHelper(appContext)
        networkMonitor = NetworkMonitor(appContext)

        AdsResponse.initialize(appContext, isDebug)

        val app = appContext as? Application
        app?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                currentActivity = WeakReference(activity)
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity?.get() === activity) {
                    currentActivity = null
                }
            }
        })

        // Process Lifecycle Observer: Automatically triggers App Open on App Resume/Foreground
        // Commented out for launcher: avoid continuous app open ads when switching back to home screen
        // ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
        //     override fun onStart(owner: LifecycleOwner) {
        //         if (isFirstAppStart) {
        //             isFirstAppStart = false
        //             Log.d(TAG, "ProcessLifecycleOwner onStart: Cold launch, skipping resume App Open.")
        //             return
        //         }
        //         Log.d(TAG, "ProcessLifecycleOwner onStart: App moved to foreground (Resume).")
        //         showAppOpenOnResumeIfAllowed()
        //     }
        // })

        // Optimization: Content Rating
        val contentRating = if (AdsResponse.isChildDirected) {
            RequestConfiguration.MAX_AD_CONTENT_RATING_G
        } else {
            RequestConfiguration.MAX_AD_CONTENT_RATING_MA
        }
        val requestConfig = RequestConfiguration.Builder()
            .setMaxAdContentRating(contentRating)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)

        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(appContext) { status ->
                Log.d(TAG, "Google Mobile Ads SDK initialized: $status")
            }
            CoroutineScope(Dispatchers.Main).launch {
                onInitialized?.invoke()
            }
        }

        // Auto reload missing ads upon network reconnection
        networkMonitor.addReconnectListener {
            Log.d(TAG, "Network reconnected: Checking for missing preloads...")
            preloadAll(appContext)
        }
    }

    /**
     * Triggers App Open ad when app resumes / enters foreground without overriding active ads.
     * Commented out for launcher app to avoid showing App Open continuously on resume.
     */
    fun showAppOpenOnResumeIfAllowed() {
        // Commented out for launcher: do not show App Open ad on resume
        // if (!enableResumeAppOpen || !AdsResponse.isAppOpenOnResume) {
        //     Log.d(TAG, "Resume App Open disabled by configuration.")
        //     return
        // }
        //
        // if (skipNextAppOpen) {
        //     Log.d(TAG, "Resume App Open suppressed: skipNextAppOpen flag active.")
        //     skipNextAppOpen = false
        //     return
        // }
        //
        // if (FullscreenAdLock.isShowingFullScreenAd()) {
        //     Log.d(TAG, "Resume App Open suppressed: another fullscreen ad is already displaying.")
        //     return
        // }
        //
        // val activity = currentActivity?.get() ?: return
        // if (activity.isFinishing || activity.isDestroyed) {
        //     return
        // }
        //
        // if (::appOpenHelper.isInitialized && appOpenHelper.isSplashActive) {
        //     Log.d(TAG, "Resume App Open suppressed: Splash screen is active.")
        //     return
        // }
        //
        // val resumeThreshold = if (AdsResponse.APP_OPEN_RESUME_CLICK_THRESHOLD > 0L) {
        //     AdsResponse.APP_OPEN_RESUME_CLICK_THRESHOLD
        // } else {
        //     AdsResponse.APP_OPEN_CLICK_THRESHOLD
        // }
        //
        // if (resumeThreshold <= 0L) {
        //     Log.d(TAG, "Resume App Open disabled by threshold <= 0")
        //     return
        // }
        //
        // appOpenResumeCount++
        // if (appOpenResumeCount < resumeThreshold) {
        //     Log.d(TAG, "Resume App Open skipped by count ($appOpenResumeCount/$resumeThreshold)")
        //     return
        // }
        // appOpenResumeCount = 0L
        //
        // Log.d(TAG, "Triggering App Open ad on app resume for Activity: ${activity.javaClass.simpleName}")
        // loadAndShowAppOpen(
        //     activity = activity,
        //     checkCount = false // count was already evaluated for resume
        // ) { event ->
        //     Log.d(TAG, "Resume App Open result: $event")
        // }
    }

    /**
     * Preloads all full-screen and native ads ready for display.
     */
    fun preloadAll(context: Context) {
        if (AdsResponse.isShowAdsURL) {
            Log.d(TAG, "preloadAll: isShowAdsURL is true, skipping AdMob preloads")
            return
        }
        preloadInterstitial()
        preloadAppOpen()
        preloadRewarded()
        preloadRewardedInterstitial()
        preloadNative()
    }

    // =========================================================================
    // Interstitial Ads
    // =========================================================================

    fun preloadInterstitial(onEvent: ((AdEvent) -> Unit)? = null) {
        if (AdsResponse.isShowAdsURL) return
        if (!::interstitialHelper.isInitialized) return
        interstitialHelper.loadAd(
            primaryAdUnitId = AdsResponse.INTERSTITIAL_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_INTERSTITIAL_ID,
            onEvent = onEvent
        )
    }

    fun showInterstitial(
        activity: Activity,
        onEvent: (AdEvent) -> Unit
    ) {
        loadAndShowInterstitial(
            activity = activity,
            onShowLoader = null,
            onHideLoader = null,
            checkCount = true,
            onEvent = onEvent
        )
    }

    /**
     * Loads and displays the interstitial ad count-wise without skipping if not ready.
     * If already preloaded, displays immediately.
     * If not ready, triggers onShowLoader (or default dialog), loads the ad, and then displays it.
     */
    fun loadAndShowInterstitial(
        activity: Activity,
        onShowLoader: (() -> Unit)? = null,
        onHideLoader: (() -> Unit)? = null,
        checkCount: Boolean = true,
        onEvent: (AdEvent) -> Unit
    ) {
        if (checkCount) {
            val threshold = AdsResponse.CLICK_THRESHOLD
            if (threshold <= 0L) {
                Log.d(TAG, "Interstitial disabled by threshold <= 0")
                onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Interstitial disabled by threshold"))
                return
            }
            interstitialClickCount++
            if (interstitialClickCount < threshold) {
                Log.d(TAG, "Interstitial skipped by count ($interstitialClickCount/$threshold)")
                onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Count ($interstitialClickCount/$threshold)"))
                return
            }
            interstitialClickCount = 0L
        }

        if (FullscreenAdLock.isShowingFullScreenAd()) {
            onEvent(AdEvent.Skipped(AdType.INTERSTITIAL, "Another fullscreen ad is active"))
            return
        }

        skipNextAppOpen = true

        if (AdsResponse.isShowAdsURL) {
            WebNativeHelper.openCustomTab(activity) {
                skipNextAppOpen = true
                onEvent(AdEvent.Dismissed(AdType.INTERSTITIAL))
            }
            return
        }

        if (!::interstitialHelper.isInitialized) {
            onEvent(AdEvent.NotReady(AdType.INTERSTITIAL))
            return
        }

        interstitialHelper.loadAndShow(
            activity = activity,
            primaryAdUnitId = AdsResponse.INTERSTITIAL_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_INTERSTITIAL_ID,
            onShowLoader = { onShowLoader?.invoke() ?: showDefaultLoader(activity) },
            onHideLoader = { onHideLoader?.invoke() ?: dismissDefaultLoader() },
            onEvent = { event ->
                if (event is AdEvent.Dismissed || event is AdEvent.Failed) {
                    skipNextAppOpen = true
                    preloadInterstitial()
                }
                onEvent(event)
            }
        )
    }

    /**
     * Action-based interstitial trigger for UI events (e.g. game over, level complete, button click).
     */
    fun onUserAction(activity: Activity, onComplete: () -> Unit) {
        onInterstitialUserAction(activity, onComplete)
    }

    fun onInterstitialUserAction(activity: Activity, onComplete: () -> Unit) {
        loadAndShowInterstitial(
            activity = activity,
            checkCount = true
        ) { event ->
            if (event is AdEvent.Dismissed || event is AdEvent.Failed || event is AdEvent.Skipped) {
                onComplete()
            }
        }
    }

    // =========================================================================
    // Banner Ads (Anchored Adaptive)
    // =========================================================================

    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        container.visibility = View.VISIBLE

        if (AdsResponse.isShowAdsURL) {
            WebNativeHelper.showWebNativeSmall(activity, container) {
                onEvent?.invoke(AdEvent.Showed(AdType.BANNER))
            }
            return
        }

        if (!::bannerHelper.isInitialized) return
        bannerHelper.loadBanner(
            activity = activity,
            container = container,
            primaryAdUnitId = AdsResponse.BANNER_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_BANNER_ID,
            onEvent = onEvent
        )
    }

    fun destroyBanner() {
        if (::bannerHelper.isInitialized) {
            bannerHelper.destroy()
        }
    }

    // =========================================================================
    // Native Ads (AdMob Advanced)
    // =========================================================================

    fun preloadNative(onEvent: ((AdEvent) -> Unit)? = null) {
        if (AdsResponse.isShowAdsURL) return
        if (!::nativeHelper.isInitialized) return
        nativeHelper.loadAd(
            primaryAdUnitId = AdsResponse.NATIVE_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_NATIVE_ID,
            onEvent = onEvent
        )
    }

    fun showNativeSmall(
        activity: Activity,
        container: ViewGroup,
        shimmerLayout: ShimmerFrameLayout? = null,
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) return

        if (AdsResponse.isShowAdsURL) {
            shimmerLayout?.stopShimmer()
            shimmerLayout?.visibility = View.GONE
            WebNativeHelper.showWebNativeSmall(activity, container) {
                onEvent?.invoke(AdEvent.Showed(AdType.NATIVE_SMALL))
            }
            return
        }

        shimmerLayout?.visibility = View.VISIBLE
        shimmerLayout?.startShimmer()

        val mainHandler = Handler(Looper.getMainLooper())
        var isDone = false

        val watchdog = Runnable {
            if (!isDone) {
                isDone = true
                shimmerLayout?.stopShimmer()
                shimmerLayout?.visibility = View.GONE
                onEvent?.invoke(AdEvent.Failed(AdType.NATIVE_SMALL, -1, "Native small timeout"))
            }
        }
        mainHandler.postDelayed(watchdog, 5000L)

        val displayAction = {
            if (!isDone && AdPreCondition.canShowOnActivity(activity)) {
                isDone = true
                mainHandler.removeCallbacks(watchdog)
                shimmerLayout?.stopShimmer()
                shimmerLayout?.visibility = View.GONE

                val smallBinding = AdUnifiedSmallBinding.inflate(activity.layoutInflater)
                populateNativeAdView(
                    adView = smallBinding.nativeAdView,
                    headlineView = smallBinding.adHeadline,
                    bodyView = smallBinding.adBody,
                    callToActionView = smallBinding.adCallToAction,
                    iconView = smallBinding.adAppIcon
                )
                container.removeAllViews()
                container.addView(smallBinding.root)
                container.visibility = View.VISIBLE
                onEvent?.invoke(AdEvent.Showed(AdType.NATIVE_SMALL))

                preloadNative()
            }
        }

        if (nativeHelper.isAdReady()) {
            displayAction()
        } else {
            preloadNative { event ->
                if (event is AdEvent.Loaded) {
                    displayAction()
                } else if (event is AdEvent.Failed) {
                    if (!isDone) {
                        isDone = true
                        mainHandler.removeCallbacks(watchdog)
                        shimmerLayout?.stopShimmer()
                        shimmerLayout?.visibility = View.GONE
                        onEvent?.invoke(event)
                    }
                }
            }
        }
    }

    /**
     * Displays Native Large ad with automated shimmer management:
     * - Shimmer starts while loading
     * - Shimmer stops and hides immediately when ad is shown or fails
     * - If isShowAdsURL is true, directly renders Web Native Large image ad
     * - Includes 5-second watchdog safety timeout so shimmer never hangs
     */
    fun showNativeLarge(
        activity: Activity,
        container: ViewGroup,
        shimmerLayout: ShimmerFrameLayout? = null,
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (!AdPreCondition.canShowOnActivity(activity)) return

        if (AdsResponse.isShowAdsURL) {
            shimmerLayout?.stopShimmer()
            shimmerLayout?.visibility = View.GONE
            WebNativeHelper.showWebNativeLarge(activity, container) {
                onEvent?.invoke(AdEvent.Showed(AdType.NATIVE_LARGE))
            }
            return
        }

        shimmerLayout?.visibility = View.VISIBLE
        shimmerLayout?.startShimmer()

        val mainHandler = Handler(Looper.getMainLooper())
        var isDone = false

        val watchdog = Runnable {
            if (!isDone) {
                isDone = true
                shimmerLayout?.stopShimmer()
                shimmerLayout?.visibility = View.GONE
                onEvent?.invoke(AdEvent.Failed(AdType.NATIVE_LARGE, -1, "Native large timeout"))
            }
        }
        mainHandler.postDelayed(watchdog, 5000L)

        val displayAction = {
            if (!isDone && AdPreCondition.canShowOnActivity(activity)) {
                isDone = true
                mainHandler.removeCallbacks(watchdog)
                shimmerLayout?.stopShimmer()
                shimmerLayout?.visibility = View.GONE

                val largeBinding = AdUnifiedLargeBinding.inflate(activity.layoutInflater)
                populateNativeAdView(
                    adView = largeBinding.nativeAdView,
                    mediaView = largeBinding.adMedia,
                    headlineView = largeBinding.adHeadline,
                    bodyView = largeBinding.adBody,
                    callToActionView = largeBinding.adCallToAction,
                    iconView = largeBinding.adAppIcon,
                    advertiserView = null,
                    starRatingView = largeBinding.adStars
                )
                container.removeAllViews()
                container.addView(largeBinding.root)
                container.visibility = View.VISIBLE
                onEvent?.invoke(AdEvent.Showed(AdType.NATIVE_LARGE))

                preloadNative()
            }
        }

        if (nativeHelper.isAdReady()) {
            displayAction()
        } else {
            preloadNative { event ->
                if (event is AdEvent.Loaded) {
                    displayAction()
                } else if (event is AdEvent.Failed) {
                    if (!isDone) {
                        isDone = true
                        mainHandler.removeCallbacks(watchdog)
                        shimmerLayout?.stopShimmer()
                        shimmerLayout?.visibility = View.GONE
                        onEvent?.invoke(event)
                    }
                }
            }
        }
    }

    fun populateNativeAdView(
        adView: NativeAdView,
        mediaView: MediaView? = null,
        headlineView: TextView? = null,
        bodyView: TextView? = null,
        callToActionView: Button? = null,
        iconView: ImageView? = null,
        advertiserView: TextView? = null,
        starRatingView: RatingBar? = null,
        nativeAd: NativeAd? = null
    ) {
        if (!::nativeHelper.isInitialized) return
        nativeHelper.populateNativeAdView(
            ad = nativeAd,
            adView = adView,
            mediaView = mediaView,
            headlineView = headlineView,
            bodyView = bodyView,
            callToActionView = callToActionView,
            iconView = iconView,
            advertiserView = advertiserView,
            starRatingView = starRatingView
        )
    }

    fun getLoadedNativeAd(): NativeAd? =
        if (::nativeHelper.isInitialized) nativeHelper.getLoadedAd() else null

    fun destroyNative() {
        if (::nativeHelper.isInitialized) {
            nativeHelper.destroy()
        }
    }

    // =========================================================================
    // App Open Ads
    // =========================================================================

    fun preloadAppOpen(onEvent: ((AdEvent) -> Unit)? = null) {
        if (AdsResponse.isShowAdsURL) return
        if (!::appOpenHelper.isInitialized) return
        appOpenHelper.loadAd(
            primaryAdUnitId = AdsResponse.APP_OPEN_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_APP_OPEN_ID,
            onEvent = onEvent
        )
    }

    /**
     * Shows App Open ad. If already loaded, displays immediately.
     * If not ready, triggers loader, loads ad, and displays it.
     * If isShowAdsURL is true, shows Custom App Open Dialog instead.
     */
    fun showAppOpen(activity: Activity, onEvent: ((AdEvent) -> Unit)? = null) {
        loadAndShowAppOpen(
            activity = activity,
            onShowLoader = null,
            onHideLoader = null,
            checkCount = true,
            onEvent = { event -> onEvent?.invoke(event) }
        )
    }

    fun loadAndShowAppOpen(
        activity: Activity,
        onShowLoader: (() -> Unit)? = null,
        onHideLoader: (() -> Unit)? = null,
        checkCount: Boolean = true,
        onEvent: (AdEvent) -> Unit
    ) {
        if (checkCount) {
            val threshold = AdsResponse.APP_OPEN_CLICK_THRESHOLD
            if (threshold <= 0L) {
                Log.d(TAG, "App Open disabled by threshold <= 0")
                onEvent(AdEvent.Skipped(AdType.APP_OPEN, "App Open disabled by threshold"))
                return
            }
            appOpenClickCount++
            if (appOpenClickCount < threshold) {
                Log.d(TAG, "App Open skipped by count ($appOpenClickCount/$threshold)")
                onEvent(AdEvent.Skipped(AdType.APP_OPEN, "Count ($appOpenClickCount/$threshold)"))
                return
            }
            appOpenClickCount = 0L
        }

        if (FullscreenAdLock.isShowingFullScreenAd()) {
            onEvent(AdEvent.Skipped(AdType.APP_OPEN, "Another fullscreen ad is active"))
            return
        }

        skipNextAppOpen = true

        if (AdsResponse.isShowAdsURL) {
            WebNativeHelper.showCustomAppOpenDialog(
                activity = activity,
                onShowed = {
                    onEvent(AdEvent.Showed(AdType.APP_OPEN))
                },
                onComplete = {
                    skipNextAppOpen = true
                    onEvent(AdEvent.Dismissed(AdType.APP_OPEN))
                }
            )
            return
        }

        if (!::appOpenHelper.isInitialized) {
            onEvent(AdEvent.NotReady(AdType.APP_OPEN))
            return
        }

        appOpenHelper.loadAndShow(
            activity = activity,
            primaryAdUnitId = AdsResponse.APP_OPEN_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_APP_OPEN_ID,
            onShowLoader = { onShowLoader?.invoke() ?: showDefaultLoader(activity) },
            onHideLoader = { onHideLoader?.invoke() ?: dismissDefaultLoader() },
            onEvent = { event ->
                if (event is AdEvent.Dismissed || event is AdEvent.Failed) {
                    skipNextAppOpen = true
                    preloadAppOpen()
                }
                onEvent(event)
            }
        )
    }

    fun onAppOpenUserAction(activity: Activity, onComplete: () -> Unit) {
        loadAndShowAppOpen(
            activity = activity,
            checkCount = true
        ) { event ->
            if (event is AdEvent.Dismissed || event is AdEvent.Failed || event is AdEvent.Skipped) {
                onComplete()
            }
        }
    }

    fun setSplashActive(active: Boolean) {
        if (::appOpenHelper.isInitialized) {
            appOpenHelper.isSplashActive = active
        }
    }

    // =========================================================================
    // Rewarded Ads (Highest CPM)
    // =========================================================================

    fun preloadRewarded(onEvent: ((AdEvent) -> Unit)? = null) {
        if (AdsResponse.isShowAdsURL) return
        if (!::rewardedHelper.isInitialized) return
        rewardedHelper.loadAd(
            primaryAdUnitId = AdsResponse.REWARDED_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_REWARDED_ID,
            onEvent = onEvent
        )
    }

    /**
     * Shows Rewarded ad. If already loaded, displays immediately.
     * If not ready, shows loader, loads ad, and displays it.
     * If isShowAdsURL is true, opens Custom Tab URL and grants reward.
     */
    fun showRewarded(activity: Activity, onEvent: (AdEvent) -> Unit) {
        loadAndShowRewarded(
            activity = activity,
            onShowLoader = null,
            onHideLoader = null,
            checkCount = true,
            onEvent = onEvent
        )
    }

    fun loadAndShowRewarded(
        activity: Activity,
        onShowLoader: (() -> Unit)? = null,
        onHideLoader: (() -> Unit)? = null,
        checkCount: Boolean = true,
        onEvent: (AdEvent) -> Unit
    ) {
        if (checkCount) {
            val threshold = AdsResponse.REWARDED_CLICK_THRESHOLD
            if (threshold <= 0L) {
                Log.d(TAG, "Rewarded disabled by threshold <= 0")
                onEvent(AdEvent.Skipped(AdType.REWARDED, "Rewarded disabled by threshold"))
                return
            }
            rewardedClickCount++
            if (rewardedClickCount < threshold) {
                Log.d(TAG, "Rewarded skipped by count ($rewardedClickCount/$threshold)")
                onEvent(AdEvent.Skipped(AdType.REWARDED, "Count ($rewardedClickCount/$threshold)"))
                return
            }
            rewardedClickCount = 0L
        }

        if (FullscreenAdLock.isShowingFullScreenAd()) {
            onEvent(AdEvent.Skipped(AdType.REWARDED, "Another fullscreen ad is active"))
            return
        }

        skipNextAppOpen = true

        if (AdsResponse.isShowAdsURL) {
            WebNativeHelper.openCustomTab(activity) {
                skipNextAppOpen = true
                onEvent(AdEvent.RewardEarned("web_reward", 1))
                onEvent(AdEvent.Dismissed(AdType.REWARDED))
            }
            return
        }

        if (!::rewardedHelper.isInitialized) {
            onEvent(AdEvent.NotReady(AdType.REWARDED))
            return
        }

        rewardedHelper.loadAndShow(
            activity = activity,
            primaryAdUnitId = AdsResponse.REWARDED_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_REWARDED_ID,
            onShowLoader = { onShowLoader?.invoke() ?: showDefaultLoader(activity) },
            onHideLoader = { onHideLoader?.invoke() ?: dismissDefaultLoader() },
            onEvent = { event ->
                if (event is AdEvent.Dismissed || event is AdEvent.Failed) {
                    skipNextAppOpen = true
                    preloadRewarded()
                }
                onEvent(event)
            }
        )
    }

    fun onRewardedUserAction(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdClosed: () -> Unit
    ) {
        loadAndShowRewarded(
            activity = activity,
            checkCount = true,
            onEvent = { event ->
                when (event) {
                    is AdEvent.RewardEarned -> onRewardEarned()
                    is AdEvent.Dismissed, is AdEvent.Failed, is AdEvent.Skipped -> onAdClosed()
                    else -> {}
                }
            }
        )
    }

    // =========================================================================
    // Rewarded Interstitial Ads
    // =========================================================================

    fun preloadRewardedInterstitial(onEvent: ((AdEvent) -> Unit)? = null) {
        if (AdsResponse.isShowAdsURL) return
        if (!::rewardedInterstitialHelper.isInitialized) return
        rewardedInterstitialHelper.loadAd(
            primaryAdUnitId = AdsResponse.REWARDED_INTERSTITIAL_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_REWARDED_INTERSTITIAL_ID,
            onEvent = onEvent
        )
    }

    /**
     * Shows Rewarded Interstitial ad count-wise. If already loaded, displays immediately.
     * If not ready, shows loader, loads ad, and displays it.
     * If isShowAdsURL is true, opens Custom Tab URL and grants reward.
     */
    fun showRewardedInterstitial(activity: Activity, onEvent: (AdEvent) -> Unit) {
        loadAndShowRewardedInterstitial(
            activity = activity,
            onShowLoader = null,
            onHideLoader = null,
            checkCount = true,
            onEvent = onEvent
        )
    }

    fun loadAndShowRewardedInterstitial(
        activity: Activity,
        onShowLoader: (() -> Unit)? = null,
        onHideLoader: (() -> Unit)? = null,
        checkCount: Boolean = true,
        onEvent: (AdEvent) -> Unit
    ) {
        if (checkCount) {
            val threshold = AdsResponse.REWARDED_INTERSTITIAL_CLICK_THRESHOLD
            if (threshold <= 0L) {
                Log.d(TAG, "Rewarded Interstitial disabled by threshold <= 0")
                onEvent(AdEvent.Skipped(AdType.REWARDED_INTERSTITIAL, "Rewarded Interstitial disabled by threshold"))
                return
            }
            rewardedInterClickCount++
            if (rewardedInterClickCount < threshold) {
                Log.d(TAG, "Rewarded Interstitial skipped by count ($rewardedInterClickCount/$threshold)")
                onEvent(AdEvent.Skipped(AdType.REWARDED_INTERSTITIAL, "Count ($rewardedInterClickCount/$threshold)"))
                return
            }
            rewardedInterClickCount = 0L
        }

        if (FullscreenAdLock.isShowingFullScreenAd()) {
            onEvent(AdEvent.Skipped(AdType.REWARDED_INTERSTITIAL, "Another fullscreen ad is active"))
            return
        }

        skipNextAppOpen = true

        if (AdsResponse.isShowAdsURL) {
            WebNativeHelper.openCustomTab(activity) {
                skipNextAppOpen = true
                onEvent(AdEvent.RewardEarned("web_reward", 1))
                onEvent(AdEvent.Dismissed(AdType.REWARDED_INTERSTITIAL))
            }
            return
        }

        if (!::rewardedInterstitialHelper.isInitialized) {
            onEvent(AdEvent.NotReady(AdType.REWARDED_INTERSTITIAL))
            return
        }

        rewardedInterstitialHelper.loadAndShow(
            activity = activity,
            primaryAdUnitId = AdsResponse.REWARDED_INTERSTITIAL_ID,
            fallbackAdUnitId = AdsResponse.FALLBACK_REWARDED_INTERSTITIAL_ID,
            onShowLoader = { onShowLoader?.invoke() ?: showDefaultLoader(activity) },
            onHideLoader = { onHideLoader?.invoke() ?: dismissDefaultLoader() },
            onEvent = { event ->
                if (event is AdEvent.Dismissed || event is AdEvent.Failed) {
                    skipNextAppOpen = true
                    preloadRewardedInterstitial()
                }
                onEvent(event)
            }
        )
    }

    fun onRewardedInterstitialUserAction(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdClosed: () -> Unit
    ) {
        loadAndShowRewardedInterstitial(
            activity = activity,
            checkCount = true,
            onEvent = { event ->
                when (event) {
                    is AdEvent.RewardEarned -> onRewardEarned()
                    is AdEvent.Dismissed, is AdEvent.Failed, is AdEvent.Skipped -> onAdClosed()
                    else -> {}
                }
            }
        )
    }

    // =========================================================================
    // Web Native Ads (Firebase Image Ads)
    // =========================================================================

    fun shouldShowWebNative(): Boolean = WebNativeHelper.shouldShowWebNative()

    fun getWebNativeLargeImageUrl(): String = AdsResponse.getRandomLargeImgUrl()

    fun getWebNativeSmallImageUrl(): String = AdsResponse.getRandomSmallImgUrl()

    fun openWebNativeUrl(context: Context) {
        WebNativeHelper.openClickUrl(context)
    }
}
