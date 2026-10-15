package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemSearchSuggestionBinding

class SearchSuggestionsAdapter(
    private val items: MutableList<String>,
    private val onSuggestionClick: (String) -> Unit,
    private val onFillQuery: (String) -> Unit
) : RecyclerView.Adapter<SearchSuggestionsAdapter.SuggestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSearchSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateSuggestions(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class SuggestionViewHolder(private val binding: ItemSearchSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(text: String) {
            binding.tvSuggestionText.text = text

            val isUrl = text.contains(".") && !text.contains(" ")
            binding.ivSuggestionIcon.setImageResource(
                if (isUrl) R.drawable.ic_globe else R.drawable.ic_search_enter
            )

            binding.root.setOnClickListener {
                onSuggestionClick(text)
            }

            binding.btnFillQuery.setOnClickListener {
                onFillQuery(text)
            }
        }
    }
}
