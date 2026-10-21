package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.HistoryAdapter
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityHistoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.PopupHistoryItemMenuBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.BrowserHistoryManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.HistoryEntry

class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyAdapter: HistoryAdapter
    private var historyList = mutableListOf<HistoryEntry>()

    companion object {
        const val EXTRA_NAVIGATE_URL = "EXTRA_NAVIGATE_URL"
        const val EXTRA_NEW_TAB_URL = "EXTRA_NEW_TAB_URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        binding.historyTopBar.setPadding(
            (16 * density).toInt(),
            (10 * density).toInt(),
            (16 * density).toInt(),
            (10 * density).toInt()
        )
        binding.rvHistoryList.setPadding(0, 0, 0, (20 * density).toInt())

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.btnHistoryBack.setOnClickListener {
            handleBackPress()
        }

        binding.btnClearAllHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        setupHistoryList()

        // Bottom Native Small Ad
        AdManager.loadSmallNativeAd(this, binding.flNativeAdSmall)
    }

    private fun setupHistoryList() {
        historyList = BrowserHistoryManager.getHistory(this)

        historyAdapter = HistoryAdapter(
            historyList,
            onItemClick = { item ->
                AdManager.showInterstitial(this) {
                    val intent = Intent().apply {
                        putExtra(EXTRA_NAVIGATE_URL, item.url)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            },
            onMoreClick = { view, item, position ->
                showItemMenu(view, item, position)
            }
        )

        binding.rvHistoryList.layoutManager = LinearLayoutManager(this)
        binding.rvHistoryList.adapter = historyAdapter

        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (historyList.isEmpty()) {
            binding.tvHistoryEmpty.visibility = View.VISIBLE
            binding.rvHistoryList.visibility = View.GONE
            binding.btnClearAllHistory.visibility = View.GONE
        } else {
            binding.tvHistoryEmpty.visibility = View.GONE
            binding.rvHistoryList.visibility = View.VISIBLE
            binding.btnClearAllHistory.visibility = View.VISIBLE
        }
    }

    /**
     * Item 3-dots popup menu (Screenshot 1).
     */
    private fun showItemMenu(anchorView: View, item: HistoryEntry, position: Int) {
        val popupBinding = PopupHistoryItemMenuBinding.inflate(layoutInflater)
        val density = resources.displayMetrics.density
        val popupWidth = (220 * density).toInt()

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

        // 1. Open In New Tab
        popupBinding.itemMenuOpenInNewTab.setOnClickListener {
            popupWindow.dismiss()
            AdManager.showInterstitial(this) {
                val intent = Intent().apply {
                    putExtra(EXTRA_NEW_TAB_URL, item.url)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }

        // 2. Share
        popupBinding.itemMenuShare.setOnClickListener {
            popupWindow.dismiss()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                putExtra(Intent.EXTRA_TEXT, item.url)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Link"))
        }

        // 3. Copy Link
        popupBinding.itemMenuCopyLink.setOnClickListener {
            popupWindow.dismiss()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("URL", item.url)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // 4. Delete
        popupBinding.itemMenuDelete.setOnClickListener {
            popupWindow.dismiss()
            BrowserHistoryManager.removeHistory(this, item)
            historyAdapter.removeItemAt(position)
            updateEmptyState()
        }

        val anchorWidth = if (anchorView.width > 0) anchorView.width else (32 * density).toInt()
        val xOffset = -(popupWidth - anchorWidth + (12 * density).toInt())
        val yOffset = (-6 * density).toInt()
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Confirmation dialog matching Screenshot 4.
     */
    private fun showClearHistoryDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_clear_history, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btnNo = view.findViewById<TextView>(R.id.btnCancelClearHistory)
        val btnYes = view.findViewById<TextView>(R.id.btnConfirmClearHistory)

        btnNo.setOnClickListener {
            dialog.dismiss()
        }

        btnYes.setOnClickListener {
            AdManager.showRewardedAd(this) {
                BrowserHistoryManager.clearAll(this)
                historyList.clear()
                historyAdapter.notifyDataSetChanged()
                updateEmptyState()
                dialog.dismiss()
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
