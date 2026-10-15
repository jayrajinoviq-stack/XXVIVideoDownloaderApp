package com.inoviq.videodownloader.videosaver.storysaver.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.inoviq.videodownloader.videosaver.storysaver.adapter.DownloadMediaAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogDownloadBottomSheetBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem

class DownloadBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDownloadBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var mediaItems: MutableList<MediaItem> = mutableListOf()
    private var onDownloadClickListener: ((List<MediaItem>) -> Unit)? = null

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

        val adapter = DownloadMediaAdapter(mediaItems) {
            updateDownloadButtonState()
        }

        binding.rvMedia.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMedia.adapter = adapter

        binding.btnClose.setOnClickListener {
            dismiss()
        }

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
