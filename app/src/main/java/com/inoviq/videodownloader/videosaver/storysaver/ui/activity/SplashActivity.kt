package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse
import com.inoviq.videodownloader.videosaver.storysaver.ads.RemoteConfigHelper
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivitySplashBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private val minSplashDurationMs = 2000L
    private var splashStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            insets
        }

        splashStartTime = System.currentTimeMillis()
        RemoteConfigHelper.waitForFetch(timeoutMs = 3500L) {
            AdsResponse.initialize(this)
            val elapsed = System.currentTimeMillis() - splashStartTime
            val remaining = (minSplashDurationMs - elapsed).coerceAtLeast(0L)
            mainHandler.postDelayed({ attemptNavigate() }, remaining)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun attemptNavigate() {
        if (hasNavigated || isFinishing || isDestroyed) return
        navigateToNext()
    }

    private fun navigateToNext() {
        if (hasNavigated) return
        hasNavigated = true
        AdManager.showAppOpenAd(this) {
            if (LauncherHelper.isDefaultLauncher(this)) {
                if (LauncherHelper.isOnboardingCompleted(this)) {
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    startActivity(Intent(this, onBoardGenderSelectionActivity::class.java))
                }
            } else {
                LauncherHelper.setWasDefaultLauncher(this, false)
                if (AdsResponse.ShowOnboardingEveryTime) {
                    LauncherHelper.setOnboardingCompleted(this, false)
                }
                startActivity(Intent(this, SetDefaultLauncherActivity::class.java))
            }
            finish()
        }
    }
}