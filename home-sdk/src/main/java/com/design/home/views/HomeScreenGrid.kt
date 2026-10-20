package com.design.home.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.RelativeLayout
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toRect
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.google.android.material.math.MathUtils
import kotlinx.collections.immutable.toImmutableList
import com.design.commons.extensions.adjustAlpha
import com.design.commons.extensions.beGone
import com.design.commons.extensions.beVisible
import com.design.commons.extensions.getContrastColor
import com.design.commons.extensions.getProperBackgroundColor
import com.design.commons.extensions.getProperTextColor
import com.design.commons.extensions.performHapticFeedback
import com.design.commons.helpers.FontHelper
import com.design.commons.helpers.ensureBackgroundThread
import com.design.commons.helpers.isSPlus
import com.design.home.R
import com.design.home.activities.LauncherHomeActivity
import com.design.home.databinding.HomeScreenGridBinding
import com.design.home.extensions.config
import com.design.home.extensions.getDrawableForPackageName
import com.design.home.extensions.homeScreenGridItemsDB
import com.design.home.extensions.isPackageInstalled
import com.design.home.helpers.ITEM_TYPE_FOLDER
import com.design.home.helpers.ITEM_TYPE_ICON
import com.design.home.helpers.ITEM_TYPE_SHORTCUT
import com.design.home.helpers.ITEM_TYPE_WIDGET
import com.design.home.helpers.WIDGET_HOST_ID
import com.design.home.models.HomeScreenGridItem
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private lateinit var binding: HomeScreenGridBinding
    private var columnCount = context.config.homeColumnCount
    private var rowCount = context.config.homeRowCount
    private var pageIndicatorsYPos = 0
    private val cells = mutableMapOf<Point, Rect>()
    private var dockCellY = 0
    var cellWidth = 0
    var cellHeight = 0

    private var iconMargin =
        (context.resources.getDimension(R.dimen.icon_side_margin) * 5 / columnCount).toInt()
    private var labelSideMargin =
        context.resources.getDimension(com.design.commons.R.dimen.tiny_margin).toInt()
    private var roundedCornerRadius =
        context.resources.getDimension(com.design.commons.R.dimen.activity_margin)
    private var folderPadding =
        context.resources.getDimension(com.design.commons.R.dimen.medium_margin)
    private var pageIndicatorRadius =
        context.resources.getDimension(R.dimen.page_indicator_dot_radius)
    private var pageIndicatorMargin = context.resources.getDimension(R.dimen.page_indicator_margin)
    private var textPaint: TextPaint
    private var contrastTextPaint: TextPaint
    private var folderTitleTextPaint: TextPaint
    private var dragShadowCirclePaint: Paint
    private var emptyPageIndicatorPaint: Paint
    private var currentPageIndicatorPaint: Paint
    private var folderBackgroundPaint: Paint
    private var folderIconBackgroundPaint: Paint
    private var draggedItem: HomeScreenGridItem? = null
    private var resizedWidget: HomeScreenGridItem? = null
    private var isFirstDraw = true
    private var iconSize = 0

    private val pager = AnimatedGridPager(
        getMaxPage = ::getMaxPage,
        redrawGrid = ::redrawGrid,
        getWidth = { width },
        getHandler = { handler },
        getNextPageBound = { right - sideMargins.right - cellWidth / 2 },
        getPrevPageBound = { left + sideMargins.left + cellWidth / 2 },
        pageChangeStarted = {
            widgetViews.forEach { it.resetTouches() }
            closeFolder()
            accessibilityHelper.invalidateRoot()
        }
    )

    private var currentlyOpenFolder: HomeScreenFolder? = null
    private var draggingLeftFolderAt: Long? = null
    private var draggingEnteredNewFolderAt: Long? = null
    var sideMargins = Rect()

    var gridItems = ArrayList<HomeScreenGridItem>()
    private var gridCenters = ArrayList<Point>()
    private var draggedItemCurrentCoords = Pair(-1, -1)
    private var widgetViews = ArrayList<MyAppWidgetHostView>()

    val appWidgetHost = MyAppWidgetHost(context, WIDGET_HOST_ID)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    private val accessibilityHelper = HomeScreenGridTouchHelper(this)
    var itemClickListener: ((HomeScreenGridItem) -> Unit)? = null
    var itemLongClickListener: ((HomeScreenGridItem) -> Unit)? = null


    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)

        val customTypeface = FontHelper.getTypeface(context)
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.launcher_label_text_size)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
            typeface = customTypeface
        }

        contrastTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperTextColor()
            textSize = context.resources.getDimension(R.dimen.launcher_label_text_size)
            setShadowLayer(2f, 0f, 0f, context.getProperTextColor().getContrastColor())
            typeface = customTypeface
        }

        folderTitleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperTextColor()
            textSize = context.resources.getDimension(com.design.commons.R.dimen.medium_text_size)
            typeface = customTypeface
        }

        dragShadowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(com.design.commons.R.color.hint_white)
            strokeWidth = context.resources.getDimension(com.design.commons.R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        emptyPageIndicatorPaint = Paint(dragShadowCirclePaint).apply {
            strokeWidth = context.resources.getDimension(R.dimen.page_indicator_stroke_width)
        }
        currentPageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(android.R.color.white)
            style = Paint.Style.FILL
        }

        folderBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperBackgroundColor()
            style = Paint.Style.FILL
        }

        folderIconBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperBackgroundColor().adjustAlpha(0.9f)
            style = Paint.Style.FILL
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(Type.systemBars() or Type.displayCutout())
            val gestures = insets.getInsets(Type.systemGestures())
            val navIgnoring = insets.getInsetsIgnoringVisibility(Type.navigationBars())

            val sideMargin = context.resources
                .getDimension(com.design.commons.R.dimen.normal_margin).toInt()

            val newLeft   = sideMargin + bars.left
            val newRight  = sideMargin + bars.right
            val newTop    = bars.top
            val newBottom = max(bars.bottom, max(gestures.bottom, navIgnoring.bottom))
            val marginsChanged = sideMargins.left != newLeft
                    || sideMargins.top != newTop
                    || sideMargins.right != newRight
                    || sideMargins.bottom != newBottom
            if (marginsChanged) {
                sideMargins.set(newLeft, newTop, newRight, newBottom)
                cells.clear()
                gridCenters.clear()
                isFirstDraw = true
                redrawGrid()
            }

            insets
        }

        fetchGridItems()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityHelper.dispatchHoverEvent(event)) return true
        return super.dispatchHoverEvent(event)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = HomeScreenGridBinding.bind(this)
    }

    fun fetchGridItems() {
        ensureBackgroundThread {
            val providers = try {
                appWidgetManager.installedProviders
            } catch (e: Exception) {
                emptyList()
            }
            gridItems = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
            gridItems.toImmutableList().forEach { item ->
                if (item.type == ITEM_TYPE_ICON) {
                    val drawable = context.getDrawableForPackageName(item.packageName)
                    if (drawable == null && !context.isPackageInstalled(item.packageName)) {
                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.deleteById(item.id!!)
                        }
                    } else {
                        item.drawable = drawable
                    }
                } else if (item.type == ITEM_TYPE_FOLDER) {
                    item.drawable = item.toFolder().generateDrawable()
                } else if (item.type == ITEM_TYPE_SHORTCUT) {
                    if (item.icon != null) {
                        item.drawable = item.icon?.toDrawable(context.resources)
                    } else {
                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.deleteById(item.id!!)
                        }
                    }
                }

                item.providerInfo =
                    providers.firstOrNull { it.provider.className == item.className }

                if (isTurboBoosterWidget(item)) {
                    var updated = false
                    if (item.getWidthInCells() != 2 || item.getHeightInCells() != 2) {
                        val span = 2
                        if (item.left + span <= columnCount) {
                            item.right = item.left + span - 1
                        } else {
                            item.left = (columnCount - span).coerceAtLeast(0)
                            item.right = item.left + span - 1
                        }
                        if (item.top + span <= rowCount - 1) {
                            item.bottom = item.top + span - 1
                        } else {
                            item.top = (rowCount - 1 - span).coerceAtLeast(0)
                            item.bottom = item.top + span - 1
                        }
                        updated = true
                    }
                    item.widthCells = item.getWidthInCells()
                    item.heightCells = item.getHeightInCells()
                    if (updated && item.id != null) {
                        val cLeft = item.left
                        val cTop = item.top
                        val cRight = item.right
                        val cBottom = item.bottom
                        val cPage = item.page
                        val cDocked = item.docked
                        val cParent = item.parentId
                        val cId = item.id!!
                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.updateItemPosition(
                                cLeft, cTop, cRight, cBottom, cPage, cDocked, cParent, cId
                            )
                        }
                    }
                }
            }

            redrawGrid()
        }
    }

    fun onPackageUninstalled(packageName: String) {
        val itemsToRemove = gridItems.filter { it.packageName == packageName }
        if (itemsToRemove.isNotEmpty()) {
            itemsToRemove.forEach { item ->
                if (item.widgetId != -1) {
                    try {
                        appWidgetHost.deleteAppWidgetId(item.widgetId)
                    } catch (_: Exception) {
                    }
                }
            }
            gridItems.removeAll { it.packageName == packageName }
            gridItems.filter { it.type == ITEM_TYPE_FOLDER }.forEach { it.drawable = null }
            redrawGrid()
        }
    }

    fun resizeGrid(newRowCount: Int, newColumnCount: Int) {
        if (columnCount != newColumnCount || rowCount != newRowCount) {
            rowCount = newRowCount
            columnCount = newColumnCount
            cells.clear()
            gridCenters.clear()
            iconMargin =
                (context.resources.getDimension(R.dimen.icon_side_margin) * 5 / columnCount).toInt()
            isFirstDraw = true
            gridItems.filter { it.type == ITEM_TYPE_WIDGET }.forEach {
                appWidgetHost.deleteAppWidgetId(it.widgetId)
            }
            widgetViews.forEach { removeView(it) }
            widgetViews.clear()
            redrawGrid()
        }
    }

    fun updateColors() {
        folderTitleTextPaint.color = context.getProperTextColor()
        contrastTextPaint.color = context.getProperTextColor()
        contrastTextPaint.setShadowLayer(
            2f, 0f, 0f, context.getProperTextColor().getContrastColor()
        )
        folderBackgroundPaint.color = context.getProperBackgroundColor()
    }

    fun removeAppIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
            }

            gridItems.removeIf { it.id == item.id }
            if (pager.isOutsideOfPageRange()) {
                post {
                    prevPage()
                }
            }
            redrawGrid()
        }
    }

    fun removeWidget(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
                widgetViews.removeIf { it.tag == item.widgetId }
            }
            gridItems.removeIf { it.id == item.id }
        }
    }

    private fun removeItemFromHomeScreen(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            if (item.id != null) {
                context.homeScreenGridItemsDB.deleteById(item.id!!)
                if (item.parentId != null) {
                    val folderId = item.parentId
                    gridItems.firstOrNull { it.id == folderId }?.drawable = null
                    gridItems
                        .filter {
                            it.parentId == item.parentId && it.left > item.left && it.id != item.id
                        }
                        .forEach {
                            it.left -= 1
                        }

                    context.homeScreenGridItemsDB.shiftFolderItems(
                        folderId = item.parentId!!,
                        shiftFrom = item.left,
                        shiftBy = -1,
                        excludingId = item.id
                    )
                }
            }

            if (item.type == ITEM_TYPE_WIDGET) {
                appWidgetHost.deleteAppWidgetId(item.widgetId)
            }

            if (
                item.page != 0 && gridItems
                    .none { it.page == item.page && it.id != item.id && it.parentId == null }
            ) {
                deletePage(item.page)
            }
        }
    }

    fun itemDraggingStarted(draggedGridItem: HomeScreenGridItem) {
        draggedItem = draggedGridItem

        if (draggedGridItem.type == ITEM_TYPE_WIDGET) {
            closeFolder()
            val draggedWidgetView = widgetViews.firstOrNull { it.tag == draggedGridItem.widgetId }
            if (draggedWidgetView != null) {
                try {
                    if (draggedWidgetView.width > 0 && draggedWidgetView.height > 0) {
                        val bitmap = Bitmap.createBitmap(
                            draggedWidgetView.width,
                            draggedWidgetView.height,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        draggedWidgetView.draw(canvas)
                        draggedItem!!.drawable = bitmap.toDrawable(context.resources)
                    }
                } catch (_: Exception) {
                }
                draggedWidgetView.beGone()
            }
        }

        if (draggedItem!!.drawable == null) {
            if (draggedItem?.type == ITEM_TYPE_FOLDER) {
                draggedItem!!.drawable = draggedGridItem.toFolder().generateDrawable()
            } else {
                draggedItem!!.drawable =
                    context.getDrawableForPackageName(draggedGridItem.packageName)
            }
        }

        redrawGrid()
    }

    fun draggedItemMoved(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        currentlyOpenFolder?.also { folder ->
            if (folder.getDrawingRect().contains(x.toFloat(), y.toFloat())) {
                draggingLeftFolderAt = null
            } else {
                draggingLeftFolderAt.also {
                    if (it == null) {
                        draggingLeftFolderAt = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - it > FOLDER_CLOSE_HOLD_THRESHOLD) {
                        closeFolder()
                    }
                }
            }
        }

        if (draggedItem?.type == ITEM_TYPE_WIDGET) {
            val draggedWidgetView = widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }
            if (draggedWidgetView != null && draggedWidgetView.visibility != View.GONE) {
                draggedWidgetView.beGone()
            }
        }

        draggedItemCurrentCoords = Pair(x, y)

        if (draggedItem?.type != ITEM_TYPE_FOLDER && draggedItem?.type != ITEM_TYPE_WIDGET) {
            val center = gridCenters.minBy {
                abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
            }
            val coveredCell = getClosestGridCells(center)
            if (coveredCell != null) {
                val coveredFolder = gridItems.firstOrNull {
                    it.type == ITEM_TYPE_FOLDER
                            && it.left == coveredCell.x && it.top == coveredCell.y
                }

                if (
                    coveredFolder != null
                    && coveredFolder.id != draggedItem?.id
                    && currentlyOpenFolder == null
                ) {
                    draggingEnteredNewFolderAt.also {
                        if (it == null) {
                            draggingEnteredNewFolderAt = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - it > FOLDER_OPEN_HOLD_THRESHOLD) {
                            if (
                                coveredFolder.toFolder().getItems()
                                    .count() >= HomeScreenGridItem.FOLDER_MAX_CAPACITY
                                && draggedItem?.parentId != coveredFolder.id
                            ) {
                                performHapticFeedback()
                                draggingEnteredNewFolderAt = null
                            } else {
                                openFolder(coveredFolder)
                            }
                        }
                    }
                } else {
                    draggingEnteredNewFolderAt = null
                }
            } else {
                draggingEnteredNewFolderAt = null
            }
        }

        pager.handleItemMovement(x, y)
        redrawGrid()
    }

    // figure out at which cell was the item dropped, if it is empty
    fun itemDraggingStopped() {
        widgetViews.forEach {
            it.hasLongPressed = false
        }

        if (draggedItem == null) {
            return
        }

        pager.itemMovementStopped()
        when (draggedItem!!.type) {
            ITEM_TYPE_FOLDER -> moveItem()
            ITEM_TYPE_ICON, ITEM_TYPE_SHORTCUT -> addAppIconOrShortcut()
            ITEM_TYPE_WIDGET -> addWidget()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun widgetLongPressed(item: HomeScreenGridItem) {
        val currentItem = gridItems.firstOrNull {
            (item.id != null && it.id == item.id) || (item.widgetId != -1 && it.widgetId == item.widgetId)
        } ?: item

        resizedWidget = currentItem
        redrawGrid()

        val widgetView = widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }
        binding.resizeFrame.beGone()
        if (widgetView != null) {
            val widgetPos = calculateWidgetPos(currentItem.getTopLeft(rowCount))
            val rawWidth = currentItem.getWidthInCells() * cellWidth
            val rawHeight = currentItem.getHeightInCells() * cellHeight
            val frameRect = Rect(widgetPos.x, widgetPos.y, widgetPos.x + rawWidth, widgetPos.y + rawHeight)
            val otherGridItems = gridItems.filterVisibleOnCurrentPageOnly()
                .filter { !it.outOfBounds() }
                .filter { (it.id == null || it.id != currentItem.id) && (currentItem.widgetId == -1 || it.widgetId != currentItem.widgetId) }
                .toMutableList() as ArrayList<HomeScreenGridItem>
            binding.resizeFrame.updateFrameCoords(
                coords = frameRect,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                sideMargins = sideMargins,
                gridItem = currentItem,
                allGridItems = otherGridItems
            )
            binding.resizeFrame.beVisible()
            binding.resizeFrame.z = 1f     // make sure the frame isnt behind the widget itself
            binding.resizeFrame.onClickListener = {
                hideResizeLines()
            }

            binding.resizeFrame.onResizeListener = { cellsRect ->
                currentItem.left = cellsRect.left
                currentItem.top = cellsRect.top
                currentItem.right = cellsRect.right
                currentItem.bottom = if (cellsRect.bottom > rowCount - 2) {
                    rowCount - 2
                } else {
                    cellsRect.bottom
                }
                currentItem.widthCells = currentItem.getWidthInCells()
                currentItem.heightCells = currentItem.getHeightInCells()
                updateWidgetPositionAndSize(widgetView, currentItem)

                val existingGridItem = gridItems.firstOrNull {
                    (currentItem.id != null && it.id == currentItem.id) ||
                        (currentItem.widgetId != -1 && it.widgetId == currentItem.widgetId)
                }
                if (existingGridItem != null && existingGridItem !== currentItem) {
                    existingGridItem.left = currentItem.left
                    existingGridItem.top = currentItem.top
                    existingGridItem.right = currentItem.right
                    existingGridItem.bottom = currentItem.bottom
                    existingGridItem.widthCells = currentItem.widthCells
                    existingGridItem.heightCells = currentItem.heightCells
                }

                val widgetTargetCells = ArrayList<Pair<Int, Int>>()
                for (xCell in currentItem.left..currentItem.right) {
                    for (yCell in currentItem.top..currentItem.bottom) {
                        widgetTargetCells.add(Pair(xCell, yCell))
                    }
                }

                gridItems.filterVisibleOnCurrentPageOnly().filter { it.id != currentItem.id }
                    .forEach { gridItem ->
                        for (xCell in gridItem.left..gridItem.right) {
                            for (
                            yCell in gridItem.getDockAdjustedTop(rowCount)
                                .rangeTo(gridItem.getDockAdjustedBottom(rowCount))
                            ) {
                                val cell = Pair(xCell, yCell)
                                val isAnyCellOccupied = widgetTargetCells.contains(cell)
                                if (isAnyCellOccupied) {
                                    if (
                                        gridItem.type == ITEM_TYPE_WIDGET && gridItem.outOfBounds()
                                    ) {
                                        removeWidget(gridItem)
                                    }
                                }
                            }
                        }
                    }

                ensureBackgroundThread {
                    val dbId = existingGridItem?.id ?: currentItem.id
                    if (dbId != null) {
                        context.homeScreenGridItemsDB.updateItemPosition(
                            left = currentItem.left,
                            top = currentItem.top,
                            right = currentItem.right,
                            bottom = currentItem.bottom,
                            page = currentItem.page,
                            docked = false,
                            parentId = null,
                            id = dbId
                        )
                    }
                }
            }

            widgetView.ignoreTouches = true
            widgetView.setOnTouchListener { v, event ->
                binding.resizeFrame.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
    }

    fun hideResizeLines() {
        if (resizedWidget == null) {
            return
        }

        binding.resizeFrame.beGone()
        val targetWidget = resizedWidget!!
        widgetViews.firstOrNull { it.tag == targetWidget.widgetId }?.apply {
            ignoreTouches = false
            setOnTouchListener(null)
            updateWidgetPositionAndSize(this, targetWidget)
        }
        resizedWidget = null
        redrawGrid()
    }

    private fun moveItem() {
        val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }
        val center = gridCenters.minBy {
            abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
        }

        var redrawIcons = false
        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val xIndex = gridCells.x
            val yIndex = gridCells.y

            // check if the destination cell is empty
            var isDroppingPositionValid = true
            val wantedCell = Pair(xIndex, yIndex)
            // No moving folder into the dock
            if (draggedHomeGridItem?.type == ITEM_TYPE_FOLDER && yIndex == rowCount - 1) {
                isDroppingPositionValid = false
            } else {
                gridItems.filterVisibleOnCurrentPageOnly().forEach { item ->
                    for (xCell in item.left..item.right) {
                        for (
                        yCell in item.getDockAdjustedTop(rowCount)
                            .rangeTo(item.getDockAdjustedBottom(rowCount))
                        ) {
                            val cell = Pair(xCell, yCell)
                            val isAnyCellOccupied = wantedCell == cell
                            if (isAnyCellOccupied) {
                                if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                    removeWidget(item)
                                } else {
                                    isDroppingPositionValid = false
                                }
                                return@forEach
                            }
                        }
                    }
                }
            }

            if (isDroppingPositionValid) {
                draggedHomeGridItem?.apply {
                    val oldPage = page
                    left = xIndex
                    top = yIndex
                    right = xIndex
                    bottom = yIndex
                    page = pager.getCurrentPage()
                    docked = yIndex == rowCount - 1

                    ensureBackgroundThread {
                        context.homeScreenGridItemsDB.updateItemPosition(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            page = page,
                            docked = docked,
                            parentId = parentId,
                            id = id!!
                        )

                        if (page != oldPage && oldPage != 0) {
                            if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                                deletePage(oldPage)
                            }
                        }
                    }
                }
                redrawIcons = true
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    private fun addAppIconOrShortcut() {
        var isDroppingPositionValid = false
        var potentialParent: HomeScreenGridItem? = null
        var xIndex: Int? = null
        var yIndex: Int? = null
        var redrawIcons = false

        val folder = currentlyOpenFolder
        if (folder != null && folder.getItemsDrawingRect().contains(
                (draggedItemCurrentCoords.first).toFloat(),
                (draggedItemCurrentCoords.second).toFloat()
            )
        ) {
            val centers = folder.getItemsGridCenters()
            if (centers.isNotEmpty()) {
                val center = centers.minByOrNull {
                    abs(it.second - draggedItemCurrentCoords.first + sideMargins.left)
                        .plus(abs(it.third - draggedItemCurrentCoords.second + sideMargins.top))
                }
                xIndex = center?.first ?: 0
            } else {
                xIndex = 0
            }

            isDroppingPositionValid = true
            potentialParent = folder.item
            yIndex = 0
            redrawIcons = true
        } else {
            val center = gridCenters.minBy {
                abs(it.x - draggedItemCurrentCoords.first + sideMargins.left)
                    .plus(abs(it.y - draggedItemCurrentCoords.second + sideMargins.top))
            }

            val gridCells = getClosestGridCells(center)
            if (gridCells != null) {
                xIndex = gridCells.x
                yIndex = gridCells.y

                // check if the destination cell is empty or a folder
                isDroppingPositionValid = true
                val wantedCell = Pair(xIndex, yIndex)
                gridItems.filterVisibleOnCurrentPageOnly().filter { it.id != draggedItem?.id }
                    .forEach { item ->
                        for (xCell in item.left..item.right) {
                            for (
                            yCell in item.getDockAdjustedTop(rowCount)
                                .rangeTo(item.getDockAdjustedBottom(rowCount))
                            ) {
                                val cell = Pair(xCell, yCell)
                                val isAnyCellOccupied = wantedCell == cell
                                if (isAnyCellOccupied) {
                                    if (item.type != ITEM_TYPE_WIDGET && !item.docked) {
                                        potentialParent = item
                                    } else {
                                        if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                            removeWidget(item)
                                        } else {
                                            isDroppingPositionValid = false
                                        }
                                    }
                                    return@forEach
                                }
                            }
                        }
                    }
            }
        }

        if (isDroppingPositionValid) {
            val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }

            if (potentialParent != null) {
                if (potentialParent.type == ITEM_TYPE_FOLDER) {
                    potentialParent.drawable = null
                    addAppIconOrShortcut(
                        draggedHomeGridItem,
                        xIndex!!,
                        yIndex!!,
                        potentialParent.id,
                        toFolderEnd = potentialParent != currentlyOpenFolder?.item
                    )
                } else {
                    val parentItem = potentialParent.copy(
                        type = ITEM_TYPE_FOLDER,
                        id = null,
                        packageName = "",
                        activityName = "",
                        drawable = null,
                        title = resources.getString(com.design.commons.R.string.folder)
                    )
                    ensureBackgroundThread {
                        val newId = context.homeScreenGridItemsDB.insert(parentItem)
                        parentItem.id = newId
                        potentialParent.apply {
                            parentId = newId
                            left = 0
                            context.homeScreenGridItemsDB.updateItemPosition(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                                page = page,
                                docked = docked,
                                parentId = newId,
                                id = id!!
                            )
                        }
                        (context as? LauncherHomeActivity)?.runOnUiThread {
                            gridItems.add(parentItem)
                            addAppIconOrShortcut(draggedHomeGridItem, xIndex!!, yIndex!!, newId)
                            parentItem.drawable = null
                            redrawGrid()
                        }
                    }
                }
                return
            } else {
                addAppIconOrShortcut(draggedHomeGridItem, xIndex!!, yIndex!!)
                return
            }
        } else {
            performHapticFeedback()
            redrawIcons = true
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    private fun addAppIconOrShortcut(
        draggedHomeGridItem: HomeScreenGridItem?,
        xIndex: Int,
        yIndex: Int,
        newParentId: Long? = null,
        toFolderEnd: Boolean = true,
    ) {
        if (newParentId != null && newParentId != draggedHomeGridItem?.parentId) {
            gridItems.firstOrNull { it.id == newParentId }?.also {
                if (it.toFolder().getItems().count() >= HomeScreenGridItem.FOLDER_MAX_CAPACITY) {
                    performHapticFeedback()
                    draggedItem = null
                    draggedItemCurrentCoords = Pair(-1, -1)
                    redrawGrid()
                    return
                }
            }
        }

        val finalXIndex = if (newParentId != null) {
            if (toFolderEnd) {
                gridItems
                    .firstOrNull { it.id == newParentId }
                    ?.toFolder()
                    ?.getItems()
                    ?.maxOfOrNull { it.left + 1 } ?: 0
            } else {
                min(
                    a = xIndex,
                    b = gridItems
                        .firstOrNull { it.id == newParentId }
                        ?.toFolder()
                        ?.getItems()
                        ?.maxOfOrNull {
                            if (draggedHomeGridItem?.parentId == newParentId) {
                                it.left
                            } else {
                                it.left + 1
                            }
                        } ?: 0)
            }
        } else {
            xIndex
        }
        // we are moving an existing home screen item from one place to another
        if (draggedHomeGridItem != null) {
            draggedHomeGridItem.apply {
                val oldParentId = parentId
                val oldLeft = left
                val oldPage = page
                left = finalXIndex
                top = yIndex
                right = finalXIndex
                bottom = yIndex
                page = pager.getCurrentPage()
                docked = yIndex == rowCount - 1
                parentId = newParentId

                val oldParent = gridItems.firstOrNull { it.id == oldParentId }
                oldParent?.drawable = null
                if (newParentId != null) {
                    gridItems.firstOrNull { it.id == newParentId }?.drawable = null
                }

                val deleteOldParent = if (oldParent?.toFolder()?.getItems()?.isEmpty() == true) {
                    gridItems.remove(oldParent)
                    true
                } else {
                    false
                }

                if (oldParentId != null && currentlyOpenFolder?.item?.id == oldParentId) {
                    if (deleteOldParent || newParentId == null) {
                        closeFolder(redraw = true)
                    }
                }

                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.updateItemPosition(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        page = page,
                        docked = docked,
                        parentId = newParentId,
                        id = id!!
                    )
                    if (deleteOldParent && oldParentId != null) {
                        context.homeScreenGridItemsDB.deleteById(oldParentId)
                    } else if (
                        oldParentId != null
                        && gridItems.none { it.parentId == oldParentId && it.left == oldLeft }
                    ) {
                        gridItems
                            .filter {
                                it.parentId == oldParentId && it.left > oldLeft && it.id != id
                            }
                            .forEach {
                                it.left -= 1
                            }
                        context.homeScreenGridItemsDB.shiftFolderItems(oldParentId, oldLeft, -1, id)
                        (context as? LauncherHomeActivity)?.runOnUiThread {
                            gridItems.firstOrNull { it.id == oldParentId }?.drawable = null
                            redrawGrid()
                        }
                    }

                    if (
                        newParentId != null
                        && gridItems.any { it.parentId == newParentId && it.left == left }
                        && (newParentId != oldParentId || left != oldLeft)
                    ) {
                        gridItems
                            .filter { it.parentId == newParentId && it.left >= left && it.id != id }
                            .forEach {
                                it.left += 1
                            }

                        context.homeScreenGridItemsDB.shiftFolderItems(
                            folderId = newParentId,
                            shiftFrom = left - 1,
                            shiftBy = +1,
                            excludingId = id
                        )
                        (context as? LauncherHomeActivity)?.runOnUiThread {
                            gridItems.firstOrNull { it.id == newParentId }?.drawable = null
                            redrawGrid()
                        }
                    }

                    if (page != oldPage && oldPage != 0) {
                        if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                            deletePage(oldPage)
                        }
                    }
                }
            }
        } else if (draggedItem != null) {
            // we are dragging a new item at the home screen from the All Apps fragment
            val newHomeScreenGridItem = HomeScreenGridItem(
                id = null,
                left = finalXIndex,
                top = yIndex,
                right = finalXIndex,
                bottom = yIndex,
                page = pager.getCurrentPage(),
                packageName = draggedItem!!.packageName,
                activityName = draggedItem!!.activityName,
                title = draggedItem!!.title,
                type = draggedItem!!.type,
                className = "",
                widgetId = -1,
                shortcutId = "",
                icon = draggedItem!!.icon,
                docked = yIndex == rowCount - 1,
                parentId = newParentId,
                drawable = draggedItem!!.drawable?.constantState?.newDrawable(context.resources)?.mutate()
                    ?: context.getDrawableForPackageName(draggedItem!!.packageName),
                providerInfo = draggedItem!!.providerInfo,
                activityInfo = draggedItem!!.activityInfo
            )

            fun finalizeFolderOrder(newItem: HomeScreenGridItem) {
                if (
                    newParentId != null
                    && gridItems.any { it.parentId == newParentId && it.left == newItem.left }
                ) {
                    gridItems
                        .filter {
                            it.parentId == newParentId
                                    && it.left >= newItem.left && it.id != newItem.id
                        }
                        .forEach {
                            it.left += 1
                        }

                    context.homeScreenGridItemsDB.shiftFolderItems(
                        folderId = newParentId,
                        shiftFrom = newItem.left - 1,
                        shiftBy = +1,
                        excludingId = newItem.id
                    )
                }
            }

            if (newHomeScreenGridItem.type == ITEM_TYPE_ICON) {
                ensureBackgroundThread {
                    storeAndShowGridItem(newHomeScreenGridItem)
                    finalizeFolderOrder(newHomeScreenGridItem)
                }
            } else if (newHomeScreenGridItem.type == ITEM_TYPE_SHORTCUT) {
                (context as? LauncherHomeActivity)?.handleShorcutCreation(newHomeScreenGridItem.activityInfo!!) { shortcutId, label, icon ->
                    ensureBackgroundThread {
                        newHomeScreenGridItem.shortcutId = shortcutId
                        newHomeScreenGridItem.title = label
                        newHomeScreenGridItem.icon = icon.toBitmap()
                        newHomeScreenGridItem.drawable = icon
                        storeAndShowGridItem(newHomeScreenGridItem)
                        finalizeFolderOrder(newHomeScreenGridItem)
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        gridItems.filter { it.type == ITEM_TYPE_FOLDER }.forEach { it.drawable = null }
        redrawGrid()
    }

    fun storeAndShowGridItem(item: HomeScreenGridItem) {
        val newId = context.homeScreenGridItemsDB.insert(item)
        item.id = newId
        gridItems.add(item)
        if (item.parentId != null) {
            gridItems.firstOrNull { it.id == item.parentId }?.drawable = null
        }
        redrawGrid()
    }

    private fun addWidget() {
        val center = gridCenters.minBy {
            abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
        }

        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val widgetRect = getWidgetOccupiedRect(gridCells)
            val widgetTargetCells = ArrayList<Pair<Int, Int>>()
            for (xCell in widgetRect.left..widgetRect.right) {
                for (yCell in widgetRect.top..widgetRect.bottom) {
                    widgetTargetCells.add(Pair(xCell, yCell))
                }
            }

            var areAllCellsEmpty = true
            gridItems.filterVisibleOnCurrentPageOnly().filter {
                (draggedItem?.id == null || it.id != draggedItem?.id) &&
                    (draggedItem?.widgetId == null || draggedItem?.widgetId == -1 || it.widgetId != draggedItem?.widgetId)
            }.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.getDockAdjustedTop(rowCount)
                        .rangeTo(item.getDockAdjustedBottom(rowCount))
                    ) {
                        val cell = Pair(xCell, yCell)
                        val isAnyCellOccupied = widgetTargetCells.contains(cell)
                        if (isAnyCellOccupied) {
                            if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                removeWidget(item)
                            } else {
                                areAllCellsEmpty = false
                                return@forEach
                            }
                        }
                    }
                }
            }

            if (areAllCellsEmpty) {
                val widgetItem = draggedItem!!.copy()
                val oldPage = widgetItem.page
                widgetItem.apply {
                    left = widgetRect.left
                    top = widgetRect.top
                    right = widgetRect.right
                    bottom = widgetRect.bottom
                    page = pager.getCurrentPage()
                    widthCells = getWidthInCells()
                    heightCells = getHeightInCells()
                }

                // If moving an existing widget, update memory & view immediately on UI thread
                val existingGridItem = gridItems.firstOrNull {
                    (widgetItem.id != null && it.id == widgetItem.id) ||
                        (widgetItem.widgetId != -1 && it.widgetId == widgetItem.widgetId)
                }
                if (existingGridItem != null) {
                    existingGridItem.left = widgetItem.left
                    existingGridItem.right = widgetItem.right
                    existingGridItem.top = widgetItem.top
                    existingGridItem.bottom = widgetItem.bottom
                    existingGridItem.page = widgetItem.page
                    existingGridItem.widthCells = widgetItem.widthCells
                    existingGridItem.heightCells = widgetItem.heightCells
                }

                val widgetView = widgetViews.firstOrNull { it.tag == widgetItem.widgetId }
                if (widgetView != null && !widgetItem.outOfBounds()) {
                    updateWidgetPositionAndSize(widgetView, widgetItem)
                    widgetView.beVisible()
                }

                ensureBackgroundThread {
                    // store the new widget at creating it, else just move the existing one
                    if (widgetItem.id == null && existingGridItem == null) {
                        val itemId = context.homeScreenGridItemsDB.insert(widgetItem)
                        widgetItem.id = itemId
                        post {
                            bindWidget(widgetItem)
                        }
                    } else {
                        val dbId = existingGridItem?.id ?: widgetItem.id!!
                        context.homeScreenGridItemsDB.updateItemPosition(
                            widgetItem.left,
                            widgetItem.top,
                            widgetItem.right,
                            widgetItem.bottom,
                            pager.getCurrentPage(),
                            false,
                            null,
                            dbId
                        )

                        if (widgetItem.page != oldPage && oldPage != 0) {
                            if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                                deletePage(oldPage)
                            }
                        }
                    }
                }
            } else {
                performHapticFeedback()
                val originalItem = gridItems.firstOrNull {
                    (draggedItem?.id != null && it.id == draggedItem?.id) ||
                        (draggedItem?.widgetId != -1 && it.widgetId == draggedItem?.widgetId)
                }
                val widgetView = widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }
                if (widgetView != null) {
                    if (originalItem != null) {
                        updateWidgetPositionAndSize(widgetView, originalItem)
                    }
                    widgetView.beVisible()
                }
            }
        } else {
            val originalItem = gridItems.firstOrNull {
                (draggedItem?.id != null && it.id == draggedItem?.id) ||
                    (draggedItem?.widgetId != -1 && it.widgetId == draggedItem?.widgetId)
            }
            val widgetView = widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }
            if (widgetView != null) {
                if (originalItem != null) {
                    updateWidgetPositionAndSize(widgetView, originalItem)
                }
                widgetView.beVisible()
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    private fun bindWidget(item: HomeScreenGridItem) {
        if (item.outOfBounds()) {
            return
        }

        val activity = context as LauncherHomeActivity
        val appWidgetProviderInfo = item.providerInfo
            ?: appWidgetManager!!.installedProviders
                .firstOrNull { it.provider.className == item.className }
        if (appWidgetProviderInfo != null) {
            item.widgetId = appWidgetHost.allocateAppWidgetId()
            ensureBackgroundThread {
                context.homeScreenGridItemsDB.updateWidgetId(item.widgetId, item.id!!)
            }

            activity.handleWidgetBinding(
                appWidgetManager = appWidgetManager,
                appWidgetId = item.widgetId,
                appWidgetInfo = appWidgetProviderInfo
            ) { canBind ->
                if (canBind) {
                    if (appWidgetProviderInfo.configure != null) {
                        activity.handleWidgetConfigureScreen(
                            appWidgetHost = appWidgetHost,
                            appWidgetId = item.widgetId
                        ) { success ->
                            if (success) {
                                placeAppWidget(appWidgetProviderInfo, item)
                            } else {
                                removeItemFromHomeScreen(item)
                            }
                        }
                    } else {
                        placeAppWidget(appWidgetProviderInfo, item)
                    }
                } else {
                    removeItemFromHomeScreen(item)
                }

                if (pager.isOutsideOfPageRange()) {
                    prevPage(redraw = true)
                }
            }
        }
    }

    fun addNewWidgetDirectly(appWidget: com.design.home.models.AppWidget): Boolean {
        val isBooster = appWidget.className.endsWith("GameBoosterWidgetProvider")
        val defaultSpan = if (isBooster) 2 else 1
        val minSpan = if (isBooster) 2 else 1
        val widthCells = (if (isBooster) defaultSpan else appWidget.widthCells).coerceAtLeast(minSpan).coerceIn(1, columnCount)
        val heightCells = (if (isBooster) defaultSpan else appWidget.heightCells).coerceAtLeast(minSpan).coerceIn(1, rowCount - 1)

        val target = findAvailableCellRect(widthCells, heightCells)
        val targetRect = target.first
        val targetPage = target.second

        val type = if (appWidget.isShortcut) ITEM_TYPE_SHORTCUT else ITEM_TYPE_WIDGET
        val widgetItem = HomeScreenGridItem(
            id = null,
            left = targetRect.left,
            top = targetRect.top,
            right = targetRect.right,
            bottom = targetRect.bottom,
            page = targetPage,
            packageName = appWidget.appPackageName,
            activityName = "",
            title = appWidget.widgetTitle,
            type = type,
            className = appWidget.className,
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appWidget.widgetPreviewImage,
            providerInfo = appWidget.providerInfo,
            activityInfo = appWidget.activityInfo,
            widthCells = widthCells,
            heightCells = heightCells
        )

        if (appWidget.isShortcut) {
            val activity = context as? LauncherHomeActivity ?: return false
            val actInfo = appWidget.activityInfo ?: return false
            activity.handleShorcutCreation(actInfo) { shortcutId, label, icon ->
                widgetItem.shortcutId = shortcutId
                widgetItem.title = label
                widgetItem.icon = icon.toBitmap()
                ensureBackgroundThread {
                    val newId = context.homeScreenGridItemsDB.insert(widgetItem)
                    widgetItem.id = newId
                    post {
                        gridItems.add(widgetItem)
                        if (pager.getCurrentPage() != targetPage) {
                            pager.skipToPage(targetPage)
                        }
                        redrawGrid()
                    }
                }
            }
            return true
        } else {
            ensureBackgroundThread {
                val newId = context.homeScreenGridItemsDB.insert(widgetItem)
                widgetItem.id = newId
                post {
                    gridItems.add(widgetItem)
                    if (pager.getCurrentPage() != targetPage) {
                        pager.skipToPage(targetPage)
                    }
                    bindWidget(widgetItem)
                    redrawGrid()
                }
            }
            return true
        }
    }

    private fun findAvailableCellRect(widthCells: Int, heightCells: Int): Pair<Rect, Int> {
        val nonDocked = gridItems.filter { it.parentId == null && !it.docked }
        val maxPage = if (nonDocked.isEmpty()) 0 else (nonDocked.maxOfOrNull { it.page } ?: 0).coerceAtLeast(0)

        for (page in 0..maxPage) {
            val itemsOnPage = gridItems.filter { it.page == page && !it.docked && it.parentId == null && !it.outOfBounds() }
            val maxRow = (rowCount - 1) - heightCells
            val maxCol = columnCount - widthCells

            if (maxRow >= 0 && maxCol >= 0) {
                for (r in 0..maxRow) {
                    for (c in 0..maxCol) {
                        val candLeft = c
                        val candTop = r
                        val candRight = c + widthCells - 1
                        val candBottom = r + heightCells - 1

                        var collides = false
                        for (item in itemsOnPage) {
                            val itemTop = item.getDockAdjustedTop(rowCount)
                            val itemBottom = item.getDockAdjustedBottom(rowCount)
                            val overlaps = !(candRight < item.left || candLeft > item.right || candBottom < itemTop || candTop > itemBottom)
                            if (overlaps) {
                                collides = true
                                break
                            }
                        }
                        if (!collides) {
                            return Pair(Rect(candLeft, candTop, candRight, candBottom), page)
                        }
                    }
                }
            }
        }

        return Pair(Rect(0, 0, widthCells - 1, heightCells - 1), maxPage + 1)
    }

    private fun placeAppWidget(
        appWidgetProviderInfo: AppWidgetProviderInfo,
        item: HomeScreenGridItem,
    ) {
        // Remove old view if already present for this widgetId to avoid duplicate views
        val oldView = widgetViews.firstOrNull { it.tag == item.widgetId }
        if (oldView != null) {
            removeView(oldView)
            widgetViews.remove(oldView)
        }

        // we have to pass the base context here, else there will be errors with the themes
        val widgetView = appWidgetHost.createView(
            (context as LauncherHomeActivity).baseContext,
            item.widgetId,
            appWidgetProviderInfo
        ) as MyAppWidgetHostView
        widgetView.tag = item.widgetId
        widgetView.setAppWidget(item.widgetId, appWidgetProviderInfo)

        try {
            val updateIntent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = appWidgetProviderInfo.provider
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(item.widgetId))
            }
            context.sendBroadcast(updateIntent)
        } catch (_: Exception) {
        }
        widgetView.longPressListener = { x, y ->
            val activity = context as? LauncherHomeActivity
            if (activity?.isAllAppsFragmentExpanded() == false) {
                val currentItem = gridItems.firstOrNull {
                    (item.id != null && it.id == item.id) || (item.widgetId != -1 && it.widgetId == item.widgetId)
                } ?: item
                activity.showHomeIconMenu(x, widgetView.y, currentItem, false)
                performHapticFeedback()
            }
        }

        widgetView.dragListener = { event ->
            val activity = context as? LauncherHomeActivity
            val currentItem = gridItems.firstOrNull {
                (item.id != null && it.id == item.id) || (item.widgetId != -1 && it.widgetId == item.widgetId)
            } ?: item

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (widgetView.isHoldTriggered || draggedItem != null) {
                        val hasMoved = (abs(event.rawX - widgetView.actionDownCoords.x) > widgetView.moveGestureThreshold) ||
                            (abs(event.rawY - widgetView.actionDownCoords.y) > widgetView.moveGestureThreshold)

                        if (hasMoved || draggedItem != null) {
                            activity?.dismissActionPopupSilently()
                            if (draggedItem == null) {
                                itemDraggingStarted(currentItem)
                            }
                            draggedItemMoved(event.rawX.toInt(), event.rawY.toInt())
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggedItem != null) {
                        itemDraggingStopped()
                        activity?.resetLongPressedIcon()
                    }
                    widgetView.resetTouches()
                }
            }
        }

        widgetView.onIgnoreInterceptedListener = {
            hideResizeLines()
        }

        val widgetSize = updateWidgetPositionAndSize(widgetView, item)
        addView(widgetView, widgetSize.width, widgetSize.height)
        widgetViews.add(widgetView)

        // remove the drawable so that it gets refreshed on long press
        item.drawable = null
        // Delete existing widget if it has already been loaded to the list
        gridItems.removeIf { it.id == item.id }
        gridItems.add(item)
        (context as LauncherHomeActivity).clearWidgetsSearch()
    }

    private fun isTurboBoosterWidget(item: HomeScreenGridItem): Boolean {
        return item.className.endsWith("GameBoosterWidgetProvider")
    }

    private fun updateWidgetPosition(
        widgetView: AppWidgetHostView,
        item: HomeScreenGridItem,
    ) {
        val currentViewPosition = pager.getCurrentViewPositionInFullPageSpace() * width.toFloat()
        val widgetPos = calculateWidgetPos(item.getTopLeft(rowCount))
        widgetView.x = widgetPos.x + width * item.page - currentViewPosition
        widgetView.y = widgetPos.y.toFloat()
    }

    private fun updateWidgetPositionAndSize(
        widgetView: AppWidgetHostView,
        item: HomeScreenGridItem,
    ): Size {
        updateWidgetPosition(widgetView, item)
        val rawWidth = item.getWidthInCells() * cellWidth
        val rawHeight = item.getHeightInCells() * cellHeight

        val density = context.resources.displayMetrics.density
        val widgetDpWidth = (rawWidth / density).toInt()
        val widgetDpHeight = (rawHeight / density).toInt()

        if (isSPlus()) {
            val sizes = listOf(SizeF(widgetDpWidth.toFloat(), widgetDpHeight.toFloat()))
            widgetView.updateAppWidgetSize(Bundle(), sizes)
        } else {
            widgetView.updateAppWidgetSize(
                Bundle(),
                widgetDpWidth,
                widgetDpHeight,
                widgetDpWidth,
                widgetDpHeight
            )
        }

        widgetView.layoutParams?.width = rawWidth
        widgetView.layoutParams?.height = rawHeight
        widgetView.requestLayout()
        return Size(rawWidth, rawHeight)
    }

    private fun updateAllWidgetPositions() {
        if (widgetViews.isEmpty() || cells.isEmpty()) return
        for (i in 0 until widgetViews.size) {
            val widgetView = widgetViews.getOrNull(i) ?: continue
            val item = gridItems.firstOrNull { it.widgetId == widgetView.tag } ?: continue
            updateWidgetPositionAndSize(widgetView, item)
        }
    }

    private fun calculateWidgetPos(topLeft: Point): Point {
        return Point(
            topLeft.x * cellWidth + sideMargins.left,
            topLeft.y * cellHeight + sideMargins.top
        )
    }

    // convert stuff like 102x192 to grid cells like 0x1
    private fun getClosestGridCells(center: Point): Point? {
        return cells.entries.firstOrNull { (_, cell) -> center.x == cell.centerX() && center.y == cell.centerY() }?.key
    }

    private fun redrawGrid() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateAllWidgetPositions()
            binding.drawingArea.invalidate()
        } else {
            post {
                updateAllWidgetPositions()
                binding.drawingArea.invalidate()
            }
        }
    }

    private fun getFakeWidth() = width - sideMargins.left - sideMargins.right

    private fun getFakeHeight() = height - sideMargins.top - sideMargins.bottom

    fun drawInto(canvas: Canvas) {
        if (cells.isEmpty()) {
            fillCellSizes()
        }

        val currentXFactor = pager.getXFactorForCurrentPage()
        val lastXFactor = pager.getXFactorForLastPage()

        fun handleMainGridItemDrawing(item: HomeScreenGridItem, xFactor: Float) {
            val offsetX = sideMargins.left + (this@HomeScreenGrid.width * xFactor).toInt()
            val offsetY = sideMargins.top
            val cell = cells[item.getTopLeft(rowCount)] ?: return
            cell.withOffset(offsetX, offsetY) {
                canvas.drawItemInCell(item, this)
            }
        }

        val isAnimating = pager.isAnimatingPageChange()
        val isSwiped = pager.isSwiped()

        for (i in 0 until gridItems.size) {
            val item = gridItems.getOrNull(i) ?: continue
            if (!item.isSingleCellType() || item.outOfBounds() || item.parentId != null) continue

            if (item.docked) {
                handleMainGridItemDrawing(item, 0f)
            } else if (pager.isItemOnCurrentPage(item)) {
                handleMainGridItemDrawing(item, currentXFactor)
            } else if (isAnimating && pager.isItemOnLastPage(item)) {
                handleMainGridItemDrawing(item, lastXFactor)
            } else if (isSwiped && pager.isItemInSwipeRange(item)) {
                handleMainGridItemDrawing(item, lastXFactor)
            }
        }

        for (i in 0 until gridItems.size) {
            val item = gridItems.getOrNull(i) ?: continue
            if (item.type != ITEM_TYPE_WIDGET || item.outOfBounds()) continue
            if (draggedItem != null && ((draggedItem?.id != null && item.id == draggedItem?.id) || (draggedItem?.widgetId != null && draggedItem?.widgetId != -1 && item.widgetId == draggedItem?.widgetId))) continue

            val existingView = widgetViews.firstOrNull { it.tag == item.widgetId }
            if (existingView != null) {
                updateWidgetPosition(existingView, item)
                existingView.beVisible()
            } else {
                val providerInfo = item.providerInfo
                    ?: try {
                        appWidgetManager?.installedProviders
                            ?.firstOrNull { it.provider.className == item.className }
                    } catch (e: Exception) {
                        null
                    }

                if (providerInfo != null) {
                    placeAppWidget(providerInfo, item)
                }
            }
        }

        // Only draw page indicators when there is a need for it
        if (pager.shouldDisplayPageChangeIndicator()) {
            val pageCount = pager.getPageCount()
            val pageIndicatorsRequiredWidth =
                pageCount * pageIndicatorRadius * 2 + pageCount * (pageIndicatorMargin - 1)
            val usableWidth = getFakeWidth()
            val pageIndicatorsStart =
                (usableWidth - pageIndicatorsRequiredWidth) / 2 + sideMargins.left
            var currentPageIndicatorLeft = pageIndicatorsStart
            val pageIndicatorY = pageIndicatorsYPos.toFloat() + sideMargins.top + iconMargin
            val pageIndicatorStep = pageIndicatorRadius * 2 + pageIndicatorMargin
            emptyPageIndicatorPaint.alpha = pager.getPageChangeIndicatorsAlpha()
            // Draw empty page indicators
            for (page in 0 until pageCount) {
                canvas.drawCircle(
                    currentPageIndicatorLeft + pageIndicatorRadius,
                    pageIndicatorY,
                    pageIndicatorRadius,
                    emptyPageIndicatorPaint
                )
                currentPageIndicatorLeft += pageIndicatorStep
            }

            // Draw current page indicator on exact position
            val currentIndicatorPosition =
                pageIndicatorsStart + pager.getCurrentViewPositionInFullPageSpace() * pageIndicatorStep
            currentPageIndicatorPaint.alpha = pager.getPageChangeIndicatorsAlpha()
            canvas.drawCircle(
                currentIndicatorPosition + pageIndicatorRadius,
                pageIndicatorY,
                pageIndicatorRadius,
                currentPageIndicatorPaint
            )
        }

        val folder = currentlyOpenFolder
        if (folder != null && folder.getItems().isNotEmpty()) {
            val items = folder.getItems()
            val folderRect = folder.getDrawingRect()

            val currentViewPosition =
                pager.getCurrentViewPositionInFullPageSpace() * width.toFloat()
            val rectOffset = width * folder.item.page - currentViewPosition
            folderRect.offset(rectOffset, 0f)

            val cell = cells[folder.item.getTopLeft(rowCount)]
            val iconCenterX = sideMargins.left + (cell?.centerX()?.toFloat() ?: (width / 2f)) + rectOffset
            val iconCenterY = sideMargins.top + if (folder.item.docked) {
                (dockCellY + cellHeight / 2f)
            } else {
                (cell?.centerY()?.toFloat() ?: (height / 2f))
            }

            val dimAlpha = (120 * folder.scale).toInt().coerceIn(0, 255)
            canvas.drawARGB(dimAlpha, 0, 0, 0)

            canvas.withScale(
                x = folder.scale,
                y = folder.scale,
                pivotX = iconCenterX,
                pivotY = iconCenterY
            ) {
                val dialogCornerRadius = roundedCornerRadius.coerceAtLeast(36f)
                canvas.drawRoundRect(
                    folderRect,
                    dialogCornerRadius,
                    dialogCornerRadius,
                    folderBackgroundPaint
                )
                val textX = folderRect.left + folderPadding
                val textY = folderRect.top + folderPadding
                val staticLayout = StaticLayout.Builder
                    .obtain(
                        folder.item.title,
                        0,
                        folder.item.title.length,
                        folderTitleTextPaint,
                        (folderRect.width() - 2 * folderPadding).toInt().coerceAtLeast(1)
                    )
                    .setMaxLines(1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()

                withTranslation(textX, textY) {
                    staticLayout.draw(canvas)
                }

                items.forEach { item ->
                    val itemRect = folder.getItemRect(item)
                    canvas.drawItemInCell(item, itemRect)
                }
            }
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            if (draggedItem!!.isSingleCellType()) {
                if (folder != null && folder.getItemsDrawingRect().contains(
                        (draggedItemCurrentCoords.first).toFloat(),
                        (draggedItemCurrentCoords.second).toFloat()
                    )
                ) {

                    val center = folder.getItemsGridCenters().minBy {
                        abs(it.second - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.third - draggedItemCurrentCoords.second + sideMargins.top)
                    }
                    val cellSize = folder.getCellSize()

                    val shadowX = center.second - cellSize / 2 + iconMargin + iconSize / 2f
                    val shadowY = center.third - cellSize / 2 + iconMargin + iconSize / 2

                    canvas.drawCircle(
                        shadowX,
                        shadowY.toFloat(),
                        iconSize / 2f,
                        dragShadowCirclePaint
                    )
                } else {
                    // draw a circle under the current cell
                    val center = gridCenters.minBy {
                        abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = getClosestGridCells(center)
                    if (gridCells != null) {
                        cells[gridCells]?.let { cell ->
                            val shadowX = cell.left + iconMargin + iconSize / 2f + sideMargins.left
                            val shadowY = if (gridCells.y == rowCount - 1) {
                                cellHeight - iconMargin - iconSize / 2f
                            } else {
                                iconMargin + iconSize / 2f
                            } + sideMargins.top + cell.top

                            canvas.drawCircle(
                                shadowX, shadowY, iconSize / 2f, dragShadowCirclePaint
                            )
                        }
                    }
                }

                // show the app icon itself at dragging, move it above the finger a bit to make it visible
                val drawableX = (draggedItemCurrentCoords.first - iconSize / 1.5f).toInt()
                val drawableY = (draggedItemCurrentCoords.second - iconSize / 1.2f).toInt()
                val newDrawable = if (draggedItem?.type == ITEM_TYPE_FOLDER) {
                    draggedItem!!.toFolder().generateDrawable()
                } else {
                    draggedItem!!.drawable?.constantState?.newDrawable()?.mutate()
                }
                newDrawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + iconSize,
                    drawableY + iconSize
                )
                newDrawable?.draw(canvas)
            } else if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                // at first draw we are loading the widget from the database at some exact spot, not dragging it
                if (!isFirstDraw) {
                    val center = gridCenters.minBy {
                        abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = getClosestGridCells(center)
                    if (gridCells != null) {
                        val widgetRect = getWidgetOccupiedRect(gridCells)
                        val widgetPos = calculateWidgetPos(Point(widgetRect.left, widgetRect.top))
                        val leftSide = widgetPos.x.toFloat()
                        val topSide = widgetPos.y.toFloat()
                        val rightSide = leftSide + draggedItem!!.getWidthInCells() * cellWidth
                        val bottomSide = topSide + draggedItem!!.getHeightInCells() * cellHeight
                        canvas.drawRoundRect(
                            leftSide,
                            topSide,
                            rightSide,
                            bottomSide,
                            roundedCornerRadius,
                            roundedCornerRadius,
                            dragShadowCirclePaint
                        )
                    }

                    // show the widget preview itself at dragging
                    draggedItem!!.drawable?.also { drawable ->
                        val targetWidth = draggedItem!!.getWidthInCells() * cellWidth
                        val targetHeight = draggedItem!!.getHeightInCells() * cellHeight
                        val drawableX = (draggedItemCurrentCoords.first - targetWidth / 2f).toInt()
                        val drawableY = (draggedItemCurrentCoords.second - targetHeight / 2f).toInt()
                        drawable.setBounds(
                            drawableX,
                            drawableY,
                            drawableX + targetWidth,
                            drawableY + targetHeight
                        )
                        drawable.draw(canvas)
                    }
                }
            }
        }

        isFirstDraw = false
    }

    private fun fillCellSizes() {
        gridItems.forEach { it.staticLayout = null }
        cellWidth = getFakeWidth() / columnCount
        cellHeight = getFakeHeight() / rowCount
        val extraXMargin = if (cellWidth > cellHeight) {
            (cellWidth - cellHeight) / 2
        } else {
            0
        }
        val extraYMargin = if (cellHeight > cellWidth) {
            (cellHeight - cellWidth) / 2
        } else {
            0
        }
        iconSize = min(cellWidth, cellHeight) - 2 * iconMargin
        pageIndicatorsYPos = (rowCount - 1) * cellHeight + extraYMargin
        for (i in 0 until columnCount) {
            for (j in 0 until rowCount) {
                val yMarginToAdd = if (j == rowCount - 1) 0 else extraYMargin
                val rect = Rect(
                    i * cellWidth + extraXMargin,
                    j * cellHeight + yMarginToAdd,
                    (i + 1) * cellWidth - extraXMargin,
                    (j + 1) * cellHeight - yMarginToAdd,
                )
                cells[Point(i, j)] = rect
                gridCenters.add(Point(rect.centerX(), rect.centerY()))
                if (j == rowCount - 1) {
                    dockCellY = j * cellHeight
                }
            }
        }
    }

    fun fragmentExpanded() {
        widgetViews.forEach {
            it.ignoreTouches = true
        }
        closeFolder(true)
    }

    fun fragmentCollapsed() {
        widgetViews.forEach {
            it.ignoreTouches = false
        }
    }

    // get the clickable area around the icon, it includes text too
    fun getClickableRect(item: HomeScreenGridItem): Rect {
        if (cells.isEmpty()) {
            fillCellSizes()
        }

        val folder = currentlyOpenFolder
        val clickableLeft: Int
        val clickableTop: Int
        if (folder != null && item.parentId == folder.item.id) {
            val itemRect = folder.getItemRect(item)
            clickableLeft = itemRect.left
            clickableTop = itemRect.top - iconMargin
        } else {
            val cell = cells[item.getTopLeft(rowCount)] ?: return Rect(0, 0, 0, 0)
            clickableLeft = cell.left + sideMargins.left
            clickableTop = if (item.docked) {
                dockCellY + cellHeight - iconSize - iconMargin
            } else {
                cell.top - iconMargin
            } + sideMargins.top
        }
        val additionalHeight = if (!item.docked && context.config.showHomeAppLabels) {
            // multiply line count by line height to get label height
            // we multiply all line heights by 2 so all widgets get the same clickable area and 2 is the max line count
            (2 * (textPaint.fontMetrics.bottom - textPaint.fontMetrics.top)).toInt()
        } else 0
        return Rect(
            clickableLeft,
            clickableTop,
            clickableLeft + iconSize + 2 * iconMargin,
            clickableTop + iconSize + 2 * iconMargin + additionalHeight
        )
    }

    // drag the center of the widget, not the top left corner
    private fun getWidgetOccupiedRect(item: Point): Rect {
        val left = item.x - floor((draggedItem!!.getWidthInCells() - 1) / 2.0).toInt()
        val rect = Rect(
            left,
            item.y,
            left + draggedItem!!.getWidthInCells() - 1,
            item.y + draggedItem!!.getHeightInCells() - 1
        )
        if (rect.left < 0) {
            rect.right -= rect.left
            rect.left = 0
        } else if (rect.right > columnCount - 1) {
            val diff = rect.right - columnCount + 1
            rect.right -= diff
            rect.left -= diff
        }

        if (rect.top < 0) {
            rect.bottom -= rect.top
            rect.top = 0
        } else if (rect.bottom > rowCount - 2) {
            val diff = rect.bottom - rowCount + 2
            rect.bottom -= diff
            rect.top -= diff
        }

        return rect
    }

    fun isClickingGridItem(x: Int, y: Int): HomeScreenGridItem? {
        if (pager.isAnimatingPageChange() || pager.isSwiped()) {
            return null
        }

        currentlyOpenFolder?.also { folder ->
            folder.getItems().forEach { gridItem ->
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            }
        }

        // if a folder is open, we only want to allow clicks on items in the folder
        if (currentlyOpenFolder != null) {
            return null
        }

        for (gridItem in gridItems.filterVisibleOnCurrentPageOnly()) {
            if (gridItem.outOfBounds()) {
                continue
            }

            if (gridItem.isSingleCellType()) {
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            } else if (gridItem.type == ITEM_TYPE_WIDGET) {
                val widgetPos = calculateWidgetPos(gridItem.getTopLeft(rowCount))
                val left = widgetPos.x.toFloat()
                val top = widgetPos.y.toFloat()
                val right = left + gridItem.getWidthInCells() * cellWidth
                val bottom = top + gridItem.getHeightInCells() * cellHeight

                if (x >= left && x <= right && y >= top && y <= bottom) {
                    return gridItem
                }
            }
        }

        return null
    }

    fun intoViewSpaceCoords(screenSpaceX: Float, screenSpaceY: Float): Pair<Float, Float> {
        val viewLocation = IntArray(2)
        getLocationOnScreen(viewLocation)
        val x = screenSpaceX - viewLocation[0]
        val y = screenSpaceY - viewLocation[1]
        return Pair(x, y)
    }

    private fun HomeScreenGridItem.outOfBounds(): Boolean {
        return (left >= columnCount
                || right >= columnCount
                || (!docked && (top >= rowCount - 1 || bottom >= rowCount - 1))
                || (type == ITEM_TYPE_WIDGET && (bottom - top > rowCount - 1 || right - left > columnCount - 1))
                )
    }

    private inner class HomeScreenGridTouchHelper(host: View) : ExploreByTouchHelper(host) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val item = isClickingGridItem(x.toInt(), y.toInt())

            return if (item != null) {
                item.id?.toInt() ?: INVALID_ID
            } else {
                INVALID_ID
            }
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>?) {
            val sorted =
                gridItems
                    .filter {
                        it.visibleOnCurrentPage()
                                || (
                                currentlyOpenFolder != null
                                        && it.parentId == currentlyOpenFolder?.item?.id
                                )
                    }
                    .sortedBy {
                        (if (it.parentId == null) {
                            it.getDockAdjustedTop(rowCount)
                        } else {
                            1
                        }) * 100 + it.left
                    }
            sorted.forEachIndexed { index, homeScreenGridItem ->
                virtualViewIds?.add(index, homeScreenGridItem.id?.toInt() ?: index)
            }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            val viewLocation = IntArray(2)
            getLocationOnScreen(viewLocation)

            // home screen
            if (virtualViewId == -1) {
                node.text = context.getString(R.string.app_name)
                val viewBounds = Rect(left, top, right, bottom)
                val onScreenBounds = Rect(viewBounds)
                onScreenBounds.offset(viewLocation[0], viewLocation[1])
                node.setBoundsInScreen(onScreenBounds)
                node.setBoundsInParent(viewBounds)

                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
                node.setParent(this@HomeScreenGrid)
            }

            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId }
                ?: throw IllegalArgumentException("Unknown id")

            node.text = if (item.type == ITEM_TYPE_WIDGET) {
                item.providerInfo?.loadLabel(context.packageManager) ?: item.title
            } else {
                item.title
            }

            val viewBounds = if (item == currentlyOpenFolder?.item) {
                currentlyOpenFolder?.getDrawingRect()?.toRect()
            } else if (item.type == ITEM_TYPE_WIDGET) {
                val widgetPos = calculateWidgetPos(item.getTopLeft(rowCount))
                val left = widgetPos.x
                val top = widgetPos.y
                val right = left + item.getWidthInCells() * cellWidth
                val bottom = top + item.getHeightInCells() * cellHeight

                Rect(left, top, right, bottom)
            } else {
                getClickableRect(item)
            }
            val onScreenBounds = Rect(viewBounds)
            onScreenBounds.offset(viewLocation[0], viewLocation[1])
            node.setBoundsInScreen(onScreenBounds)
            node.setBoundsInParent(viewBounds)

            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            node.setParent(this@HomeScreenGrid)
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId }
                ?: throw IllegalArgumentException("Unknown id")
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> itemClickListener?.apply {
                    if (item == currentlyOpenFolder?.item) {
                        closeFolder(true)
                    } else {
                        invoke(item)
                    }
                    return true
                }

                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> itemLongClickListener?.apply {
                    invoke(item)
                    return true
                }
            }

            return false
        }
    }

    private fun deletePage(page: Int) {
        gridItems.filter { it.page > page }.forEach {
            it.page -= 1
        }
        context.homeScreenGridItemsDB.shiftPage(page, -1)

        if (pager.isOutsideOfPageRange()) {
            post {
                prevPage()
            }
        }
    }

    fun getMaxPage(): Int =
        gridItems.filter { !it.docked && !it.outOfBounds() }.maxOfOrNull { it.page } ?: 0

    fun nextPage(redraw: Boolean = false): Boolean {
        return pager.nextPage(redraw)
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        return pager.prevPage(redraw)
    }

    fun skipToPage(targetPage: Int): Boolean {
        return pager.skipToPage(targetPage)
    }

    fun getCurrentIconSize(): Int = iconSize

    /** Returns the index of the currently visible home screen page (0-based). */
    fun getCurrentPage(): Int = pager.getCurrentPage()


    fun setSwipeMovement(diffX: Float) {
        if (draggedItem == null) {
            pager.setSwipeMovement(diffX)
        }
    }

    fun finalizeSwipe(velocity: Float = 0f) {
        val flingThreshold = 400f * context.resources.displayMetrics.density
        pager.finalizeSwipe(velocity, flingThreshold)
    }

    fun openFolder(folder: HomeScreenGridItem) {
        if (currentlyOpenFolder == null) {
            currentlyOpenFolder = folder.toFolder(animateOpening = true)
            redrawGrid()
        } else if (currentlyOpenFolder?.item?.id != folder.id) {
            closeFolder()
        }
        accessibilityHelper.invalidateRoot()
    }

    fun closeFolder(redraw: Boolean = false) {
        val folderItem = currentlyOpenFolder?.item
        folderItem?.drawable = null
        currentlyOpenFolder?.animateClosing {
            folderItem?.drawable = null
            currentlyOpenFolder = null
            if (redraw) {
                redrawGrid()
            }
            accessibilityHelper.invalidateRoot()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.actionMasked == MotionEvent.ACTION_DOWN && resizedWidget != null) {
            val rx = binding.resizeFrame.x
            val ry = binding.resizeFrame.y
            val rw = binding.resizeFrame.width
            val rh = binding.resizeFrame.height
            if (ev.x < rx || ev.x > rx + rw || ev.y < ry || ev.y > ry + rh) {
                hideResizeLines()
            }
        }
        return if (currentlyOpenFolder != null) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    private fun Canvas.drawItemInCell(item: HomeScreenGridItem, cell: Rect) {
        if (item.id != draggedItem?.id) {
            val drawableX = cell.left + iconMargin

            val drawable = if (item.type == ITEM_TYPE_FOLDER) {
                item.drawable ?: item.toFolder().generateDrawable()?.also { item.drawable = it }
            } else {
                item.drawable ?: context.getDrawableForPackageName(item.packageName)?.also { item.drawable = it }
            }

            if (item.docked) {
                val drawableY = dockCellY + cellHeight - iconMargin - iconSize + sideMargins.top

                drawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + iconSize,
                    drawableY + iconSize
                )
            } else {
                val drawableY = cell.top + iconMargin
                drawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + iconSize,
                    drawableY + iconSize
                )

                if (item.id != draggedItem?.id && item.title.isNotEmpty() && context.config.showHomeAppLabels) {
                    val textX = cell.left.toFloat() + labelSideMargin
                    val textY = cell.top.toFloat() + iconSize + iconMargin + labelSideMargin
                    val maxTextWidth = cellWidth - 2 * labelSideMargin
                    if (maxTextWidth > 0) {
                        val textPaintToUse = if (item.parentId == null) {
                            textPaint
                        } else {
                            contrastTextPaint
                        }
                        val staticLayout = item.staticLayout?.takeIf { it.width == maxTextWidth && it.paint.textSize == textPaintToUse.textSize } ?: run {
                            StaticLayout.Builder
                                .obtain(
                                    item.title,
                                    0,
                                    item.title.length,
                                    textPaintToUse,
                                    maxTextWidth
                                )
                                .setMaxLines(2)
                                .setEllipsize(TextUtils.TruncateAt.END)
                                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                                .build()
                                .also { item.staticLayout = it }
                        }

                        withTranslation(textX, textY) {
                            staticLayout.draw(this)
                        }
                    }
                }
            }

            drawable?.draw(this)
        }
    }

    private fun Rect.withOffset(offsetX: Int, offsetY: Int, block: Rect.() -> Unit) {
        offset(offsetX, offsetY)
        try {
            block()
        } finally {
            offset(-offsetX, -offsetY)
        }
    }

    private fun ArrayList<HomeScreenGridItem>.filterVisibleOnCurrentPageOnly() =
        filter { it.visibleOnCurrentPage() }

    private fun HomeScreenGridItem.visibleOnCurrentPage() =
        (pager.isItemOnCurrentPage(this) || docked) && parentId == null

    private fun HomeScreenGridItem.isSingleCellType() =
        (type == ITEM_TYPE_ICON || type == ITEM_TYPE_SHORTCUT || type == ITEM_TYPE_FOLDER)

    private fun HomeScreenGridItem.toFolder(animateOpening: Boolean = false) =
        HomeScreenFolder(this, animateOpening)

    private inner class HomeScreenFolder(
        val item: HomeScreenGridItem,
        animateOpening: Boolean,
    ) {
        var scale: Float = 1f
        private var closing = false

        init {
            if (animateOpening) {
                scale = 0f
                post {
                    ValueAnimator.ofFloat(0f, 1f)
                        .apply {
                            interpolator = DecelerateInterpolator()
                            addUpdateListener {
                                scale = it.animatedValue as Float
                                redrawGrid()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    super.onAnimationEnd(animation)
                                    scale = 1f
                                    redrawGrid()
                                }
                            })
                            duration = FOLDER_ANIMATION_DURATION
                            start()
                        }
                }
            }
        }

        fun getItems(): List<HomeScreenGridItem> {
            return gridItems
                .toList()
                .filter { it.isSingleCellType() && it.parentId == item.id }
                .sortedBy { it.left }
        }

        fun generateDrawable(): Drawable? {
            if (iconSize == 0) {
                return null
            }

            val items = getItems()
            if (items.isEmpty()) {
                return null
            }

            val bitmap = createBitmap(iconSize, iconSize)
            val canvas = Canvas(bitmap)

            val cornerRadius = roundedCornerRadius.coerceAtLeast(iconSize * 0.22f)
            val bgRect = RectF(0f, 0f, iconSize.toFloat(), iconSize.toFloat())
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, folderIconBackgroundPaint)

            val maxPreviewCount = 9
            val previewItems = items.take(maxPreviewCount)
            val padding = iconSize * 0.12f
            val availableSize = iconSize - (2 * padding)
            val gap = iconSize * 0.04f
            val miniIconSize = (availableSize - 2 * gap) / 3f

            previewItems.forEachIndexed { index, gridItem ->
                val col = index % 3
                val row = index / 3
                val iconX = (padding + col * (miniIconSize + gap)).toInt()
                val iconY = (padding + row * (miniIconSize + gap)).toInt()
                val miniIconEnd = miniIconSize.toInt()

                val miniDrawable = gridItem.drawable?.constantState?.newDrawable(resources)?.mutate()
                    ?: (if (gridItem.icon != null) gridItem.icon?.toDrawable(resources) else context.getDrawableForPackageName(gridItem.packageName))

                miniDrawable?.setBounds(
                    iconX,
                    iconY,
                    iconX + miniIconEnd,
                    iconY + miniIconEnd
                )
                miniDrawable?.draw(canvas)
            }

            return bitmap.toDrawable(resources)
        }

        fun getDrawingRect(): RectF {
            val count = getItems().count()
            if (count == 0) {
                return RectF(0f, 0f, 0f, 0f)
            }
            val columnsCount = 3
            val rowsCount = ceil(count.toFloat() / columnsCount).toInt().coerceAtLeast(1)
            val cellSize = getCellSize()
            val gap = getGapSize()
            val yGap = if (context.config.showHomeAppLabels) {
                gap + textPaint.textSize + 2 * labelSideMargin
            } else gap

            val cell = cells[item.getTopLeft(rowCount)]
            val iconCenterY = sideMargins.top + if (item.docked) {
                (dockCellY + cellHeight / 2f)
            } else {
                (cell?.centerY()?.toFloat() ?: (height / 2f))
            }

            val maxDialogWidth = width - sideMargins.left - sideMargins.right - 32f
            val folderDialogWidth = min(
                maxDialogWidth,
                columnsCount * cellSize + 2 * folderPadding + (columnsCount - 1) * gap
            )
            val folderDialogHeight =
                rowsCount * cellSize + 3 * folderPadding + folderTitleTextPaint.textSize + rowsCount * yGap

            val folderDialogLeft = (width - folderDialogWidth) / 2f

            val isIconNearBottom = iconCenterY > height * 0.5f
            var folderDialogTop = if (isIconNearBottom) {
                iconCenterY - cellHeight / 2f - folderDialogHeight - 24f
            } else {
                iconCenterY + cellHeight / 2f + 24f
            }

            val minTop = sideMargins.top.toFloat() + 32f
            val maxTop = height - sideMargins.bottom.toFloat() - folderDialogHeight - 32f
            folderDialogTop = folderDialogTop.coerceIn(minTop, maxTop.coerceAtLeast(minTop))

            return RectF(
                folderDialogLeft,
                folderDialogTop,
                folderDialogLeft + folderDialogWidth,
                folderDialogTop + folderDialogHeight
            )
        }

        fun getItemsDrawingRect(): RectF {
            val folderRect = getDrawingRect()
            return RectF(
                folderRect.left + folderPadding,
                folderRect.top + folderPadding * 2 + folderTitleTextPaint.textSize,
                folderRect.right - folderPadding,
                folderRect.bottom - folderPadding
            )
        }

        fun getItemsGridCenters(): List<Triple<Int, Int, Int>> {
            val count = getItems().count()
            if (count == 0) {
                return emptyList()
            }

            val columnsCount = 3
            val rowsCount = ceil(count.toFloat() / columnsCount).toInt().coerceAtLeast(1)
            val folderItemsRect = getItemsDrawingRect()
            val cellSize = getCellSize()
            val gap = getGapSize()
            val yGap = if (context.config.showHomeAppLabels) {
                gap + textPaint.textSize + 2 * labelSideMargin
            } else gap
            return (0 until columnsCount * rowsCount)
                .toList()
                .map { Pair(it % columnsCount, it / columnsCount) }
                .mapIndexed { index, (x, y) ->
                    Triple(
                        index,
                        (folderItemsRect.left + x * cellSize + x * gap + cellSize / 2).toInt(),
                        (folderItemsRect.top + y * cellSize + y * yGap + cellSize / 2).toInt()
                    )
                }
        }

        private fun getItemPosition(item: HomeScreenGridItem): Pair<Int, Int> {
            val columnsCount = 3
            val column = item.left % columnsCount
            val row = item.left / columnsCount
            return Pair(row, column)
        }

        fun getItemRect(item: HomeScreenGridItem): Rect {
            val (row, column) = getItemPosition(item)
            val itemsRect = getItemsDrawingRect()
            val cellSize = getCellSize()
            val gapSize = getGapSize()
            val yGapSize = if (context.config.showHomeAppLabels) {
                gapSize + textPaint.textSize + 2 * labelSideMargin
            } else gapSize
            val left = (itemsRect.left + column * cellSize + column * gapSize).roundToInt()
            val top = (itemsRect.top + row * cellSize + row * yGapSize).roundToInt()
            return Rect(
                left,
                top,
                left + cellSize,
                top + cellSize
            )
        }

        fun animateClosing(callback: () -> Unit) {
            post {
                if (closing) {
                    return@post
                }
                closing = true
                ValueAnimator.ofFloat(scale, 0f)
                    .apply {
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            scale = it.animatedValue as Float
                            redrawGrid()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                scale = 0f
                                callback()
                            }
                        })
                        duration = (FOLDER_ANIMATION_DURATION * scale).toLong().coerceAtLeast(50L)
                        start()
                    }
            }
        }

        fun getCellSize(): Int = min(cellWidth, cellHeight)

        private fun getGapSize(): Float {
            val cellSize = getCellSize()
            return if (cellSize == cellWidth) {
                0f
            } else {
                cellSize / 5f
            }
        }
    }

    companion object {
        private const val FOLDER_OPEN_HOLD_THRESHOLD = 500L
        private const val FOLDER_CLOSE_HOLD_THRESHOLD = 300L
        private const val FOLDER_ANIMATION_DURATION = 200L
    }
}

