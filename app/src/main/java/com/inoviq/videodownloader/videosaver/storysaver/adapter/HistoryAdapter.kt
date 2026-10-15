package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.utils.HistoryEntry

class HistoryAdapter(
    private val items: MutableList<HistoryEntry>,
    private val onItemClick: (HistoryEntry) -> Unit,
    private val onMoreClick: (View, HistoryEntry, Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivHistoryPlatformIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvHistoryUrl)
        val btnMore: ImageView = view.findViewById(R.id.btnHistoryMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history_row, parent, false)
        return HistoryViewHolder(v)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title.ifBlank { "Website" }
        holder.tvUrl.text = item.url

        val lowerUrl = item.url.lowercase()
        val lowerTitle = item.title.lowercase()
        when {
            lowerUrl.contains("instagram") || lowerTitle.contains("instagram") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logo_instagram)
            }
            lowerUrl.contains("facebook") || lowerTitle.contains("facebook") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logos_facebook)
            }
            lowerUrl.contains("tiktok") || lowerTitle.contains("tiktok") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logos_tiktok)
            }
            lowerUrl.contains("whatsapp") || lowerTitle.contains("whatsapp") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logos_whatsapp)
            }
            lowerUrl.contains("twitter") || lowerUrl.contains("x.com") || lowerTitle.contains("twitter") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logo_twitter_x)
            }
            else -> {
                holder.ivIcon.setImageResource(R.drawable.ic_globe)
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.btnMore.setOnClickListener { view ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onMoreClick(view, item, pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<HistoryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItemAt(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }
}
