package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Standard AdMob AdManager with:
 * 1. Remote config / server response tags (is_rewarded_show_on_click, interstitial_click_count).
 * 2. Clicks 1 to (interval - 1): Do not show ad, execute user action immediately.
 * 3. Every 4th click (interval reached):
 *    - Reset click counter to 0.
 *    - If is_rewarded_show_on_click == true: Alternate between Interstitial and Rewarded ads.
 *    - If is_rewarded_show_on_click == false: Always show Interstitial Ad only.
 * 4. Completely independent direct rewarded ads:
 *    - Does NOT alter click counter or alternating toggle.
 *    - Only tracks direct reward count.
 * 5. Smart caching & performance:
 *    - Loading guards (isInterstitialLoading, isRewardedLoading) to prevent duplicate requests.
 *    - Preload on app start and right after ad closes.
 *    - Silently ignore background preload failures without infinite retries.
 *    - Zero delay if ad is cached; transparent loader with 5-second safe timeout if not cached.
 */
object AdManager {

    private const val TAG = "AdManager"

    // Default AdMob Test Ad Unit IDs
    private const val DEFAULT_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val DEFAULT_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    // ── 1. Remote Configuration / Server Response Tags ────────────────────────
    @Volatile
    var isRewardedShowOnClick: Boolean = true
        private set

    @Volatile
    var interstitialClickCount: Int = 4
        private set

    @Volatile
    var interstitialAdId: String = DEFAULT_INTERSTITIAL_ID
        private set

    @Volatile
    var rewardedAdId: String = DEFAULT_REWARDED_ID
        private set

    // ── 2. Click Counter & Alternating State ──────────────────────────────────
    private var clickCounter: Int = 0
    private var isNextClickRewarded: Boolean = false

    // Independent Direct Rewarded Tracking
    private var directRewardedCount: Int = 0

    // ── 3. Ad Caching & Loading State ─────────────────────────────────────────
    private var mInterstitialAd: InterstitialAd? = null
    private var mRewardedAd: RewardedAd? = null

    @Volatile
    private var isInterstitialLoading: Boolean = false

    @Volatile
    private var isRewardedLoading: Boolean = false

    // Temporary one-time listeners for on-demand requests
    private var interstitialLoadListener: ((InterstitialAd?) -> Unit)? = null
    private var rewardedLoadListener: ((RewardedAd?) -> Unit)? = null

    // Transparent progress dialog
    private var progressDialog: Dialog? = null

    // ── Dynamic Configuration Update ──────────────────────────────────────────
    /**
     * Updates configuration dynamically from server response or remote config.
     *
     * @param clickCount Interval of clicks before triggering an ad (default: 4)
     * @param isRewardedOnClick Whether to alternate interstitial and rewarded ads on click interval
     * @param interId AdMob Interstitial Ad Unit ID
     * @param rewardId AdMob Rewarded Ad Unit ID
     */
    fun updateConfigFromResponse(
        clickCount: Int? = null,
        isRewardedOnClick: Boolean? = null,
        interId: String? = null,
        rewardId: String? = null
    ) {
        if (clickCount != null && clickCount > 0) {
            interstitialClickCount = clickCount
            Log.d(TAG, "Config updated: interstitialClickCount = $interstitialClickCount")
        }
        if (isRewardedOnClick != null) {
            isRewardedShowOnClick = isRewardedOnClick
            Log.d(TAG, "Config updated: isRewardedShowOnClick = $isRewardedShowOnClick")
        }
        if (!interId.isNullOrBlank()) {
            interstitialAdId = interId.trim()
            Log.d(TAG, "Config updated: interstitialAdId = $interstitialAdId")
        }
        if (!rewardId.isNullOrBlank()) {
            rewardedAdId = rewardId.trim()
            Log.d(TAG, "Config updated: rewardedAdId = $rewardedAdId")
        }
    }

    // ── Initialization & Preload Triggers ─────────────────────────────────────
    /**
     * Initializes Google Mobile Ads SDK and preloads ads in the background.
     */
    fun init(context: Context) {
        MobileAds.initialize(context) {
            Log.d(TAG, "MobileAds initialized successfully.")
            preloadAll(context)
        }
    }

    /**
     * Preload both interstitial and rewarded ads into cache.
     */
    fun preloadAll(context: Context) {
        preloadInterstitial(context)
        preloadRewarded(context)
    }

