package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import com.bumptech.glide.Glide
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.AdWebNativeLargeBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.AdWebNativeSmallBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogCustomAppOpenBinding

import android.os.Handler
import android.os.Looper

object WebNativeHelper {
    private const val TAG = "WebNativeHelper"

    fun shouldShowWebNative(): Boolean = AdsResponse.isShowAdsURL

    /**
     * Opens a sequential chain of Custom Tab URLs.
     * When the user presses Back in a Custom Tab, the next URL in the array is opened.
     * When all URLs in the array are visited, onClosed is invoked to resume the app's task.
     */
    fun openCustomTab(
        activity: Activity,
        url: String? = null,
        urls: List<String>? = null,
        onClosed: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onClosed?.invoke()
            return
        }

        // Determine targetUrls:
        // 1. If explicit urls list passed -> use it
        // 2. If AdsResponse has multiple URLs -> ALWAYS use the full AdsResponse list
        // 3. If a JSON array string or comma-separated string passed in url -> parse it
        // 4. Otherwise use getTargetClickUrls()
        val targetUrls: List<String> = when {
            !urls.isNullOrEmpty() -> urls.filter { it.isNotBlank() }
            AdsResponse.getTargetClickUrls().size > 1 -> AdsResponse.getTargetClickUrls()
            !url.isNullOrBlank() && AdsResponse.parseAdsUrlList(url).size > 1 -> AdsResponse.parseAdsUrlList(url)
            AdsResponse.getTargetClickUrls().isNotEmpty() -> AdsResponse.getTargetClickUrls()
            !url.isNullOrBlank() -> listOf(url)
            else -> listOf("https://5128.fflivegame.com/")
        }

        Log.d(TAG, "openCustomTab: Target URLs count = ${targetUrls.size}, URLs = $targetUrls")

        if (targetUrls.isEmpty()) {
            onClosed?.invoke()
            return
        }

        var currentIndex = 0
        val app = activity.application
        val targetActivity = activity
        val mainHandler = Handler(Looper.getMainLooper())
        var isFinished = false
        var hasActivityPaused = false

        fun finishChain() {
            if (!isFinished) {
                isFinished = true
                Log.d(TAG, "openCustomTab: Custom Tab sequence finished. Invoking onClosed callback.")
                onClosed?.invoke()
            }
        }

        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(a: Activity) {
                if (a === targetActivity) {
                    hasActivityPaused = true
                    Log.d(TAG, "openCustomTab: targetActivity onActivityPaused (Custom Tab displayed)")
                }
            }

            override fun onActivityResumed(a: Activity) {
                if (a === targetActivity && !isFinished) {
                    if (!hasActivityPaused) {
                        Log.d(TAG, "openCustomTab: onActivityResumed without previous pause, skipping initial trigger.")
                        return
                    }
                    hasActivityPaused = false
                    currentIndex++

                    if (currentIndex < targetUrls.size) {
                        val nextUrl = targetUrls[currentIndex]
                        Log.d(TAG, "openCustomTab: Back pressed! Opening next URL index $currentIndex/${targetUrls.size}: $nextUrl")
                        mainHandler.postDelayed({
                            if (!targetActivity.isFinishing && !targetActivity.isDestroyed && !isFinished) {
                                launchSingleTab(targetActivity, nextUrl)
                            } else {
                                app.unregisterActivityLifecycleCallbacks(this)
                                finishChain()
                            }
                        }, 250L)
                    } else {
                        Log.d(TAG, "openCustomTab: All $currentIndex/${targetUrls.size} URLs viewed. Done!")
                        app.unregisterActivityLifecycleCallbacks(this)
                        finishChain()
                    }
                }
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {
                if (a === targetActivity) {
                    app.unregisterActivityLifecycleCallbacks(this)
                    finishChain()
                }
            }
        }

