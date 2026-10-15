package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemHowToWorkSlideBinding

class HowToWorkAdapter : RecyclerView.Adapter<HowToWorkAdapter.SlideViewHolder>() {

    private val stepImages = listOf(
        R.drawable.img_how_to_work_step1,
        R.drawable.img_how_to_work_step2,
        R.drawable.img_how_to_work_step3
    )

    override fun getItemCount(): Int = 4

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemHowToWorkSlideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class SlideViewHolder(private val binding: ItemHowToWorkSlideBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            if (position < 3) {
                binding.ivStepIllustration.visibility = View.VISIBLE
                binding.cvDisclaimer.visibility = View.GONE
                binding.ivStepIllustration.setImageResource(stepImages[position])
            } else {
                binding.ivStepIllustration.visibility = View.GONE
                binding.cvDisclaimer.visibility = View.VISIBLE
            }
        }
    }
}
