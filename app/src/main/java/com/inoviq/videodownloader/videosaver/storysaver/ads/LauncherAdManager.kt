package com.inoviq.videodownloader.videosaver.storysaver.ads

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.WebNativeHelper

/**
 * Dedicated Ad Manager for Launcher operations.
 * - Tracks launcher click counter against [AdsResponse.LAUNCHER_CLICK_THRESHOLD]
 * - Honors [AdsResponse.isLauncherAdsShow]
 * - If [AdsResponse.isLauncherWebAdShow] is true, alternates between Interstitial Ad and Web Ad URL opening
 * - If [AdsResponse.isLauncherWebAdShow] is false, shows Interstitial Ad on threshold
 * - Side panel native ad loads standard AdMob Native ad without web fallback images
 */
object LauncherAdManager {
    private const val TAG = "LauncherAdManager"

    private var launcherClickCounter: Long = 0
    private var alternateToggle: Boolean = false

    /**
     * Called whenever an app icon or item is launched from the launcher home or side panel.
     */
    fun onLauncherAppLaunched(activity: Activity, onComplete: () -> Unit) {
        if (!AdsResponse.isLauncherAdsShow) {
            Log.d(TAG, "Launcher ads disabled (isLauncherAdsShow=false). Proceeding immediately.")
            onComplete()
            return
        }

        launcherClickCounter++
        val threshold = AdsResponse.LAUNCHER_CLICK_THRESHOLD.coerceAtLeast(1L)
        Log.d(TAG, "onLauncherAppLaunched: count=$launcherClickCounter, threshold=$threshold, isWebAdShow=${AdsResponse.isLauncherWebAdShow}")

        if (launcherClickCounter >= threshold) {
            launcherClickCounter = 0

            if (AdsResponse.isLauncherWebAdShow) {
                alternateToggle = !alternateToggle
                if (alternateToggle) {
                    Log.d(TAG, "Showing alternate Interstitial Ad")
                    AdManager.showInterstitialDirect(activity) {
                        onComplete()
                    }
                } else {
                    Log.d(TAG, "Showing alternate Web Ad redirect via Custom Tab sequence")
                    WebNativeHelper.openCustomTab(activity) {
                        onComplete()
                    }
                }
            } else {
                Log.d(TAG, "Showing standard Interstitial Ad")
                AdManager.showInterstitialDirect(activity) {
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }

    /**
     * Loads clean Native Ad into the launcher side panel.
     * Does NOT display fallback web images in the panel.
     */
    fun loadSidePanelNativeAd(activity: Activity, container: ViewGroup) {
        if (!AdsResponse.isLauncherAdsShow) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        AdManager.loadLargeNativeAd(
            activity = activity,
            container = container,
            onFinished = {
                // If container still has no child or ad failed, hide it cleanly
                if (container.childCount == 0) {
                    container.visibility = View.GONE
                }
            }
        )
    }

    /**
     * Loads Native Ad into the launcher settings screen container.
     */
    fun loadSettingsNativeAd(activity: Activity, container: ViewGroup) {
        if (!AdsResponse.isLauncherAdsShow) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        AdManager.loadSmallNativeAd(
            activity = activity,
            container = container,
            onFinished = {
                if (container.childCount == 0) {
                    container.visibility = View.GONE
                }
            }
        )
    }
}
