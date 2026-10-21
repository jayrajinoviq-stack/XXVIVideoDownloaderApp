package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.NetworkLiveObserver

/**
 * BaseActivity providing:
 * 1. fontScale = 1.0f override for robust, un-distorted UI across all device font settings.
 * 2. Edge-to-edge transparent system bars support.
 * 3. Unified back press handling triggering Interstitial Ad count-wise before finishing.
 * 4. Automatic real-time NetworkLiveObserver detecting offline states.
 */
open class BaseActivity : AppCompatActivity() {

    lateinit var networkLiveObserver: NetworkLiveObserver
        private set

    override fun attachBaseContext(newBase: Context) {
        val configuration = newBase.resources.configuration
        configuration.fontScale = 1.0f
        super.attachBaseContext(
            newBase.createConfigurationContext(configuration)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            }
        )

        networkLiveObserver = NetworkLiveObserver(this)
        lifecycle.addObserver(networkLiveObserver)
    }

    open fun handleBackPress() {
        AdManager.showInterstitial(this) {
            finish()
        }
    }
}
