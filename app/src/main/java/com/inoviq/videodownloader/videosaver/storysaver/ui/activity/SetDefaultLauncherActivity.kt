package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse
import com.inoviq.videodownloader.videosaver.storysaver.ads.RemoteConfigHelper
import com.inoviq.videodownloader.videosaver.storysaver.ads.internal.WebNativeHelper
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivitySetDefaultLauncherBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper

class SetDefaultLauncherActivity : BaseActivity() {

    private lateinit var binding: ActivitySetDefaultLauncherBinding

    private val defaultLauncherResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (LauncherHelper.isDefaultLauncher(this)) {
            LauncherHelper.setShouldOpenMainOnNextLauncherHome(this, true)
            LauncherHelper.openLauncherHome(this)
            finish()
        } else {
            LauncherHelper.setShouldOpenMainOnNextLauncherHome(this, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LauncherHelper.isDefaultLauncher(this)) {
            if (LauncherHelper.isOnboardingCompleted(this)) {
                startActivity(Intent(this, MainActivity::class.java))
                Log.d("Tag_show", "onCreate: MainActivity Show")
                finish()
            } else {
                openOnboarding(showWebAd = true)
            }
            return
        }

        if (AdsResponse.ShowOnboardingEveryTime) {
            LauncherHelper.setOnboardingCompleted(this, false)
        }

        binding = ActivitySetDefaultLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        val footerHtml = "You Can Change Or Disable These Features Anytime<br>From <font color='#FA9500'>Settings &gt; Apps &gt; Default Apps &gt; Home App</font>"
        binding.tvFooterInfo.text = Html.fromHtml(footerHtml, Html.FROM_HTML_MODE_COMPACT)

        // Visibility controlled by Firebase Remote Config `isSkip` (default true)
        val isSkip = RemoteConfigHelper.getBoolean("isSkip", true)
        binding.btnSkip.visibility = if (isSkip) View.VISIBLE else View.GONE

        initListeners()
    }

    override fun onResume() {
        super.onResume()
        if (LauncherHelper.isDefaultLauncher(this)) {
            if (LauncherHelper.shouldOpenMainOnNextLauncherHome(this)) {
                LauncherHelper.openLauncherHome(this)
                finish()
            } else {
                if (LauncherHelper.isOnboardingCompleted(this)) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    openOnboarding(showWebAd = true)
                }
            }
        } else {
            LauncherHelper.setShouldOpenMainOnNextLauncherHome(this, false)
            if (AdsResponse.ShowOnboardingEveryTime) {
                LauncherHelper.setOnboardingCompleted(this, false)
            }
        }
    }

    private fun openOnboarding(showWebAd: Boolean) {
        val launch = {
            startActivity(Intent(this, onBoardGenderSelectionActivity::class.java))
            finish()
        }

        if (showWebAd && WebNativeHelper.shouldShowWebNative()) {
            AdsManager.skipNextAppOpen = true // avoid an App Open ad when returning from the tab
            WebNativeHelper.openCustomTab(this) {
                if (!isFinishing && !isDestroyed) launch()
            }
        } else {
            launch()
        }
    }

    private fun initListeners() {
        binding.btnLottie.setOnClickListener {
            LauncherHelper.setShouldOpenMainOnNextLauncherHome(this, true)
            if (LauncherHelper.isDefaultLauncher(this)) {
                LauncherHelper.openLauncherHome(this)
                finish()
            } else {
                LauncherHelper.requestDefaultLauncher(this, defaultLauncherResult)
            }
        }

        binding.btnSkip.setOnClickListener {
            AdManager.showInterstitial(this) {
                navigateToNextScreen()
            }
        }
    }

    override fun handleBackPress() {
        AdManager.showInterstitial(this) {
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        if (LauncherHelper.shouldShowOnboarding(this)) {
            openOnboarding(showWebAd = false)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

typealias SetDefaultActivity = SetDefaultLauncherActivity
