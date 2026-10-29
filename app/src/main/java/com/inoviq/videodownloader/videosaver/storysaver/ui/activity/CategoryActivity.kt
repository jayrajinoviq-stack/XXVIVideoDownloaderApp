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
import com.inoviq.videodownloader.videosaver.storysaver.adapter.CategoryAdapter
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityCategoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.CategoryItem
import com.inoviq.videodownloader.videosaver.storysaver.repository.CategoryRepository
import com.inoviq.videodownloader.videosaver.storysaver.repository.UserPreferencesRepository

class CategoryActivity : BaseActivity() {

    private lateinit var binding: ActivityCategoryBinding
    private lateinit var adapter: CategoryAdapter
    private val categoryRepository = CategoryRepository()
    private lateinit var preferencesRepository: UserPreferencesRepository
    private var currentList: List<CategoryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCategoryBinding.inflate(layoutInflater)
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
        binding.adContainerCategory.visibility = View.VISIBLE
        AdManager.loadSmallNativeAd(
            activity = this,
            container = binding.adContainerCategory,
            lifecycleOwner = this,
            onFinished = {
                if (binding.adContainerCategory.childCount > 0) {
                    binding.adContainerCategory.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter { selectedItem ->
            val updated = currentList.map { item ->
                item.copy(isSelected = item.id == selectedItem.id)
            }
            currentList = updated
            adapter.submitList(updated)
            preferencesRepository.selectedCategory = selectedItem.id
        }
        binding.rvCategories.adapter = adapter
        binding.rvCategories.itemAnimator = null
    }

    private fun setupListeners() {
        binding.btnNextCategory.setOnClickListener {
            AdManager.showInterstitial(this) {
                val intent = Intent(this, WatchCategoryActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun loadData() {
        val saved = preferencesRepository.selectedCategory
        currentList = categoryRepository.getCategories(saved)
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

typealias CategorySelectionActivity = CategoryActivity
