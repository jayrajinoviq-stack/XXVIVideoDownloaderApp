package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemTabRowBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.BrowserTab

class TabSwitcherAdapter(
    private val tabs: MutableList<BrowserTab>,
    private val onTabClick: (BrowserTab) -> Unit,
    private val onCloseClick: (BrowserTab, Int) -> Unit
) : RecyclerView.Adapter<TabSwitcherAdapter.TabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(tabs[position])
    }

    override fun getItemCount(): Int = tabs.size

    inner class TabViewHolder(private val binding: ItemTabRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: BrowserTab) {
            binding.tvTabTitle.text = tab.title

            // Match favicon/icon according to platform or Homepage
            val iconRes = when {
                tab.title.contains("instagram", true) || tab.url.contains("instagram", true) ->
                    R.drawable.img_logo_instagram
                tab.title.contains("twitter", true) || tab.title.contains("x", true) || tab.url.contains("twitter", true) || tab.url.contains("x.com", true) ->
                    R.drawable.img_logo_twitter_x
                tab.title.contains("facebook", true) || tab.url.contains("facebook", true) ->
                    R.drawable.img_logos_facebook
                tab.title.contains("tiktok", true) || tab.url.contains("tiktok", true) ->
                    R.drawable.img_logos_tiktok
                tab.title.contains("whatsapp", true) || tab.url.contains("whatsapp", true) ->
                    R.drawable.img_logos_whatsapp
                else -> R.drawable.ic_globe
            }
            binding.ivTabIcon.setImageResource(iconRes)

            // Active tab styling (warm light orange background) vs normal
            if (tab.isCurrent) {
                binding.tabRowRoot.setBackgroundResource(R.drawable.bg_tab_item_active)
            } else {
                binding.tabRowRoot.setBackgroundResource(android.R.color.transparent)
            }

            // One tab is required: hide cancel button if only 1 tab exists, show if multiple
            if (tabs.size <= 1) {
                binding.btnRemoveTab.visibility = android.view.View.GONE
            } else {
                binding.btnRemoveTab.visibility = android.view.View.VISIBLE
            }

            binding.tabRowRoot.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTabClick(tabs[pos])
                }
            }

            binding.btnRemoveTab.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onCloseClick(tabs[pos], pos)
                }
            }
        }
    }
}
