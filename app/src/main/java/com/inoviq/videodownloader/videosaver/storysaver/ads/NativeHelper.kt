package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdEvent
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdState
import com.inoviq.videodownloader.videosaver.storysaver.ads.model.AdType
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

class NativeHelper(private val context: Context) {

    private var nativeAd: NativeAd? = null
    var state: AdState = AdState.Idle
        private set
    private var isUsingFallback = false
    private var loadedUnitId: String = ""
    private val pendingLoadCallbacks = mutableListOf<(AdEvent) -> Unit>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    fun isAdReady(): Boolean = nativeAd != null && state is AdState.Ready

    fun consumeLoadedAd(): NativeAd? {
        val ad = nativeAd
        nativeAd = null
        state = AdState.Idle
        return ad
    }
    fun loadAd(
        primaryAdUnitId: String,
        fallbackAdUnitId: String = "",
        onEvent: ((AdEvent) -> Unit)? = null
    ) {
        if (nativeAd != null && state is AdState.Ready) {
            Log.d(TAG, "Native ad already loaded and ready in cache. Notifying immediately.")
            mainHandler.post {
                onEvent?.invoke(AdEvent.Loaded(AdType.NATIVE_LARGE))
            }
            return
        }

        val effectivePrimaryId = if (primaryAdUnitId.isNotBlank()) primaryAdUnitId else fallbackAdUnitId
        val effectiveFallbackId = if (primaryAdUnitId.isNotBlank()) fallbackAdUnitId else ""

        if (effectivePrimaryId.isBlank()) {
            Log.w(TAG, "Cannot load native ad: Ad unit ID is blank.")
            mainHandler.post {
                onEvent?.invoke(AdEvent.Failed(AdType.NATIVE_LARGE, -1, "Ad unit ID is blank"))
            }
            return
        }

        onEvent?.let {
            synchronized(pendingLoadCallbacks) {
                pendingLoadCallbacks.add(it)
            }
        }

        if (state is AdState.Loading) {
            Log.d(TAG, "Native ad is already loading. Attached listener to in-flight request.")
            return
        }

        state = AdState.Loading
        isUsingFallback = (primaryAdUnitId.isBlank() && fallbackAdUnitId.isNotBlank())
        requestNativeAd(effectivePrimaryId, effectiveFallbackId)
    }

