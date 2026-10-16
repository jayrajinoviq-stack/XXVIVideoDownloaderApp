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
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogSearchEngineBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemSearchEngineRowBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.SearchEngineManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

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
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
        updateEngineLabel()
    }

    private fun setupWindowInsets() {
        val density = resources.displayMetrics.density

        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsTopBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                (16 * density).toInt(),
                systemBars.top + (10 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.nsvSettingsContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom + (20 * density).toInt())
            insets
        }
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
            try {
                WebView(this).clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
            } catch (_: Exception) {}
            Toast.makeText(this, "Browser cache cleared successfully", Toast.LENGTH_SHORT).show()
        }

        binding.switchRecentlyUsed.isChecked = BrowserHistoryManager.isRecentlyUsedEnabled(this)
        binding.switchRecentlyUsed.setOnCheckedChangeListener { _, isChecked ->
            BrowserHistoryManager.setRecentlyUsedEnabled(this, isChecked)
        }

        binding.cardClearHistory.setOnClickListener {
            BrowserHistoryManager.clearAll(this)
            Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
        }

        binding.cardHowToWork.setOnClickListener {
            startActivity(Intent(this, HowToWorkActivity::class.java))
        }

        binding.cardFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@inoviq.com"))
                putExtra(Intent.EXTRA_SUBJECT, "XXVI Video Downloader Feedback")
            }
            try {
                startActivity(Intent.createChooser(intent, "Send Feedback"))
            } catch (_: Exception) {
                Toast.makeText(this, "No email client installed", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardPrivacyPolicy.setOnClickListener {
            showCustomInfoDialog(
                "Privacy Policy",
                "XXVI Video Downloader respects your privacy. We do not collect or sell your personal browsing data. All media downloads are saved locally on your device storage."
            )
        }

        binding.cardTerms.setOnClickListener {
            showCustomInfoDialog(
                "Terms & Conditions",
                "By using this application, you agree to respect copyright laws and intellectual property rights of media creators. This tool is designed for personal content archival purposes."
            )
        }
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
