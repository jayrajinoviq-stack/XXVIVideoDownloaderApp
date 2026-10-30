package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.app.Dialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.BuildConfig
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivitySettingsBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogClearHistoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogSearchEngineBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemSearchEngineRowBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.SearchEngineManager

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
        updateEngineLabel()

        // Bottom Native Small Ad
        AdManager.loadSmallNativeAd(this, binding.flNativeAdSmall)
    }

    override fun onResume() {
        super.onResume()
        binding.switchWifiOnly.isChecked = DownloadHelper.isDownloadWifiOnly(this)
    }

    private fun setupWindowInsets() {
        val density = resources.displayMetrics.density
        binding.settingsTopBar.setPadding(
            (16 * density).toInt(),
            (12 * density).toInt(),
            (16 * density).toInt(),
            (12 * density).toInt()
        )
        binding.nsvSettingsContent.setPadding(0, 0, 0, (20 * density).toInt())

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.VersionCode.text = BuildConfig.VERSION_NAME

    }

    private fun setupListeners() {
        binding.btnSettingsBack.setOnClickListener {
            handleBackPress()
        }

        // Show actual dedicated download location path without creating empty folder prematurely
        val downloadDir = DownloadHelper.getDownloadDirectory(this, createIfMissing = false)
        binding.tvDownloadLocationPath.text = downloadDir.absolutePath

        binding.cardDownloadLocation.setOnClickListener {
            openDownloadLocation()
        }

        // Download With Wi-Fi Only Switch
        binding.switchWifiOnly.isChecked = DownloadHelper.isDownloadWifiOnly(this)
        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            DownloadHelper.setDownloadWifiOnly(this, isChecked)
        }

        binding.cardSearchEngine.setOnClickListener {
            showSearchEngineDialog()
        }

        binding.cardClearCache.setOnClickListener {
            showConfirmationDialog(
                title = "Clear Cache",
                message = "Would you like to clear browser cache and cookies?"
            ) {
                AdsManager.onRewardedUserAction(
                    activity = this,
                    onRewardEarned = {},
                    onAdClosed = {
                        try {
                            WebView(this).clearCache(true)
                            CookieManager.getInstance().removeAllCookies(null)
                        } catch (_: Exception) {}
                        Toast.makeText(this, "Browser cache cleared successfully", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        binding.cardClearHistory.setOnClickListener {
            showConfirmationDialog(
                title = "Clear History",
                message = "Would you like to clear all browsing history?"
            ) {
                AdsManager.onRewardedUserAction(
                    activity = this,
                    onRewardEarned = {},
                    onAdClosed = {
                        BrowserHistoryManager.clearAll(this)
                        Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        binding.cardHowToWork.setOnClickListener {
            AdManager.showInterstitial(this) {
                startActivity(Intent(this, HowToWorkActivity::class.java))
            }
        }

        binding.cardPrivacyPolicy.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/xxvi-video-downloader/privacy-policy"))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardTerms.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/xxvi-video-downloader/terms-and-conditions"))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        positiveText: String = "YES",
        negativeText: String = "NO",
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogClearHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.tvClearHistoryTitle.text = title
        dialogBinding.tvClearHistoryMsg.text = message
        dialogBinding.btnCancelClearHistory.text = negativeText
        dialogBinding.btnConfirmClearHistory.text = positiveText

        dialogBinding.btnCancelClearHistory.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmClearHistory.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }

    private fun showCustomInfoDialog(title: String, message: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val alertBinding = com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogCustomAlertBinding.inflate(layoutInflater)
        dialog.setContentView(alertBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        alertBinding.tvAlertTitle.text = title
        alertBinding.tvAlertMsg.text = message
        alertBinding.btnAlertPositive.text = "CLOSE"
        alertBinding.btnAlertNegative.visibility = android.view.View.GONE
        alertBinding.btnAlertPositive.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openDownloadLocation() {
        val wasAlreadyCreated = DownloadHelper.isDownloadDirectoryCreated(this)
        val dir = DownloadHelper.getDownloadDirectory(this, createIfMissing = true)
        binding.tvDownloadLocationPath.text = dir.absolutePath
        var opened = false

        // 1. Try launching system file manager / viewer pointing to the downloads folder
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", dir)
            val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(folderIntent)
            opened = true
        } catch (_: Exception) {}

        // 2. Try launching DownloadManager VIEW_DOWNLOADS
        if (!opened) {
            try {
                val dmIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                startActivity(dmIntent)
                opened = true
            } catch (_: Exception) {}
        }

        val statusMsg = if (wasAlreadyCreated) {
            "Download folder is ready:\n\n${dir.absolutePath}\n\nAll downloaded videos, audio, and images are saved here."
        } else {
            "Download folder created successfully:\n\n${dir.absolutePath}\n\nAll future downloads will be saved here."
        }

        if (opened) {
            Toast.makeText(this, if (wasAlreadyCreated) "Opening: ${dir.name}" else "Created & opened: ${dir.name}", Toast.LENGTH_SHORT).show()
        } else {
            showCustomInfoDialog("Download Location", statusMsg)
        }
    }

    private fun updateEngineLabel() {
        val currentEngine = SearchEngineManager.getSearchEngine(this)
        binding.tvSearchEngineLabel.text = "Search Engine ( $currentEngine )"
    }

    private fun showSearchEngineDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogSearchEngineBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        var selectedEngine = SearchEngineManager.getSearchEngine(this)

        val rowBindings = mutableListOf<ItemSearchEngineRowBinding>()

        SearchEngineManager.ALL_ENGINES.forEach { engineName ->
            val rowBinding = ItemSearchEngineRowBinding.inflate(layoutInflater, dialogBinding.llEnginesContainer, false)
            rowBinding.tvEngineName.text = engineName
            rowBinding.switchEngine.isChecked = (engineName == selectedEngine)

            rowBinding.llRowClickArea.setOnClickListener {
                selectedEngine = engineName
                rowBindings.forEach { rb ->
                    rb.switchEngine.isChecked = (rb.tvEngineName.text == selectedEngine)
                }
                SearchEngineManager.setSearchEngine(this, selectedEngine)
                updateEngineLabel()
                dialogBinding.root.postDelayed({
                    dialog.dismiss()
                }, 180L)
            }

            rowBindings.add(rowBinding)
            dialogBinding.llEnginesContainer.addView(rowBinding.root)
        }

        dialog.show()
    }
}
