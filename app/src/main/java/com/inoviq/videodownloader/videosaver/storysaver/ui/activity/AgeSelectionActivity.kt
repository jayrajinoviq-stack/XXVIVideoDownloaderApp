package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityAgeSelectionBinding
import com.inoviq.videodownloader.videosaver.storysaver.repository.UserPreferencesRepository

class AgeSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityAgeSelectionBinding
    private var selectedIndex = 1 // 22-31 is index 1 (default selected)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAgeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initAgeSelection()
        loadNativeAd()
    }

    private fun loadNativeAd() {
        binding.flNativeAd.visibility = View.VISIBLE
        AdManager.loadSmallNativeAd(
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

    private fun initAgeSelection() {
        updateSelectionUI(1)

        binding.cardAge1.setOnClickListener { updateSelectionUI(0) }
        binding.cardAge2.setOnClickListener { updateSelectionUI(1) }
        binding.cardAge3.setOnClickListener { updateSelectionUI(2) }
        binding.cardAge4.setOnClickListener { updateSelectionUI(3) }

        val ageRanges = arrayOf("18-21", "22-31", "32-39", "40+")
        binding.btnNext.setOnClickListener {
            UserPreferencesRepository(this).selectedAgeRange = ageRanges.getOrElse(selectedIndex) { "22-31" }
            AdManager.showInterstitial(this) {
                val intent = Intent(this@AgeSelectionActivity, CategoryActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun updateSelectionUI(index: Int) {
        selectedIndex = index

        val cards = arrayOf(binding.cardAge1, binding.cardAge2, binding.cardAge3, binding.cardAge4)
        val radios = arrayOf(binding.ivRadio1, binding.ivRadio2, binding.ivRadio3, binding.ivRadio4)

        for (i in cards.indices) {
            if (i == index) {
                cards[i].setBackgroundResource(R.drawable.bg_gender_card_selected)
                radios[i].setImageResource(R.drawable.ic_radio_selected)
            } else {
                cards[i].setBackgroundResource(R.drawable.bg_gender_card_unselected)
                radios[i].setImageResource(R.drawable.ic_radio_unselected)
            }
        }
    }
}