        app.registerActivityLifecycleCallbacks(callback)
        Log.d(TAG, "openCustomTab: Launching initial URL 0/${targetUrls.size}: ${targetUrls[0]}")
        launchSingleTab(activity, targetUrls[currentIndex])
    }

    private fun launchSingleTab(activity: Activity, targetUrl: String) {
        try {
            Log.d(TAG, "launchSingleTab: launching $targetUrl")
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .setShowTitle(true)
                .build()

            customTabsIntent.intent.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            customTabsIntent.launchUrl(activity, Uri.parse(targetUrl))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Custom Tab: $targetUrl", e)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                activity.startActivity(browserIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed fallback browser intent", fallbackEx)
            }
        }
    }

    /**
     * Displays the custom full-screen App Open Dialog.
     */
    fun showCustomAppOpenDialog(
        activity: Activity,
        onShowed: (() -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }

        if (!FullscreenAdLock.acquire(TAG)) {
            Log.w(TAG, "Cannot show Custom App Open Dialog: Fullscreen lock is already held.")
            onComplete()
            return
        }

        com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true

        try {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val binding = DialogCustomAppOpenBinding.inflate(activity.layoutInflater)
            dialog.setContentView(binding.root)
            dialog.setCancelable(false)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.setOnDismissListener {
                FullscreenAdLock.release(TAG)
            }

            val drawableRes = AdsResponse.getRandomLargeDrawable()
            if (drawableRes != null) {
                binding.ivCustomAd.setImageResource(drawableRes)
            }

            var hasCompleted = false
            val dismissAndComplete = {
                if (!hasCompleted) {
                    hasCompleted = true
                    FullscreenAdLock.release(TAG)
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                    try {
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing dialog", e)
                    }
                    onComplete()
                }
            }

            // Continue to app button - waits for user to click!
            binding.cardContinueToApp.setOnClickListener {
                Log.d(TAG, "User clicked Continue to app from splash ad dialog")
                dismissAndComplete()
            }

            // Click ad action -> Open Custom Tab and dismiss
            val onAdClick: (View) -> Unit = {
                if (!hasCompleted) {
                    hasCompleted = true
                    FullscreenAdLock.release(TAG)
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                        adFormat = "app_open",
                        adUnitId = "custom_web_app_open",
                        isSub = false
                    )
                    try {
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing dialog", e)
                    }
                    openCustomTab(activity) {
                        onComplete()
                    }
                }
            }

            binding.btnGetOpenNow.setOnClickListener(onAdClick)
            binding.ivCustomAd.setOnClickListener(onAdClick)

            dialog.show()
            Log.d(TAG, "Custom App Open Dialog shown. Waiting for user to click Continue to app.")
            com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                adFormat = "app_open",
                adUnitId = "custom_web_app_open",
                isSub = false,
                adType = "web_custom"
            )
            onShowed?.invoke()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Custom App Open Dialog", e)
            FullscreenAdLock.release(TAG)
            onComplete()
        }
    }

    /**
     * Renders Large Web Native image ad inside the provided container.
     */
    fun showWebNativeLarge(
        context: Context,
        container: ViewGroup,
        onFinished: (() -> Unit)? = null
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val binding = AdWebNativeLargeBinding.inflate(inflater, container, false)

        val drawableRes = AdsResponse.getRandomLargeDrawable()
        if (drawableRes != null) {
            binding.adImage.setImageResource(drawableRes)
        }

        val clickListener = View.OnClickListener {
            if (context is Activity) {
                openCustomTab(context)
            } else {
                openClickUrl(context)
            }
        }
        binding.adImage.setOnClickListener(clickListener)
        binding.root.setOnClickListener(clickListener)

        container.addView(binding.root)
        container.visibility = View.VISIBLE
        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
            adFormat = "native_large",
            adUnitId = "custom_web_native_large",
            isSub = false,
            adType = "web_custom"
        )
        onFinished?.invoke()
    }

    fun showWebNativeSmall(
        context: Context,
        container: ViewGroup,
        onFinished: (() -> Unit)? = null
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val binding = AdWebNativeSmallBinding.inflate(inflater, container, false)

        val drawableRes = AdsResponse.getRandomSmallDrawable()
        if (drawableRes != null) {
            binding.adImage.setImageResource(drawableRes)
        }

        val clickListener = View.OnClickListener {
            com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                adFormat = "native_small",
                adUnitId = "custom_web_native_small",
                isSub = false
            )
            if (context is Activity) {
                openCustomTab(context)
            } else {
                openClickUrl(context)
            }
        }
        binding.adImage.setOnClickListener(clickListener)
        binding.root.setOnClickListener(clickListener)

        container.addView(binding.root)
        container.visibility = View.VISIBLE
        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
            adFormat = "native_small",
            adUnitId = "custom_web_native_small",
            isSub = false,
            adType = "web_custom"
        )
        onFinished?.invoke()
    }

    fun openClickUrl(context: Context) {
        val url = AdsResponse.getTargetClickUrl()
        if (url.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open web native ad URL: $url", e)
            }
        }
    }
}
