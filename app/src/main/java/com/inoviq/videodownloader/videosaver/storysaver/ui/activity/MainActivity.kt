package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.TabSwitcherAdapter
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityMainBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.BrowserTab
import com.inoviq.videodownloader.videosaver.storysaver.model.HistoryItem
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.BrowserFragment
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.FilesFragment
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.TabStore
import kotlin.math.abs

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var browserFragment: BrowserFragment
    private val openTabs = mutableListOf<BrowserTab>()
    private val historyList = mutableListOf<HistoryItem>()
    private val bookmarksList = mutableListOf<Pair<String, String>>()
    private lateinit var tabAdapter: TabSwitcherAdapter
    private lateinit var inAppUpdateHelper: com.inoviq.videodownloader.videosaver.storysaver.utils.InAppUpdateHelper
    private val historyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val navUrl = result.data?.getStringExtra(HistoryActivity.EXTRA_NAVIGATE_URL)
                val newTabUrl = result.data?.getStringExtra(HistoryActivity.EXTRA_NEW_TAB_URL)
                if (!navUrl.isNullOrBlank()) {
                    browserFragment.loadUrl(navUrl)
                } else if (!newTabUrl.isNullOrBlank()) {
                    addNewTab(newTabUrl)
                }
            }
        }
    private val bookmarkLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val navUrl = result.data?.getStringExtra(BookmarkActivity.EXTRA_NAVIGATE_URL)
                val newTabUrl = result.data?.getStringExtra(BookmarkActivity.EXTRA_NEW_TAB_URL)
                if (!navUrl.isNullOrBlank()) {
                    browserFragment.loadUrl(navUrl)
                } else if (!newTabUrl.isNullOrBlank()) {
                    addNewTab(newTabUrl)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        // Smooth slide-in transition from left when MainActivity opens
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_left,
                R.anim.no_anim
            )
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.no_anim,
                R.anim.slide_out_left
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.no_anim
            )
        }

        val density = resources.displayMetrics.density

        // Fit system window insets for root DrawerLayout and dispatch down to fragmentContainer and drawer tabs
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.fragmentContainer.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            binding.llDrawerTabsHeader.setPadding(
                (20 * density).toInt(),
                systemBars.top + (10 * density).toInt(),
                (20 * density).toInt(),
                (12 * density).toInt()
            )
            binding.rvDrawerTabs.setPadding(
                (6 * density).toInt(),
                (4 * density).toInt(),
                (6 * density).toInt(),
                systemBars.bottom + (20 * density).toInt()
            )
            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
        binding.root.post {
            ViewCompat.requestApplyInsets(binding.fragmentContainer)
        }

        initTabsAndHistory()
        setupFragmentsAndNavigation()
        setupTabsDrawer()

        handleIntent(intent)

        // Initialize Google Play In-App Update check
        inAppUpdateHelper =
            com.inoviq.videodownloader.videosaver.storysaver.utils.InAppUpdateHelper(
                this,
                binding.root
            )
        inAppUpdateHelper.checkForUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("SHOW_DOWNLOADS", false)) {
            binding.root.post { showDownloads() }
        }
        if (intent.getBooleanExtra("FOCUS_SEARCH", false)) {
            binding.root.post {
                if (::browserFragment.isInitialized) {
                    browserFragment.focusSearchInput()
                }
            }
        }
    }

    private fun initTabsAndHistory() {
        val savedTabs = TabStore.getSavedTabs(this)
        openTabs.clear()
        openTabs.addAll(savedTabs)

        historyList.addAll(
            listOf(
                HistoryItem("Instagram - Photos & Videos", "https://www.instagram.com/"),
                HistoryItem("Facebook - Log In or Sign Up", "https://www.facebook.com/"),
                HistoryItem("TikTok - Trending Videos & Sounds", "https://www.tiktok.com/")
            )
        )

        bookmarksList.addAll(
            listOf(
                "Facebook" to "https://m.facebook.com/",
                "Instagram" to "https://www.instagram.com/",
                "TikTok" to "https://www.tiktok.com/",
                "Twitter" to "https://twitter.com/"
            )
        )
    }

    private fun setupFragmentsAndNavigation() {
        val existingBrowser =
            supportFragmentManager.findFragmentByTag("BROWSER") as? BrowserFragment
        browserFragment = existingBrowser ?: BrowserFragment()

        val firstTab = openTabs.firstOrNull { it.isCurrent } ?: openTabs.firstOrNull()
        if (firstTab != null) {
            browserFragment.setInitialTab(firstTab.id, firstTab.url)
        }

        if (existingBrowser == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, browserFragment, "BROWSER")
                .commit()
        }

        browserFragment.onTabSwitcherClicked = {
            openTabsDrawer()
        }

        // When BrowserFragment navigates to a new page or changes title
        browserFragment.onTabUpdated = { title, url ->
            val current = openTabs.firstOrNull { it.isCurrent }
            if (current != null) {
                current.title = title
                current.url = url
                tabAdapter.notifyDataSetChanged()
                TabStore.saveTabs(this, openTabs)
            }
            if (url.isNotBlank() && historyList.none { it.url == url }) {
                historyList.add(0, HistoryItem(title, url))
            }
        }

        // Ensure active tab's WebView session is activated after fragment view is ready
        binding.root.post {
            val currentTab = openTabs.firstOrNull { it.isCurrent } ?: openTabs.firstOrNull()
            if (currentTab != null) {
                browserFragment.switchToTab(currentTab.id, currentTab.url)
            }
        }
    }

    private fun setupTabsDrawer() {
        // Active tabs recycler inside left side drawer (Image 3)
        tabAdapter = TabSwitcherAdapter(
            openTabs,
            onTabClick = { tab ->
                AdManager.showInterstitial(this) {
                    openTabs.forEach { it.isCurrent = false }
                    tab.isCurrent = true
                    tabAdapter.notifyDataSetChanged()
                    // Switch to the tapped tab instantly — no reload
                    browserFragment.switchToTab(tab.id, tab.url)
                    closeTabsDrawer()
                    TabStore.saveTabs(this, openTabs)
                }
            },
            onCloseClick = { tab, position ->
                closeTab(tab, position)
            }
        )

        binding.rvDrawerTabs.layoutManager = LinearLayoutManager(this)
        binding.rvDrawerTabs.itemAnimator =
            androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 220L
                removeDuration = 200L
                changeDuration = 180L
            }
        binding.rvDrawerTabs.adapter = tabAdapter

        // Header '+' button inside Drawer (Vector 1): creates new tab (Image 3)
        binding.btnAddNewTabFromDrawer.setOnClickListener {
            AdManager.showInterstitial(this) {
                addNewTab()
            }
        }

        updateTabCountState()
    }

    /**
     * Opens Left Side Drawer (Image 3).
     */
    fun openTabsDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    /**
     * Closes Left Side Drawer.
     */
    fun closeTabsDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    /**
     * Creates a new Homepage tab or navigates to specified URL, switches to it, and closes Tabs drawer.
     */
    fun addNewTab(initialUrl: String? = null) {
        val hasUrl = !initialUrl.isNullOrBlank()
        val title = if (hasUrl) browserFragment.derivePlatformTitle(initialUrl!!) else "Homepage"
        val newTab = BrowserTab(
            id = System.currentTimeMillis().toString(),
            title = title,
            url = initialUrl ?: "",
            isCurrent = true
        )
        openTabs.forEach { it.isCurrent = false }
        openTabs.add(newTab)
        tabAdapter.notifyDataSetChanged()
        binding.rvDrawerTabs.smoothScrollToPosition(openTabs.size - 1)
        updateTabCountState()

        // Switch to the new tab's isolated WebView session
        browserFragment.switchToTab(newTab.id, initialUrl ?: "")
        closeTabsDrawer()
        TabStore.saveTabs(this, openTabs)
    }

    private fun closeTab(tab: BrowserTab, position: Int) {
        if (openTabs.size > 1) {
            val wasCurrent = tab.isCurrent
            openTabs.removeAt(position)
            // Destroy the closed tab's WebView to free memory
            browserFragment.destroyTab(tab.id)
            if (wasCurrent) {
                val next = openTabs.last()
                next.isCurrent = true
                // Switch to the next tab instantly
                browserFragment.switchToTab(next.id, next.url)
            }
            tabAdapter.notifyDataSetChanged()
            updateTabCountState()
            TabStore.saveTabs(this, openTabs)
        }
    }

    override fun onPause() {
        super.onPause()
        TabStore.saveTabs(this, openTabs)
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateHelper.isInitialized) {
            inAppUpdateHelper.onResume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::inAppUpdateHelper.isInitialized) {
            inAppUpdateHelper.onDestroy()
        }
    }

    override fun onStop() {
        super.onStop()
        TabStore.saveTabs(this, openTabs)
    }

    private fun updateTabCountState() {
        browserFragment.updateTabCount(openTabs.size)
    }

    /**
     * Shows Downloads as a smooth, lightweight BottomSheet dialog (Image 2 MenuItemDownloads).
     */
    fun showDownloads() {
        val filesDialog = FilesFragment()
        filesDialog.show(supportFragmentManager, "FILES_DIALOG")
    }

    fun showHistoryDialog() {
        AdManager.showInterstitial(this) {
            historyLauncher.launch(Intent(this, HistoryActivity::class.java))
        }
    }

    fun showBookmarksDialog() {
        AdManager.showInterstitial(this) {
            bookmarkLauncher.launch(Intent(this, BookmarkActivity::class.java))
        }
    }

    fun addBookmark(title: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank() ||
            trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.equals("home", ignoreCase = true) ||
            title.equals("Homepage", ignoreCase = true)
        ) {
            Toast.makeText(this, "Cannot bookmark home screen", Toast.LENGTH_SHORT).show()
            return
        }
        val targetTitle = if (title.isBlank()) "Bookmark" else title
        val added = BookmarkManager.addBookmark(this, targetTitle, trimmed)
        if (added) {
            Toast.makeText(this, "Bookmark added!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Already bookmarked", Toast.LENGTH_SHORT).show()
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var isSliding = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val screenWidth by lazy { resources.displayMetrics.widthPixels }
    private var isClosing = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            return super.dispatchTouchEvent(ev)
        }
        if (isClosing) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                isSliding = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY

                // Detect right-to-left slide gesture (dx < 0) starting from the right side of the screen
                if (!isSliding && dx < -touchSlop && abs(dx) > abs(dy) * 1.5f && downX > screenWidth * 0.7f) {
                    isSliding = true
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }

                if (isSliding) {
                    binding.root.translationX = dx.coerceAtMost(0f)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(ev)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (isSliding) {
                    isSliding = false
                    val curX = binding.root.translationX
                    if (abs(curX) > screenWidth * 0.20f || vx < -800f) {
                        animateAndClose(curX)
                    } else {
                        animateBack()
                    }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun animateAndClose(fromX: Float) {
        if (isClosing) return
        isClosing = true
        ObjectAnimator.ofFloat(binding.root, View.TRANSLATION_X, fromX, -screenWidth.toFloat())
            .apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        finish()
                        overridePendingTransition(0, 0)
                    }
                })
                start()
            }
    }

    private fun animateBack() {
        ObjectAnimator.ofFloat(binding.root, View.TRANSLATION_X, binding.root.translationX, 0f)
            .apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                start()
            }
    }


    override fun handleBackPress() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeTabsDrawer()
            return
        }

        if (::browserFragment.isInitialized && browserFragment.canGoBack()) {
            browserFragment.goBack()
            return
        }
        if (LauncherHelper.isDefaultLauncher(this@MainActivity)) {
            animateAndClose(binding.root.translationX)
        } else {
            finish()
        }

    }
}