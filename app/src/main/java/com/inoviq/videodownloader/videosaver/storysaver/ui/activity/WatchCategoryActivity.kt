package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.WatchCategoryAdapter
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityWatchCategoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.WatchCategoryItem
import com.inoviq.videodownloader.videosaver.storysaver.repository.UserPreferencesRepository
import com.inoviq.videodownloader.videosaver.storysaver.repository.WatchCategoryRepository

class WatchCategoryActivity : BaseActivity() {

    private lateinit var binding: ActivityWatchCategoryBinding
    private lateinit var adapter: WatchCategoryAdapter
    private val watchCategoryRepository = WatchCategoryRepository()
    private lateinit var preferencesRepository: UserPreferencesRepository
    private var currentList: List<WatchCategoryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityWatchCategoryBinding.inflate(layoutInflater)
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

        setupRecyclerView()
        setupListeners()
        loadData()
        loadNativeAd()
    }

    private fun loadNativeAd() {
        binding.adContainerWatch.visibility = View.VISIBLE
        AdManager.loadSmallNativeAd(
            activity = this,
            container = binding.adContainerWatch,
            lifecycleOwner = this,
            onFinished = {
                if (binding.adContainerWatch.childCount > 0) {
                    binding.adContainerWatch.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun setupRecyclerView() {
        adapter = WatchCategoryAdapter { selectedItem ->
            val updated = currentList.map { item ->
                item.copy(isSelected = item.id == selectedItem.id)
            }
            currentList = updated
            adapter.submitList(updated)
            preferencesRepository.selectedWatchCategory = selectedItem.id
        }
        binding.rvWatchCategories.adapter = adapter
        binding.rvWatchCategories.itemAnimator = null
    }

    private fun setupListeners() {
        binding.btnNextWatch.setOnClickListener {
            AdManager.showInterstitial(this) {
                val intent = Intent(this, FeatureHdActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun loadData() {
        val saved = preferencesRepository.selectedWatchCategory
        currentList = watchCategoryRepository.getWatchCategories(saved)
        adapter.submitList(currentList)
    }

    override fun finish() {
        super.finish()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
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
