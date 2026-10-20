package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.WhatsAppStatusAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityWhatsappStatusBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogAppNotInstalledBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogStatusPreviewBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogWrongFolderBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import com.inoviq.videodownloader.videosaver.storysaver.model.WhatsAppStatusItem
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.FilesFragment
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadStore
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class WhatsAppStatusActivity : AppCompatActivity() {

    companion object {
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

        private const val PREFS_NAME = "xxvi_wa_status_prefs"
        private const val KEY_WA_URI = "saved_wa_tree_uri"
        private const val KEY_WB_URI = "saved_wb_tree_uri"

        private const val FILTER_ALL = "ALL"
        private const val FILTER_PHOTOS = "PHOTOS"
        private const val FILTER_VIDEOS = "VIDEOS"
    }

    private lateinit var binding: ActivityWhatsappStatusBinding
    private lateinit var statusAdapter: WhatsAppStatusAdapter

    private var isBusinessMode = false
    private var currentFilter = FILTER_ALL

    private val allStatuses = mutableListOf<WhatsAppStatusItem>()
    private val displayedStatuses = mutableListOf<WhatsAppStatusItem>()

    // Launcher for Storage Access Framework directory picker
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            handleFolderPermissionResult(uri)
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityWhatsappStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        setupToolbar()
        setupAppSwitchers()
        setupFilterTabs()
        setupRecyclerView()
        setupPermissionAndEmptyButtons()

        // Check availability: if WhatsApp is not installed but Business is, start in Business mode
        val hasWA = isAppInstalled(this, PACKAGE_WHATSAPP)
        val hasWB = isAppInstalled(this, PACKAGE_WHATSAPP_BUSINESS)
        if (!hasWA && hasWB) {
            selectAppMode(business = true)
        } else {
            selectAppMode(business = false)
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Open Downloads Tab directly
        binding.btnOpenDownloads.setOnClickListener {
            openDownloadsTab()
        }
    }

    private fun openDownloadsTab() {
        val filesDialog = FilesFragment()
        filesDialog.show(supportFragmentManager, "FILES_DIALOG")
    }

    private fun setupAppSwitchers() {
        // Button 1: WhatsApp
        binding.btnTabWhatsApp.setOnClickListener {
            if (isBusinessMode) {
                selectAppMode(business = false)
            }
        }

        // Button 2: WhatsApp Business
        binding.btnTabWhatsAppBusiness.setOnClickListener {
            if (!isBusinessMode) {
                selectAppMode(business = true)
            }
        }
    }

    private fun selectAppMode(business: Boolean) {
        val targetPackage = if (business) PACKAGE_WHATSAPP_BUSINESS else PACKAGE_WHATSAPP

        // First requirement: check if app is installed
        if (!isAppInstalled(this, targetPackage)) {
            showAppNotInstalledDialog(business)
            return
        }

        isBusinessMode = business

        // Update UI Tab indicators
        if (business) {
            binding.btnTabWhatsApp.setBackgroundResource(R.drawable.bg_wa_tab_inactive)
            binding.tvTabWhatsAppTitle.setTextColor(ContextCompat.getColor(this, R.color.text_dark_brown))

            binding.btnTabWhatsAppBusiness.setBackgroundResource(R.drawable.bg_wa_tab_active)
            binding.tvTabWhatsAppBusinessTitle.setTextColor(Color.WHITE)

            binding.tvToolbarTitle.text = "WA Business Status"
        } else {
            binding.btnTabWhatsApp.setBackgroundResource(R.drawable.bg_wa_tab_active)
            binding.tvTabWhatsAppTitle.setTextColor(Color.WHITE)

            binding.btnTabWhatsAppBusiness.setBackgroundResource(R.drawable.bg_wa_tab_inactive)
            binding.tvTabWhatsAppBusinessTitle.setTextColor(ContextCompat.getColor(this, R.color.text_dark_brown))

            binding.tvToolbarTitle.text = "WhatsApp Status"
        }

        checkPermissionAndLoadStatuses()
    }

    private fun setupFilterTabs() {
        binding.chipFilterAll.setOnClickListener {
            setFilter(FILTER_ALL)
        }
        binding.chipFilterPhotos.setOnClickListener {
            setFilter(FILTER_PHOTOS)
        }
        binding.chipFilterVideos.setOnClickListener {
            setFilter(FILTER_VIDEOS)
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter

        binding.chipFilterAll.setBackgroundResource(if (filter == FILTER_ALL) R.drawable.bg_tab_item_active else R.drawable.bg_wa_tab_inactive)
        binding.chipFilterAll.setTextColor(if (filter == FILTER_ALL) ContextCompat.getColor(this, R.color.primary_orange) else ContextCompat.getColor(this, R.color.text_gray))

        binding.chipFilterPhotos.setBackgroundResource(if (filter == FILTER_PHOTOS) R.drawable.bg_tab_item_active else R.drawable.bg_wa_tab_inactive)
        binding.chipFilterPhotos.setTextColor(if (filter == FILTER_PHOTOS) ContextCompat.getColor(this, R.color.primary_orange) else ContextCompat.getColor(this, R.color.text_gray))

        binding.chipFilterVideos.setBackgroundResource(if (filter == FILTER_VIDEOS) R.drawable.bg_tab_item_active else R.drawable.bg_wa_tab_inactive)
        binding.chipFilterVideos.setTextColor(if (filter == FILTER_VIDEOS) ContextCompat.getColor(this, R.color.primary_orange) else ContextCompat.getColor(this, R.color.text_gray))

        applyCurrentFilter()
    }

    private fun setupRecyclerView() {
        statusAdapter = WhatsAppStatusAdapter(
            items = displayedStatuses,
            onItemClick = { item ->
                showStatusPreviewDialog(item)
            },
            onDownloadClick = { item, position ->
                downloadStatus(item) {
                    statusAdapter.markItemSaved(position)
                }
            }
        )

        binding.rvStatusesGrid.layoutManager = GridLayoutManager(this, 3)
        binding.rvStatusesGrid.adapter = statusAdapter
    }

    private fun setupPermissionAndEmptyButtons() {
        // "Grant Folder Access" button in permission card
        binding.btnGrantPermission.setOnClickListener {
            requestFolderPermission()
        }

        // "Open WhatsApp" button in empty state card
        binding.btnOpenWhatsAppApp.setOnClickListener {
            openTargetWhatsAppApp()
        }
    }

    private fun openTargetWhatsAppApp() {
        val targetPkg = if (isBusinessMode) PACKAGE_WHATSAPP_BUSINESS else PACKAGE_WHATSAPP
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Could not open app", Toast.LENGTH_SHORT).show()
            }
        } else {
            showAppNotInstalledDialog(isBusinessMode)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Permission & Location Validation
    // ---------------------------------------------------------------------------------------------

    private fun checkPermissionAndLoadStatuses() {
        // 1. Check legacy storage for Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val legacyDir = getLegacyStatusDir(isBusinessMode)
            if (legacyDir != null && legacyDir.exists() && legacyDir.canRead()) {
                showContentState()
                loadStatusesFromLegacyDir(legacyDir)
                return
            }
        }

        // 2. Check persisted SAF tree permission for Android 11+
        val savedUri = getSavedTreeUri(isBusinessMode)
        if (savedUri != null && isPermissionPersisted(savedUri) && isLocationAllowed(savedUri, isBusinessMode)) {
            showContentState()
            loadStatusesFromTreeUri(savedUri)
        } else {
            // Permission needed
            showPermissionState()
        }
    }

    /**
     * Strictly verifies that the selected location is the official WhatsApp status folder.
     * Prevents other/random directories from being saved.
     */
    private fun isLocationAllowed(treeUri: Uri, isBusiness: Boolean): Boolean {
        val decoded = Uri.decode(treeUri.toString()).lowercase()
        return if (isBusiness) {
            decoded.contains("com.whatsapp.w4b") || decoded.contains("whatsapp business") || decoded.contains("w4b")
        } else {
            (decoded.contains("com.whatsapp") || decoded.contains("whatsapp")) && !decoded.contains("com.whatsapp.w4b")
        }
    }

    /**
     * Builds the exact initial URI pointing to WhatsApp status directory for SAF picker.
     */
    private fun getInitialFolderUri(isBusiness: Boolean): Uri {
        val relativePath = if (isBusiness) {
            "Android%2Fmedia%2Fcom.whatsapp.w4b%2FWhatsApp%20Business%2FMedia%2F.Statuses"
        } else {
            "Android%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses"
        }
        return Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$relativePath")
    }

    private fun requestFolderPermission() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val initialUri = getInitialFolderUri(isBusinessMode)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                }
                putExtra("android.content.extra.SHOW_ADVANCED", true)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            }
            folderPickerLauncher.launch(intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI))
        } catch (_: Exception) {
            // Fallback launch
            folderPickerLauncher.launch(null)
        }
    }

    private fun handleFolderPermissionResult(treeUri: Uri) {
        // Strict Validation: "give othar location permistion that not requred set again to click and note that open again status location not last given so given on status whatsapp statusbussiness whatsapp only allow"
        if (!isLocationAllowed(treeUri, isBusinessMode)) {
            showWrongFolderDialog()
            return
        }

        try {
            // Take persistable permission
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            saveTreeUri(isBusinessMode, treeUri)
            showContentState()
            loadStatusesFromTreeUri(treeUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to persist permission: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPermissionPersisted(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { it.uri == uri }
    }

    private fun getSavedTreeUri(isBusiness: Boolean): Uri? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) KEY_WB_URI else KEY_WA_URI
        val str = prefs.getString(key, null) ?: return null
        return try {
            Uri.parse(str)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveTreeUri(isBusiness: Boolean, uri: Uri) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) KEY_WB_URI else KEY_WA_URI
        prefs.edit().putString(key, uri.toString()).apply()
    }

    private fun clearSavedTreeUri(isBusiness: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) KEY_WB_URI else KEY_WA_URI
        prefs.edit().remove(key).apply()
    }

    private fun getLegacyStatusDir(isBusiness: Boolean): File? {
        val baseDir = Environment.getExternalStorageDirectory()
        val paths = if (isBusiness) {
            listOf(
                File(baseDir, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
                File(baseDir, "WhatsApp Business/Media/.Statuses")
            )
        } else {
            listOf(
                File(baseDir, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
                File(baseDir, "WhatsApp/Media/.Statuses")
            )
        }
        return paths.firstOrNull { it.exists() && it.canRead() }
    }

    // ---------------------------------------------------------------------------------------------
    // Status Loading
    // ---------------------------------------------------------------------------------------------

    private fun loadStatusesFromTreeUri(treeUri: Uri) {
        showLoadingState()

        thread(start = true, name = "LoadStatusesFromSAF") {
            val rootDoc = DocumentFile.fromTreeUri(this, treeUri)
            val items = mutableListOf<WhatsAppStatusItem>()

            if (rootDoc != null && rootDoc.exists()) {
                val statusDirDoc = resolveStatusDir(rootDoc)
                val files = statusDirDoc.listFiles()
                val downloadDir = DownloadHelper.getDownloadDirectory(this)

                for (doc in files) {
                    val name = doc.name ?: continue
                    val lower = name.lowercase()
                    if (lower.startsWith(".nomedia")) continue

                    val isJpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                    val isMp4 = lower.endsWith(".mp4")

                    if (isJpg || isMp4) {
                        val alreadySaved = File(downloadDir, name).exists()
                        items.add(
                            WhatsAppStatusItem(
                                uri = doc.uri,
                                name = name,
                                isVideo = isMp4,
                                sizeBytes = doc.length(),
                                lastModified = doc.lastModified(),
                                documentFile = doc,
                                isSaved = alreadySaved
                            )
                        )
                    }
                }
            }

            // Sort by most recent first
            items.sortByDescending { it.lastModified }

            runOnUiThread {
                allStatuses.clear()
                allStatuses.addAll(items)
                hideLoadingState()

                if (allStatuses.isEmpty()) {
                    showEmptyState()
                } else {
                    showContentState()
                    applyCurrentFilter()
                }
            }
        }
    }

    private fun resolveStatusDir(doc: DocumentFile): DocumentFile {
        val name = doc.name ?: ""
        if (name.equals(".Statuses", ignoreCase = true)) {
            return doc
        }

        // If user picked Media or parent, search for .Statuses
        val statuses = doc.findFile(".Statuses")
        if (statuses != null && statuses.isDirectory) {
            return statuses
        }

        val media = doc.findFile("Media")
        if (media != null && media.isDirectory) {
            val mediaStatuses = media.findFile(".Statuses")
            if (mediaStatuses != null && mediaStatuses.isDirectory) {
                return mediaStatuses
            }
        }

        return doc
    }

    private fun loadStatusesFromLegacyDir(dir: File) {
        showLoadingState()

        thread(start = true, name = "LoadStatusesFromLegacy") {
            val items = mutableListOf<WhatsAppStatusItem>()
            val files = dir.listFiles()
            val downloadDir = DownloadHelper.getDownloadDirectory(this)

            if (files != null) {
                for (f in files) {
                    val name = f.name
                    val lower = name.lowercase()
                    if (lower.startsWith(".nomedia")) continue

                    val isJpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                    val isMp4 = lower.endsWith(".mp4")

                    if (isJpg || isMp4) {
                        val alreadySaved = File(downloadDir, name).exists()
                        items.add(
                            WhatsAppStatusItem(
                                uri = Uri.fromFile(f),
                                name = name,
                                isVideo = isMp4,
                                sizeBytes = f.length(),
                                lastModified = f.lastModified(),
                                file = f,
                                isSaved = alreadySaved
                            )
                        )
                    }
                }
            }

            items.sortByDescending { it.lastModified }

            runOnUiThread {
                allStatuses.clear()
                allStatuses.addAll(items)
                hideLoadingState()

                if (allStatuses.isEmpty()) {
                    showEmptyState()
                } else {
                    showContentState()
                    applyCurrentFilter()
                }
            }
        }
    }

    private fun applyCurrentFilter() {
        displayedStatuses.clear()
        when (currentFilter) {
            FILTER_PHOTOS -> displayedStatuses.addAll(allStatuses.filter { !it.isVideo })
            FILTER_VIDEOS -> displayedStatuses.addAll(allStatuses.filter { it.isVideo })
            else -> displayedStatuses.addAll(allStatuses)
        }

        statusAdapter.notifyDataSetChanged()
        binding.tvStatusCount.text = "${displayedStatuses.size} items"
    }

    // ---------------------------------------------------------------------------------------------
    // UI State Management
    // ---------------------------------------------------------------------------------------------

    private fun showLoadingState() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.rvStatusesGrid.visibility = View.GONE
        binding.llPermissionRequired.visibility = View.GONE
        binding.llEmptyState.visibility = View.GONE
    }

    private fun hideLoadingState() {
        binding.pbLoading.visibility = View.GONE
    }

    private fun showContentState() {
        binding.pbLoading.visibility = View.GONE
        binding.rvStatusesGrid.visibility = View.VISIBLE
        binding.llPermissionRequired.visibility = View.GONE
        binding.llEmptyState.visibility = View.GONE
        binding.llFilterContainer.visibility = View.VISIBLE
    }

    private fun showPermissionState() {
        binding.pbLoading.visibility = View.GONE
        binding.rvStatusesGrid.visibility = View.GONE
        binding.llPermissionRequired.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE
        binding.llFilterContainer.visibility = View.GONE

        val appName = if (isBusinessMode) "WhatsApp Business" else "WhatsApp"
        binding.tvPermissionDesc.text = "To display viewed $appName statuses, please grant access to the $appName Statuses folder.\nTap below and click 'USE THIS FOLDER'."
    }

    private fun showEmptyState() {
        binding.pbLoading.visibility = View.GONE
        binding.rvStatusesGrid.visibility = View.GONE
        binding.llPermissionRequired.visibility = View.GONE
        binding.llEmptyState.visibility = View.VISIBLE
        binding.llFilterContainer.visibility = View.VISIBLE
        binding.tvStatusCount.text = "0 items"

        val appName = if (isBusinessMode) "WhatsApp Business" else "WhatsApp"
        binding.tvEmptySubtitle.text = "No viewed $appName statuses in the last 24 hours.\nOpen $appName, view your contacts' statuses, then return here to save them!"
        binding.btnOpenWhatsAppApp.text = "Open $appName"
    }

    // ---------------------------------------------------------------------------------------------
    // Dialogs
    // ---------------------------------------------------------------------------------------------

    private fun showAppNotInstalledDialog(isBusiness: Boolean) {
        val dialog = Dialog(this)
        val dBinding = DialogAppNotInstalledBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val appName = if (isBusiness) "WhatsApp Business" else "WhatsApp"
        val pkg = if (isBusiness) PACKAGE_WHATSAPP_BUSINESS else PACKAGE_WHATSAPP

        dBinding.tvAppNotInstalledTitle.text = "$appName Not Installed"
        dBinding.tvAppNotInstalledMsg.text = "You don't have $appName installed on your device. Please install it from Google Play Store to view and download statuses."

        val handleCancel = {
            dialog.dismiss()
            if (!isBusiness) {
                // If regular WhatsApp not installed, switch to WhatsApp Business if available
                if (isAppInstalled(this, PACKAGE_WHATSAPP_BUSINESS)) {
                    selectAppMode(business = true)
                } else {
                    // Both not installed -> back to Home
                    finish()
                }
            } else {
                // If WhatsApp Business not installed, switch to regular WhatsApp if available
                if (isAppInstalled(this, PACKAGE_WHATSAPP)) {
                    selectAppMode(business = false)
                } else {
                    // Both not installed -> back to Home
                    finish()
                }
            }
        }

        dBinding.btnCancelNotInstalled.setOnClickListener {
            handleCancel()
        }

        dialog.setOnCancelListener {
            handleCancel()
        }

        dBinding.btnInstallApp.setOnClickListener {
            dialog.dismiss()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (_: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            }
        }

        dialog.show()
    }

    private fun showWrongFolderDialog() {
        val dialog = Dialog(this)
        val dBinding = DialogWrongFolderBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val appName = if (isBusinessMode) "WhatsApp Business" else "WhatsApp"
        val folderPath = if (isBusinessMode) {
            "Android > media > com.whatsapp.w4b > WhatsApp Business > Media > .Statuses"
        } else {
            "Android > media > com.whatsapp > WhatsApp > Media > .Statuses"
        }

        dBinding.tvWrongFolderMsg.text = "You selected a different folder. Only the $appName Statuses folder is allowed.\n\nPlease select:\n$folderPath"

        dBinding.btnCancelWrongFolder.setOnClickListener {
            dialog.dismiss()
        }

        dBinding.btnTryAgainWrongFolder.setOnClickListener {
            dialog.dismiss()
            requestFolderPermission()
        }

        dialog.show()
    }

    // ---------------------------------------------------------------------------------------------
    // Full-Screen Status Preview
    // ---------------------------------------------------------------------------------------------

    private fun showStatusPreviewDialog(item: WhatsAppStatusItem) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dBinding = DialogStatusPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        ViewCompat.setOnApplyWindowInsetsListener(dBinding.root) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(dBinding.root)

        dBinding.tvPreviewTitle.text = if (item.isVideo) "Video Status" else "Photo Status"

        if (item.isVideo) {
            dBinding.ivPreviewImage.visibility = View.GONE
            dBinding.vvPreviewVideo.visibility = View.VISIBLE
            dBinding.ivVideoPlayPauseOverlay.visibility = View.GONE

            dBinding.vvPreviewVideo.setVideoURI(item.uri)
            dBinding.vvPreviewVideo.setOnPreparedListener { mp ->
                mp.isLooping = true
                dBinding.vvPreviewVideo.start()
            }

            dBinding.flPreviewContainer.setOnClickListener {
                if (dBinding.vvPreviewVideo.isPlaying) {
                    dBinding.vvPreviewVideo.pause()
                    dBinding.ivVideoPlayPauseOverlay.visibility = View.VISIBLE
                } else {
                    dBinding.vvPreviewVideo.start()
                    dBinding.ivVideoPlayPauseOverlay.visibility = View.GONE
                }
            }
        } else {
            dBinding.vvPreviewVideo.visibility = View.GONE
            dBinding.ivPreviewImage.visibility = View.VISIBLE
            dBinding.ivVideoPlayPauseOverlay.visibility = View.GONE

            Glide.with(this)
                .load(item.uri)
                .fitCenter()
                .into(dBinding.ivPreviewImage)
        }

        dBinding.btnPreviewClose.setOnClickListener {
            dialog.dismiss()
        }

        dBinding.btnPreviewShareTop.setOnClickListener {
            shareStatusMedia(item)
        }

        dBinding.btnPreviewShare.setOnClickListener {
            shareStatusMedia(item)
        }

        dBinding.btnPreviewRepost.setOnClickListener {
            repostStatusMedia(item)
        }

        dBinding.btnPreviewDownload.setOnClickListener {
            downloadStatus(item) {
                dBinding.tvPreviewDownloadBtnText.text = "Saved ✓"
                val idx = displayedStatuses.indexOfFirst { it.name == item.name }
                if (idx != -1) {
                    statusAdapter.markItemSaved(idx)
                }
            }
        }

        dialog.show()
    }

    // ---------------------------------------------------------------------------------------------
    // Download Status -> Download tab integration
    // ---------------------------------------------------------------------------------------------

    private fun downloadStatus(item: WhatsAppStatusItem, onSuccess: (() -> Unit)? = null) {
        val targetDir = DownloadHelper.getDownloadDirectory(this)
        val ext = if (item.isVideo) ".mp4" else ".jpg"
        val prefix = if (isBusinessMode) "whatsapp_business_status" else "whatsapp_status"
        val destFile = File(targetDir, "${prefix}_${System.currentTimeMillis() % 10000000}$ext")

        thread(start = true, name = "DownloadWhatsAppStatus") {
            try {
                contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Index in device media scanner
                MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), null, null)

                // Save to app DownloadStore so it is displayed in the Downloads tab!
                val mediaItem = MediaItem(
                    id = System.currentTimeMillis().toString(),
                    title = destFile.name,
                    url = Uri.fromFile(destFile).toString(),
                    thumbnailUrl = destFile.absolutePath,
                    sizeBytes = destFile.length(),
                    sizeFormatted = Formatter.formatFileSize(this, destFile.length()),
                    isVideo = item.isVideo,
                    isDownloaded = true,
                    downloadProgress = 100,
                    localPath = destFile.absolutePath
                )
                DownloadStore.saveDownloadedItem(this, mediaItem)

                runOnUiThread {
                    item.isSaved = true
                    onSuccess?.invoke()

                    // Show success snackbar with action to view in download tab
                    val rootView: View = binding.llWhatsAppStatusRoot
                    Snackbar.make(
                        rootView,
                        "Status saved! Available in Downloads tab.",
                        Snackbar.LENGTH_LONG
                    ).setAction("VIEW") {
                        openDownloadsTab()
                    }.setActionTextColor(ContextCompat.getColor(this, R.color.primary_orange))
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to save status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareStatusMedia(item: WhatsAppStatusItem) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.isVideo) "video/mp4" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Status"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share media", Toast.LENGTH_SHORT).show()
        }
    }

    private fun repostStatusMedia(item: WhatsAppStatusItem) {
        val targetPkg = if (isBusinessMode) PACKAGE_WHATSAPP_BUSINESS else PACKAGE_WHATSAPP
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.isVideo) "video/mp4" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                `package` = targetPkg
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open WhatsApp to repost", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }
}
