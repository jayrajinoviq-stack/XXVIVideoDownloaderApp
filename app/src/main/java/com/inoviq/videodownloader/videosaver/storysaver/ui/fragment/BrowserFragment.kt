package com.inoviq.videodownloader.videosaver.storysaver.ui.fragment

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.app.Dialog
import android.net.Uri
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.HomeScreenShortcutAdapter
import com.inoviq.videodownloader.videosaver.storysaver.adapter.SearchSuggestionsAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogAddShortcutBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogRemoveShortcutBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogShortcutLimitBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.FragmentBrowserBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemOverlayRecentBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.PopupBrowserMenuBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.SettingsActivity
import com.inoviq.videodownloader.videosaver.storysaver.ui.dialogs.DownloadBottomSheetFragment
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.FacebookMediaSniffer
import com.inoviq.videodownloader.videosaver.storysaver.utils.SearchEngineManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.HistoryEntry
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.HomeScreenShortcut
import com.inoviq.videodownloader.videosaver.storysaver.utils.HomeScreenShortcutManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.NotificationPermissionHelper
import java.net.URLEncoder
import org.json.JSONArray

class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private val detectedMediaList = mutableListOf<MediaItem>()
    @Volatile
    private var lastInterceptedVideoUrl: String? = null
    @Volatile
    private var lastInterceptedVideoTime: Long = 0L

    private var currentTabCount: Int = 1
    private lateinit var suggestionsAdapter: SearchSuggestionsAdapter
    private lateinit var overlaySuggestionsAdapter: SearchSuggestionsAdapter
    private lateinit var shortcutsAdapter: HomeScreenShortcutAdapter
    private val homeShortcutsList = mutableListOf<HomeScreenShortcut>()
    private var isProgrammaticTextChange = false

    @Volatile
    private var savedActiveUrl: String = ""
    private var savedActiveTitle: String = ""
    private var isSearchOverlayOpen: Boolean = false

    var onTabSwitcherClicked: ((View) -> Unit)? = null
    var onTabUpdated: ((title: String, url: String) -> Unit)? = null
    var onMediaDownloaded: ((List<MediaItem>) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTabCountBadge.text = currentTabCount.toString()
        setupTopBar()
        setupHomeScreenShortcuts()
        setupWebView()

        binding.nsvHomeScreen.setOnTouchListener { _, _ ->
            binding.cardSearchSuggestions.visibility = View.GONE
            false
        }
        binding.webView.setOnTouchListener { _, _ ->
            binding.cardSearchSuggestions.visibility = View.GONE
            false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.flFeedbackBottomContainer) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = v.resources.displayMetrics.density
            val navBottom = navBars.bottom
            v.setPadding(0, (6 * density).toInt(), 0, navBottom + (10 * density).toInt())
            insets
        }

        // By default on first open, show Home Screen (Image 4)
        showHomeScreen()
    }

    private fun setupTopBar() {
        // Tab count badge click -> open / toggle Tab Switcher
        binding.btnTabCount.setOnClickListener {
            if (isSearchOverlayOpen) {
                exitSearchOverlay(restorePreviousUrl = true)
            }
            onTabSwitcherClicked?.invoke(it)
        }

        // Tap top address bar:
        // Opens Chrome-style search mode overlay, clears text, shows Current Page Card + 3-4 Recent items
        binding.etUrl.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (!isSearchOverlayOpen) {
                    openSearchOverlay()
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isSearchOverlayOpen) {
                openSearchOverlay()
            }
        }

        // Search & URL entry on keyboard action
        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etUrl.text.toString().trim()
                if (input.isNotEmpty()) {
                    exitSearchOverlay(restorePreviousUrl = false)
                    performSearchOrNavigate(input)
                } else {
                    exitSearchOverlay(restorePreviousUrl = true)
                }
                true
            } else false
        }

        // Prominent Home Search Bar: 1st click selects all, 2nd click places cursor
        binding.etHomeSearch.setOnTouchListener { _, event ->
            if (!binding.etHomeSearch.hasFocus()) {
                if (event.action == MotionEvent.ACTION_UP) {
                    binding.etHomeSearch.requestFocus()
                    binding.etHomeSearch.post {
                        binding.etHomeSearch.selectAll()
                    }
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(binding.etHomeSearch, InputMethodManager.SHOW_IMPLICIT)
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.etHomeSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etHomeSearch.post {
                    binding.etHomeSearch.selectAll()
                }
            } else {
                if (!binding.etUrl.hasFocus()) {
                    binding.cardSearchSuggestions.visibility = View.GONE
                }
            }
        }

        binding.etHomeSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etHomeSearch.text.toString().trim()
                if (input.isNotEmpty()) {
                    binding.cardSearchSuggestions.visibility = View.GONE
                    binding.etUrl.clearFocus()
                    binding.etHomeSearch.clearFocus()
                    hideKeyboard()
                    performSearchOrNavigate(input)
                }
                true
            } else false
        }

        binding.btnHomeSearchAction.setOnClickListener {
            val input = binding.etHomeSearch.text.toString().trim()
            if (input.isNotEmpty()) {
                binding.cardSearchSuggestions.visibility = View.GONE
                binding.etUrl.clearFocus()
                binding.etHomeSearch.clearFocus()
                hideKeyboard()
                performSearchOrNavigate(input)
            } else {
                binding.etHomeSearch.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etHomeSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // Home button (Image 1) - resets to clean home state
        binding.btnHome.setOnClickListener {
            if (isSearchOverlayOpen) {
                exitSearchOverlay(restorePreviousUrl = false)
            }
            showHomeScreen(keepTopUrl = false)
        }

        // Settings Screen (Image 2)
        binding.btnSettings.setOnClickListener {
            if (isSearchOverlayOpen) {
                exitSearchOverlay(restorePreviousUrl = true)
            }
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // More Menu Popup (Images 1 and 2)
        binding.btnMore.setOnClickListener { view ->
            if (isSearchOverlayOpen) {
                exitSearchOverlay(restorePreviousUrl = true)
            }
            showCustomBrowserMenu(view)
        }

        setupSearchOverlay()
        setupSearchSuggestions()
    }

    private fun setupSearchOverlay() {
        binding.viewSearchOverlayBackdrop.setOnClickListener {
            exitSearchOverlay(restorePreviousUrl = true)
        }

        binding.btnClearUrl.setOnClickListener {
            binding.etUrl.setText("")
        }

        binding.btnOverlayShare.setOnClickListener {
            if (savedActiveUrl.isNotBlank()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, savedActiveUrl)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Link"))
            }
        }

        binding.btnOverlayCopy.setOnClickListener {
            if (savedActiveUrl.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("URL", savedActiveUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOverlayEdit.setOnClickListener {
            isProgrammaticTextChange = true
            binding.etUrl.setText(savedActiveUrl)
            isProgrammaticTextChange = false
            binding.etUrl.setSelection(savedActiveUrl.length)
            binding.cardSearchCurrentPage.visibility = View.GONE
            binding.btnClearUrl.visibility = View.VISIBLE
            binding.etUrl.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etUrl, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun openSearchOverlay() {
        if (isSearchOverlayOpen) return
        isSearchOverlayOpen = true

        val b = _binding ?: return
        savedActiveUrl = b.etUrl.text.toString().trim()
        savedActiveTitle = b.webView.title ?: derivePlatformTitle(savedActiveUrl)

        // Clear top address bar for new query entry as requested
        isProgrammaticTextChange = true
        b.etUrl.setText("")
        isProgrammaticTextChange = false
        b.btnClearUrl.visibility = View.GONE

        // Setup Current Page Card if on a website
        val isHomeScreen = b.clHomeScreen.visibility == View.VISIBLE ||
                b.webView.visibility != View.VISIBLE ||
                savedActiveUrl.isBlank() ||
                savedActiveUrl.equals("about:blank", true) ||
                savedActiveUrl.equals("home", true)

        if (!isHomeScreen && savedActiveUrl.isNotBlank()) {
            b.cardSearchCurrentPage.visibility = View.VISIBLE
            val displayTitle = if (savedActiveTitle.isNotBlank() && !savedActiveTitle.equals("about:blank", true)) {
                savedActiveTitle
            } else {
                try { Uri.parse(savedActiveUrl).host ?: savedActiveUrl } catch (e: Exception) { savedActiveUrl }
            }
            b.tvCurrentPageTitle.text = displayTitle
            b.tvCurrentPageUrl.text = savedActiveUrl

            val iconRes = resolvePlatformIcon(savedActiveUrl, savedActiveTitle)
            if (iconRes != R.drawable.ic_globe) {
                b.ivCurrentPageFavicon.setImageResource(iconRes)
                b.ivCurrentPageFavicon.visibility = View.VISIBLE
                b.tvCurrentPageLetter.visibility = View.GONE
            } else {
                val host = try { Uri.parse(savedActiveUrl).host ?: "" } catch (e: Exception) { "" }
                val initial = (host.ifBlank { savedActiveTitle }).trimStart { !it.isLetterOrDigit() }.take(1).uppercase()
                if (initial.isNotBlank() && initial[0].isLetter()) {
                    b.tvCurrentPageLetter.text = initial
                    b.tvCurrentPageLetter.visibility = View.VISIBLE
                    b.ivCurrentPageFavicon.visibility = View.GONE
                } else {
                    b.ivCurrentPageFavicon.setImageResource(R.drawable.ic_globe)
                    b.ivCurrentPageFavicon.visibility = View.VISIBLE
                    b.tvCurrentPageLetter.visibility = View.GONE
                }
            }
        } else {
            b.cardSearchCurrentPage.visibility = View.GONE
        }

        // Setup 3 to 4 recent search/visited items below
        populateOverlayRecentRow()

        // Show overlay
        b.llSearchOverlay.visibility = View.VISIBLE
        b.llSearchOverlayHeader.visibility = View.VISIBLE
        b.rvOverlaySuggestions.visibility = View.GONE

        // Request focus and open soft keyboard
        b.etUrl.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(b.etUrl, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun populateOverlayRecentRow() {
        val b = _binding ?: return
        val context = requireContext()
        b.llOverlayRecentRow.removeAllViews()

        val historyList = BrowserHistoryManager.getHistory(context)
            .filter { it.url.isNotBlank() && !it.url.equals("about:blank", true) && !it.url.equals("home", true) }
            .distinctBy { it.url }
            .toMutableList()

        if (historyList.size < 4) {
            val shortcuts = HomeScreenShortcutManager.getShortcuts(context)
            for (sc in shortcuts) {
                if (sc.url.isNotBlank() && historyList.none { it.url.equals(sc.url, ignoreCase = true) }) {
                    historyList.add(HistoryEntry(title = sc.title, url = sc.url))
                    if (historyList.size >= 4) break
                }
            }
        }

        val displayItems = historyList.take(4)
        if (displayItems.isEmpty()) {
            b.llOverlayRecentRow.visibility = View.GONE
            return
        }

        b.llOverlayRecentRow.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(context)
        for (item in displayItems) {
            val itemBinding = ItemOverlayRecentBinding.inflate(inflater, b.llOverlayRecentRow, false)
            itemBinding.tvRecentTitle.text = if (item.title.isNotBlank() && !item.title.equals("Website", true)) {
                item.title
            } else {
                try { Uri.parse(item.url).host ?: item.url } catch (e: Exception) { item.url }
            }

            val iconRes = resolvePlatformIcon(item.url, item.title)
            if (iconRes != R.drawable.ic_globe) {
                itemBinding.ivRecentIcon.setImageResource(iconRes)
                itemBinding.ivRecentIcon.visibility = View.VISIBLE
                itemBinding.tvRecentInitial.visibility = View.GONE
            } else {
                val host = try { Uri.parse(item.url).host ?: "" } catch (e: Exception) { "" }
                val initial = (host.ifBlank { item.title }).trimStart { !it.isLetterOrDigit() }.take(1).uppercase()
                if (initial.isNotBlank() && initial[0].isLetter()) {
                    itemBinding.tvRecentInitial.text = initial
                    itemBinding.tvRecentInitial.visibility = View.VISIBLE
                    itemBinding.ivRecentIcon.visibility = View.GONE
                } else {
                    itemBinding.ivRecentIcon.setImageResource(R.drawable.ic_globe)
                    itemBinding.ivRecentIcon.visibility = View.VISIBLE
                    itemBinding.tvRecentInitial.visibility = View.GONE
                }
            }

            itemBinding.root.setOnClickListener {
                exitSearchOverlay(restorePreviousUrl = false)
                loadUrl(item.url)
            }

            b.llOverlayRecentRow.addView(itemBinding.root)
        }
    }

    private fun exitSearchOverlay(restorePreviousUrl: Boolean) {
        if (!isSearchOverlayOpen) return
        isSearchOverlayOpen = false

        val b = _binding ?: return
        b.llSearchOverlay.visibility = View.GONE
        b.btnClearUrl.visibility = View.GONE

        if (restorePreviousUrl) {
            isProgrammaticTextChange = true
            b.etUrl.setText(savedActiveUrl)
            isProgrammaticTextChange = false
        }

        b.etUrl.clearFocus()
        b.etHomeSearch.clearFocus()
        hideKeyboard()
    }

    private fun setupSearchSuggestions() {
        val popularDomains = listOf(
            "facebook.com", "instagram.com", "tiktok.com",
            "twitter.com", "youtube.com", "google.com",
            "reddit.com", "pinterest.com", "snapchat.com"
        )

        suggestionsAdapter = SearchSuggestionsAdapter(
            mutableListOf(),
            onSuggestionClick = { text ->
                binding.cardSearchSuggestions.visibility = View.GONE
                binding.etUrl.clearFocus()
                binding.etHomeSearch.clearFocus()
                hideKeyboard()
                performSearchOrNavigate(text)
            },
            onFillQuery = { text ->
                binding.etHomeSearch.setText(text)
                binding.etHomeSearch.setSelection(text.length)
            }
        )

        binding.rvSearchSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchSuggestions.adapter = suggestionsAdapter

        overlaySuggestionsAdapter = SearchSuggestionsAdapter(
            mutableListOf(),
            onSuggestionClick = { text ->
                exitSearchOverlay(restorePreviousUrl = false)
                performSearchOrNavigate(text)
            },
            onFillQuery = { text ->
                isProgrammaticTextChange = true
                binding.etUrl.setText(text)
                isProgrammaticTextChange = false
                binding.etUrl.setSelection(text.length)
            }
        )

        binding.rvOverlaySuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOverlaySuggestions.adapter = overlaySuggestionsAdapter

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticTextChange) return

                val query = s?.toString()?.trim() ?: ""

                if (isSearchOverlayOpen) {
                    if (query.isNotEmpty()) {
                        binding.btnClearUrl.visibility = View.VISIBLE
                        binding.cardSearchCurrentPage.visibility = View.GONE
                        binding.llOverlayRecentRow.visibility = View.GONE

                        val filtered = mutableListOf<String>()
                        popularDomains.filter { it.contains(query, ignoreCase = true) }.forEach { filtered.add(it) }
                        filtered.add("$query video download")
                        filtered.add("$query reels download")
                        filtered.add("$query login")

                        overlaySuggestionsAdapter.updateSuggestions(filtered.distinct().take(6))
                        binding.rvOverlaySuggestions.visibility = View.VISIBLE
                    } else {
                        binding.btnClearUrl.visibility = View.GONE
                        binding.rvOverlaySuggestions.visibility = View.GONE

                        val isHomeScreen = binding.clHomeScreen.visibility == View.VISIBLE ||
                                binding.webView.visibility != View.VISIBLE ||
                                savedActiveUrl.isBlank() ||
                                savedActiveUrl.equals("about:blank", true) ||
                                savedActiveUrl.equals("home", true)

                        if (!isHomeScreen && savedActiveUrl.isNotBlank()) {
                            binding.cardSearchCurrentPage.visibility = View.VISIBLE
                        }
                        binding.llOverlayRecentRow.visibility = View.VISIBLE
                    }
                    return
                }

                val isHomeFocused = binding.etHomeSearch.hasFocus()
                if (query.isNotEmpty() && isHomeFocused) {
                    val filtered = mutableListOf<String>()
                    popularDomains.filter { it.contains(query, ignoreCase = true) }.forEach { filtered.add(it) }
                    filtered.add("$query video download")
                    filtered.add("$query reels download")
                    filtered.add("$query login")

                    suggestionsAdapter.updateSuggestions(filtered.distinct().take(6))
                    binding.cardSearchSuggestions.visibility = View.VISIBLE
                } else {
                    binding.cardSearchSuggestions.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etUrl.addTextChangedListener(textWatcher)
        binding.etHomeSearch.addTextChangedListener(textWatcher)

        binding.etHomeSearch.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !binding.etHomeSearch.hasFocus()) {
                binding.cardSearchSuggestions.visibility = View.GONE
            }
        }
    }

    /**
     * Custom popup menu identical to Image 1 and Image 2.
     */
    private fun showCustomBrowserMenu(anchorView: View) {
        val context = requireContext()
        val popupBinding = PopupBrowserMenuBinding.inflate(layoutInflater)
        val density = resources.displayMetrics.density

        val popupWidth = (195 * density).toInt()
        val popupWindow = PopupWindow(
            popupBinding.root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12 * density
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        val isHomeScreen = binding.clHomeScreen.visibility == View.VISIBLE ||
                binding.webView.visibility != View.VISIBLE ||
                binding.webView.url.isNullOrBlank() ||
                binding.webView.url.equals("about:blank", ignoreCase = true)

        if (isHomeScreen) {
            popupBinding.menuItemShare.alpha = 0.5f
            popupBinding.menuItemAddBookmark.alpha = 0.5f
            popupBinding.menuItemCopyLink.alpha = 0.5f
        }

        // 1. New Tab
        popupBinding.menuItemNewTab.setOnClickListener {
            popupWindow.dismiss()
            (activity as? MainActivity)?.addNewTab()
        }

        // 2. Share
        popupBinding.menuItemShare.setOnClickListener {
            popupWindow.dismiss()
            if (isHomeScreen) {
                Toast.makeText(context, "Cannot share home screen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentUrl = binding.webView.url ?: binding.etUrl.text.toString().trim()
            if (currentUrl.isBlank() || currentUrl.equals("about:blank", true) || currentUrl.equals("home", true)) {
                Toast.makeText(context, "Cannot share home screen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Share URL")
                putExtra(Intent.EXTRA_TEXT, currentUrl)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Link"))
        }

        // 3. Downloads (Image 2)
        popupBinding.menuItemDownloads.setOnClickListener {
            popupWindow.dismiss()
            (activity as? MainActivity)?.showDownloads()
        }

        // 4. History
        popupBinding.menuItemHistory.setOnClickListener {
            popupWindow.dismiss()
            (activity as? MainActivity)?.showHistoryDialog()
        }

        // 4. Bookmark
        popupBinding.menuItemBookmark.setOnClickListener {
            popupWindow.dismiss()
            (activity as? MainActivity)?.showBookmarksDialog()
        }

        // 5. Add Bookmark
        popupBinding.menuItemAddBookmark.setOnClickListener {
            popupWindow.dismiss()
            if (isHomeScreen) {
                Toast.makeText(context, "Cannot bookmark home screen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentUrl = binding.webView.url ?: binding.etUrl.text.toString().trim()
            if (currentUrl.isBlank() || currentUrl.equals("about:blank", true) || currentUrl.equals("home", true)) {
                Toast.makeText(context, "Cannot bookmark home screen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val title = binding.webView.title?.takeIf { it.isNotBlank() } ?: derivePlatformTitle(currentUrl)
            (activity as? MainActivity)?.addBookmark(title, currentUrl)
        }

        // 6. Copy Link
        popupBinding.menuItemCopyLink.setOnClickListener {
            popupWindow.dismiss()
            if (isHomeScreen) {
                Toast.makeText(context, "Cannot copy link from home screen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentUrl = binding.webView.url ?: binding.etUrl.text.toString().trim()
            if (currentUrl.isBlank() || currentUrl.equals("about:blank", true) || currentUrl.equals("home", true)) {
                Toast.makeText(context, "Cannot copy link from home screen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("URL", currentUrl)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Anchor with right-aligned offset giving comfortable space from screen edge
        val xOffset = (-165 * density).toInt()
        val yOffset = (10 * density).toInt()
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Bind dynamic shortcuts on Home Screen with add, limit, and remove capability.
     */
    private fun setupHomeScreenShortcuts() {
        val ctx = requireContext()
        homeShortcutsList.clear()
        homeShortcutsList.addAll(HomeScreenShortcutManager.getShortcuts(ctx))

        shortcutsAdapter = HomeScreenShortcutAdapter(
            homeShortcutsList,
            onShortcutClick = { shortcut ->
                loadUrl(shortcut.url)
            },
            onAddClick = {
                if (homeShortcutsList.size >= HomeScreenShortcutManager.MAX_SHORTCUTS) {
                    showShortcutLimitDialog()
                } else {
                    showAddShortcutDialog()
                }
            },
            onRemoveClick = { shortcut ->
                showRemoveShortcutDialog(shortcut)
            }
        )

        binding.rvShortcutsGrid.layoutManager = GridLayoutManager(ctx, 3)
        binding.rvShortcutsGrid.adapter = shortcutsAdapter

        // Tap on home scroll background to dismiss edit mode
        binding.nsvHomeScreen.setOnClickListener {
            if (::shortcutsAdapter.isInitialized && shortcutsAdapter.isEditMode) {
                shortcutsAdapter.setEditMode(false)
            }
        }

        refreshRecentlyUsedWebsites()

        // Feedback Button
        binding.btnFeedback.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val dialog = Dialog(ctx)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            val alertBinding = com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogCustomAlertBinding.inflate(layoutInflater)
            dialog.setContentView(alertBinding.root)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

            alertBinding.tvAlertTitle.text = "Feedback & Questions"
            alertBinding.tvAlertMsg.text = "Have a feature request, question, or need assistance? Reach out to our support team anytime at support@xxvi.app"
            alertBinding.btnAlertNegative.visibility = View.VISIBLE
            alertBinding.btnAlertNegative.text = "CANCEL"
            alertBinding.btnAlertPositive.text = "SEND EMAIL"

            alertBinding.btnAlertNegative.setOnClickListener {
                dialog.dismiss()
            }

            alertBinding.btnAlertPositive.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:support@xxvi.app?subject=XXVI%20Feedback")
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Email app not found", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.show()
        }
    }

    private fun showAddShortcutDialog() {
        val ctx = context ?: return
        val dialog = Dialog(ctx)
        val dBinding = DialogAddShortcutBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dBinding.btnCancelAddShortcut.setOnClickListener {
            dialog.dismiss()
        }

        dBinding.btnConfirmAddShortcut.setOnClickListener {
            val name = dBinding.etShortcutName.text.toString().trim()
            val url = dBinding.etShortcutUrl.text.toString().trim()

            if (url.isBlank()) {
                Toast.makeText(ctx, "Please enter a website URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val added = HomeScreenShortcutManager.addShortcut(ctx, name, url)
            if (added) {
                homeShortcutsList.clear()
                homeShortcutsList.addAll(HomeScreenShortcutManager.getShortcuts(ctx))
                shortcutsAdapter.notifyDataSetChanged()
                dialog.dismiss()
                Toast.makeText(ctx, "Shortcut added", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                showShortcutLimitDialog()
            }
        }

        dialog.show()
    }

    private fun showRemoveShortcutDialog(shortcut: HomeScreenShortcut) {
        val ctx = context ?: return
        val dialog = Dialog(ctx)
        val dBinding = DialogRemoveShortcutBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dBinding.tvRemoveShortcutMsg.text = "Would you like to remove \"${shortcut.title}\" from your shortcuts?"

        dBinding.btnCancelRemove.setOnClickListener {
            dialog.dismiss()
        }

        dBinding.btnConfirmRemove.setOnClickListener {
            HomeScreenShortcutManager.removeShortcut(ctx, shortcut.id)
            homeShortcutsList.clear()
            homeShortcutsList.addAll(HomeScreenShortcutManager.getShortcuts(ctx))
            shortcutsAdapter.notifyDataSetChanged()
            dialog.dismiss()
            Toast.makeText(ctx, "Shortcut removed", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showShortcutLimitDialog() {
        val ctx = context ?: return
        val dialog = Dialog(ctx)
        val dBinding = DialogShortcutLimitBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dBinding.btnConfirmLimit.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toggleDesktopMode() {
        val current = binding.webView.settings.userAgentString ?: ""
        if (current.contains("Mobile")) {
            binding.webView.settings.userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Toast.makeText(requireContext(), "Switched to Desktop Site", Toast.LENGTH_SHORT).show()
        } else {
            binding.webView.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            Toast.makeText(requireContext(), "Switched to Mobile Site", Toast.LENGTH_SHORT).show()
        }
        binding.webView.reload()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Enable persistent cookies and storage for seamless login sessions
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }

        // Bridge to receive clicks on the injected download buttons
        binding.webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onMediaClicked(url: String, type: String, title: String, thumb: String) {
                    activity?.runOnUiThread {
                        handleMediaDownloadFromWeb(url, type, title, thumb)
                    }
                }

                @JavascriptInterface
                fun onMultiMediaClicked(jsonArrayStr: String, type: String, title: String) {
                    activity?.runOnUiThread {
                        handleMultiMediaDownloadFromWeb(jsonArrayStr, type, title)
                    }
                }

                @JavascriptInterface
                fun onMediaDetected(url: String, type: String, title: String, thumb: String) {
                    activity?.runOnUiThread {
                        addDetectedMedia(title, url, type.equals("video", true), thumb)
                    }
                }
            },
            "XXVIBridge"
        )

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val urlStr = uri.toString()

                if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                    return false
                }

                return try {
                    val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                    context?.startActivity(intent)
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.pbLoading.visibility = View.VISIBLE
                binding.cardSearchSuggestions.visibility = View.GONE
                if (!url.isNullOrBlank() && !url.equals("about:blank", true) && !url.equals("home", true)) {
                    savedActiveUrl = url
                    val title = derivePlatformTitle(url)
                    savedActiveTitle = title
                    isProgrammaticTextChange = true
                    binding.etUrl.setText(url)
                    isProgrammaticTextChange = false
                    onTabUpdated?.invoke(title, url)
                }
                injectDownloaderScript(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.pbLoading.visibility = View.GONE
                binding.cardSearchSuggestions.visibility = View.GONE
                // Flush cookies to ensure logins persist across sessions
                CookieManager.getInstance().flush()
                if (!url.isNullOrBlank() && !url.equals("about:blank", true) && !url.equals("home", true)) {
                    savedActiveUrl = url
                    val title = derivePlatformTitle(url)
                    savedActiveTitle = title
                    isProgrammaticTextChange = true
                    binding.etUrl.setText(url)
                    isProgrammaticTextChange = false
                    onTabUpdated?.invoke(title, url)
                    context?.let { BrowserHistoryManager.addHistory(it, title, url) }
                }
                injectDownloaderScript(view)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                val isVideo = url.endsWith(".mp4", ignoreCase = true) ||
                        url.contains(".mp4?", ignoreCase = true) ||
                        url.contains(".webm", ignoreCase = true) ||
                        url.contains(".m4v", ignoreCase = true) ||
                        url.contains(".mov", ignoreCase = true) ||
                        url.contains(".m3u8", ignoreCase = true) ||
                        url.contains("video.xx.fbcdn.net", ignoreCase = true) ||
                        url.contains("video.fdel", ignoreCase = true) ||
                        url.contains("video.fsin", ignoreCase = true) ||
                        url.contains("video.fmaa", ignoreCase = true) ||
                        (url.contains("video-", ignoreCase = true) && url.contains("fbcdn.net", ignoreCase = true)) ||
                        (url.contains("fbcdn.net", ignoreCase = true) && (url.contains("/v/t") || url.contains("bytestart=")) && !url.contains(".jpg") && !url.contains(".png") && !url.contains(".webp")) ||
                        (url.contains("cdninstagram.com", ignoreCase = true) && (url.contains(".mp4") || url.contains("/v/"))) ||
                        url.contains("tiktokcdn.com", ignoreCase = true) ||
                        url.contains("byteoversea.com", ignoreCase = true) ||
                        url.contains("video.twimg.com", ignoreCase = true) ||
                        url.contains("whatsapp.net", ignoreCase = true) ||
                        url.contains("videoplayback", ignoreCase = true) ||
                        url.contains("mime=video", ignoreCase = true)

                if (isVideo) {
                    lastInterceptedVideoUrl = url
                    lastInterceptedVideoTime = System.currentTimeMillis()
                    val webUrl = savedActiveUrl
                    val platform = DownloadHelper.derivePlatformPrefix(if (webUrl.isNotBlank() && !webUrl.startsWith("about:") && !webUrl.startsWith("home")) webUrl else url)
                    activity?.runOnUiThread {
                        addDetectedMedia("${platform}_video_${System.currentTimeMillis() % 1000000}", url, true)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.pbLoading.progress = newProgress
                if (newProgress in 40..95) {
                    injectDownloaderScript(view)
                }
                if (newProgress >= 100) {
                    binding.pbLoading.visibility = View.GONE
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                android.util.Log.d("XXVI_CONSOLE", "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()}")
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }

    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (_binding != null && isResumed && binding.webView.visibility == View.VISIBLE) {
                injectDownloaderScript(binding.webView)
                heartbeatHandler.postDelayed(this, 1500L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        heartbeatHandler.post(heartbeatRunnable)
        refreshRecentlyUsedWebsites()
        if (::shortcutsAdapter.isInitialized && isAdded) {
            shortcutsAdapter.updateShortcuts(HomeScreenShortcutManager.getShortcuts(requireContext()))
        }
    }

    override fun onPause() {
        super.onPause()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun injectDownloaderScript(view: WebView?) {
        view?.evaluateJavascript(FacebookMediaSniffer.getInjectionScript(), null)
    }

    fun handleMediaDownloadFromWeb(url: String, type: String, title: String, thumb: String) {
        if (!isAdded) return

        var targetUrl = url
        val isVid = type.contains("video", true) || type.contains("reel", true) ||
                url.contains(".mp4", true) || url.contains(".webm", true) ||
                url.contains("video.xx.fbcdn.net", true) ||
                (url.contains("fbcdn.net", true) && url.contains("/v/t", true) && !url.contains(".jpg", true))

        if (targetUrl.isBlank() || !targetUrl.startsWith("http") || targetUrl.startsWith("blob:")) {
            val intercepted = lastInterceptedVideoUrl
            if (isVid && !intercepted.isNullOrBlank()) {
                targetUrl = intercepted
            } else {
                val candidate = (if (isVid) detectedMediaList.firstOrNull { it.isVideo }?.url else null)
                    ?: detectedMediaList.firstOrNull { it.isVideo == isVid }?.url
                    ?: detectedMediaList.firstOrNull()?.url
                if (!candidate.isNullOrBlank() && candidate.startsWith("http")) {
                    targetUrl = candidate
                } else if (isVid) {
                    targetUrl = ""
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Extracting media... Opening download options",
                        Toast.LENGTH_SHORT
                    ).show()
                    showDownloadBottomSheet()
                    return
                }
            }
        }

        targetUrl = DownloadHelper.cleanMediaUrl(targetUrl)

        if (!isVid && DownloadHelper.isProfileOrAvatarUrl(targetUrl)) {
            return
        }

        val platform = DownloadHelper.derivePlatformPrefix(if (savedActiveUrl.isNotBlank()) savedActiveUrl else targetUrl)
        val cleanName = if (title.isBlank() || title.equals("facebook_photo", true) || title.equals("facebook_video", true) || title.equals("media", true)) {
            "${platform}_${if (isVid) "video" else "photo"}_${System.currentTimeMillis() % 100000000}"
        } else if (!title.startsWith(platform, ignoreCase = true) && !title.contains("_")) {
            "${platform}_$title"
        } else {
            title
        }
        val cleanThumb = if (thumb.startsWith("http")) thumb else targetUrl

        val mediaItem = MediaItem(
            id = System.currentTimeMillis().toString(),
            title = cleanName,
            url = targetUrl,
            thumbnailUrl = cleanThumb,
            sizeFormatted = if (isVid) "5.19MB" else "15.67kB",
            isVideo = isVid,
            isChecked = true
        )
        addDetectedMedia(cleanName, targetUrl, isVid, cleanThumb)

        // Notification permission check (up to 2 times system prompt, then custom settings dialog)
        activity?.let { NotificationPermissionHelper.checkAndRequestPermission(it) }

        // For a single media item click: show ONLY this single clicked item at download time!
        val candidates = mutableListOf(mediaItem)

        // Open bottom sheet dialog BEFORE start download
        val sheet = DownloadBottomSheetFragment.newInstance(candidates) { selectedItems ->
            selectedItems.forEach { selected ->
                var actualUrl = selected.url
                if (actualUrl.isBlank() || actualUrl.startsWith("blob:")) {
                    actualUrl = (lastInterceptedVideoUrl ?: "").ifBlank {
                        detectedMediaList.firstOrNull { it.isVideo }?.url ?: ""
                    }
                }
                if (actualUrl.isBlank()) return@forEach

                val downloadedItem = DownloadHelper.downloadMedia(
                    context = requireContext(),
                    url = actualUrl,
                    type = if (selected.isAudio) "audio" else if (selected.isVideo) "video" else "image",
                    customName = selected.title,
                    thumbUrl = selected.thumbnailUrl
                ) { updatedItem ->
                    activity?.runOnUiThread {
                        onMediaDownloaded?.invoke(listOf(updatedItem))
                    }
                }

                if (downloadedItem != null) {
                    onMediaDownloaded?.invoke(listOf(downloadedItem))
                    addDetectedMedia(downloadedItem.title, actualUrl, downloadedItem.isVideo, selected.thumbnailUrl)
                }
            }

            val rootV = _binding?.root
            if (rootV != null) {
                val message = if (selectedItems.size == 1) {
                    "Downloading ${selectedItems.first().title}..."
                } else {
                    "Downloading ${selectedItems.size} files..."
                }
                com.google.android.material.snackbar.Snackbar.make(
                    rootV,
                    message,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).setAction("VIEW") {
                    (activity as? MainActivity)?.showDownloads()
                }.show()
            }
        }
        sheet.show(parentFragmentManager, DownloadBottomSheetFragment.TAG)
    }

    /**
     * Handles downloading multiple images belonging to the same post / carousel (multipal gram).
     */
    fun handleMultiMediaDownloadFromWeb(jsonArrayStr: String, type: String, title: String) {
        if (!isAdded) return
        activity?.let { NotificationPermissionHelper.checkAndRequestPermission(it) }
        try {
            val jsonArray = JSONArray(jsonArrayStr)
            val candidates = mutableListOf<MediaItem>()
            val platform = DownloadHelper.derivePlatformPrefix(savedActiveUrl)

            var hasAnySelected = false
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getJSONObject(i).optBoolean("isSelected", false)) {
                    hasAnySelected = true
                    break
                }
            }

            val isRequestedVideo = type.contains("video", true) || type.contains("reel", true)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isVideo = obj.optBoolean("isVideo", false)
                // If downloading a video, never include photos
                if (isRequestedVideo && !isVideo) continue
                // If downloading photos, never include videos
                if (!isRequestedVideo && isVideo) continue

                val rawUrl = obj.optString("url")
                val url = DownloadHelper.cleanMediaUrl(rawUrl)
                if (url.isBlank() || !url.startsWith("http")) continue
                if (!isVideo && DownloadHelper.isProfileOrAvatarUrl(url)) continue
                val thumb = obj.optString("thumb").ifBlank { url }
                val itemTitle = obj.optString("title").ifBlank {
                    "${platform}_${if (isVideo) "video" else "photo"}_${System.currentTimeMillis() % 1000000}_${i + 1}"
                }
                val isChecked = if (hasAnySelected) {
                    obj.optBoolean("isSelected", false)
                } else {
                    i == 0
                }

                val item = MediaItem(
                    id = (System.currentTimeMillis() + i).toString(),
                    title = itemTitle,
                    url = url,
                    thumbnailUrl = thumb,
                    sizeFormatted = if (isVideo) "5.19MB" else "15.67kB",
                    isVideo = isVideo,
                    isChecked = isChecked
                )
                candidates.add(item)
                addDetectedMedia(itemTitle, url, isVideo, thumb)
            }

            if (candidates.isEmpty()) return

            if (candidates.size == 1 || candidates.none { it.isChecked }) {
                candidates[0] = candidates[0].copy(isChecked = true)
            }

            val sheet = DownloadBottomSheetFragment.newInstance(candidates) { selectedItems ->
                selectedItems.forEach { selected ->
                    val downloadedItem = DownloadHelper.downloadMedia(
                        context = requireContext(),
                        url = selected.url,
                        type = if (selected.isAudio) "audio" else if (selected.isVideo) "video" else "image",
                        customName = selected.title,
                        thumbUrl = selected.thumbnailUrl
                    ) { updatedItem ->
                        activity?.runOnUiThread {
                            onMediaDownloaded?.invoke(listOf(updatedItem))
                        }
                    }

                    if (downloadedItem != null) {
                        onMediaDownloaded?.invoke(listOf(downloadedItem))
                    }
                }

                val rootV = _binding?.root
                if (rootV != null) {
                    val message = if (selectedItems.size == 1) {
                        "Downloading ${selectedItems.first().title}..."
                    } else {
                        "Downloading ${selectedItems.size} files..."
                    }
                    com.google.android.material.snackbar.Snackbar.make(
                        rootV,
                        message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("VIEW") {
                        (activity as? MainActivity)?.showDownloads()
                    }.show()
                }
            }
            sheet.show(parentFragmentManager, DownloadBottomSheetFragment.TAG)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addDetectedMedia(name: String, url: String, isVideo: Boolean, thumb: String? = null) {
        if (url.isBlank() || !url.startsWith("http") || url.startsWith("blob:")) return
        if (!isVideo && DownloadHelper.isProfileOrAvatarUrl(url)) return
        if (detectedMediaList.none { it.url == url }) {
            val currentWebUrl = savedActiveUrl
            val platform = DownloadHelper.derivePlatformPrefix(if (currentWebUrl.isNotBlank() && !currentWebUrl.startsWith("about:") && !currentWebUrl.startsWith("home")) currentWebUrl else url)
            val cleanName = if (name.isBlank() || name.equals("facebook_photo", true) || name.equals("facebook_video", true) || name.equals("media", true)) {
                "${platform}_${if (isVideo) "video" else "photo"}_${System.currentTimeMillis() % 100000000}"
            } else if (!name.startsWith(platform, ignoreCase = true) && !name.contains("_")) {
                "${platform}_$name"
            } else {
                name
            }
            detectedMediaList.add(
                0,
                MediaItem(
                    id = System.currentTimeMillis().toString(),
                    title = cleanName,
                    url = url,
                    thumbnailUrl = if (!thumb.isNullOrBlank() && thumb.startsWith("http")) thumb else url,
                    sizeFormatted = if (isVideo) "3.42 MB" else "15.67kB",
                    isVideo = isVideo
                )
            )
        }
    }

    /**
     * Switch view state to Home Screen (Image 4).
     */
    fun showHomeScreen(keepTopUrl: Boolean = false) {
        val b = _binding ?: return
        if (isSearchOverlayOpen) {
            exitSearchOverlay(restorePreviousUrl = false)
        }
        isProgrammaticTextChange = true
        b.cardSearchSuggestions.visibility = View.GONE
        b.etUrl.clearFocus()
        b.etHomeSearch.clearFocus()
        hideKeyboard()

        b.clHomeScreen.visibility = View.VISIBLE
        b.webView.visibility = View.GONE
        b.nsvFacebookFeed.visibility = View.GONE

        // Request notification permission on Home screen (up to 2 times system prompt, then custom dialog)
        activity?.let { NotificationPermissionHelper.checkAndRequestPermission(it) }

        b.etHomeSearch.setText("")
        if (!keepTopUrl) {
            b.etUrl.setText("")
            savedActiveUrl = ""
            savedActiveTitle = ""
        }
        b.etUrl.hint = "Search Or type URL"
        isProgrammaticTextChange = false

        refreshRecentlyUsedWebsites()
        if (::shortcutsAdapter.isInitialized) {
            shortcutsAdapter.setEditMode(false)
        }
        onTabUpdated?.invoke("Homepage", b.etUrl.text.toString())
    }

    /**
     * Populates the Recently Used Website section on the Home Screen:
     * - Title and container are always visible on Home Screen as required
     * - Item 1: 1st recent website (defaults to Facebook if none)
     * - Item 2: 2nd recent website (defaults to duckduckgo.com if none)
     * - Item 3: "View All" button opening all previously used websites in HistoryActivity
     */
    fun refreshRecentlyUsedWebsites() {
        val b = _binding ?: return
        val ctx = context ?: return

        b.tvRecentlyUsedTitle.visibility = View.VISIBLE
        b.llRecentlyUsedContainer.visibility = View.VISIBLE

        val recents = BrowserHistoryManager.getRecentlyUsedWebsites24h(ctx)

        // Item 1: 1st recent website
        val item1 = recents.getOrNull(0) ?: HistoryEntry(title = "Facebook", url = "https://m.facebook.com/")
        b.btnRecentItem1.visibility = View.VISIBLE
        b.tvRecentTitle1.text = item1.title.ifBlank { "Facebook" }
        b.ivRecentIcon1.setImageResource(resolvePlatformIcon(item1.url, item1.title))
        b.btnRecentItem1.setOnClickListener {
            loadUrl(item1.url)
        }

        // Item 2: 2nd recent website
        val item2 = recents.getOrNull(1) ?: HistoryEntry(title = "duckduckgo.com", url = "https://duckduckgo.com/")
        b.btnRecentItem2.visibility = View.VISIBLE
        b.tvRecentTitle2.text = item2.title.ifBlank { "duckduckgo.com" }
        b.ivRecentIcon2.setImageResource(resolvePlatformIcon(item2.url, item2.title))
        b.btnRecentItem2.setOnClickListener {
            loadUrl(item2.url)
        }

        // Item 3: "All" / "View All" - opens all previously used websites in HistoryActivity
        b.btnRecentAll.visibility = View.VISIBLE
        b.btnRecentAll.setOnClickListener {
            (activity as? MainActivity)?.showHistoryDialog()
        }
    }

    private fun resolvePlatformIcon(url: String, title: String): Int {
        val lowerUrl = url.lowercase()
        val lowerTitle = title.lowercase()
        return when {
            lowerUrl.contains("instagram") || lowerTitle.contains("instagram") -> R.drawable.img_logo_instagram
            lowerUrl.contains("facebook") || lowerTitle.contains("facebook") -> R.drawable.img_logos_facebook
            lowerUrl.contains("tiktok") || lowerTitle.contains("tiktok") -> R.drawable.img_logos_tiktok
            lowerUrl.contains("whatsapp") || lowerTitle.contains("whatsapp") -> R.drawable.img_logos_whatsapp
            lowerUrl.contains("twitter") || lowerUrl.contains("x.com") || lowerTitle.contains("twitter") -> R.drawable.img_logo_twitter_x
            lowerUrl.contains("youtube") || lowerTitle.contains("youtube") -> R.drawable.ic_logo_youtube
            lowerUrl.contains("google") || lowerTitle.contains("google") -> R.drawable.ic_logo_google
            else -> R.drawable.ic_globe
        }
    }

    /**
     * Parse input and load URL or Google Search query (Images 4 & 5).
     */
    fun performSearchOrNavigate(input: String) {
        val target = input.trim()
        if (target.isEmpty()) return

        binding.cardSearchSuggestions.visibility = View.GONE
        binding.etUrl.clearFocus()
        binding.etHomeSearch.clearFocus()
        hideKeyboard()

        val finalUrl = when {
            target.startsWith("http://") || target.startsWith("https://") -> target
            target.contains(".") && !target.contains(" ") -> "https://$target"
            else -> SearchEngineManager.buildSearchUrl(requireContext(), target)
        }

        loadUrl(finalUrl)
    }

    /**
     * Switch view state to WebView and navigate to URL (Image 5).
     */
    fun loadUrl(url: String) {
        val target = url.trim()
        if (target.isEmpty() || target.equals("about:blank", true) || target.equals("home", true)) {
            showHomeScreen(keepTopUrl = false)
            return
        }
        val b = _binding ?: return
        if (isSearchOverlayOpen) {
            exitSearchOverlay(restorePreviousUrl = false)
        }

        b.cardSearchSuggestions.visibility = View.GONE
        b.etUrl.clearFocus()
        b.etHomeSearch.clearFocus()
        hideKeyboard()

        b.clHomeScreen.visibility = View.GONE
        b.webView.visibility = View.VISIBLE
        b.nsvFacebookFeed.visibility = View.GONE

        // Clear detected media from previous page
        detectedMediaList.clear()

        val finalUrl = when {
            target.startsWith("http://") || target.startsWith("https://") -> target
            target.contains(".") && !target.contains(" ") -> "https://$target"
            else -> SearchEngineManager.buildSearchUrl(requireContext(), target)
        }

        isProgrammaticTextChange = true
        b.etUrl.setText(finalUrl)
        isProgrammaticTextChange = false
        b.webView.loadUrl(finalUrl)

        val derivedTitle = derivePlatformTitle(finalUrl)
        onTabUpdated?.invoke(derivedTitle, finalUrl)
    }

    fun derivePlatformTitle(url: String): String {
        return when {
            url.contains("facebook.com", true) -> "Facebook"
            url.contains("instagram.com", true) -> "Instagram"
            url.contains("tiktok.com", true) -> "TikTok"
            url.contains("whatsapp.com", true) -> "WhatsApp"
            url.contains("twitter.com", true) || url.contains("x.com", true) -> "Twitter"
            url.contains("youtube.com", true) -> "YouTube"
            url.contains("google.com", true) -> "Google"
            url.isBlank() -> "Homepage"
            else -> {
                try {
                    val host = android.net.Uri.parse(url).host ?: url
                    host.removePrefix("www.").removePrefix("m.")
                } catch (_: Exception) {
                    url
                }
            }
        }
    }

    fun showDownloadBottomSheet() {
        if (!isAdded) return
        activity?.let { NotificationPermissionHelper.checkAndRequestPermission(it) }
        if (detectedMediaList.isEmpty()) {
            Toast.makeText(requireContext(), "No media detected on this page yet", Toast.LENGTH_SHORT).show()
            return
        }
        val items = detectedMediaList.mapIndexed { index, item -> item.copy(isChecked = (index == 0)) }
        val sheet = DownloadBottomSheetFragment.newInstance(items) { selectedItems ->
            selectedItems.forEach { item ->
                DownloadHelper.downloadMedia(
                    context = requireContext(),
                    url = item.url,
                    type = if (item.isVideo) "video" else "image",
                    customName = item.title,
                    thumbUrl = item.thumbnailUrl
                ) { updatedItem ->
                    activity?.runOnUiThread {
                        onMediaDownloaded?.invoke(listOf(updatedItem))
                    }
                }
            }
            onMediaDownloaded?.invoke(selectedItems)

            val rootV = _binding?.root
            if (rootV != null) {
                com.google.android.material.snackbar.Snackbar.make(
                    rootV,
                    "${selectedItems.size} files downloading...",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).setAction("VIEW") {
                    (activity as? MainActivity)?.showDownloads()
                }.show()
            }
        }
        sheet.show(parentFragmentManager, DownloadBottomSheetFragment.TAG)
    }

    fun canGoBack(): Boolean {
        if (::shortcutsAdapter.isInitialized && shortcutsAdapter.isEditMode) {
            return true
        }
        if (isSearchOverlayOpen) {
            return true
        }
        val b = _binding ?: return false
        return (b.webView.visibility == View.VISIBLE && b.webView.canGoBack()) ||
               (b.webView.visibility == View.VISIBLE && b.clHomeScreen.visibility != View.VISIBLE)
    }

    fun goBack() {
        if (::shortcutsAdapter.isInitialized && shortcutsAdapter.isEditMode) {
            shortcutsAdapter.setEditMode(false)
            return
        }
        if (isSearchOverlayOpen) {
            exitSearchOverlay(restorePreviousUrl = true)
            return
        }
        val b = _binding ?: return
        if (b.cardSearchSuggestions.visibility == View.VISIBLE) {
            b.cardSearchSuggestions.visibility = View.GONE
            b.etUrl.clearFocus()
            b.etHomeSearch.clearFocus()
            hideKeyboard()
            return
        }
        if (b.webView.visibility == View.VISIBLE && b.webView.canGoBack()) {
            b.webView.goBack()
        } else if (b.webView.visibility == View.VISIBLE) {
            showHomeScreen(keepTopUrl = false)
        }
    }

    fun updateTabCount(count: Int) {
        currentTabCount = count
        _binding?.tvTabCountBadge?.text = count.toString()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etUrl.windowToken, 0)
        imm?.hideSoftInputFromWindow(binding.etHomeSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