    private fun requestNativeAd(
        unitId: String,
        fallbackUnitId: String
    ) {
        val builder = AdLoader.Builder(context, unitId)
            .forNativeAd { ad ->
                Log.d(TAG, "Native ad loaded successfully ($unitId).")
                nativeAd?.destroy()
                nativeAd = ad
                loadedUnitId = unitId
                state = AdState.Ready
                dispatchCallbacks(AdEvent.Loaded(AdType.NATIVE_LARGE))
            }
            .withAdListener(object : AdListener() {
                override fun onAdImpression() {
                    Log.d(TAG, "Native ad impression confirmed for unit: $unitId")
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdImpressionConfirmed(
                        adFormat = "native",
                        adUnitId = loadedUnitId.ifEmpty { unitId },
                        isSub = isUsingFallback
                    )
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Native ad failed to load ($unitId): ${loadAdError.message}")
                    if (!isUsingFallback) {
                        // Main native ad failed -> log fail_main_native
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logMainAdFailed(
                            adFormat = "native",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        if (fallbackUnitId.isNotBlank()) {
                            Log.d(TAG, "Attempting fallback native unit: $fallbackUnitId")
                            isUsingFallback = true
                            requestNativeAd(fallbackUnitId, "")
                        } else {
                            state = AdState.Failed(loadAdError.message)
                            dispatchCallbacks(
                                AdEvent.Failed(
                                    AdType.NATIVE_LARGE,
                                    loadAdError.code,
                                    loadAdError.message
                                )
                            )
                        }
                    } else {
                        // Sub native ad failed -> log fail_sub_native
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logSubAdFailed(
                            adFormat = "native",
                            adUnitId = unitId,
                            errorCode = loadAdError.code,
                            errorMessage = loadAdError.message
                        )
                        state = AdState.Failed(loadAdError.message)
                        dispatchCallbacks(
                            AdEvent.Failed(
                                AdType.NATIVE_LARGE,
                                loadAdError.code,
                                loadAdError.message
                            )
                        )
                        // Request control: Stop, do not call again
                        Log.w(TAG, "Both main and fallback native ad failed. Request control active, stopping retry.")
                    }
                }

                override fun onAdClicked() {
                    com.inoviq.videodownloader.videosaver.storysaver.ads.AnalyticsHelper.logAdClicked(
                        adFormat = "native",
                        adUnitId = loadedUnitId.ifEmpty { unitId },
                        isSub = isUsingFallback
                    )
                    dispatchCallbacks(AdEvent.Clicked(AdType.NATIVE_LARGE))
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setRequestMultipleImages(false)
                    .build()
            )

        val request = AdRequestBuilder.buildRequest(unitId)
        val adLoader = builder.build()
        if (request is com.google.android.gms.ads.admanager.AdManagerAdRequest) {
            adLoader.loadAd(request)
        } else {
            adLoader.loadAd(request)
        }
    }

    private fun dispatchCallbacks(event: AdEvent) {
        mainHandler.post {
            val callbacks = synchronized(pendingLoadCallbacks) {
                val list = ArrayList(pendingLoadCallbacks)
                pendingLoadCallbacks.clear()
                list
            }
            callbacks.forEach { it.invoke(event) }
        }
    }

    /**
     * Binds the loaded native ad into a NativeAdView container.
     */
    fun populateNativeAdView(
        ad: NativeAd?,
        adView: NativeAdView,
        mediaView: MediaView?,
        headlineView: TextView?,
        bodyView: TextView?,
        callToActionView: Button?,
        iconView: ImageView?,
        advertiserView: TextView? = null,
        starRatingView: RatingBar? = null
    ) {
        val nativeAd = ad ?: this.nativeAd ?: return

        // Set the media view
        if (mediaView != null) {
            adView.mediaView = mediaView
            mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }

        // Set other asset views
        if (headlineView != null) adView.headlineView = headlineView
        if (bodyView != null) adView.bodyView = bodyView
        if (callToActionView != null) adView.callToActionView = callToActionView
        if (iconView != null) adView.iconView = iconView
        if (advertiserView != null) adView.advertiserView = advertiserView
        if (starRatingView != null) adView.starRatingView = starRatingView

        // Populate assets
        headlineView?.text = nativeAd.headline

        if (nativeAd.body == null) {
            bodyView?.visibility = View.INVISIBLE
        } else {
            bodyView?.visibility = View.VISIBLE
            bodyView?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            callToActionView?.visibility = View.INVISIBLE
        } else {
            callToActionView?.visibility = View.VISIBLE
            callToActionView?.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            iconView?.visibility = View.GONE
        } else {
            iconView?.setImageDrawable(nativeAd.icon?.drawable)
            iconView?.visibility = View.VISIBLE
        }

        if (advertiserView != null) {
            if (nativeAd.advertiser == null) {
                advertiserView.visibility = View.INVISIBLE
            } else {
                advertiserView.text = nativeAd.advertiser
                advertiserView.visibility = View.VISIBLE
            }
        }

        if (starRatingView != null) {
            if (nativeAd.starRating == null) {
                starRatingView.visibility = View.INVISIBLE
            } else {
                starRatingView.rating = nativeAd.starRating!!.toFloat()
                starRatingView.visibility = View.VISIBLE
            }
        }

        // Assign native ad object to NativeAdView
        adView.setNativeAd(nativeAd)
    }

    fun getLoadedAd(): NativeAd? = nativeAd

    /**
     * Safely destroys the native ad to prevent memory leaks.
     */
    fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
        state = AdState.Idle
    }

    companion object {
        private const val TAG = "NativeHelper"
    }
}
