package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemFileCardBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import java.io.File

class DownloadedFilesAdapter(
    private var files: MutableList<MediaItem>,
    private val onDeleteSingle: (MediaItem) -> Unit,
    private val onPauseResume: ((MediaItem) -> Unit)? = null,
    private val onCancelDownload: ((MediaItem) -> Unit)? = null,
    private val onSelectionChanged: (selectedCount: Int, totalCount: Int) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit,
    private val onItemClick: ((MediaItem) -> Unit)? = null
) : RecyclerView.Adapter<DownloadedFilesAdapter.FileViewHolder>() {

    companion object {
        const val PAYLOAD_PROGRESS = "PAYLOAD_PROGRESS"
        const val PAYLOAD_PAUSE_RESUME = "PAYLOAD_PAUSE_RESUME"
        const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }

    var isMultiSelectMode: Boolean = false
        private set

    val selectedItems = mutableSetOf<String>()

    fun updateList(newList: List<MediaItem>) {
        val oldSnapshot = ArrayList(this.files)
        val newSnapshot = ArrayList(newList)
        val diffCallback = MediaItemDiffCallback(oldSnapshot, newSnapshot)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.files = newSnapshot
        diffResult.dispatchUpdatesTo(this)
    }

    fun setMultiSelectMode(enabled: Boolean) {
        if (isMultiSelectMode == enabled) return
        isMultiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
        onMultiSelectModeChanged(enabled)
        onSelectionChanged(selectedItems.size, files.size)
    }

    fun selectAll(select: Boolean) {
        selectedItems.clear()
        if (select) {
            files.forEach { selectedItems.add(it.id.ifBlank { it.url }) }
        }
        for (i in files.indices) {
            notifyItemChanged(i, PAYLOAD_SELECTION)
        }
        onSelectionChanged(selectedItems.size, files.size)
    }

    fun toggleSelection(item: MediaItem) {
        val key = item.id.ifBlank { item.url }
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        val idx = files.indexOfFirst { (it.id.isNotBlank() && it.id == key) || it.url == key }
        if (idx != -1) {
            notifyItemChanged(idx, PAYLOAD_SELECTION)
        } else {
            notifyDataSetChanged()
        }
        onSelectionChanged(selectedItems.size, files.size)
    }

    fun getSelectedFiles(): List<MediaItem> {
        return files.filter { selectedItems.contains(it.id.ifBlank { it.url }) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun onBindViewHolder(
        holder: FileViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bindPayload(files[position], payloads)
        }
    }

    override fun getItemCount(): Int = files.size

    inner class FileViewHolder(private val binding: ItemFileCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindPayload(item: MediaItem, payloads: List<Any>) {
            val key = item.id.ifBlank { item.url }
            val isSelected = selectedItems.contains(key)
            val isDownloading = !item.isDownloaded

            var handled = false
            for (payload in payloads) {
                val payloadSet: Set<String> = when (payload) {
                    is Set<*> -> payload.filterIsInstance<String>().toSet()
                    is String -> setOf(payload)
                    else -> emptySet()
                }

                if (payloadSet.contains(PAYLOAD_PROGRESS) || payloadSet.contains(PAYLOAD_PAUSE_RESUME)) {
                    if (isDownloading) {
                        binding.pbDownloadProgress.visibility = View.VISIBLE
                        if (item.downloadProgress > 0) {
                            binding.pbDownloadProgress.isIndeterminate = false
                            binding.pbDownloadProgress.progress = item.downloadProgress
                            binding.tvFileSize.text = if (item.isPaused) {
                                "${item.downloadProgress}% • Paused"
                            } else {
                                "${item.downloadProgress}% • Downloading..."
                            }
                        } else {
                            binding.pbDownloadProgress.isIndeterminate = !item.isPaused
                            binding.tvFileSize.text = if (item.isPaused) "Paused" else "Downloading..."
                        }
                        binding.llActions.visibility = View.GONE
                        binding.llDownloadControls.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
                        binding.btnPauseResume.setImageResource(
                            if (item.isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause
                        )
                    } else {
                        binding.pbDownloadProgress.visibility = View.GONE
                        binding.pbDownloadProgress.isIndeterminate = false
                        binding.tvFileSize.text = "${item.sizeFormatted} • Saved"
                        binding.llDownloadControls.visibility = View.GONE
                        binding.llActions.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
                    }
                    handled = true
                }

                if (payloadSet.contains(PAYLOAD_SELECTION)) {
                    binding.ivCheckbox.setImageResource(
                        if (isSelected) R.drawable.ic_custom_checkbox_checked
                        else R.drawable.ic_custom_checkbox_unchecked
                    )
                    if (isSelected) {
                        binding.root.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_orange)
                        binding.root.strokeWidth = (2 * binding.root.context.resources.displayMetrics.density).toInt()
                    } else {
                        binding.root.strokeColor = ContextCompat.getColor(binding.root.context, R.color.card_stroke)
                        binding.root.strokeWidth = (1 * binding.root.context.resources.displayMetrics.density).toInt()
                    }
                    handled = true
                }
            }

            if (!handled) {
                bind(item)
            }
        }

        fun bind(item: MediaItem) {
            val context = binding.root.context
            val key = item.id.ifBlank { item.url }
            val isSelected = selectedItems.contains(key)

            binding.tvFileName.text = item.title
            val isDownloading = !item.isDownloaded
            if (isDownloading) {
                binding.pbDownloadProgress.visibility = View.VISIBLE
                if (item.downloadProgress > 0) {
                    binding.pbDownloadProgress.isIndeterminate = false
                    binding.pbDownloadProgress.progress = item.downloadProgress
                    binding.tvFileSize.text = if (item.isPaused) {
                        "${item.downloadProgress}% • Paused"
                    } else {
                        "${item.downloadProgress}% • Downloading..."
                    }
                } else {
                    binding.pbDownloadProgress.isIndeterminate = !item.isPaused
                    binding.tvFileSize.text = if (item.isPaused) "Paused" else "Downloading..."
                }
                binding.llActions.visibility = View.GONE
                binding.llDownloadControls.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
                binding.btnPauseResume.setImageResource(
                    if (item.isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause
                )
            } else {
                binding.pbDownloadProgress.visibility = View.GONE
                binding.pbDownloadProgress.isIndeterminate = false
                binding.tvFileSize.text = "${item.sizeFormatted} • Saved"
                binding.llDownloadControls.visibility = View.GONE
                binding.llActions.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
            }

            when {
                item.isAudio -> {
                    binding.tvFileType.text = "AUDIO"
                    binding.ivPlayBadge.setImageResource(R.drawable.ic_audio_note)
                    binding.ivPlayBadge.visibility = View.VISIBLE
                }
                item.isVideo -> {
                    binding.tvFileType.text = "VIDEO"
                    binding.ivPlayBadge.setImageResource(R.drawable.ic_play_arrow)
                    binding.ivPlayBadge.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvFileType.text = "IMAGE"
                    binding.ivPlayBadge.visibility = View.GONE
                }
            }

            val localPathStr = item.localPath
            val thumbUrlStr = item.thumbnailUrl

            val thumbModel: Any = when {
                item.isAudio -> R.drawable.app_logo
                item.isVideo -> {
                    val thumbFile = when {
                        !thumbUrlStr.isNullOrEmpty() && File(thumbUrlStr).exists() && !thumbUrlStr.endsWith(".mp4", ignoreCase = true) -> File(thumbUrlStr)
                        !localPathStr.isNullOrEmpty() -> {
                            val f = File(localPathStr)
                            val siblingThumb = File(f.parentFile, "${f.nameWithoutExtension}_thumb.jpg")
                            if (siblingThumb.exists()) siblingThumb else null
                        }
                        else -> null
                    }

                    if (thumbFile != null && thumbFile.exists()) {
                        thumbFile
                    } else if (!thumbUrlStr.isNullOrBlank() && thumbUrlStr.startsWith("http")) {
                        thumbUrlStr
                    } else if (!localPathStr.isNullOrBlank() && File(localPathStr).exists()) {
                        val videoFile = File(localPathStr)
                        val extractedFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}_thumb.jpg")
                        if (extractedFile.exists()) {
                            extractedFile
                        } else {
                            kotlin.concurrent.thread {
                                try {
                                    val retriever = android.media.MediaMetadataRetriever()
                                    retriever.setDataSource(videoFile.absolutePath)
                                    val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        ?: retriever.getFrameAtTime(500000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        ?: retriever.frameAtTime
                                    if (bitmap != null) {
                                        val outStream: java.io.OutputStream = java.io.FileOutputStream(extractedFile)
                                        outStream.use { out ->
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                        }
                                        binding.ivFileThumb.post {
                                            binding.ivFileThumb.load(extractedFile) {
                                                crossfade(true)
                                                placeholder(R.drawable.app_logo)
                                                transformations(RoundedCornersTransformation(16f))
                                            }
                                        }
                                    }
                                    retriever.release()
                                } catch (_: Exception) {}
                            }
                            R.drawable.app_logo
                        }
                    } else {
                        R.drawable.app_logo
                    }
                }
                else -> {
                    if (!localPathStr.isNullOrEmpty() && File(localPathStr).exists()) {
                        File(localPathStr)
                    } else if (!thumbUrlStr.isNullOrEmpty()) {
                        thumbUrlStr
                    } else {
                        R.drawable.app_logo
                    }
                }
            }

            binding.ivFileThumb.load(thumbModel) {
                crossfade(true)
                placeholder(R.drawable.app_logo)
                error(R.drawable.app_logo)
                transformations(RoundedCornersTransformation(16f))
            }

            if (isMultiSelectMode) {
                binding.llActions.visibility = View.GONE
                binding.llDownloadControls.visibility = View.GONE
                binding.ivCheckbox.visibility = View.VISIBLE
                binding.ivCheckbox.setImageResource(
                    if (isSelected) R.drawable.ic_custom_checkbox_checked
                    else R.drawable.ic_custom_checkbox_unchecked
                )

                if (isSelected) {
                    binding.root.strokeColor = ContextCompat.getColor(context, R.color.primary_orange)
                    binding.root.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                } else {
                    binding.root.strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                    binding.root.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                }

                binding.root.setOnClickListener {
                    toggleSelection(item)
                }

                binding.root.setOnLongClickListener {
                    toggleSelection(item)
                    true
                }
            } else {
                binding.ivCheckbox.visibility = View.GONE
                binding.root.strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                binding.root.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()

                if (isDownloading) {
                    binding.llActions.visibility = View.GONE
                    binding.llDownloadControls.visibility = View.VISIBLE

                    binding.btnPauseResume.setImageResource(
                        if (item.isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause
                    )
                    binding.btnPauseResume.setOnClickListener {
                        onPauseResume?.invoke(item)
                    }
                    binding.btnCancelDownload.setOnClickListener {
                        onCancelDownload?.invoke(item)
                    }
                } else {
                    binding.llDownloadControls.visibility = View.GONE
                    binding.llActions.visibility = View.VISIBLE

                    binding.btnShare.setOnClickListener {
                        try {
                            val file = item.localPath?.let { File(it) }
                            if (file != null && file.exists()) {
                                val uri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val mimeType = when {
                                    item.isAudio -> "audio/mpeg"
                                    item.isVideo -> "video/mp4"
                                    else -> "image/jpeg"
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "Downloaded via XXVI Downloader")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Media"))
                            } else {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, item.url)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share URL"))
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    binding.btnDelete.setOnClickListener {
                        onDeleteSingle(item)
                    }
                }

                binding.root.setOnClickListener {
                    if (isDownloading) {
                        onPauseResume?.invoke(item)
                        return@setOnClickListener
                    }
                    onItemClick?.invoke(item)
                }

                binding.root.setOnLongClickListener {
                    setMultiSelectMode(true)
                    toggleSelection(item)
                    true
                }
            }
        }
    }
}

class MediaItemDiffCallback(
    private val oldList: List<MediaItem>,
    private val newList: List<MediaItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return when {
            old.id.isNotBlank() && new.id.isNotBlank() -> old.id == new.id
            !old.localPath.isNullOrBlank() && !new.localPath.isNullOrBlank() -> old.localPath == new.localPath
            old.url.isNotBlank() && new.url.isNotBlank() -> old.url == new.url && old.title == new.title
            else -> old.title == new.title
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return old.downloadProgress == new.downloadProgress &&
                old.isPaused == new.isPaused &&
                old.isDownloaded == new.isDownloaded &&
                old.sizeFormatted == new.sizeFormatted &&
                old.sizeBytes == new.sizeBytes &&
                old.title == new.title &&
                old.localPath == new.localPath
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        val payloads = mutableSetOf<String>()
        if (old.downloadProgress != new.downloadProgress || old.sizeFormatted != new.sizeFormatted || old.isDownloaded != new.isDownloaded) {
            payloads.add(DownloadedFilesAdapter.PAYLOAD_PROGRESS)
        }
        if (old.isPaused != new.isPaused) {
            payloads.add(DownloadedFilesAdapter.PAYLOAD_PAUSE_RESUME)
        }
        return if (payloads.isNotEmpty()) payloads else null
    }
}
