package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemWhatsappStatusBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.WhatsAppStatusItem

class WhatsAppStatusAdapter(
    private val items: MutableList<WhatsAppStatusItem>,
    private val onItemClick: (WhatsAppStatusItem) -> Unit,
    private val onDownloadClick: (WhatsAppStatusItem, Int) -> Unit
) : RecyclerView.Adapter<WhatsAppStatusAdapter.StatusViewHolder>() {

    fun updateData(newItems: List<WhatsAppStatusItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun markItemSaved(position: Int) {
        if (position in 0 until items.size) {
            items[position].isSaved = true
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemWhatsappStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class StatusViewHolder(private val binding: ItemWhatsappStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WhatsAppStatusItem, position: Int) {
            // Load thumbnail using Glide
            Glide.with(binding.root.context)
                .load(item.uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_search_bar)
                .into(binding.ivStatusThumbnail)

            // Video indicator badge
            binding.llVideoIndicator.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            // Downloaded checkmark badge vs Quick Download button
            if (item.isSaved) {
                binding.btnQuickDownload.visibility = View.GONE
                binding.ivDownloadedBadge.visibility = View.VISIBLE
            } else {
                binding.btnQuickDownload.visibility = View.VISIBLE
                binding.ivDownloadedBadge.visibility = View.GONE
            }

            // Click card to open full-screen preview
            binding.root.setOnClickListener {
                onItemClick(item)
            }

            // 1-Tap quick download button
            binding.btnQuickDownload.setOnClickListener {
                onDownloadClick(item, position)
            }
        }
    }
}
