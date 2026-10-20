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
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivitySettingsBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogClearHistoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogSearchEngineBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemSearchEngineRowBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.SearchEngineManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
        updateEngineLabel()
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
    }

    private fun setupListeners() {
        binding.btnSettingsBack.setOnClickListener {
            finish()
        }

        // Show actual dedicated download location path
        val downloadDir = DownloadHelper.getDownloadDirectory(this)
        binding.tvDownloadLocationPath.text = downloadDir.absolutePath

        binding.cardDownloadLocation.setOnClickListener {
            openDownloadLocation()
        }

        binding.cardSearchEngine.setOnClickListener {
            showSearchEngineDialog()
        }

        binding.cardClearCache.setOnClickListener {
            showConfirmationDialog(
                title = "Clear Cache",
                message = "Would you like to clear browser cache and cookies?"
            ) {
                try {
                    WebView(this).clearCache(true)
                    CookieManager.getInstance().removeAllCookies(null)
                } catch (_: Exception) {}
                Toast.makeText(this, "Browser cache cleared successfully", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchRecentlyUsed.isChecked = BrowserHistoryManager.isRecentlyUsedEnabled(this)
        binding.switchRecentlyUsed.setOnCheckedChangeListener { _, isChecked ->
            BrowserHistoryManager.setRecentlyUsedEnabled(this, isChecked)
        }

        binding.cardClearHistory.setOnClickListener {
            showConfirmationDialog(
                title = "Clear History",
                message = "Would you like to clear all browsing history?"
            ) {
                BrowserHistoryManager.clearAll(this)
                Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardHowToWork.setOnClickListener {
            startActivity(Intent(this, HowToWorkActivity::class.java))
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
        val dir = DownloadHelper.getDownloadDirectory(this)
        var opened = false

        // 1. Try launching DownloadManager VIEW_DOWNLOADS
        try {
            val dmIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            startActivity(dmIntent)
            opened = true
        } catch (_: Exception) {}

        // 2. Try launching system file manager / viewer pointing to the downloads folder
        if (!opened) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", dir)
                val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(folderIntent)
                opened = true
            } catch (_: Exception) {}
        }

        if (opened) {
            Toast.makeText(this, "Location: ${dir.absolutePath}", Toast.LENGTH_SHORT).show()
        } else {
            showCustomInfoDialog(
                "Download Location",
                "Your downloaded videos and images are saved in:\n\n${dir.absolutePath}\n\nYou can access them directly using any File Manager app or Gallery."
            )
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
