package com.inoviq.videodownloader.videosaver.storysaver.ui.fragment

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogDeleteConfirmationBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.FragmentFilesBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadProgressManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadStore
import java.io.File

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

    private val downloadProgressListener = object : DownloadProgressManager.DownloadProgressListener {
        override fun onDownloadStarted(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id || it.title == item.title }
            if (idx != -1) {
                allFiles[idx] = item
            } else {
                allFiles.add(0, item)
            }
            if (_binding != null) updateFilter()
        }

        override fun onProgressUpdated(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id || it.title == item.title }
            if (idx != -1) {
                allFiles[idx] = item
                val dispIdx = displayedFiles.indexOfFirst { it.id == item.id || it.title == item.title }
                if (dispIdx != -1 && ::adapter.isInitialized) {
                    displayedFiles[dispIdx] = item
                    adapter.notifyItemChanged(dispIdx)
                }
            } else {
                allFiles.add(0, item)
                if (_binding != null) updateFilter()
            }
        }

        override fun onDownloadPaused(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id || it.title == item.title }
            if (idx != -1) {
                allFiles[idx] = item
                val dispIdx = displayedFiles.indexOfFirst { it.id == item.id || it.title == item.title }
                if (dispIdx != -1 && ::adapter.isInitialized) {
                    displayedFiles[dispIdx] = item
                    adapter.notifyItemChanged(dispIdx)
                }
            }
        }

        override fun onDownloadResumed(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id || it.title == item.title }
            if (idx != -1) {
                allFiles[idx] = item
                val dispIdx = displayedFiles.indexOfFirst { it.id == item.id || it.title == item.title }
                if (dispIdx != -1 && ::adapter.isInitialized) {
                    displayedFiles[dispIdx] = item
                    adapter.notifyItemChanged(dispIdx)
                }
            }
        }

        override fun onDownloadCompleted(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id || it.title == item.title }
            if (idx != -1) {
                allFiles[idx] = item
            } else {
                allFiles.add(0, item)
            }
            if (_binding != null) updateFilter()
        }

        override fun onDownloadFailed(item: MediaItem, error: String) {
            val idx = allFiles.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                allFiles.removeAt(idx)
                if (_binding != null) updateFilter()
            }
        }

        override fun onDownloadCancelled(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                allFiles.removeAt(idx)
                if (_binding != null) updateFilter()
            }
        }
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
        setupMultiSelectActions()
        DownloadProgressManager.registerListener(downloadProgressListener)
        loadPersistedFiles()

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (::adapter.isInitialized && adapter.isMultiSelectMode) {
                    adapter.setMultiSelectMode(false)
                    return@setOnKeyListener true
                }
            }
            false
        }
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
        val saved = DownloadStore.getDownloadedItems(context).toMutableList()
        val active = DownloadProgressManager.getActiveDownloads()
        active.forEach { activeItem ->
            val idx = saved.indexOfFirst { it.id == activeItem.id || it.title == activeItem.title }
            if (idx != -1) {
                saved[idx] = activeItem
            } else {
                saved.add(0, activeItem)
            }
        }
        allFiles.clear()
        allFiles.addAll(saved)
        updateFilter()
    }

    private fun setupRecyclerView() {
        adapter = DownloadedFilesAdapter(
            displayedFiles,
            onDeleteSingle = { item ->
                showDeleteConfirmationDialog {
                    val context = context ?: return@showDeleteConfirmationDialog
                    DownloadStore.removeDownloadedItem(context, item)
                    try {
                        item.localPath?.let { path ->
                            val f = File(path)
                            if (f.exists()) f.delete()
                        }
                    } catch (_: Exception) {}
                    allFiles.removeAll { it.id == item.id || it.title == item.title || it.url == item.url }
                    updateFilter()
                    Toast.makeText(context, "Deleted ${item.title}", Toast.LENGTH_SHORT).show()
                }
            },
            onPauseResume = { item ->
                val ctx = context ?: return@DownloadedFilesAdapter
                if (item.isPaused) {
                    com.inoviq.videodownloader.videosaver.storysaver.service.DownloadService.resumeDownload(ctx, item.id)
                } else {
                    com.inoviq.videodownloader.videosaver.storysaver.service.DownloadService.pauseDownload(ctx, item.id)
                }
            },
            onCancelDownload = { item ->
                val ctx = context ?: return@DownloadedFilesAdapter
                com.inoviq.videodownloader.videosaver.storysaver.service.DownloadService.cancelDownload(ctx, item.id)
                allFiles.removeAll { it.id == item.id }
                updateFilter()
            },
            onSelectionChanged = { selectedCount, totalCount ->
                binding.tvSelectedCount.text = "$selectedCount Selected"
                val allSelected = selectedCount > 0 && selectedCount == totalCount
                binding.ivSelectAllCheckbox.setImageResource(
                    if (allSelected) R.drawable.ic_custom_checkbox_checked
                    else R.drawable.ic_custom_checkbox_unchecked
                )
            },
            onMultiSelectModeChanged = { isMulti ->
                binding.llNormalHeader.visibility = if (isMulti) View.GONE else View.VISIBLE
                binding.llMultiSelectHeader.visibility = if (isMulti) View.VISIBLE else View.GONE
            }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter
    }

    private fun setupMultiSelectActions() {
        binding.btnCancelMultiSelect.setOnClickListener {
            adapter.setMultiSelectMode(false)
        }

        binding.llSelectAll.setOnClickListener {
            val total = displayedFiles.size
            val currentSelected = adapter.selectedItems.size
            val shouldSelectAll = currentSelected < total
            adapter.selectAll(shouldSelectAll)
        }

        binding.btnMultiDelete.setOnClickListener {
            val selected = adapter.getSelectedFiles()
            if (selected.isEmpty()) {
                Toast.makeText(context, "No files selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showDeleteConfirmationDialog {
                val ctx = context ?: return@showDeleteConfirmationDialog
                selected.forEach { item ->
                    DownloadStore.removeDownloadedItem(ctx, item)
                    try {
                        item.localPath?.let { path ->
                            val f = File(path)
                            if (f.exists()) f.delete()
                        }
                    } catch (_: Exception) {}
                    allFiles.removeAll { it.id == item.id || it.title == item.title || it.url == item.url }
                }
                val count = selected.size
                adapter.setMultiSelectMode(false)
                updateFilter()
                Toast.makeText(ctx, "Deleted $count files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog(onConfirm: () -> Unit) {
        val context = context ?: return
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((320 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogBinding.tvDeleteDialogMessage.text = "Are you sure you went to delete It?"

        dialogBinding.btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            if (::adapter.isInitialized && adapter.isMultiSelectMode) {
                adapter.setMultiSelectMode(false)
            }
            currentFilter = "ALL"
            updateFilter()
        }
        binding.chipVideos.setOnClickListener {
            if (::adapter.isInitialized && adapter.isMultiSelectMode) {
                adapter.setMultiSelectMode(false)
            }
            currentFilter = "VIDEOS"
            updateFilter()
        }
        binding.chipImages.setOnClickListener {
            if (::adapter.isInitialized && adapter.isMultiSelectMode) {
                adapter.setMultiSelectMode(false)
            }
            currentFilter = "IMAGES"
            updateFilter()
        }
    }

    private fun updateFilter() {
        displayedFiles.clear()
        when (currentFilter) {
            "ALL" -> displayedFiles.addAll(allFiles)
            "VIDEOS" -> displayedFiles.addAll(allFiles.filter { it.isVideo })
            "IMAGES" -> displayedFiles.addAll(allFiles.filter { !it.isVideo && !it.isAudio })
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
        DownloadProgressManager.unregisterListener(downloadProgressListener)
        super.onDestroyView()
        _binding = null
    }
}
