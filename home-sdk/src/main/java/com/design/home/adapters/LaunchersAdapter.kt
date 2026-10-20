package com.design.home.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.design.commons.extensions.beVisibleIf
import com.design.commons.extensions.getColoredDrawableWithColor
import com.design.commons.extensions.getProperTextColor
import com.design.commons.extensions.realScreenSize
import com.design.home.R
import com.design.home.activities.SimpleActivity
import com.design.home.databinding.ItemLauncherLabelBinding
import com.design.home.extensions.animateScale
import com.design.home.extensions.config
import com.design.home.extensions.getDrawableForPackageName
import com.design.home.interfaces.AllAppsListener
import com.design.home.models.AppLauncher

const val PAYLOAD_TITLE = "payload_title"
const val PAYLOAD_ICON = "payload_icon"
const val PAYLOAD_LABEL_VISIBILITY = "payload_label_visibility"

class LaunchersAdapter(
    val activity: SimpleActivity,
    val allAppsListener: AllAppsListener,
    val itemClick: (Any) -> Unit
) : ListAdapter<AppLauncher, LaunchersAdapter.ViewHolder>(AppLauncherDiffCallback()),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textColor = activity.getProperTextColor()
    private var iconPadding = 0

    init {
        setHasStableIds(true)
        calculateIconWidth()
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).getLauncherIdentifier().hashCode().toLong()
    }

    fun launchFirstApp(): Boolean {
        val launcher = currentList.firstOrNull() ?: return false
        itemClick(launcher)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLauncherLabelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val launcher = getItem(position)
            var handled = false
            for (payload in payloads) {
                if (payload is Set<*>) {
                    if (payload.contains(PAYLOAD_TITLE)) {
                        holder.bindTitle(launcher)
                        handled = true
                    }
                    if (payload.contains(PAYLOAD_ICON)) {
                        holder.bindIcon(launcher)
                        handled = true
                    }
                    if (payload.contains(PAYLOAD_LABEL_VISIBILITY)) {
                        holder.bindLabelVisibility()
                        handled = true
                    }
                }
            }
            if (!handled) {
                holder.bindView(launcher)
            }
        }
    }

    override fun submitList(list: List<AppLauncher>?) {
        calculateIconWidth()
        super.submitList(list)
    }

    override fun submitList(list: List<AppLauncher>?, commitCallback: Runnable?) {
        calculateIconWidth()
        super.submitList(list, commitCallback)
    }

    private fun calculateIconWidth() {
        val currentColumnCount = activity.config.drawerColumnCount
        val iconWidth = activity.realScreenSize.x / currentColumnCount
        iconPadding = (iconWidth * 0.1f).toInt()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTextColor(newTextColor: Int) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(private val binding: ItemLauncherLabelBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bindView(launcher: AppLauncher) {
            itemView.scaleX = LAUNCHER_SCALE_NORMAL
            itemView.scaleY = LAUNCHER_SCALE_NORMAL

            bindTitle(launcher)
            bindIcon(launcher)
            setupTouchAndClicks(launcher)
        }

        fun bindTitle(launcher: AppLauncher) {
            binding.launcherLabel.text = launcher.title
            binding.launcherLabel.setTextColor(textColor)
            bindLabelVisibility()
        }

        fun bindLabelVisibility() {
            binding.launcherLabel.beVisibleIf(activity.config.showDrawerAppLabels)
        }

        fun bindIcon(launcher: AppLauncher) {
            val identifier = launcher.getLauncherIdentifier()
            binding.launcherIcon.setPadding(iconPadding, iconPadding, iconPadding, 0)
            binding.launcherIcon.alpha = 1f

            if (launcher.drawable != null) {
                if (binding.launcherIcon.tag != identifier) {
                    Glide.with(activity).clear(binding.launcherIcon)
                    binding.launcherIcon.setImageDrawable(launcher.drawable)
                    binding.launcherIcon.tag = identifier
                }
            } else {
                if (binding.launcherIcon.tag != identifier) {
                    val placeholderDrawable = activity.resources.getColoredDrawableWithColor(
                        drawableId = R.drawable.placeholder_drawable,
                        color = launcher.thumbnailColor
                    )
                    binding.launcherIcon.tag = identifier
                    Glide.with(activity)
                        .load(activity.getDrawableForPackageName(launcher.packageName))
                        .placeholder(placeholderDrawable)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .into(binding.launcherIcon)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupTouchAndClicks(launcher: AppLauncher) {
            itemView.setOnClickListener { itemClick(launcher) }
            itemView.setOnLongClickListener {
                val location = IntArray(2)
                itemView.getLocationOnScreen(location)
                allAppsListener.onAppLauncherLongPressed(
                    x = (location[0] + itemView.width / 2).toFloat(),
                    y = location[1].toFloat(),
                    appLauncher = launcher
                )
                true
            }

            itemView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.launcherIcon.drawable?.alpha = LAUNCHER_ALPHA_PRESSED
                        itemView.animateScale(
                            from = LAUNCHER_SCALE_NORMAL,
                            to = LAUNCHER_SCALE_PRESSED,
                            duration = LAUNCHER_SCALE_UP_DURATION
                        )
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        binding.launcherIcon.drawable?.alpha = LAUNCHER_ALPHA_NORMAL
                        itemView.animateScale(
                            from = LAUNCHER_SCALE_PRESSED,
                            to = LAUNCHER_SCALE_NORMAL,
                            duration = LAUNCHER_SCALE_DOWN_DURATION
                        )
                    }
                }
                false
            }
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.getBubbleText() ?: ""

    companion object {
        private const val LAUNCHER_SCALE_NORMAL = 1f
        private const val LAUNCHER_SCALE_PRESSED = 1.15f
        private const val LAUNCHER_SCALE_UP_DURATION = 100L
        private const val LAUNCHER_SCALE_DOWN_DURATION = 50L
        private const val LAUNCHER_ALPHA_NORMAL = 255
        private const val LAUNCHER_ALPHA_PRESSED = 220
    }
}

private class AppLauncherDiffCallback : DiffUtil.ItemCallback<AppLauncher>() {
    override fun areItemsTheSame(oldItem: AppLauncher, newItem: AppLauncher): Boolean {
        return oldItem.getLauncherIdentifier() == newItem.getLauncherIdentifier()
    }

    override fun areContentsTheSame(oldItem: AppLauncher, newItem: AppLauncher): Boolean {
        return oldItem.title == newItem.title &&
                oldItem.getLauncherIdentifier() == newItem.getLauncherIdentifier() &&
                oldItem.order == newItem.order &&
                oldItem.thumbnailColor == newItem.thumbnailColor &&
                (oldItem.drawable === newItem.drawable || (oldItem.drawable != null && newItem.drawable != null))
    }

    override fun getChangePayload(oldItem: AppLauncher, newItem: AppLauncher): Any? {
        val payloads = mutableSetOf<String>()
        if (oldItem.title != newItem.title) {
            payloads.add(PAYLOAD_TITLE)
        }
        if (oldItem.drawable !== newItem.drawable || oldItem.thumbnailColor != newItem.thumbnailColor) {
            payloads.add(PAYLOAD_ICON)
        }
        return if (payloads.isNotEmpty()) payloads else null
    }
}
