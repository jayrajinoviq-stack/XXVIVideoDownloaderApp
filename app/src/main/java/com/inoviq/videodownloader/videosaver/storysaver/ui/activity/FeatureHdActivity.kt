package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityFeatureHdBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.FeatureWalkthroughItem
import com.inoviq.videodownloader.videosaver.storysaver.repository.UserPreferencesRepository
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper

class FeatureHdActivity : BaseActivity() {

    private lateinit var binding: ActivityFeatureHdBinding
    private lateinit var preferencesRepository: UserPreferencesRepository
    private var currentIndex = 0

    private val walkthroughItems = listOf(
        FeatureWalkthroughItem(
            iconRes = R.drawable.ic_walkthrough_speed,
            title = "Speed Meets Video Quality",
            description = "Download Videos Faster And Keep Every Moment Looking Sharp In Full HD."
        ),
        FeatureWalkthroughItem(
            iconRes = R.drawable.ic_walkthrough_fast,
            title = "Fast Downloads, Made Easy",
            description = "Enjoy A Smooth Downloading Experience Without Unnecessary Steps."
        ),
        FeatureWalkthroughItem(
            iconRes = R.drawable.ic_walkthrough_favorites,
            title = "Your Favorites, Always Ready",
            description = "Save The Videos You Love And Find Them Anytime With Ease."
        ),
        FeatureWalkthroughItem(
            iconRes = R.drawable.ic_walkthrough_link,
            title = "Quick Link Download",
            description = "Copy, Paste, And Save Your Favourite Videos With Ease."
        ),
        FeatureWalkthroughItem(
            iconRes = R.drawable.ic_walkthrough_lightning_download,
            title = "Download At Lightning Speed",
            description = "Download The Videos You Love Without The Hassle."
        ),
        FeatureWalkthroughItem(
            iconRes = R.drawable.ic_walkthrough_easy,
            title = "Quick & Easy Downloads",
            description = "Enjoy A Smooth, Simple Way To Download Your Favorite Videos."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityFeatureHdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        preferencesRepository = UserPreferencesRepository(applicationContext)

        renderCurrentItem()
        setupListeners()
        loadNativeAd()
        setupBackPress()
    }

    private fun renderCurrentItem() {
        val item = walkthroughItems[currentIndex]
        binding.ivWalkthroughIcon.setImageResource(item.iconRes)
        binding.tvWalkthroughTitle.text = item.title
        binding.tvWalkthroughDesc.text = item.description

        if (currentIndex == walkthroughItems.size - 1) {
            binding.tvBtnNextText.text = getString(R.string.start_app_btn)
        } else {
            binding.tvBtnNextText.text = getString(R.string.btn_next)
        }
    }

    private fun setupListeners() {
        binding.btnNextWalkthrough.setOnClickListener {
            AdManager.showInterstitial(this) {
                if (currentIndex < walkthroughItems.size - 1) {
                    currentIndex++
                    renderCurrentItem()
                } else {
                    LauncherHelper.setOnboardingCompleted(this, true)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun loadNativeAd() {
        binding.adContainerWalkthrough.visibility = View.VISIBLE
        AdManager.loadLargeNativeAd(
            activity = this,
            container = binding.adContainerWalkthrough,
            lifecycleOwner = this,
            onFinished = {
                if (binding.adContainerWalkthrough.childCount > 0) {
                    binding.adContainerWalkthrough.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentIndex > 0) {
                    currentIndex--
                    renderCurrentItem()
                } else {
                    handleBackPress()
                }
            }
        })
    }

    override fun finish() {
        super.finish()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.no_anim,
                    R.anim.slide_out_left
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(
                    R.anim.no_anim,
                    R.anim.slide_out_left
                )
            }
        } catch (_: Exception) {}
    }
}
