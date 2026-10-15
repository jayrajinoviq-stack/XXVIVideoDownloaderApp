package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.TabSwitcherAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityMainBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.BrowserTab
import com.inoviq.videodownloader.videosaver.storysaver.model.HistoryItem
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.BrowserFragment
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.FilesFragment
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var browserFragment: BrowserFragment
    private val openTabs = mutableListOf<BrowserTab>()
    private val historyList = mutableListOf<HistoryItem>()
    private val bookmarksList = mutableListOf<Pair<String, String>>()
    private lateinit var tabAdapter: TabSwitcherAdapter

    private val historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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

    private val bookmarkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density

        // Fit system window insets for browser screen
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Fit system window insets for left side drawer (edge-to-edge background, inset content)
        ViewCompat.setOnApplyWindowInsetsListener(binding.navDrawerTabs) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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

        initTabsAndHistory()
        setupFragmentsAndNavigation()
        setupTabsDrawer()
        setupBackNavigation()
    }

    private fun initTabsAndHistory() {
        openTabs.add(
            BrowserTab(
                id = "1",
                title = "Homepage",
                url = "",
                isCurrent = true
            )
        )

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
        val existingBrowser = supportFragmentManager.findFragmentByTag("BROWSER") as? BrowserFragment
        browserFragment = existingBrowser ?: BrowserFragment()

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
            }
            if (url.isNotBlank() && historyList.none { it.url == url }) {
                historyList.add(0, HistoryItem(title, url))
            }
        }
    }

    private fun setupTabsDrawer() {
        // Active tabs recycler inside left side drawer (Image 3)
        tabAdapter = TabSwitcherAdapter(
            openTabs,
            onTabClick = { tab ->
                openTabs.forEach { it.isCurrent = false }
                tab.isCurrent = true
                tabAdapter.notifyDataSetChanged()

                if (tab.url.isBlank() || tab.url.equals("home", true)) {
                    browserFragment.showHomeScreen(keepTopUrl = false)
                } else {
                    browserFragment.loadUrl(tab.url)
                }
                closeTabsDrawer()
            },
            onCloseClick = { tab, position ->
                closeTab(tab, position)
            }
        )

        binding.rvDrawerTabs.layoutManager = LinearLayoutManager(this)
        binding.rvDrawerTabs.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            addDuration = 220L
            removeDuration = 200L
            changeDuration = 180L
        }
        binding.rvDrawerTabs.adapter = tabAdapter

        // Header '+' button inside Drawer (Vector 1): creates new tab (Image 3)
        binding.btnAddNewTabFromDrawer.setOnClickListener {
            addNewTab()
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

        if (hasUrl) {
            browserFragment.loadUrl(initialUrl!!)
        } else {
            browserFragment.showHomeScreen(keepTopUrl = false)
        }
        closeTabsDrawer()
    }

    private fun closeTab(tab: BrowserTab, position: Int) {
        if (openTabs.size > 1) {
            val wasCurrent = tab.isCurrent
            openTabs.removeAt(position)
            if (wasCurrent) {
                val next = openTabs.last()
                next.isCurrent = true
                if (next.url.isBlank() || next.url.equals("home", true)) {
                    browserFragment.showHomeScreen(keepTopUrl = false)
                } else {
                    browserFragment.loadUrl(next.url)
                }
            }
            tabAdapter.notifyDataSetChanged()
            updateTabCountState()
        }
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
        historyLauncher.launch(Intent(this, HistoryActivity::class.java))
    }

    fun showBookmarksDialog() {
        bookmarkLauncher.launch(Intent(this, BookmarkActivity::class.java))
    }

    fun addBookmark(title: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank() ||
            trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.equals("home", ignoreCase = true) ||
            title.equals("Homepage", ignoreCase = true)) {
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

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    closeTabsDrawer()
                    return
                }

                if (browserFragment.canGoBack()) {
                    browserFragment.goBack()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }
}