package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityOnBoardGenderSelectionBinding
import com.inoviq.videodownloader.videosaver.storysaver.repository.UserPreferencesRepository

class onBoardGenderSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityOnBoardGenderSelectionBinding
    private var isFemaleSelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityOnBoardGenderSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initGenderSelection()
        loadNativeAd()
    }

    private fun loadNativeAd() {
        binding.flNativeAd.visibility = View.VISIBLE
        AdManager.loadLargeNativeAd(
            activity = this,
            container = binding.flNativeAd,
            lifecycleOwner = this,
            onFinished = {
                if (binding.flNativeAd.childCount > 0) {
                    binding.flNativeAd.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun initGenderSelection() {
        // Female selected by default as in design
        updateGenderUI(isFemale = true)

        binding.cardMale.setOnClickListener {
            updateGenderUI(isFemale = false)
        }

        binding.cardFemale.setOnClickListener {
            updateGenderUI(isFemale = true)
        }

        binding.btnNext.setOnClickListener {
            UserPreferencesRepository(this).selectedGender = if (isFemaleSelected) "FEMALE" else "MALE"
            AdManager.showInterstitial(this) {
                val intent = Intent(this@onBoardGenderSelectionActivity, AgeSelectionActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun updateGenderUI(isFemale: Boolean) {
        isFemaleSelected = isFemale

        if (isFemale) {
            binding.cardFemale.setBackgroundResource(R.drawable.bg_gender_card_selected)
            binding.ivFemaleRadio.setImageResource(R.drawable.ic_radio_selected)

            binding.cardMale.setBackgroundResource(R.drawable.bg_gender_card_unselected)
            binding.ivMaleRadio.setImageResource(R.drawable.ic_radio_unselected)
        } else {
            binding.cardMale.setBackgroundResource(R.drawable.bg_gender_card_selected)
            binding.ivMaleRadio.setImageResource(R.drawable.ic_radio_selected)

            binding.cardFemale.setBackgroundResource(R.drawable.bg_gender_card_unselected)
            binding.ivFemaleRadio.setImageResource(R.drawable.ic_radio_unselected)
        }
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
