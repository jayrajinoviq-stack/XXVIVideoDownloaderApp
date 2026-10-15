package com.inoviq.videodownloader.videosaver.storysaver.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.DownloadedFilesAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.FragmentFilesBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadStore

class FilesFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private val allFiles = mutableListOf<MediaItem>()
    private val displayedFiles = mutableListOf<MediaItem>()
    private lateinit var adapter: DownloadedFilesAdapter
    private var currentFilter = "ALL" // "ALL", "VIDEOS", "IMAGES"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
        binding.btnCloseDownloads.setOnClickListener {
            dismiss()
        }
        setupRecyclerView()
        setupFilterChips()
        loadPersistedFiles()
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog
        val sheet = d?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val displayMetrics = resources.displayMetrics
            val desiredHeight = (displayMetrics.heightPixels * 0.88).toInt()
            it.layoutParams.height = desiredHeight
            behavior.peekHeight = desiredHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onResume() {
        super.onResume()
        loadPersistedFiles()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadPersistedFiles()
        }
    }

    private fun loadPersistedFiles() {
        val context = context ?: return
        DownloadStore.cleanMockItems(context)
        val saved = DownloadStore.getDownloadedItems(context)
        allFiles.clear()
        allFiles.addAll(saved)
        updateFilter()
    }

    private fun setupRecyclerView() {
        adapter = DownloadedFilesAdapter(displayedFiles) { item ->
            val context = context ?: return@DownloadedFilesAdapter
            DownloadStore.removeDownloadedItem(context, item)
            allFiles.removeAll { it.id == item.id || it.title == item.title || it.url == item.url }
            updateFilter()
            Toast.makeText(context, "Deleted ${item.title}", Toast.LENGTH_SHORT).show()
        }
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            currentFilter = "ALL"
            updateFilter()
        }
        binding.chipVideos.setOnClickListener {
            currentFilter = "VIDEOS"
            updateFilter()
        }
        binding.chipImages.setOnClickListener {
            currentFilter = "IMAGES"
            updateFilter()
        }
    }

    private fun updateFilter() {
        displayedFiles.clear()
        when (currentFilter) {
            "ALL" -> displayedFiles.addAll(allFiles)
            "VIDEOS" -> displayedFiles.addAll(allFiles.filter { it.isVideo })
            "IMAGES" -> displayedFiles.addAll(allFiles.filter { !it.isVideo })
        }
        adapter.notifyDataSetChanged()

        val ctx = context ?: return
        val activeBg = ContextCompat.getDrawable(ctx, R.drawable.bg_btn_download_orange)
        val inactiveBg = ContextCompat.getDrawable(ctx, R.drawable.bg_btn_close_outline)
        val white = ContextCompat.getColor(ctx, R.color.white)
        val dark = ContextCompat.getColor(ctx, R.color.text_dark)

        binding.chipAll.background = if (currentFilter == "ALL") activeBg else inactiveBg
        binding.chipAll.setTextColor(if (currentFilter == "ALL") white else dark)

        binding.chipVideos.background = if (currentFilter == "VIDEOS") activeBg else inactiveBg
        binding.chipVideos.setTextColor(if (currentFilter == "VIDEOS") white else dark)

        binding.chipImages.background = if (currentFilter == "IMAGES") activeBg else inactiveBg
        binding.chipImages.setTextColor(if (currentFilter == "IMAGES") white else dark)

        binding.tvFileCountSummary.text = "${allFiles.size} ${if (allFiles.size == 1) "file" else "files"} saved"
        binding.llEmptyState.visibility = if (displayedFiles.isEmpty()) View.VISIBLE else View.GONE
    }

    fun addDownloadedFiles(items: List<MediaItem>) {
        val ctx = context
        if (ctx != null) {
            items.forEach { DownloadStore.saveDownloadedItem(ctx, it) }
        }
        items.forEach { item ->
            if (DownloadStore.isMockItem(item)) return@forEach
            val idx = allFiles.indexOfFirst { it.id == item.id || it.title == item.title || it.url == item.url }
            if (idx != -1) {
                allFiles[idx] = item
            } else {
                allFiles.add(0, item)
            }
        }
        if (_binding != null) {
            updateFilter()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
