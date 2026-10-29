package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemCategoryBinding
import com.inoviq.videodownloader.videosaver.storysaver.model.CategoryItem

class CategoryAdapter(
    private val onItemSelected: (CategoryItem) -> Unit
) : ListAdapter<CategoryItem, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: CategoryViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_CHECKED_STATE)) {
            holder.bindSelection(getItem(position).isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemSelected(getItem(position))
                }
            }
        }

        fun bind(item: CategoryItem) {
            binding.ivCategoryIcon.setImageResource(item.iconRes)
            binding.tvCategoryTitle.text = item.title
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
                binding.cardCategory.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_orange)
                binding.cardCategory.strokeWidth = (1.8f * binding.root.resources.displayMetrics.density).toInt()
            } else {
                binding.cardCategory.strokeColor = ContextCompat.getColor(binding.root.context, R.color.card_stroke)
                binding.cardCategory.strokeWidth = (0.8f * binding.root.resources.displayMetrics.density).toInt()
            }
        }
    }

    companion object {
        const val PAYLOAD_CHECKED_STATE = "PAYLOAD_CHECKED_STATE"
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryItem>() {
        override fun areItemsTheSame(oldItem: CategoryItem, newItem: CategoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CategoryItem, newItem: CategoryItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: CategoryItem, newItem: CategoryItem): Any? {
            return if (oldItem.isSelected != newItem.isSelected) {
                PAYLOAD_CHECKED_STATE
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }
}
