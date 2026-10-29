package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemFeatureWalkthroughBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.FeatureWalkthroughItem

class FeatureWalkthroughAdapter(
    private val items: List<FeatureWalkthroughItem>
) : RecyclerView.Adapter<FeatureWalkthroughAdapter.WalkthroughViewHolder>() {

    inner class WalkthroughViewHolder(val binding: ItemFeatureWalkthroughBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FeatureWalkthroughItem) {
            binding.ivWalkthroughIcon.setImageResource(item.iconRes)
            binding.tvWalkthroughTitle.text = item.title
            binding.tvWalkthroughDesc.text = item.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalkthroughViewHolder {
        val binding = ItemFeatureWalkthroughBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WalkthroughViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WalkthroughViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
