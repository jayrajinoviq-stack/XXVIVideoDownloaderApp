package com.design.home.views

import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.design.commons.extensions.getProperBackgroundColor
import com.design.commons.extensions.getProperTextColor
import com.design.home.R
import com.design.home.databinding.LayoutLauncherPopupBinding

data class PopupActionItem(
    val id: Int,
    val title: String,
    val icon: Drawable?,
    val isShortcut: Boolean = false,
    val onClick: () -> Unit,
)

class LauncherActionPopup(private val binding: LayoutLauncherPopupBinding) {

    val isShowing: Boolean
        get() = binding.root.visibility == View.VISIBLE && binding.launcherPopupCard.visibility == View.VISIBLE

    private var currentDismissListener: (() -> Unit)? = null

    fun show(
        anchorX: Float,
        anchorY: Float,
        anchorRect: RectF? = null,
        items: List<PopupActionItem>,
        shortcutItems: List<PopupActionItem> = emptyList(),
        onDismiss: (() -> Unit)? = null,
    ) {
        if (items.isEmpty() && shortcutItems.isEmpty()) return

        currentDismissListener = onDismiss
        val context = binding.root.context
        val textColor = context.getProperTextColor()
        val bgColor = context.getProperBackgroundColor()

        binding.launcherPopupCard.setCardBackgroundColor(bgColor)

        val inflater = LayoutInflater.from(context)
        val itemsHolder = binding.launcherPopupItemsHolder
        itemsHolder.removeAllViews()

        // 1. Add shortcuts if present
        if (shortcutItems.isNotEmpty()) {
            for (item in shortcutItems) {
                val itemView = inflater.inflate(R.layout.item_launcher_popup_action, itemsHolder, false)
                val iconView = itemView.findViewById<ImageView>(R.id.action_item_icon)
                val titleView = itemView.findViewById<TextView>(R.id.action_item_title)

                if (item.icon != null) {
                    iconView.setImageDrawable(item.icon)
                } else {
                    iconView.visibility = View.GONE
                }

                titleView.text = item.title
                titleView.setTextColor(textColor)

                itemView.setOnClickListener {
                    dismiss(animate = true)
                    item.onClick()
                }
                itemsHolder.addView(itemView)
            }

            if (items.isNotEmpty()) {
                val divider = inflater.inflate(R.layout.item_launcher_popup_divider, itemsHolder, false)
                itemsHolder.addView(divider)
            }
        }

        // 2. Add standard actions
        for (item in items) {
            val itemView = inflater.inflate(R.layout.item_launcher_popup_action, itemsHolder, false)
            val iconView = itemView.findViewById<ImageView>(R.id.action_item_icon)
            val titleView = itemView.findViewById<TextView>(R.id.action_item_title)

            if (item.icon != null) {
                iconView.setImageDrawable(item.icon)
                iconView.setColorFilter(textColor)
            } else {
                iconView.visibility = View.GONE
            }

            titleView.text = item.title
            titleView.setTextColor(textColor)

            itemView.setOnClickListener {
                dismiss(animate = true)
                item.onClick()
            }
            itemsHolder.addView(itemView)
        }

        binding.launcherPopupScrim.setOnClickListener {
            dismiss(animate = true)
        }

        binding.root.visibility = View.VISIBLE
        binding.launcherPopupCard.visibility = View.INVISIBLE

        binding.root.post {
            val containerWidth = binding.root.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels
            val containerHeight = binding.root.height.takeIf { it > 0 }
                ?: context.resources.displayMetrics.heightPixels
            val density = context.resources.displayMetrics.density

            binding.launcherPopupCard.measure(
                View.MeasureSpec.makeMeasureSpec((containerWidth * 0.85f).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(containerHeight, View.MeasureSpec.AT_MOST)
            )

            val cardWidth = binding.launcherPopupCard.measuredWidth
            val cardHeight = binding.launcherPopupCard.measuredHeight

            val marginSide = 16f * density
            val marginTop = 36f * density
            val marginBottom = 48f * density
            val gap = 8f * density

            val aTop = anchorRect?.top ?: (anchorY - 24f * density)
            val aBottom = anchorRect?.bottom ?: (anchorY + 24f * density)
            val aCenterX = anchorRect?.centerX() ?: anchorX
            val aCenterY = anchorRect?.centerY() ?: anchorY

            // Smart Vertical Placement
            val spaceBelow = (containerHeight - marginBottom) - (aBottom + gap)
            val spaceAbove = (aTop - gap) - marginTop

            val openBelow = if (spaceBelow >= cardHeight && aCenterY < containerHeight * 0.52f) {
                true
            } else if (spaceAbove >= cardHeight) {
                false
            } else {
                spaceBelow >= spaceAbove
            }

            val targetY = if (openBelow) {
                (aBottom + gap).coerceAtMost(containerHeight - marginBottom - cardHeight).coerceAtLeast(marginTop)
            } else {
                (aTop - gap - cardHeight).coerceAtLeast(marginTop)
            }
            val pivotY = if (openBelow) 0f else cardHeight.toFloat()

            // Smart Horizontal Placement with Edge Clamping
            var targetX = aCenterX - (cardWidth / 2f)
            val minX = marginSide
            val maxX = (containerWidth - marginSide - cardWidth).coerceAtLeast(minX)
            targetX = targetX.coerceIn(minX, maxX)

            val pivotRatioX = if (cardWidth > 0) {
                ((aCenterX - targetX) / cardWidth.toFloat()).coerceIn(0.1f, 0.9f)
            } else {
                0.5f
            }
            val pivotX = pivotRatioX * cardWidth

            binding.launcherPopupCard.x = targetX
            binding.launcherPopupCard.y = targetY
            binding.launcherPopupCard.pivotX = pivotX
            binding.launcherPopupCard.pivotY = pivotY
            binding.launcherPopupCard.scaleX = 0.55f
            binding.launcherPopupCard.scaleY = 0.55f
            binding.launcherPopupCard.alpha = 0f
            binding.launcherPopupCard.visibility = View.VISIBLE

            binding.launcherPopupCard.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
        }
    }

    fun dismiss(animate: Boolean = true) {
        if (binding.root.visibility != View.VISIBLE) return

        val listener = currentDismissListener
        currentDismissListener = null

        if (!animate) {
            binding.launcherPopupCard.animate().cancel()
            binding.launcherPopupCard.visibility = View.GONE
            binding.root.visibility = View.GONE
            listener?.invoke()
            return
        }

        binding.launcherPopupCard.animate()
            .scaleX(0.7f)
            .scaleY(0.7f)
            .alpha(0f)
            .setDuration(150)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                binding.launcherPopupCard.visibility = View.GONE
                binding.root.visibility = View.GONE
                listener?.invoke()
            }
            .start()
    }
}
