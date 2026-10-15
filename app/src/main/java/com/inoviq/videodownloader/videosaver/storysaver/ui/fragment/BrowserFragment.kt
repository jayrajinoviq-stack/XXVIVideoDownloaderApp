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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.SearchSuggestionsAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.FragmentBrowserBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.PopupBrowserMenuBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.SettingsActivity
import com.inoviq.videodownloader.videosaver.storysaver.ui.dialogs.DownloadBottomSheetFragment
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.FacebookMediaSniffer
import com.inoviq.videodownloader.videosaver.storysaver.utils.SearchEngineManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkManager
import java.net.URLEncoder

class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private val detectedMediaList = mutableListOf<MediaItem>()
    private var currentTabCount: Int = 1
    private lateinit var suggestionsAdapter: SearchSuggestionsAdapter

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

        // By default on first open, show Home Screen (Image 4)
        showHomeScreen()
    }

    private fun setupTopBar() {
        // Tab count badge click -> open / toggle Tab Switcher
        binding.btnTabCount.setOnClickListener {
            onTabSwitcherClicked?.invoke(it)
        }

        // Search & URL entry on keyboard action
        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etUrl.text.toString().trim()
                performSearchOrNavigate(input)
                hideKeyboard()
                true
            } else false
        }

        // Prominent Home Search Bar (Image 4 & Target Mockup)
        binding.etHomeSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etHomeSearch.text.toString().trim()
                if (input.isNotEmpty()) {
                    performSearchOrNavigate(input)
                    hideKeyboard()
                }
                true
            } else false
        }

        binding.btnHomeSearchAction.setOnClickListener {
            val input = binding.etHomeSearch.text.toString().trim()
            if (input.isNotEmpty()) {
                performSearchOrNavigate(input)
                hideKeyboard()
            } else {
                binding.etHomeSearch.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etHomeSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // Home button (Image 1)
        binding.btnHome.setOnClickListener {
            showHomeScreen()
        }

        // Settings Screen (Image 2)
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // More Menu Popup (Images 1 and 2)
        binding.btnMore.setOnClickListener { view ->
            showCustomBrowserMenu(view)
        }

        setupSearchSuggestions()
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
                performSearchOrNavigate(text)
                hideKeyboard()
            },
            onFillQuery = { text ->
                if (binding.etHomeSearch.hasFocus()) {
                    binding.etHomeSearch.setText(text)
                    binding.etHomeSearch.setSelection(text.length)
                } else {
                    binding.etUrl.setText(text)
                    binding.etUrl.setSelection(text.length)
                }
            }
        )

        binding.rvSearchSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchSuggestions.adapter = suggestionsAdapter

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                val isFocused = binding.etUrl.hasFocus() || binding.etHomeSearch.hasFocus()
                if (query.isNotEmpty() && isFocused) {
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

        val focusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !binding.etUrl.hasFocus() && !binding.etHomeSearch.hasFocus()) {
                binding.cardSearchSuggestions.visibility = View.GONE
            }
        }
        binding.etUrl.onFocusChangeListener = focusChangeListener
        binding.etHomeSearch.onFocusChangeListener = focusChangeListener
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

        val isHomeScreen = binding.nsvHomeScreen.visibility == View.VISIBLE ||
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
     * Bind shortcuts on Home Screen (Image 4).
     */
    private fun setupHomeScreenShortcuts() {
        // Facebook Shortcut
        binding.btnShortcutFacebook.setOnClickListener {
            loadUrl("https://m.facebook.com/")
        }

        // Instagram Shortcut
        binding.btnShortcutInstagram.setOnClickListener {
            loadUrl("https://www.instagram.com/")
        }

        // TikTok Shortcut
        binding.btnShortcutTikTok.setOnClickListener {
            loadUrl("https://www.tiktok.com/")
        }

        // WhatsApp Shortcut
        binding.btnShortcutWhatsApp.setOnClickListener {
            loadUrl("https://web.whatsapp.com/")
        }

        // Twitter Shortcut
        binding.btnShortcutTwitter.setOnClickListener {
            loadUrl("https://twitter.com/")
        }

        refreshRecentlyUsedWebsites()

        // Feedback Button
        binding.btnFeedback.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Feedback & Questions")
                .setMessage("Have a feature request, question, or need assistance? Reach out to our support team anytime at support@xxvi.app")
                .setPositiveButton("Send Feedback") { _, _ ->
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:support@xxvi.app?subject=XXVI%20Feedback")
                    }
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Email app not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
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
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
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
                fun onMediaDetected(url: String, type: String, title: String, thumb: String) {
                    activity?.runOnUiThread {
                        addDetectedMedia(title, url, type.equals("video", true), thumb)
                    }
                }
            },
            "XXVIBridge"
        )

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.pbLoading.visibility = View.VISIBLE
                if (!url.isNullOrBlank()) {
                    binding.etUrl.setText(url)
                    val title = derivePlatformTitle(url)
                    onTabUpdated?.invoke(title, url)
                }
                injectDownloaderScript(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.pbLoading.visibility = View.GONE
                // Flush cookies to ensure logins persist across sessions
                CookieManager.getInstance().flush()
                if (!url.isNullOrBlank() && !url.equals("about:blank", true)) {
                    binding.etUrl.setText(url)
                    val title = derivePlatformTitle(url)
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
                if (url.endsWith(".mp4") || url.contains(".mp4?") ||
                    url.contains("video.xx.fbcdn.net") || (url.contains("/v/t") && url.contains(".mp4"))
                ) {
                    activity?.runOnUiThread {
                        addDetectedMedia("facebook_video_${System.currentTimeMillis() % 100000}", url, true)
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
        val isVid = type.contains("video", true) || type.contains("reel", true)

        if (targetUrl.isBlank() || !targetUrl.startsWith("http") || targetUrl.startsWith("blob:")) {
            val candidate = detectedMediaList.firstOrNull { it.isVideo == isVid }?.url
                ?: detectedMediaList.firstOrNull()?.url
            if (!candidate.isNullOrBlank() && candidate.startsWith("http")) {
                targetUrl = candidate
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

        val downloadedItem = DownloadHelper.downloadMedia(
            context = requireContext(),
            url = targetUrl,
            type = if (isVid) "video" else "image",
            customName = title,
            thumbUrl = if (thumb.startsWith("http")) thumb else targetUrl
        ) { updatedItem ->
            activity?.runOnUiThread {
                onMediaDownloaded?.invoke(listOf(updatedItem))
            }
        }

        if (downloadedItem != null) {
            onMediaDownloaded?.invoke(listOf(downloadedItem))
            addDetectedMedia(downloadedItem.title, targetUrl, downloadedItem.isVideo, thumb)

            val rootV = _binding?.root
            if (rootV != null) {
                com.google.android.material.snackbar.Snackbar.make(
                    rootV,
                    "Saved ${downloadedItem.title} to Files!",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).setAction("VIEW") {
                    (activity as? MainActivity)?.showDownloads()
                }.show()
            }
        }
    }

    fun addDetectedMedia(name: String, url: String, isVideo: Boolean, thumb: String? = null) {
        if (url.isBlank()) return
        if (detectedMediaList.none { it.url == url }) {
            detectedMediaList.add(
                0,
                MediaItem(
                    id = System.currentTimeMillis().toString(),
                    title = name,
                    url = url,
                    thumbnailUrl = thumb ?: url,
                    sizeFormatted = if (isVideo) "3.42 MB" else "15.67 kB",
                    isVideo = isVideo
                )
            )
        }
    }

    /**
     * Switch view state to Home Screen (Image 4).
     */
    fun showHomeScreen(keepTopUrl: Boolean = true) {
        val b = _binding ?: return
        b.nsvHomeScreen.visibility = View.VISIBLE
        b.webView.visibility = View.GONE
        b.nsvFacebookFeed.visibility = View.GONE
        b.cardSearchSuggestions.visibility = View.GONE

        b.etHomeSearch.setText("")
        if (!keepTopUrl) {
            b.etUrl.setText("")
        }
        if (b.etUrl.text.isNullOrBlank()) {
            b.etUrl.hint = "Search Or type URL"
        }

        refreshRecentlyUsedWebsites()
        onTabUpdated?.invoke("Homepage", b.etUrl.text.toString())
    }

    /**
     * Dynamically populates the Recently Used Website row with 24-hour visited sites:
     * - Item 1: Most recently used website in last 24h (icon + name)
     * - Item 2: 2nd most recently used website in last 24h (icon + name)
     * - Item 3: "All" button opening all previously used websites in HistoryActivity
     */
    fun refreshRecentlyUsedWebsites() {
        val b = _binding ?: return
        val ctx = context ?: return

        val isEnabled = BrowserHistoryManager.isRecentlyUsedEnabled(ctx)
        b.tvRecentlyUsedTitle.visibility = if (isEnabled) View.VISIBLE else View.GONE
        b.llRecentlyUsedContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE
        if (!isEnabled) return

        val recents = BrowserHistoryManager.getRecentlyUsedWebsites24h(ctx)

        // Item 1: 1st recent website in last 24h
        val item1 = recents.getOrNull(0)
        if (item1 != null) {
            b.btnRecentItem1.visibility = View.VISIBLE
            b.tvRecentTitle1.text = item1.title.ifBlank { "Website" }
            b.ivRecentIcon1.setImageResource(resolvePlatformIcon(item1.url, item1.title))
            b.btnRecentItem1.setOnClickListener {
                loadUrl(item1.url)
            }
        } else {
            b.btnRecentItem1.visibility = View.INVISIBLE
        }

        // Item 2: 2nd recent website in last 24h
        val item2 = recents.getOrNull(1)
        if (item2 != null) {
            b.btnRecentItem2.visibility = View.VISIBLE
            b.tvRecentTitle2.text = item2.title.ifBlank { "Website" }
            b.ivRecentIcon2.setImageResource(resolvePlatformIcon(item2.url, item2.title))
            b.btnRecentItem2.setOnClickListener {
                loadUrl(item2.url)
            }
        } else {
            b.btnRecentItem2.visibility = View.INVISIBLE
        }

        // Item 3: "All" - opens all previously used websites in HistoryActivity
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
            showHomeScreen()
            return
        }
        val b = _binding ?: return

        b.nsvHomeScreen.visibility = View.GONE
        b.webView.visibility = View.VISIBLE
        b.nsvFacebookFeed.visibility = View.GONE
        b.cardSearchSuggestions.visibility = View.GONE

        val finalUrl = when {
            target.startsWith("http://") || target.startsWith("https://") -> target
            target.contains(".") && !target.contains(" ") -> "https://$target"
            else -> SearchEngineManager.buildSearchUrl(requireContext(), target)
        }

        b.etUrl.setText(finalUrl)
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
        val sheet = DownloadBottomSheetFragment.newInstance(detectedMediaList) { selectedItems ->
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
        val b = _binding ?: return false
        return (b.webView.visibility == View.VISIBLE && b.webView.canGoBack()) ||
               (b.webView.visibility == View.VISIBLE && b.nsvHomeScreen.visibility != View.VISIBLE)
    }

    fun goBack() {
        val b = _binding ?: return
        if (b.webView.visibility == View.VISIBLE && b.webView.canGoBack()) {
            b.webView.goBack()
        } else if (b.webView.visibility == View.VISIBLE) {
            showHomeScreen()
        }
    }

    fun updateTabCount(count: Int) {
        currentTabCount = count
        _binding?.tvTabCountBadge?.text = count.toString()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etUrl.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
