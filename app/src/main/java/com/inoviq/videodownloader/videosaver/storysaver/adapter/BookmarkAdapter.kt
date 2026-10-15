package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkEntry

class BookmarkAdapter(
    private val items: MutableList<BookmarkEntry>,
    private val onItemClick: (BookmarkEntry) -> Unit,
    private val onRibbonClick: (BookmarkEntry, Int) -> Unit,
    private val onAddTabClick: (BookmarkEntry) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    inner class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivBookmarkPlatformIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvBookmarkTitle)
        val btnRibbon: ImageView = view.findViewById(R.id.btnBookmarkRibbon)
        val btnAddTab: ImageView = view.findViewById(R.id.btnBookmarkAddTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark_row, parent, false)
        return BookmarkViewHolder(v)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title.ifBlank { "Bookmark" }

        val lowerUrl = item.url.lowercase()
        val lowerTitle = item.title.lowercase()
        when {
            lowerUrl.contains("instagram") || lowerTitle.contains("instagram") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logo_instagram)
            }
            lowerUrl.contains("facebook") || lowerTitle.contains("facebook") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logos_facebook)
            }
            lowerUrl.contains("whatsapp") || lowerTitle.contains("whatsapp") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logos_whatsapp)
            }
            lowerUrl.contains("tiktok") || lowerTitle.contains("tiktok") -> {
                holder.ivIcon.setImageResource(R.drawable.img_logos_tiktok)
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

        holder.btnRibbon.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onRibbonClick(item, pos)
            }
        }

        holder.btnAddTab.setOnClickListener {
            onAddTabClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<BookmarkEntry>) {
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
