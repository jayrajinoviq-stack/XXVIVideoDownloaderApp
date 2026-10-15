package com.inoviq.videodownloader.videosaver.storysaver.ui.customview

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.inoviq.videodownloader.videosaver.storysaver.R

/**
 * Custom Wavy Organic Double-Circle Bottom Navigation View matching Image 2.
 * Consists of two distinct circular lobes joined by a deep, smooth concave waist
 * and an orange circular indicator with uniform cream rim border.
 */
class WavyBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.nav_cream_bg)
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(11.5f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val wavePath = Path()
    private val argbEvaluator = ArgbEvaluator()

    private val colorSelected = Color.WHITE
    private val colorUnselected = ContextCompat.getColor(context, R.color.text_gray)
    private val colorOrangeStart = 0xFFFA9500.toInt()
    private val colorOrangeEnd = 0xFFFD7B01.toInt()

    private var tabDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_nav_tab)?.mutate()
    private var filesDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_nav_files)?.mutate()

    // 0.0f = Tab selected, 1.0f = Files selected
    var indicatorProgress: Float = 0f
        private set

    var currentSelectedTab: Int = 0
        private set

    var onTabSelectedListener: ((Int) -> Unit)? = null

    private var animator: ValueAnimator? = null

    init {
        // Soft drop shadow for organic depth
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        wavePaint.setShadowLayer(dpToPx(6f), 0f, dpToPx(3f), 0x1A000000)
    }

    fun selectTab(index: Int, animate: Boolean = true) {
        if (index !in 0..1) return
        currentSelectedTab = index
        val targetProgress = if (index == 0) 0f else 1f

        if (!animate) {
            animator?.cancel()
            indicatorProgress = targetProgress
            invalidate()
            onTabSelectedListener?.invoke(index)
            return
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(indicatorProgress, targetProgress).apply {
            duration = 300L
            interpolator = OvershootInterpolator(0.85f)
            addUpdateListener {
                indicatorProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        onTabSelectedListener?.invoke(index)
    }

    fun setScrollProgress(position: Int, offset: Float) {
        if (animator?.isRunning == true) return
        val raw = position + offset
        indicatorProgress = raw.coerceIn(0f, 1f)
        currentSelectedTab = if (indicatorProgress > 0.5f) 1 else 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = dpToPx(3.5f)
        val r = (h / 2f) - padding
        val cy = h / 2f

        val x1 = r + padding
        val x2 = w - r - padding
        val midX = (x1 + x2) / 2f

        // Deep organic concave pinch in the middle matching Image 2
        val waistR = r * 0.58f

        // Departure angle from circle contours to waist
        val theta = Math.toRadians(40.0)
        val sinT = Math.sin(theta).toFloat()
        val cosT = Math.cos(theta).toFloat()

        // Key contour points
        val p1x = x1 + r * sinT
        val p1y = cy - r * cosT

        val myTop = cy - waistR

        val p2x = x2 - r * sinT
        val p2y = cy - r * cosT

        val p3x = x2 - r * sinT
        val p3y = cy + r * cosT

        val myBot = cy + waistR

        val p4x = x1 + r * sinT
        val p4y = cy + r * cosT

        val handleDist = (midX - p1x) * 0.5f

        // 1. Build Seamless Organic Double-Lobe Peanut Path (Image 2)
        wavePath.reset()

        // Start at P1 on Left Circle
        wavePath.moveTo(p1x, p1y)

        // Top bezier curve: Left Circle -> Midpoint waist
        wavePath.cubicTo(
            p1x + handleDist * cosT, p1y + handleDist * sinT,
            midX - handleDist, myTop,
            midX, myTop
        )

        // Top bezier curve: Midpoint waist -> Right Circle
        wavePath.cubicTo(
            midX + handleDist, myTop,
            p2x - handleDist * cosT, p2y + handleDist * sinT,
            p2x, p2y
        )

        // Right Circle Arc: from P2 (230°) clockwise to P3 (130°) -> sweep 260°
        val rightRect = RectF(x2 - r, cy - r, x2 + r, cy + r)
        wavePath.arcTo(rightRect, 230f, 260f, false)

        // Bottom bezier curve: Right Circle -> Midpoint waist
        wavePath.cubicTo(
            p3x - handleDist * cosT, p3y - handleDist * sinT,
            midX + handleDist, myBot,
            midX, myBot
        )

        // Bottom bezier curve: Midpoint waist -> Left Circle
        wavePath.cubicTo(
            midX - handleDist, myBot,
            p4x + handleDist * cosT, p4y - handleDist * sinT,
            p4x, p4y
        )

        // Left Circle Arc: from P4 (50°) clockwise to P1 (310°) -> sweep 260°
        val leftRect = RectF(x1 - r, cy - r, x1 + r, cy + r)
        wavePath.arcTo(leftRect, 50f, 260f, false)

        wavePath.close()

        canvas.drawPath(wavePath, wavePaint)

        // 2. Moving Orange Indicator Circle with uniform cream rim border (Image 2)
        val indR = r - dpToPx(4.5f)
        val indX = x1 + indicatorProgress * (x2 - x1)

        indicatorPaint.shader = LinearGradient(
            indX, cy - indR,
            indX, cy + indR,
            colorOrangeStart,
            colorOrangeEnd,
            Shader.TileMode.CLAMP
        )
        indicatorPaint.setShadowLayer(dpToPx(5f), 0f, dpToPx(2.5f), 0x33FD7B01)
        canvas.drawCircle(indX, cy, indR, indicatorPaint)

        // 3. Draw Tab Item (Left Lobe)
        val tabColorFactor = (1f - indicatorProgress).coerceIn(0f, 1f)
        val tabColor = argbEvaluator.evaluate(tabColorFactor, colorUnselected, colorSelected) as Int
        drawItem(canvas, tabDrawable, "Tab", x1, cy, tabColor)

        // 4. Draw Files Item (Right Lobe)
        val filesColorFactor = indicatorProgress.coerceIn(0f, 1f)
        val filesColor = argbEvaluator.evaluate(filesColorFactor, colorUnselected, colorSelected) as Int
        drawItem(canvas, filesDrawable, "Files", x2, cy, filesColor)
    }

    private fun drawItem(
        canvas: Canvas,
        drawable: Drawable?,
        label: String,
        centerX: Float,
        centerY: Float,
        color: Int
    ) {
        val iconSize = dpToPx(18f).toInt()
        val iconLeft = (centerX - iconSize / 2f).toInt()
        val iconTop = (centerY - dpToPx(13.5f)).toInt()

        drawable?.let {
            it.setBounds(
                iconLeft,
                iconTop,
                iconLeft + iconSize,
                iconTop + iconSize
            )
            it.setTint(color)
            it.draw(canvas)
        }

        textPaint.color = color
        textPaint.textSize = dpToPx(11f)
        val textY = centerY + dpToPx(13.5f)

        canvas.drawText(label, centerX, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val midX = width / 2f
            if (event.x < midX) {
                selectTab(0, animate = true)
            } else {
                selectTab(1, animate = true)
            }
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
