package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
    private val onDelete: (MediaItem) -> Unit
) : RecyclerView.Adapter<DownloadedFilesAdapter.FileViewHolder>() {

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
            binding.tvFileName.text = item.title
            binding.tvFileSize.text = "${item.sizeFormatted} • Saved"
            binding.tvFileType.text = if (item.isVideo) "VIDEO" else "IMAGE"
            binding.ivPlayBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            val localPathStr = item.localPath
            val thumbModel: Any = if (!localPathStr.isNullOrEmpty() && File(localPathStr).exists()) {
                File(localPathStr)
            } else if (!item.thumbnailUrl.isNullOrEmpty()) {
                item.thumbnailUrl!!
            } else {
                R.drawable.app_logo
            }

            binding.ivFileThumb.load(thumbModel) {
                crossfade(true)
                placeholder(R.drawable.app_logo)
                error(R.drawable.app_logo)
                transformations(RoundedCornersTransformation(16f))
            }

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
                onDelete(item)
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
        }
    }
}
