package com.inoviq.videodownloader.videosaver.storysaver.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.DownloadMediaAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogDownloadBottomSheetBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem

class DownloadBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDownloadBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var mediaItems: MutableList<MediaItem> = mutableListOf()
    private var onDownloadClickListener: ((List<MediaItem>) -> Unit)? = null
    private var selectedQuality: String = "720P" // "720P", "480P", "Audio"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDownloadBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        val isSingleMode = mediaItems.size <= 1
        if (isSingleMode) {
            setupSingleVideoMode()
        } else {
            setupMultiMediaMode()
        }
    }

    private fun setupSingleVideoMode() {
        binding.llSingleVideoMode.visibility = View.VISIBLE
        binding.rvMedia.visibility = View.GONE
        binding.btnDownload.text = "Download"

        val item = mediaItems.firstOrNull() ?: return

        // Bind Title & Thumbnail
        binding.tvSingleTitle.text = item.title
        val thumb = if (!item.thumbnailUrl.isNullOrBlank()) item.thumbnailUrl else item.url
        binding.ivSingleThumbnail.load(thumb) {
            crossfade(true)
            placeholder(R.drawable.app_logo)
            error(R.drawable.app_logo)
            transformations(RoundedCornersTransformation(14f))
        }

        // Rename Button
        binding.btnSingleRename.setOnClickListener {
            showSingleRenameDialog(requireContext(), item)
        }

        // Quality Selection Listeners
        updateQualitySelection("720P")

        binding.cardQuality720p.setOnClickListener {
            updateQualitySelection("720P")
        }

        binding.cardQuality480p.setOnClickListener {
            updateQualitySelection("480P")
        }

        binding.cardQualityAudio.setOnClickListener {
            updateQualitySelection("Audio")
        }

        binding.btnDownload.setOnClickListener {
            val finalItem = item.copy(
                sizeFormatted = when (selectedQuality) {
                    "720P" -> "5.19MB"
                    "480P" -> "0.94MB"
                    else -> "165kB"
                }
            )

            Toast.makeText(
                requireContext(),
                "Downloading ${finalItem.title} ($selectedQuality)...",
                Toast.LENGTH_SHORT
            ).show()

            onDownloadClickListener?.invoke(listOf(finalItem))
            dismiss()
        }
    }

    private fun updateQualitySelection(quality: String) {
        selectedQuality = quality

        val activeTextColor = Color.BLACK
        val inactiveTextColor = Color.parseColor("#6B7280")

        // 720P
        val is720p = quality == "720P"
        binding.llQuality720pBox.setBackgroundResource(
            if (is720p) R.drawable.bg_quality_card_selected else R.drawable.bg_quality_card_unselected
        )
        binding.tvQuality720pTitle.setTextColor(if (is720p) activeTextColor else inactiveTextColor)
        binding.ivBadge720p.visibility = if (is720p) View.VISIBLE else View.GONE

        // 480P
        val is480p = quality == "480P"
        binding.llQuality480pBox.setBackgroundResource(
            if (is480p) R.drawable.bg_quality_card_selected else R.drawable.bg_quality_card_unselected
        )
        binding.tvQuality480pTitle.setTextColor(if (is480p) activeTextColor else inactiveTextColor)
        binding.ivBadge480p.visibility = if (is480p) View.VISIBLE else View.GONE

        // Audio
        val isAudio = quality == "Audio"
        binding.llQualityAudioBox.setBackgroundResource(
            if (isAudio) R.drawable.bg_quality_card_selected else R.drawable.bg_quality_card_unselected
        )
        binding.tvQualityAudioTitle.setTextColor(if (isAudio) activeTextColor else inactiveTextColor)
        binding.ivBadgeAudio.visibility = if (isAudio) View.VISIBLE else View.GONE
    }

    private fun showSingleRenameDialog(context: Context, item: MediaItem) {
        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val renameBinding = com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogRenameMediaBinding.inflate(layoutInflater)
        dialog.setContentView(renameBinding.root)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        renameBinding.etRenameInput.setText(item.title)
        renameBinding.etRenameInput.setSelection(renameBinding.etRenameInput.text.length)

        renameBinding.btnCancelRename.setOnClickListener {
            dialog.dismiss()
        }

        renameBinding.btnConfirmRename.setOnClickListener {
            val newName = renameBinding.etRenameInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                item.title = newName
                binding.tvSingleTitle.text = newName
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupMultiMediaMode() {
        binding.llSingleVideoMode.visibility = View.GONE
        binding.rvMedia.visibility = View.VISIBLE

        val adapter = DownloadMediaAdapter(mediaItems) {
            updateDownloadButtonState()
        }

        binding.rvMedia.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMedia.adapter = adapter

        binding.btnDownload.setOnClickListener {
            val checked = adapter.getCheckedItems()
            if (checked.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one file to download", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(
                requireContext(),
                "Downloading ${checked.size} ${if (checked.size == 1) "item" else "items"}...",
                Toast.LENGTH_SHORT
            ).show()

            onDownloadClickListener?.invoke(checked)
            dismiss()
        }
    }

    private fun updateDownloadButtonState() {
        val adapter = binding.rvMedia.adapter as? DownloadMediaAdapter
        val count = adapter?.getCheckedItems()?.size ?: 0
        binding.btnDownload.text = if (count > 0) "Download ($count)" else "Download"
    }

    fun setMediaList(items: List<MediaItem>, onDownload: (List<MediaItem>) -> Unit) {
        mediaItems = items.toMutableList()
        onDownloadClickListener = onDownload
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DownloadBottomSheetFragment"

        fun newInstance(
            items: List<MediaItem>,
            onDownload: (List<MediaItem>) -> Unit
        ): DownloadBottomSheetFragment {
            return DownloadBottomSheetFragment().apply {
                setMediaList(items, onDownload)
            }
        }
    }
}
