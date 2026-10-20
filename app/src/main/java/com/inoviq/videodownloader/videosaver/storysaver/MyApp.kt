package com.inoviq.videodownloader.videosaver.storysaver

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.view.ViewGroup
import com.design.home.helpers.LeftPanelRegistry
import com.design.home.interfaces.LeftPanelProvider
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.ads.LauncherAdManager
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Ads SDK and preload cached units
        AdManager.init(this)

        // Register Launcher Left Panel Provider Bridge
        LeftPanelRegistry.provider = object : LeftPanelProvider {
            override fun openLeftPanel(activity: Activity) {
                val intent = Intent(activity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                    activity,
                    R.anim.slide_in_left,
                    R.anim.no_anim
                )
                activity.startActivity(intent, options.toBundle())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    activity.overrideActivityTransition(
                        Activity.OVERRIDE_TRANSITION_OPEN,
                        R.anim.slide_in_left,
                        R.anim.no_anim
                    )
                } else {
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(
                        R.anim.slide_in_left,
                        R.anim.no_anim
                    )
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
