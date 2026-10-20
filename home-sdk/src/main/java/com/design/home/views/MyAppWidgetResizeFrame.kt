package com.design.home.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.design.home.R
import com.design.home.extensions.config
import com.design.home.extensions.getCellCount
import com.design.home.helpers.MAX_CLICK_DURATION
import com.design.home.models.HomeScreenGridItem
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class MyAppWidgetResizeFrame(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private val resizeWidgetLinePaint: Paint
    private val resizeWidgetLineDotPaint: Paint
    private val resizeWidgetDotBorderPaint: Paint

    private var actionDownCoords = PointF()
    private var actionDownMS = 0L
    private var frameRect = Rect(0, 0, 0, 0)    // coords in pixels within HomeScreenGrid
    private var cellsRect = Rect(0, 0, 0, 0)    // cell coordinates (left, top, right, bottom inclusive)
    private var cellWidth = 0
    private var cellHeight = 0
    private var minResizeWidthCells = 1
    private var minResizeHeightCells = 1
    private val occupiedCells = HashSet<Pair<Int, Int>>()
    private var resizedItem: HomeScreenGridItem? = null
    private var sideMargins = Rect()

    private val lineDotRadius = context.resources.getDimension(R.dimen.resize_frame_dot_radius)
    private val handleTouchRadius = max(lineDotRadius * 4f, 48f * context.resources.displayMetrics.density)

    var onClickListener: (() -> Unit)? = null
    var onResizeListener: ((cellsRect: Rect) -> Unit)? = null

    companion object {
        private const val DRAGGING_NONE = 0
        private const val DRAGGING_LEFT = 1
        private const val DRAGGING_TOP = 2
        private const val DRAGGING_RIGHT = 3
        private const val DRAGGING_BOTTOM = 4
    }

    private var dragDirection = DRAGGING_NONE
    private var allowHorizontal = true
    private var allowVertical = true

    init {
        background = ColorDrawable(Color.TRANSPARENT)

        val stroke = context.resources.getDimension(com.design.commons.R.dimen.tiny_margin)
        resizeWidgetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = stroke
            style = Paint.Style.STROKE
        }

        resizeWidgetLineDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        resizeWidgetDotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44000000")
            strokeWidth = stroke * 0.75f
            style = Paint.Style.STROKE
        }
    }

    fun updateFrameCoords(
        coords: Rect,
        cellWidth: Int,
        cellHeight: Int,
        sideMargins: Rect,
        gridItem: HomeScreenGridItem,
        allGridItems: ArrayList<HomeScreenGridItem>
    ) {
        frameRect = Rect(coords)
        cellsRect = Rect(gridItem.left, gridItem.top, gridItem.right, gridItem.bottom)
        this.cellWidth = cellWidth
        this.cellHeight = cellHeight
        this.sideMargins = Rect(sideMargins)
        this.resizedItem = gridItem

        val maxCols = context.config.homeColumnCount
        val maxRows = context.config.homeRowCount

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val providerInfo = gridItem.providerInfo
            ?: appWidgetManager?.getAppWidgetInfo(gridItem.widgetId)
            ?: appWidgetManager?.installedProviders?.firstOrNull { it.provider.className == gridItem.className }

        val isBooster = gridItem.className.endsWith("GameBoosterWidgetProvider")
        val isSmartClock = gridItem.className.contains("SmartTop") || gridItem.className.contains("SmartClock") || gridItem.title.contains("Smart Clock")

        val resizeMode = providerInfo?.resizeMode ?: (AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL)
        allowHorizontal = if (isSmartClock) true else (resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        allowVertical = if (isSmartClock) false else if (isBooster) true else (resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

        minResizeWidthCells = if (isBooster) {
            2
        } else if (isSmartClock) {
            3
        } else if (providerInfo != null && providerInfo.minResizeWidth > 0) {
            context.getCellCount(providerInfo.minResizeWidth).coerceIn(1, maxCols)
        } else {
            1
        }

        minResizeHeightCells = if (isBooster) {
            2
        } else if (isSmartClock) {
            1
        } else if (providerInfo != null && providerInfo.minResizeHeight > 0) {
            context.getCellCount(providerInfo.minResizeHeight).coerceIn(1, max(1, maxRows - 1))
        } else {
            1
        }

        occupiedCells.clear()
        allGridItems.forEach { item ->
            val isCurrentWidget = (item.id != null && item.id == gridItem.id) ||
                (item.widgetId != -1 && item.widgetId == gridItem.widgetId)

            if (!isCurrentWidget && item.page == gridItem.page) {
                val startY = item.getDockAdjustedTop(maxRows)
                val endY = item.getDockAdjustedBottom(maxRows)
                for (x in item.left..item.right) {
                    for (y in startY..endY) {
                        occupiedCells.add(Pair(x, y))
                    }
                }
            }
        }

        // Dock row is reserved
        for (x in 0 until maxCols) {
            occupiedCells.add(Pair(x, maxRows - 1))
        }

        redrawFrame()
    }

    private fun redrawFrame() {
        val newWidth = max(1, frameRect.right - frameRect.left)
        val newHeight = max(1, frameRect.bottom - frameRect.top)
        layoutParams.width = newWidth
        layoutParams.height = newHeight
        x = frameRect.left.toFloat()
        y = frameRect.top.toFloat()
        requestLayout()
        invalidate()
    }

    private fun cellChanged() {
        onResizeListener?.invoke(cellsRect)
    }

    private fun isRectFree(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val maxCols = context.config.homeColumnCount
        val maxWidgetRows = context.config.homeRowCount - 1

        if (left < 0 || top < 0 || right >= maxCols || bottom >= maxWidgetRows) {
            return false
        }
        if (right < left || bottom < top) {
            return false
        }
        val widthCells = right - left + 1
        val heightCells = bottom - top + 1
        if (widthCells < minResizeWidthCells || heightCells < minResizeHeightCells) {
            return false
        }
        for (x in left..right) {
            for (y in top..bottom) {
                if (occupiedCells.contains(Pair(x, y))) {
                    return false
                }
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                actionDownCoords.set(event.rawX, event.rawY)
                actionDownMS = System.currentTimeMillis()
                dragDirection = DRAGGING_NONE

                val w = width.toFloat()
                val h = height.toFloat()

                // Calculate Euclidean distances to handle centers
                val handleLeftDist = hypot(event.x - lineDotRadius, event.y - h / 2f)
                val handleRightDist = hypot(event.x - (w - lineDotRadius), event.y - h / 2f)
                val handleTopDist = hypot(event.x - w / 2f, event.y - lineDotRadius)
                val handleBottomDist = hypot(event.x - w / 2f, event.y - (h - lineDotRadius))

                val minHandleDist = minOf(handleLeftDist, handleRightDist, handleTopDist, handleBottomDist)

                if (minHandleDist <= handleTouchRadius) {
                    dragDirection = when (minHandleDist) {
                        handleLeftDist -> if (allowHorizontal) DRAGGING_LEFT else DRAGGING_NONE
                        handleRightDist -> if (allowHorizontal) DRAGGING_RIGHT else DRAGGING_NONE
                        handleTopDist -> if (allowVertical) DRAGGING_TOP else DRAGGING_NONE
                        handleBottomDist -> if (allowVertical) DRAGGING_BOTTOM else DRAGGING_NONE
                        else -> DRAGGING_NONE
                    }
                } else {
                    // Fallback to edge proximity
                    val edgeThreshold = 24f * context.resources.displayMetrics.density
                    val distLeft = abs(event.x)
                    val distRight = abs(event.x - w)
                    val distTop = abs(event.y)
                    val distBottom = abs(event.y - h)
                    val minEdgeDist = minOf(distLeft, distRight, distTop, distBottom)
                    if (minEdgeDist <= edgeThreshold) {
                        dragDirection = when (minEdgeDist) {
                            distLeft -> DRAGGING_LEFT
                            distRight -> DRAGGING_RIGHT
                            distTop -> DRAGGING_TOP
                            distBottom -> DRAGGING_BOTTOM
                            else -> DRAGGING_NONE
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragDirection == DRAGGING_NONE) {
                    return true
                }

                val maxCols = context.config.homeColumnCount
                val maxWidgetRows = context.config.homeRowCount - 1

                val touchXInGrid = x + event.x
                val touchYInGrid = y + event.y

                when (dragDirection) {
                    DRAGGING_LEFT -> {
                        val targetCellLeft = ((touchXInGrid - sideMargins.left) / cellWidth.toFloat())
                            .roundToInt()

                        var bestLeft = cellsRect.left
                        if (targetCellLeft < cellsRect.left) {
                            for (c in (cellsRect.left - 1) downTo maxOf(0, targetCellLeft)) {
                                if (isRectFree(c, cellsRect.top, cellsRect.right, cellsRect.bottom)) {
                                    bestLeft = c
                                } else {
                                    break
                                }
                            }
                        } else if (targetCellLeft > cellsRect.left) {
                            val maxShrinkLeft = cellsRect.right - minResizeWidthCells + 1
                            val candidate = targetCellLeft.coerceAtMost(maxShrinkLeft)
                            if (isRectFree(candidate, cellsRect.top, cellsRect.right, cellsRect.bottom)) {
                                bestLeft = candidate
                            }
                        }

                        if (cellsRect.left != bestLeft) {
                            cellsRect.left = bestLeft
                            cellChanged()
                        }

                        val allowedMinLeft = bestLeft * cellWidth + sideMargins.left
                        val allowedMaxLeft = (cellsRect.right - minResizeWidthCells + 1) * cellWidth + sideMargins.left
                        frameRect.left = touchXInGrid.toInt().coerceIn(allowedMinLeft, allowedMaxLeft)
                    }

                    DRAGGING_RIGHT -> {
                        val targetCellRight = (((touchXInGrid - sideMargins.left) / cellWidth.toFloat()) - 1)
                            .roundToInt()

                        var bestRight = cellsRect.right
                        if (targetCellRight > cellsRect.right) {
                            for (c in (cellsRect.right + 1)..minOf(targetCellRight, maxCols - 1)) {
                                if (isRectFree(cellsRect.left, cellsRect.top, c, cellsRect.bottom)) {
                                    bestRight = c
                                } else {
                                    break
                                }
                            }
                        } else if (targetCellRight < cellsRect.right) {
                            val minShrinkRight = cellsRect.left + minResizeWidthCells - 1
                            val candidate = targetCellRight.coerceAtLeast(minShrinkRight)
                            if (isRectFree(cellsRect.left, cellsRect.top, candidate, cellsRect.bottom)) {
                                bestRight = candidate
                            }
                        }

                        if (cellsRect.right != bestRight) {
                            cellsRect.right = bestRight
                            cellChanged()
                        }

                        val allowedMinRight = (cellsRect.left + minResizeWidthCells) * cellWidth + sideMargins.left
                        val allowedMaxRight = (bestRight + 1) * cellWidth + sideMargins.left
                        frameRect.right = touchXInGrid.toInt().coerceIn(allowedMinRight, allowedMaxRight)
                    }

                    DRAGGING_TOP -> {
                        val targetCellTop = ((touchYInGrid - sideMargins.top) / cellHeight.toFloat())
                            .roundToInt()

                        var bestTop = cellsRect.top
                        if (targetCellTop < cellsRect.top) {
                            for (c in (cellsRect.top - 1) downTo maxOf(0, targetCellTop)) {
                                if (isRectFree(cellsRect.left, c, cellsRect.right, cellsRect.bottom)) {
                                    bestTop = c
                                } else {
                                    break
                                }
                            }
                        } else if (targetCellTop > cellsRect.top) {
                            val maxShrinkTop = cellsRect.bottom - minResizeHeightCells + 1
                            val candidate = targetCellTop.coerceAtMost(maxShrinkTop)
                            if (isRectFree(cellsRect.left, candidate, cellsRect.right, cellsRect.bottom)) {
                                bestTop = candidate
                            }
                        }

                        if (cellsRect.top != bestTop) {
                            cellsRect.top = bestTop
                            cellChanged()
                        }

                        val allowedMinTop = bestTop * cellHeight + sideMargins.top
                        val allowedMaxTop = (cellsRect.bottom - minResizeHeightCells + 1) * cellHeight + sideMargins.top
                        frameRect.top = touchYInGrid.toInt().coerceIn(allowedMinTop, allowedMaxTop)
                    }

                    DRAGGING_BOTTOM -> {
                        val targetCellBottom = (((touchYInGrid - sideMargins.top) / cellHeight.toFloat()) - 1)
                            .roundToInt()

                        var bestBottom = cellsRect.bottom
                        if (targetCellBottom > cellsRect.bottom) {
                            for (c in (cellsRect.bottom + 1)..minOf(targetCellBottom, maxWidgetRows - 1)) {
                                if (isRectFree(cellsRect.left, cellsRect.top, cellsRect.right, c)) {
                                    bestBottom = c
                                } else {
                                    break
                                }
                            }
                        } else if (targetCellBottom < cellsRect.bottom) {
                            val minShrinkBottom = cellsRect.top + minResizeHeightCells - 1
                            val candidate = targetCellBottom.coerceAtLeast(minShrinkBottom)
                            if (isRectFree(cellsRect.left, cellsRect.top, candidate, cellsRect.bottom)) {
                                bestBottom = candidate
                            }
                        }

                        if (cellsRect.bottom != bestBottom) {
                            cellsRect.bottom = bestBottom
                            cellChanged()
                        }

                        val allowedMinBottom = (cellsRect.top + minResizeHeightCells) * cellHeight + sideMargins.top
                        val allowedMaxBottom = (bestBottom + 1) * cellHeight + sideMargins.top
                        frameRect.bottom = touchYInGrid.toInt().coerceIn(allowedMinBottom, allowedMaxBottom)
                    }
                }

                redrawFrame()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragDirection == DRAGGING_NONE) {
                    onClickListener?.invoke()
                } else if (System.currentTimeMillis() - actionDownMS < MAX_CLICK_DURATION) {
                    onClickListener?.invoke()
                } else {
                    frameRect.left = cellsRect.left * cellWidth + sideMargins.left
                    frameRect.top = cellsRect.top * cellHeight + sideMargins.top
                    frameRect.right = (cellsRect.right + 1) * cellWidth + sideMargins.left
                    frameRect.bottom = (cellsRect.bottom + 1) * cellHeight + sideMargins.top

                    cellChanged()
                    redrawFrame()
                }
                dragDirection = DRAGGING_NONE
            }
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width > 0 && height > 0) {
            val r = lineDotRadius
            canvas.drawRect(r, r, width.toFloat() - r, height.toFloat() - r, resizeWidgetLinePaint)

            // Left handle
            if (allowHorizontal) {
                canvas.drawCircle(r, height / 2f, r, resizeWidgetLineDotPaint)
                canvas.drawCircle(r, height / 2f, r, resizeWidgetDotBorderPaint)
            }

            // Top handle
            if (allowVertical) {
                canvas.drawCircle(width / 2f, r, r, resizeWidgetLineDotPaint)
                canvas.drawCircle(width / 2f, r, r, resizeWidgetDotBorderPaint)
            }

            // Right handle
            if (allowHorizontal) {
                canvas.drawCircle(width - r, height / 2f, r, resizeWidgetLineDotPaint)
                canvas.drawCircle(width - r, height / 2f, r, resizeWidgetDotBorderPaint)
            }

            // Bottom handle
            if (allowVertical) {
                canvas.drawCircle(width / 2f, height - r, r, resizeWidgetLineDotPaint)
                canvas.drawCircle(width / 2f, height - r, r, resizeWidgetDotBorderPaint)
            }
        }
    }
}