/**
 * Helper class responsible for managing current page and providing utilities for animating page changes,
 * as well as partial dragigng between pages
 */
private class AnimatedGridPager(
    private val getMaxPage: () -> Int,
    private val redrawGrid: () -> Unit,
    private val getWidth: () -> Int,
    private val getHandler: () -> Handler,
    private val getNextPageBound: () -> Int,
    private val getPrevPageBound: () -> Int,
    private val pageChangeStarted: () -> Unit,
) {

    companion object {
        private const val PAGE_CHANGE_HOLD_THRESHOLD = 350L
        private const val PAGE_INDICATORS_FADE_DELAY = PAGE_CHANGE_HOLD_THRESHOLD + 300L

        private enum class PageChangeArea {
            LEFT,
            MIDDLE,
            RIGHT
        }
    }

    private var lastPage = 0
    private var currentPage = 0
    private var pageChangeLastArea = PageChangeArea.MIDDLE
    private var pageChangeLastAreaEntryTime = 0L
    private var pageChangeAnimLeftPercentage = 0f
    private var pageChangeEnabled = true
    private var pageChangeIndicatorsAlpha = 0f
    private var pageChangeSwipedPercentage = 0f
    private var isSwiping = false

    fun getCurrentPage() = currentPage

    fun isItemOnCurrentPage(item: HomeScreenGridItem) = item.page == currentPage

    fun isItemOnLastPage(item: HomeScreenGridItem) = item.page == lastPage

    fun getPageCount() = max(getMaxPage(), currentPage) + 1


    fun isOutsideOfPageRange() = currentPage > getMaxPage()

    fun isItemInSwipeRange(item: HomeScreenGridItem) =
        ((pageChangeSwipedPercentage > 0f && item.page == currentPage - 1) || (pageChangeSwipedPercentage < 0f && item.page == currentPage + 1))

    fun isSwiped() = abs(pageChangeSwipedPercentage) > 0f

    fun isAnimatingPageChange() = pageChangeAnimLeftPercentage != 0f

    fun shouldDisplayPageChangeIndicator() =
        isSwiped() || isAnimatingPageChange() || pageChangeIndicatorsAlpha != 0f

    fun getPageChangeIndicatorsAlpha() = if (pageChangeIndicatorsAlpha != 0f) {
        (pageChangeIndicatorsAlpha * 255.0f).toInt()
    } else {
        255
    }

    fun getXFactorForCurrentPage(): Float {
        return if (abs(pageChangeSwipedPercentage) > 0f) {
            pageChangeSwipedPercentage
        } else {
            if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage
            } else {
                -pageChangeAnimLeftPercentage
            }
        }
    }

    fun getXFactorForLastPage(): Float {
        return if (abs(pageChangeSwipedPercentage) > 0f) {
            (1 - abs(pageChangeSwipedPercentage)) * -sign(pageChangeSwipedPercentage)
        } else {
            if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage - 1
            } else {
                1 - pageChangeAnimLeftPercentage
            }
        }
    }

    fun getCurrentViewPositionInFullPageSpace(): Float {
        val rangeStart = lastPage.toFloat()
        val rangeEndPage = if (abs(pageChangeSwipedPercentage) > 0f) {
            if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
        } else {
            currentPage
        }
        val rangeEnd = rangeEndPage.toFloat()
        val lerpAmount = if (pageChangeAnimLeftPercentage != 0f) {
            1 - pageChangeAnimLeftPercentage
        } else {
            abs(pageChangeSwipedPercentage)
        }
        return MathUtils.lerp(rangeStart, rangeEnd, lerpAmount)
    }

    fun setSwipeMovement(diffX: Float) {
        if (!pageChangeEnabled) {
            return
        }

        if (currentPage < getMaxPage() && diffX > 0f || currentPage > 0 && diffX < 0f) {
            pageChangeSwipedPercentage = (-diffX / getWidth().toFloat()).coerceIn(-1f, 1f)
            if (!isSwiping) {
                isSwiping = true
                pageChangeStarted()
            }
            redrawGrid()
        }
    }

    fun finalizeSwipe(velocity: Float = 0f, flingThreshold: Float = 0f) {
        isSwiping = false
        if (abs(pageChangeSwipedPercentage) == 0f) {
            return
        }

        val swipedDist = abs(pageChangeSwipedPercentage)
        val isFlingNext = flingThreshold > 0f && velocity < -flingThreshold
        val isFlingPrev = flingThreshold > 0f && velocity > flingThreshold

        val shouldChangePage = when {
            pageChangeSwipedPercentage < 0f -> {
                (isFlingNext || swipedDist > 0.25f) && !isFlingPrev && currentPage < getMaxPage()
            }
            pageChangeSwipedPercentage > 0f -> {
                (isFlingPrev || swipedDist > 0.25f) && !isFlingNext && currentPage > 0
            }
            else -> false
        }

        if (shouldChangePage) {
            lastPage = currentPage
            currentPage = if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
            handlePageChange(true)
        } else {
            lastPage = if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
            pageChangeSwipedPercentage =
                sign(pageChangeSwipedPercentage) * (1 - abs(pageChangeSwipedPercentage))
            handlePageChange(true)
        }
    }

    fun handleItemMovement(x: Int, y: Int) {
        showIndicators()
        if (x > getNextPageBound()) {
            doWithPageChangeDelay(PageChangeArea.RIGHT) {
                nextOrAdditionalPage()
            }
        } else if (x < getPrevPageBound()) {
            doWithPageChangeDelay(PageChangeArea.LEFT) {
                prevPage()
            }
        } else {
            clearPageChangeFlags()
        }
    }

    fun itemMovementStopped() {
        scheduleIndicatorsFade()
    }

    fun nextPage(redraw: Boolean = false): Boolean {
        if (currentPage < getMaxPage() && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        if (currentPage > 0 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage--
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun skipToPage(targetPage: Int): Boolean {
        if (currentPage != targetPage && targetPage < getMaxPage() + 1) {
            lastPage = currentPage
            currentPage = targetPage
            handlePageChange()
            return true
        }

        return false
    }

    private val checkAndExecuteDelayedPageChange: Runnable = Runnable {
        if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            when (pageChangeLastArea) {
                PageChangeArea.RIGHT -> nextOrAdditionalPage(true)
                PageChangeArea.LEFT -> prevPage(true)
                else -> clearPageChangeFlags()
            }
        }
    }

    private val startFadingIndicators: Runnable = Runnable {
        ValueAnimator.ofFloat(1f, 0f)
            .apply {
                addUpdateListener {
                    pageChangeIndicatorsAlpha = it.animatedValue as Float
                    redrawGrid()
                }
                start()
            }
    }

    private fun showIndicators() {
        pageChangeIndicatorsAlpha = 1f
        getHandler().removeCallbacks(startFadingIndicators)
    }

    private fun clearPageChangeFlags() {
        pageChangeLastArea = PageChangeArea.MIDDLE
        pageChangeLastAreaEntryTime = 0
        getHandler().removeCallbacks(checkAndExecuteDelayedPageChange)
    }

    private fun schedulePageChange() {
        pageChangeLastAreaEntryTime = System.currentTimeMillis()
        getHandler().postDelayed(checkAndExecuteDelayedPageChange, PAGE_CHANGE_HOLD_THRESHOLD)
    }

    private fun scheduleIndicatorsFade() {
        pageChangeIndicatorsAlpha = 1f
        getHandler().postDelayed(startFadingIndicators, PAGE_INDICATORS_FADE_DELAY)
    }

    private fun doWithPageChangeDelay(needed: PageChangeArea, pageChangeFunction: () -> Boolean) {
        if (pageChangeLastArea != needed) {
            pageChangeLastArea = needed
            schedulePageChange()
        } else if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            if (pageChangeFunction()) {
                clearPageChangeFlags()
            }
        }
    }

    private fun nextOrAdditionalPage(redraw: Boolean = false): Boolean {
        if (currentPage < getMaxPage() + 1 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            handlePageChange(redraw)
            return true
        }

        return false
    }

    private fun handlePageChange(redraw: Boolean = false) {
        pageChangeEnabled = false
        pageChangeIndicatorsAlpha = 0f
        val startingAt = 1 - abs(pageChangeSwipedPercentage)
        pageChangeSwipedPercentage = 0f
        getHandler().removeCallbacks(startFadingIndicators)
        pageChangeStarted()
        if (redraw) {
            redrawGrid()
        }
        val animDuration = (startingAt * 260f).toLong().coerceIn(140L, 260L)
        ValueAnimator.ofFloat(startingAt, 0f)
            .apply {
                interpolator = DecelerateInterpolator(1.5f)
                duration = animDuration
                addUpdateListener {
                    pageChangeAnimLeftPercentage = it.animatedValue as Float
                    redrawGrid()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        pageChangeAnimLeftPercentage = 0f
                        pageChangeEnabled = true
                        lastPage = currentPage
                        clearPageChangeFlags()
                        scheduleIndicatorsFade()
                        redrawGrid()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        super.onAnimationCancel(animation)
                        pageChangeAnimLeftPercentage = 0f
                        pageChangeEnabled = true
                        lastPage = currentPage
                        clearPageChangeFlags()
                        scheduleIndicatorsFade()
                        redrawGrid()
                    }
                })
                start()
            }
    }
}
