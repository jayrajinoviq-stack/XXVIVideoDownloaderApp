package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemWatchCategoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.WatchCategoryItem

class WatchCategoryAdapter(
    private val onItemSelected: (WatchCategoryItem) -> Unit
) : ListAdapter<WatchCategoryItem, WatchCategoryAdapter.WatchCategoryViewHolder>(WatchCategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchCategoryViewHolder {
        val binding = ItemWatchCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WatchCategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WatchCategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: WatchCategoryViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_CHECKED_STATE)) {
            holder.bindSelection(getItem(position).isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class WatchCategoryViewHolder(
        private val binding: ItemWatchCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemSelected(getItem(position))
                }
            }
        }

        fun bind(item: WatchCategoryItem) {
            binding.ivWatchLogo.setImageResource(item.logoRes)
            binding.tvWatchTitle.text = item.title
            bindSelection(item.isSelected)
        }

        fun bindSelection(isSelected: Boolean) {
            val radioRes = if (isSelected) {
                R.drawable.ic_radio_selected
            } else {
                R.drawable.ic_radio_unselected
            }
            binding.ivRadio.setImageResource(radioRes)

            if (isSelected) {
                binding.cardWatchCategory.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_orange)
                binding.cardWatchCategory.strokeWidth = (1.8f * binding.root.resources.displayMetrics.density).toInt()
            } else {
                binding.cardWatchCategory.strokeColor = ContextCompat.getColor(binding.root.context, R.color.card_stroke)
                binding.cardWatchCategory.strokeWidth = (0.8f * binding.root.resources.displayMetrics.density).toInt()
            }
        }
    }

    companion object {
        const val PAYLOAD_CHECKED_STATE = "PAYLOAD_CHECKED_STATE"
    }

    class WatchCategoryDiffCallback : DiffUtil.ItemCallback<WatchCategoryItem>() {
        override fun areItemsTheSame(oldItem: WatchCategoryItem, newItem: WatchCategoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WatchCategoryItem, newItem: WatchCategoryItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: WatchCategoryItem, newItem: WatchCategoryItem): Any? {
            return if (oldItem.isSelected != newItem.isSelected) {
                PAYLOAD_CHECKED_STATE
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }
}