    /**
     * Preloads Interstitial ad in background with duplicate request guard.
     */
    fun preloadInterstitial(context: Context) {
        if (mInterstitialAd != null) {
            Log.d(TAG, "Interstitial ad already cached. Skipping preload.")
            return
        }
        if (isInterstitialLoading) {
            Log.d(TAG, "Interstitial ad is currently loading. Skipping duplicate request.")
            return
        }

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            interstitialAdId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad preloaded successfully.")
                    mInterstitialAd = interstitialAd
                    isInterstitialLoading = false

                    val listener = interstitialLoadListener
                    interstitialLoadListener = null
                    listener?.invoke(interstitialAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad preload failed: ${loadAdError.message} (code: ${loadAdError.code})")
                    mInterstitialAd = null
                    isInterstitialLoading = false

                    val listener = interstitialLoadListener
                    interstitialLoadListener = null
                    listener?.invoke(null)
                    // Silently ignore background failure — do NOT retry in an infinite loop
                }
            }
        )
    }

    /**
     * Preloads Rewarded ad in background with duplicate request guard.
     */
    fun preloadRewarded(context: Context) {
        if (mRewardedAd != null) {
            Log.d(TAG, "Rewarded ad already cached. Skipping preload.")
            return
        }
        if (isRewardedLoading) {
            Log.d(TAG, "Rewarded ad is currently loading. Skipping duplicate request.")
            return
        }

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context.applicationContext,
            rewardedAdId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Log.d(TAG, "Rewarded ad preloaded successfully.")
                    mRewardedAd = rewardedAd
                    isRewardedLoading = false

                    val listener = rewardedLoadListener
                    rewardedLoadListener = null
                    listener?.invoke(rewardedAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad preload failed: ${loadAdError.message} (code: ${loadAdError.code})")
                    mRewardedAd = null
                    isRewardedLoading = false

                    val listener = rewardedLoadListener
                    rewardedLoadListener = null
                    listener?.invoke(null)
                    // Silently ignore background failure — do NOT retry in an infinite loop
                }
            }
        )
    }

    // ── Click-based Ad Execution ──────────────────────────────────────────────
    /**
     * Wraps button clicks cleanly:
     * - Clicks 1 to (interval - 1): Action runs immediately, no ad.
     * - Every interval (e.g. 4th click):
     *   - Resets click counter to 0.
     *   - If isRewardedShowOnClick == true: alternates between Interstitial and Rewarded ads.
     *   - If isRewardedShowOnClick == false: always shows Interstitial Ad only.
     */
    fun showInterstitialWithClick(
        activity: Activity,
        onProceed: () -> Unit
    ) {
        clickCounter++
        Log.d(TAG, "Button click registered: count = $clickCounter / $interstitialClickCount")

        if (clickCounter < interstitialClickCount) {
            // Interval not yet reached — execute action immediately
            onProceed()
            return
        }

        // Interval reached — reset counter
        clickCounter = 0

        // Determine ad type to show
        val shouldShowRewarded = if (isRewardedShowOnClick) {
            val current = isNextClickRewarded
            isNextClickRewarded = !isNextClickRewarded // Alternate for next time
            current
        } else {
            false
        }

        if (shouldShowRewarded) {
            Log.d(TAG, "Interval reached -> Showing Alternated Rewarded Ad")
            showRewardedInternal(
                activity = activity,
                onUserEarnedReward = {}, // Click-based rewarded ad doesn't require separate unlock logic
                onProceed = onProceed
            )
        } else {
            Log.d(TAG, "Interval reached -> Showing Interstitial Ad")
            showInterstitialInternal(
                activity = activity,
                onProceed = onProceed
            )
        }
    }

    // ── Direct Rewarded Ad Execution ──────────────────────────────────────────
    /**
     * Independent method for direct rewarded ad calls (e.g. "Watch Ad to Unlock Feature").
     *
     * STRICT RULES:
     * - Completely independent from click counter and alternating toggle.
     * - Does NOT increment or reset `clickCounter`.
     * - Does NOT alter `isNextClickRewarded`.
     * - Tracks only its own `directRewardedCount`.
     */
    fun showDirectRewardedAd(
        activity: Activity,
        onUserEarnedReward: () -> Unit = {},
        onProceed: () -> Unit
    ) {
        directRewardedCount++
        Log.d(TAG, "Direct rewarded ad triggered (direct count = $directRewardedCount)")

        showRewardedInternal(
            activity = activity,
            onUserEarnedReward = onUserEarnedReward,
            onProceed = onProceed
        )
    }

    // ── Internal Display & 5-Second Safe Timeout Logic ────────────────────────
    private fun showInterstitialInternal(
        activity: Activity,
        onProceed: () -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        var isActionExecuted = false

        fun safeProceed() {
            if (!isActionExecuted) {
                isActionExecuted = true
                onProceed()
            }
        }

        if (activity.isFinishing || activity.isDestroyed) {
            safeProceed()
            return
        }

        // 1. If ad is already loaded: Display directly with ZERO delay (no loader dialog)
        val cachedAd = mInterstitialAd
        if (cachedAd != null) {
            mInterstitialAd = null // Consume cached ad
            displayInterstitial(activity, cachedAd, ::safeProceed)
            return
        }

        // 2. Ad is NOT loaded: Show transparent progress loader with 5-second safe timeout
        showLoadingDialog(activity)

        val timeoutRunnable = Runnable {
            Log.w(TAG, "Interstitial load timed out (5s safe timeout). Proceeding with user action.")
            interstitialLoadListener = null
            dismissLoadingDialog()
            safeProceed()
        }
        mainHandler.postDelayed(timeoutRunnable, 5000L)

        interstitialLoadListener = { loadedAd ->
            mainHandler.removeCallbacks(timeoutRunnable)
            dismissLoadingDialog()

            if (loadedAd != null && !activity.isFinishing && !activity.isDestroyed) {
                mInterstitialAd = null // Consumed
                displayInterstitial(activity, loadedAd, ::safeProceed)
            } else {
                Log.d(TAG, "Interstitial ad unavailable or failed. Proceeding with action.")
                safeProceed()
            }
        }

        // Trigger load if not already in progress
        if (!isInterstitialLoading) {
            preloadInterstitial(activity)
        }
    }

    private fun displayInterstitial(
        activity: Activity,
        ad: InterstitialAd,
        onFinish: () -> Unit
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed. Preloading next ad in background.")
                preloadInterstitial(activity)
                onFinish()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${adError.message}. Preloading next ad.")
                preloadInterstitial(activity)
                onFinish()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial showed full screen.")
            }
        }

        ad.show(activity)
    }

    private fun showRewardedInternal(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        onProceed: () -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        var isActionExecuted = false

        fun safeProceed() {
            if (!isActionExecuted) {
                isActionExecuted = true
                onProceed()
            }
        }

        if (activity.isFinishing || activity.isDestroyed) {
            safeProceed()
            return
        }

        // 1. If ad is already loaded: Display directly with ZERO delay (no loader dialog)
        val cachedAd = mRewardedAd
        if (cachedAd != null) {
            mRewardedAd = null // Consume cached ad
            displayRewarded(activity, cachedAd, onUserEarnedReward, ::safeProceed)
            return
        }

        // 2. Ad is NOT loaded: Show transparent progress loader with 5-second safe timeout
        showLoadingDialog(activity)

        val timeoutRunnable = Runnable {
            Log.w(TAG, "Rewarded ad load timed out (5s safe timeout). Proceeding with user action.")
            rewardedLoadListener = null
            dismissLoadingDialog()
            safeProceed()
        }
        mainHandler.postDelayed(timeoutRunnable, 5000L)

        rewardedLoadListener = { loadedAd ->
            mainHandler.removeCallbacks(timeoutRunnable)
            dismissLoadingDialog()

            if (loadedAd != null && !activity.isFinishing && !activity.isDestroyed) {
                mRewardedAd = null // Consumed
                displayRewarded(activity, loadedAd, onUserEarnedReward, ::safeProceed)
            } else {
                Log.d(TAG, "Rewarded ad unavailable or failed. Proceeding with action.")
                safeProceed()
            }
        }

        // Trigger load if not already in progress
        if (!isRewardedLoading) {
            preloadRewarded(activity)
        }
    }

    private fun displayRewarded(
        activity: Activity,
        ad: RewardedAd,
        onUserEarnedReward: () -> Unit,
        onFinish: () -> Unit
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed. Preloading next ad in background.")
                preloadRewarded(activity)
                onFinish()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${adError.message}. Preloading next ad.")
                preloadRewarded(activity)
                onFinish()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed full screen.")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onUserEarnedReward()
        }
    }

    // ── Transparent Progress Loader Dialog ────────────────────────────────────
    private fun showLoadingDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        dismissLoadingDialog()

        try {
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                progressDialog = Dialog(activity).apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    val progressBar = ProgressBar(activity).apply {
                        isIndeterminate = true
                    }
                    val container = FrameLayout(activity).apply {
                        val pad = (24 * activity.resources.displayMetrics.density).toInt()
                        setPadding(pad, pad, pad, pad)
                        addView(
                            progressBar,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        )
                    }
                    setContentView(container)
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    window?.apply {
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    }
                    show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying progress loader: ${e.message}")
        }
    }

    private fun dismissLoadingDialog() {
        try {
            progressDialog?.let { dialog ->
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing progress loader: ${e.message}")
        } finally {
            progressDialog = null
        }
    }

    // ── Diagnostic / Inspection Helpers ───────────────────────────────────────
    fun getCurrentClickCount(): Int = clickCounter
    fun getDirectRewardedCount(): Int = directRewardedCount
    fun isNextAdRewarded(): Boolean = isNextClickRewarded
    fun isInterstitialLoaded(): Boolean = mInterstitialAd != null
    fun isRewardedLoaded(): Boolean = mRewardedAd != null
}
