package com.inoviq.videodownloader.videosaver.storysaver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ItemHomeShortcutBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.HomeScreenShortcut
import com.inoviq.videodownloader.videosaver.storysaver.utils.HomeScreenShortcutManager

class HomeScreenShortcutAdapter(
    private val shortcuts: MutableList<HomeScreenShortcut>,
    private val onShortcutClick: (HomeScreenShortcut) -> Unit,
    private val onAddClick: () -> Unit,
    private val onRemoveClick: (HomeScreenShortcut) -> Unit
) : RecyclerView.Adapter<HomeScreenShortcutAdapter.ShortcutViewHolder>() {

    var isEditMode: Boolean = false
        private set

    fun setEditMode(editMode: Boolean) {
        if (this.isEditMode != editMode) {
            this.isEditMode = editMode
            notifyDataSetChanged()
        }
    }

    fun updateShortcuts(newShortcuts: List<HomeScreenShortcut>) {
        shortcuts.clear()
        shortcuts.addAll(newShortcuts)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        // All shortcuts + 1 "Add" item at the end
        return shortcuts.size + 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemHomeShortcutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        val isAddTile = position == shortcuts.size
        if (isAddTile) {
            holder.bindAddTile()
        } else {
            holder.bindShortcut(shortcuts[position])
        }
    }

    inner class ShortcutViewHolder(private val binding: ItemHomeShortcutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindShortcut(shortcut: HomeScreenShortcut) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            binding.tvShortcutTitle.text = shortcut.title

            val iconRes = HomeScreenShortcutManager.resolveIconRes(shortcut)
            binding.ivShortcutIcon.setImageResource(iconRes)

            if (iconRes == R.drawable.ic_globe) {
                binding.ivShortcutIcon.setBackgroundResource(R.drawable.bg_recent_all_circle)
                val pad = (7 * density).toInt()
                binding.ivShortcutIcon.setPadding(pad, pad, pad, pad)
            } else {
                binding.ivShortcutIcon.background = null
                binding.ivShortcutIcon.setPadding(0, 0, 0, 0)
            }

            // Show cancel badge on top-left when in edit mode
            binding.btnCancelShortcut.visibility = if (isEditMode) View.VISIBLE else View.GONE
            binding.btnCancelShortcut.setOnClickListener {
                onRemoveClick(shortcut)
            }

            binding.llShortcutContainer.setOnClickListener {
                if (isEditMode) {
                    setEditMode(false)
                } else {
                    onShortcutClick(shortcut)
                }
            }

            binding.llShortcutContainer.setOnLongClickListener {
                if (!isEditMode) {
                    setEditMode(true)
                }
                true
            }
        }

        fun bindAddTile() {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            binding.tvShortcutTitle.text = "Add"
            binding.ivShortcutIcon.setImageResource(R.drawable.ic_add_shortcut_plus)
            binding.ivShortcutIcon.setBackgroundResource(R.drawable.bg_recent_all_circle)
            val pad = (8 * density).toInt()
            binding.ivShortcutIcon.setPadding(pad, pad, pad, pad)

            // The Add tile never shows the cancel badge
            binding.btnCancelShortcut.visibility = View.GONE

            binding.llShortcutContainer.setOnClickListener {
                if (isEditMode) {
                    setEditMode(false)
                } else {
                    onAddClick()
                }
            }

            binding.llShortcutContainer.setOnLongClickListener {
                if (isEditMode) {
                    setEditMode(false)
                }
                true
            }
        }
    }
}
