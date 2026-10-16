package com.inoviq.videodownloader.videosaver.storysaver

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.inoviq.videodownloader.videosaver.storysaver.utils.AdManager

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Google Mobile Ads SDK and trigger background preload
        AdManager.init(this)
    }
}
