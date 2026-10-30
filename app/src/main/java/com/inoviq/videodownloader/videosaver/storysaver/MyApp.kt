package com.inoviq.videodownloader.videosaver.storysaver

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.view.ViewGroup
import com.design.home.helpers.LeftPanelRegistry
import com.design.home.interfaces.LeftPanelProvider
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse
import com.inoviq.videodownloader.videosaver.storysaver.ads.LauncherAdManager
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.onBoardGenderSelectionActivity
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Ads SDK and preload cached units
        AdManager.init(this)

        // Initialize YoutubeDL for fast instant media downloads and stream muxing (FFmpeg binary is bundled)
        kotlin.concurrent.thread(name = "YoutubeDL_Init", isDaemon = true) {
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
                android.util.Log.d("MyApp", "YoutubeDL initialized successfully with FFmpeg bundled")
            } catch (e: Exception) {
                android.util.Log.e("MyApp", "Failed to initialize YoutubeDL in MyApp", e)
            }
        }

        // Listen for Activity resume to check launcher status and refresh Firebase Remote Config
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val activityName = activity.javaClass.simpleName
                if (activityName == "LauncherHomeActivity") {
                    com.inoviq.videodownloader.videosaver.storysaver.ads.RemoteConfigHelper.fetchInBackgroundIfStale(activity)
                }

                // If default launcher is removed from anywhere (including background or settings), restart app from SplashActivity first
                if (activityName != "SplashActivity") {
                    val isDefault = LauncherHelper.isDefaultLauncher(activity)
                    val wasDefault = LauncherHelper.wasDefaultLauncher(activity)
                    val isLauncherHome = activityName == "LauncherHomeActivity"

                    if (!isDefault && (wasDefault || isLauncherHome)) {
                        LauncherHelper.setWasDefaultLauncher(activity, false)
                        val intent = Intent(activity, com.inoviq.videodownloader.videosaver.storysaver.ui.activity.SplashActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        activity.startActivity(intent)
                        activity.finish()
                        return
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Register Launcher Left Panel Provider Bridge
        LeftPanelRegistry.provider = object : LeftPanelProvider {
            override fun checkAndOpenMainFlow(activity: Activity): Boolean {
                if (LauncherHelper.shouldOpenMainOnNextLauncherHome(activity)) {
                    LauncherHelper.setShouldOpenMainOnNextLauncherHome(activity, false)
                    openLeftPanel(activity)
                    return true
                }
                return false
            }

            override fun openLeftPanel(activity: Activity) {
                if (LauncherHelper.isOnboardingCompleted(activity)) {
                    LauncherHelper.openMainActivity(activity)
                } else {
                    val launchOnboarding = {
                        val intent = Intent(activity, onBoardGenderSelectionActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        activity.startActivity(intent)
                    }

                    if (com.inoviq.videodownloader.videosaver.storysaver.ads.internal.WebNativeHelper.shouldShowWebNative()) {
                        com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager.skipNextAppOpen = true
                        com.inoviq.videodownloader.videosaver.storysaver.ads.internal.WebNativeHelper.openCustomTab(activity) {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                launchOnboarding()
                            }
                        }
                    } else {
                        launchOnboarding()
                    }
                }
            }

            override fun openDownloadList(activity: Activity) {
                LauncherHelper.openMainActivity(activity, showDownloads = true)
            }

            override fun openSearchTab(activity: Activity) {
                LauncherHelper.openMainActivity(activity, focusSearch = true)
            }

            override fun loadSidePanelNativeAd(activity: Activity, container: ViewGroup) {
                LauncherAdManager.loadSidePanelNativeAd(activity, container)
            }

            override fun loadSettingsNativeAd(activity: Activity, container: ViewGroup) {
                LauncherAdManager.loadSettingsNativeAd(activity, container)
            }

            override fun onLauncherAppLaunched(activity: Activity, onComplete: () -> Unit) {
                LauncherAdManager.onLauncherAppLaunched(activity, onComplete)
            }
        }
    }
}
