package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemFileCardBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import java.io.File

class DownloadedFilesAdapter(
    private val files: MutableList<MediaItem>,
    private val onDeleteSingle: (MediaItem) -> Unit,
    private val onSelectionChanged: (selectedCount: Int, totalCount: Int) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<DownloadedFilesAdapter.FileViewHolder>() {

    var isMultiSelectMode: Boolean = false
        private set

    val selectedItems = mutableSetOf<String>()

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
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size, files.size)
    }

    fun toggleSelection(item: MediaItem) {
        val key = item.id.ifBlank { item.url }
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        notifyDataSetChanged()
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

    override fun getItemCount(): Int = files.size

    inner class FileViewHolder(private val binding: ItemFileCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            val context = binding.root.context
            val key = item.id.ifBlank { item.url }
            val isSelected = selectedItems.contains(key)

            binding.tvFileName.text = item.title
            binding.tvFileSize.text = "${item.sizeFormatted} • Saved"
            binding.tvFileType.text = if (item.isVideo) "VIDEO" else "IMAGE"
            binding.ivPlayBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            val localPathStr = item.localPath
            val thumbUrlStr = item.thumbnailUrl

            val thumbModel: Any = if (item.isVideo) {
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
                                val bitmap = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
            } else {
                if (!localPathStr.isNullOrEmpty() && File(localPathStr).exists()) {
                    File(localPathStr)
                } else if (!thumbUrlStr.isNullOrEmpty()) {
                    thumbUrlStr
                } else {
                    R.drawable.app_logo
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
                binding.llActions.visibility = View.VISIBLE
                binding.ivCheckbox.visibility = View.GONE
                binding.root.strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                binding.root.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()

                binding.btnShare.setOnClickListener {
                    try {
                        val file = item.localPath?.let { File(it) }
                        if (file != null && file.exists()) {
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (item.isVideo) "video/mp4" else "image/jpeg"
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

                binding.root.setOnClickListener {
                    try {
                        val file = item.localPath?.let { File(it) }
                        if (file != null && file.exists()) {
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(viewIntent)
                        } else if (item.url.startsWith("http")) {
                            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                            context.startActivity(viewIntent)
                        } else {
                            Toast.makeText(context, "Opening ${item.title}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "No app found to open this media", Toast.LENGTH_SHORT).show()
                    }
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
