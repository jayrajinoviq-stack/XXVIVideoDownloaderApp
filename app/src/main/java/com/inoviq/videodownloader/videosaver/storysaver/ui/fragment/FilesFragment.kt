package com.inoviq.videodownloader.videosaver.storysaver.ui.fragment

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.DownloadedFilesAdapter
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogDeleteConfirmationBinding
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogMediaPreviewBinding
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
    private var currentFilter = "ALL" // "ALL", "VIDEOS", "IMAGES", "AUDIOS"

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
                    adapter.notifyItemChanged(dispIdx, DownloadedFilesAdapter.PAYLOAD_PROGRESS)
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
                    adapter.notifyItemChanged(dispIdx, DownloadedFilesAdapter.PAYLOAD_PAUSE_RESUME)
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
                    adapter.notifyItemChanged(dispIdx, DownloadedFilesAdapter.PAYLOAD_PAUSE_RESUME)
                }
            }
        }

        override fun onDownloadCompleted(item: MediaItem) {
            val idx = allFiles.indexOfFirst { it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath) }
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
            context?.let { DownloadStore.removeDownloadedItem(it, item) }
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
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvFiles) { v, insets ->
            val navBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val density = resources.displayMetrics.density
            v.setPadding(0, (8 * density).toInt(), 0, navBars.bottom + (16 * density).toInt())
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

        activity?.let { act ->
            AdManager.loadSmallNativeAd(act, binding.flNativeAdSmall)
        }

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
                    allFiles.removeAll { it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath) }
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
            },
            onItemClick = { item ->
                showMediaPreviewDialog(item)
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
                    allFiles.removeAll { it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath) }
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
        binding.chipAudios.setOnClickListener {
            if (::adapter.isInitialized && adapter.isMultiSelectMode) {
                adapter.setMultiSelectMode(false)
            }
            currentFilter = "AUDIOS"
            updateFilter()
        }
    }

    private fun updateFilter() {
        val newFiltered = when (currentFilter) {
            "ALL" -> allFiles.toList()
            "VIDEOS" -> allFiles.filter { it.isVideo && !it.isAudio }
            "IMAGES" -> allFiles.filter { !it.isVideo && !it.isAudio }
            "AUDIOS" -> allFiles.filter { it.isAudio }
            else -> allFiles.toList()
        }
        displayedFiles.clear()
        displayedFiles.addAll(newFiltered)
        if (::adapter.isInitialized) {
            adapter.updateList(displayedFiles)
        }

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

        binding.chipAudios.background = if (currentFilter == "AUDIOS") activeBg else inactiveBg
        binding.chipAudios.setTextColor(if (currentFilter == "AUDIOS") white else dark)

        binding.tvFileCountSummary.text = "${allFiles.size} ${if (allFiles.size == 1) "file" else "files"} saved"
        binding.llEmptyState.visibility = if (displayedFiles.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun shareMediaItem(item: MediaItem) {
        val ctx = context ?: return
        try {
            val file = item.localPath?.let { File(it) }
            if (file != null && file.exists()) {
                val uri: Uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )
                val mimeType = when {
                    item.isAudio -> if (file.name.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "audio/mp4"
                    item.isVideo -> "video/mp4"
                    else -> "image/jpeg"
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Downloaded via XXVI Downloader")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(sendIntent, "Share Media"))
            } else if (item.url.startsWith("http")) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.url)
                }
                ctx.startActivity(Intent.createChooser(sendIntent, "Share URL"))
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMediaPreviewDialog(item: MediaItem) {
        val ctx = context ?: return
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dBinding = DialogMediaPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        ViewCompat.setOnApplyWindowInsetsListener(dBinding.root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            dBinding.rlPreviewTopBar.setPadding(0, systemBars.top, 0, 0)
            dBinding.llPreviewBottomBar.setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                systemBars.bottom + (14 * resources.displayMetrics.density).toInt()
            )
            insets
        }
        ViewCompat.requestApplyInsets(dBinding.root)

        dBinding.tvPreviewTitle.text = item.title

        dBinding.btnPreviewBack.setOnClickListener {
            dialog.dismiss()
        }

        dBinding.btnPreviewShareBottom.setOnClickListener {
            shareMediaItem(item)
        }

        val deleteAction = {
            showDeleteConfirmationDialog {
                DownloadStore.removeDownloadedItem(ctx, item)
                try {
                    item.localPath?.let { path ->
                        val f = File(path)
                        if (f.exists()) f.delete()
                    }
                } catch (_: Exception) {}
                allFiles.removeAll { it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath) }
                updateFilter()
                dialog.dismiss()
                Toast.makeText(ctx, "Deleted ${item.title}", Toast.LENGTH_SHORT).show()
            }
        }
        dBinding.btnPreviewDeleteBottom.setOnClickListener { deleteAction() }

        val file = item.localPath?.let { File(it) }
        val fileUri = if (file != null && file.exists()) Uri.fromFile(file) else Uri.parse(item.url)

        val handler = Handler(Looper.getMainLooper())
        var mediaPlayer: MediaPlayer? = null
        var updateProgressRunnable: Runnable? = null

        when {
            item.isAudio -> {
                // Apply app's elegant warm cream light theme for audio preview
                dBinding.rlMediaPreviewRoot.setBackgroundColor(Color.parseColor("#FFFFF9F5"))
                dBinding.rlPreviewTopBar.setBackgroundColor(Color.parseColor("#FFFFF9F5"))
                dBinding.tvPreviewTitle.setTextColor(Color.parseColor("#352208"))
                dBinding.btnPreviewBack.setImageResource(R.drawable.ic_btn_back)
                dBinding.btnPreviewBack.imageTintList = null
                dBinding.llPreviewBottomBar.setBackgroundColor(Color.parseColor("#FFFFF9F5"))
                dBinding.flAudioArtContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFEBD4"))
                dBinding.tvAudioTrackTitle.setTextColor(Color.parseColor("#352208"))
                dBinding.tvAudioTrackSub.setTextColor(Color.parseColor("#7D8597"))
                dBinding.tvAudioCurrentTime.setTextColor(Color.parseColor("#352208"))
                dBinding.tvAudioTotalTime.setTextColor(Color.parseColor("#7D8597"))

                dialog.window?.let { win ->
                    androidx.core.view.WindowInsetsControllerCompat(win, win.decorView).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = true
                    }
                }

                dBinding.ivPreviewImage.visibility = View.GONE
                dBinding.flVideoPreview.visibility = View.GONE
                dBinding.llAudioPreview.visibility = View.VISIBLE

                dBinding.tvAudioTrackTitle.text = item.title
                dBinding.tvAudioTrackSub.text = "${item.sizeFormatted} • Audio"

                try {
                    val mp = MediaPlayer()
                    mediaPlayer = mp
                    if (file != null && file.exists()) {
                        mp.setDataSource(file.absolutePath)
                    } else {
                        mp.setDataSource(ctx, fileUri)
                    }
                    mp.setOnPreparedListener { player ->
                        val duration = player.duration
                        dBinding.tvAudioTotalTime.text = formatTime(duration)
                        dBinding.sbAudioProgress.max = if (duration > 0) duration else 1000
                        player.start()
                        dBinding.btnAudioPlayPause.setImageResource(R.drawable.ic_pause)

                        updateProgressRunnable = object : Runnable {
                            override fun run() {
                                if (dialog.isShowing && player.isPlaying) {
                                    val current = player.currentPosition
                                    dBinding.sbAudioProgress.progress = current
                                    dBinding.tvAudioCurrentTime.text = formatTime(current)
                                    handler.postDelayed(this, 250)
                                }
                            }
                        }
                        handler.post(updateProgressRunnable!!)
                    }
                    mp.setOnCompletionListener {
                        dBinding.btnAudioPlayPause.setImageResource(R.drawable.ic_play_arrow)
                        dBinding.sbAudioProgress.progress = 0
                        dBinding.tvAudioCurrentTime.text = "00:00"
                    }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(ctx, "Cannot play audio: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                dBinding.btnAudioPlayPause.setOnClickListener {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            mp.pause()
                            dBinding.btnAudioPlayPause.setImageResource(R.drawable.ic_play_arrow)
                        } else {
                            mp.start()
                            dBinding.btnAudioPlayPause.setImageResource(R.drawable.ic_pause)
                            updateProgressRunnable?.let { handler.post(it) }
                        }
                    }
                }

                dBinding.sbAudioProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mediaPlayer?.seekTo(progress)
                            dBinding.tvAudioCurrentTime.text = formatTime(progress)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            item.isVideo -> {
                dBinding.btnPreviewBack.setImageResource(R.drawable.ic_btn_back_dark)
                dBinding.btnPreviewBack.imageTintList = null
                dBinding.ivPreviewImage.visibility = View.GONE
                dBinding.llAudioPreview.visibility = View.GONE
                dBinding.flVideoPreview.visibility = View.VISIBLE
                dBinding.ivVideoPlayPauseOverlay.visibility = View.GONE

                dBinding.vvPreviewVideo.setVideoURI(fileUri)
                dBinding.vvPreviewVideo.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    val duration = dBinding.vvPreviewVideo.duration
                    dBinding.tvVideoTotalTime.text = formatTime(duration)
                    dBinding.sbVideoProgress.max = if (duration > 0) duration else 1000
                    dBinding.vvPreviewVideo.start()
                    dBinding.btnVideoPlayPauseSmall.setImageResource(R.drawable.ic_pause)

                    updateProgressRunnable = object : Runnable {
                        override fun run() {
                            if (dialog.isShowing && dBinding.vvPreviewVideo.isPlaying) {
                                val current = dBinding.vvPreviewVideo.currentPosition
                                dBinding.sbVideoProgress.progress = current
                                dBinding.tvVideoCurrentTime.text = formatTime(current)
                                handler.postDelayed(this, 250)
                            }
                        }
                    }
                    handler.post(updateProgressRunnable!!)
                }

                val togglePlayPause = {
                    if (dBinding.vvPreviewVideo.isPlaying) {
                        dBinding.vvPreviewVideo.pause()
                        dBinding.ivVideoPlayPauseOverlay.visibility = View.VISIBLE
                        dBinding.btnVideoPlayPauseSmall.setImageResource(R.drawable.ic_play_arrow)
                    } else {
                        dBinding.vvPreviewVideo.start()
                        dBinding.ivVideoPlayPauseOverlay.visibility = View.GONE
                        dBinding.btnVideoPlayPauseSmall.setImageResource(R.drawable.ic_pause)
                        updateProgressRunnable?.let { handler.post(it) }
                    }
                }

                dBinding.flVideoPreview.setOnClickListener { togglePlayPause() }
                dBinding.btnVideoPlayPauseSmall.setOnClickListener { togglePlayPause() }

                dBinding.sbVideoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            dBinding.vvPreviewVideo.seekTo(progress)
                            dBinding.tvVideoCurrentTime.text = formatTime(progress)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }

            else -> {
                // Image Preview
                dBinding.btnPreviewBack.setImageResource(R.drawable.ic_btn_back_dark)
                dBinding.btnPreviewBack.imageTintList = null
                dBinding.flVideoPreview.visibility = View.GONE
                dBinding.llAudioPreview.visibility = View.GONE
                dBinding.ivPreviewImage.visibility = View.VISIBLE

                dBinding.ivPreviewImage.load(if (file != null && file.exists()) file else item.url) {
                    crossfade(true)
                    placeholder(R.drawable.app_logo)
                    error(R.drawable.app_logo)
                }
            }
        }

        dialog.setOnDismissListener {
            updateProgressRunnable?.let { handler.removeCallbacks(it) }
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            try {
                dBinding.vvPreviewVideo.stopPlayback()
            } catch (_: Exception) {}
        }

        dialog.show()
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun addDownloadedFiles(items: List<MediaItem>) {
        val ctx = context
        if (ctx != null) {
            items.forEach { DownloadStore.saveDownloadedItem(ctx, it) }
        }
        items.forEach { item ->
            if (DownloadStore.isMockItem(item)) return@forEach
            val idx = allFiles.indexOfFirst { it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath) }
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
