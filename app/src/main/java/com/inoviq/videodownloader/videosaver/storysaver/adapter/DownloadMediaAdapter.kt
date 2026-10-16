package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemDownloadMediaBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem

class DownloadMediaAdapter(
    private val items: MutableList<MediaItem>,
    private val onItemsChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<DownloadMediaAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemDownloadMediaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun getCheckedItems(): List<MediaItem> = items.filter { it.isChecked }

    inner class MediaViewHolder(private val binding: ItemDownloadMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            binding.tvTitle.text = item.title
            binding.tvSize.text = item.sizeFormatted

            val checkIcon = if (item.isChecked) R.drawable.ic_custom_checkbox_checked else R.drawable.ic_custom_checkbox_unchecked
            binding.cbSelect.setImageResource(checkIcon)

            // Load thumbnail using Coil or fallback
            val thumb = if (!item.thumbnailUrl.isNullOrEmpty()) item.thumbnailUrl else item.url
            if (!thumb.isNullOrEmpty()) {
                binding.ivThumbnail.load(thumb) {
                    crossfade(true)
                    placeholder(R.drawable.app_logo)
                    error(R.drawable.app_logo)
                    transformations(RoundedCornersTransformation(14f))
                }
            } else {
                binding.ivThumbnail.setImageResource(R.drawable.app_logo)
            }

            binding.cbSelect.setOnClickListener {
                item.isChecked = !item.isChecked
                binding.cbSelect.setImageResource(
                    if (item.isChecked) R.drawable.ic_custom_checkbox_checked else R.drawable.ic_custom_checkbox_unchecked
                )
                onItemsChanged?.invoke()
            }

            binding.root.setOnClickListener {
                item.isChecked = !item.isChecked
                binding.cbSelect.setImageResource(
                    if (item.isChecked) R.drawable.ic_custom_checkbox_checked else R.drawable.ic_custom_checkbox_unchecked
                )
                onItemsChanged?.invoke()
            }

            binding.btnRename.setOnClickListener {
                showRenameDialog(binding.root.context, item)
            }
        }

        private fun showRenameDialog(context: Context, item: MediaItem) {
            val dialog = android.app.Dialog(context)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            val renameBinding = com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogRenameMediaBinding.inflate(LayoutInflater.from(context))
            dialog.setContentView(renameBinding.root)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.window?.setLayout((320 * context.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

            renameBinding.etRenameInput.setText(item.title)
            renameBinding.etRenameInput.setSelection(renameBinding.etRenameInput.text.length)

            renameBinding.btnCancelRename.setOnClickListener {
                dialog.dismiss()
            }

            renameBinding.btnConfirmRename.setOnClickListener {
                val newName = renameBinding.etRenameInput.text.toString().trim()
                if (newName.isNotEmpty()) {
                    item.title = newName
                    binding.tvTitle.text = newName
                    onItemsChanged?.invoke()
                }
                dialog.dismiss()
            }

            dialog.show()
        }
    }
}
