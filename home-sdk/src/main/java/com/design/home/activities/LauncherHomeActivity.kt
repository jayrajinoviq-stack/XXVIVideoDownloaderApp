package com.design.home.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.RectF
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.viewbinding.ViewBinding
import kotlinx.collections.immutable.toImmutableList
import com.design.commons.R as CommonR
import com.design.home.R
import com.design.commons.extensions.appLaunched
import com.design.commons.extensions.beVisible
import com.design.commons.extensions.getContrastColor
import com.design.commons.extensions.getPopupMenuTheme
import com.design.commons.extensions.getProperBackgroundColor
import com.design.commons.extensions.insetsController
import com.design.commons.extensions.isPackageInstalled
import com.design.commons.extensions.onGlobalLayout
import com.design.commons.extensions.performHapticFeedback
import com.design.commons.extensions.realScreenSize
import com.design.commons.extensions.showErrorToast
import com.design.commons.extensions.showKeyboard
import com.design.commons.extensions.toast
import com.design.commons.extensions.viewBinding
import com.design.commons.helpers.DARK_GREY
import com.design.commons.helpers.ensureBackgroundThread
import com.design.commons.helpers.isOreoMr1Plus
import com.design.commons.helpers.isQPlus
import com.design.home.BuildConfig
import com.design.home.databinding.ActivityLauncherHomeBinding
import com.design.home.databinding.AllAppsFragmentBinding
import com.design.home.databinding.WidgetsFragmentBinding
import com.design.home.dialogs.RenameItemDialog
import com.design.home.extensions.config
import com.design.home.extensions.canAppBeUninstalled
import com.design.home.extensions.getDrawableForPackageName
import com.design.home.extensions.getLabel
import com.design.home.extensions.handleGridItemPopupMenu
import com.design.home.extensions.hiddenIconsDB
import com.design.home.extensions.homeScreenGridItemsDB
import com.design.home.extensions.isDefaultLauncher
import com.design.home.extensions.launchApp
import com.design.home.extensions.launchAppInfo
import com.design.home.extensions.launchersDB
import com.design.home.extensions.roleManager
import com.design.home.extensions.supportsDarkText
import com.design.home.extensions.uninstallApp
import com.design.home.fragments.MyFragment
import com.design.home.helpers.ITEM_TYPE_FOLDER
import com.design.home.helpers.ITEM_TYPE_ICON
import com.design.home.helpers.ITEM_TYPE_SHORTCUT
import com.design.home.helpers.ITEM_TYPE_WIDGET
import com.design.home.helpers.IconCache
import com.design.home.helpers.RecentAppsManager
import com.design.home.helpers.REQUEST_ALLOW_BINDING_WIDGET
import com.design.home.helpers.REQUEST_CONFIGURE_WIDGET
import com.design.home.helpers.REQUEST_CREATE_SHORTCUT
import com.design.home.helpers.REQUEST_SET_DEFAULT
import com.design.home.views.LauncherActionPopup
import com.design.home.views.PopupActionItem
import com.design.home.helpers.UNINSTALL_APP_REQUEST_CODE
import com.design.home.interfaces.FlingListener
import com.design.home.interfaces.ItemMenuListener
import com.design.home.helpers.LeftPanelRegistry
import com.design.home.widgets.SmartTopWidgetProvider
import com.design.home.helpers.WeatherManager
import com.design.home.models.AppLauncher
import com.design.home.models.HiddenIcon
import com.design.home.models.HomeScreenGridItem
import com.design.home.receivers.LockDeviceAdminReceiver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LauncherHomeActivity : SimpleActivity(), FlingListener {
    private var mTouchDownX = -1
    private var mTouchDownY = -1
    private var mAllAppsFragmentY = 0
    private var mWidgetsFragmentY = 0
    private var mScreenHeight = 0
    private var mMoveGestureThreshold = 0
    private var mIgnoreUpEvent = false
    private var mIgnoreMoveEvents = false
    //    private var mIgnoreXMoveEvents = false
//    private var mIgnoreYMoveEvents = false
//    private var mLongPressedIcon: HomeScreenGridItem? = null

    private var panelTouchDownX = 0f
    private var panelStartX = 0f
    private var mSidePanelWasDragged = false
    private var mSidePanelDragActive = false
    private var mSidePanelTouchDownX = 0f
    private var mSidePanelTouchDownY = 0f
    private var mSidePanelInitialX = 0f
    private var mSidePanelVelocityTracker: VelocityTracker? = null
    private var mPanelWasExpandedOnDown = false
    private var mLastSidePanelRecentIds: List<String> = emptyList()
    private var mSidePanelDataReady = false
    private var mIgnoreXMoveEvents = false
    private var mIgnoreYMoveEvents = false
    private var mScreenWidth = 0
    private var mSidePanelX = 0
    private var mVelocityTracker: VelocityTracker? = null
    private var mLongPressedIcon: HomeScreenGridItem? = null
    private var mLeftPanelSwipeActive = false
    private val mSidePanelIconApps = mutableMapOf<android.widget.ImageView, AppLauncher>()

    private var mOpenPopupMenu: PopupMenu? = null
    private var mOpenActionPopup: LauncherActionPopup? = null
    private var mLastTouchCoords = Pair(-1f, -1f)
    private var mActionOnCanBindWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnWidgetConfiguredWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnAddShortcut:
        ((shortcutId: String, label: String, icon: Drawable) -> Unit)? = null
    private var wasJustPaused: Boolean = false

    private var wallpaperColorChangeListener: WallpaperManager.OnColorsChangedListener? = null
    private var wallpaperSupportsDarkText: Boolean? = null

    private lateinit var mDetector: GestureDetectorCompat
    private val binding by viewBinding(ActivityLauncherHomeBinding::inflate)

    companion object {
        private var mLastUpEvent = 0L
        private const val ANIMATION_DURATION = 150L
        private const val APP_DRAWER_CLOSE_DELAY = 300L
        private const val APP_DRAWER_STATE = "app_drawer_state"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(applicationContext.packageName)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.allAppsFragment.root, binding.widgetsFragment.root),
            padBottomImeAndSystem = listOf(
                binding.allAppsFragment.allAppsGrid, binding.widgetsFragment.widgetsList
            ),
            padBottomSystem = listOf(binding.homeScreenGrid.root)
        )

        mDetector = GestureDetectorCompat(this, MyGestureListener(this))

//        mScreenHeight = realScreenSize.y
//        mAllAppsFragmentY = mScreenHeight
//        mWidgetsFragmentY = mScreenHeight
//        mMoveGestureThreshold = resources.getDimensionPixelSize(R.dimen.move_gesture_threshold)

        mScreenHeight = realScreenSize.y
        mScreenWidth = realScreenSize.x
        mAllAppsFragmentY = mScreenHeight
        mWidgetsFragmentY = mScreenHeight
        mMoveGestureThreshold = resources.getDimensionPixelSize(R.dimen.move_gesture_threshold)




        binding.sidePanelFragment.root.x = mScreenWidth.toFloat()
        binding.sidePanelFragment.root.beVisible()
        updateSidePanelScrim(mScreenWidth.toFloat())

        setupSidePanelTouch()

        arrayOf(
            binding.allAppsFragment.root as MyFragment<*>,
            binding.widgetsFragment.root as MyFragment<*>
        ).forEach { fragment ->
            fragment.setupFragment(this)
            fragment.y = mScreenHeight.toFloat()
            fragment.beVisible()
        }

        handleIntentAction(intent)

        binding.homeScreenGrid.root.itemClickListener = {
            performItemClick(it)
        }

        binding.homeScreenGrid.root.itemLongClickListener = {
            performItemLongClick(
                x = binding.homeScreenGrid.root.getClickableRect(it).left.toFloat(),
                clickedGridItem = it
            )
        }

        checkAndAddSmartTopWidget()
        checkAndAddTurboBoosterWidget()

        setupWallpaperColorListener()
        setupPackageListeners()
        mOpenActionPopup = LauncherActionPopup(binding.launcherPopupHolder)
    }

    private val smartWidgetLock = Any()

    private fun setupWallpaperColorListener() {
        updateWallpaperDesiredDimensions()
        if (isOreoMr1Plus()) {
            val wallpaperManager = WallpaperManager.getInstance(this)
            wallpaperColorChangeListener =
                WallpaperManager.OnColorsChangedListener { colors, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                        wallpaperSupportsDarkText = colors?.supportsDarkText() ?: run {
                            refreshWallpaperSupportsDarkText()
                            wallpaperSupportsDarkText
                        }
                        if (!isAllAppsFragmentExpanded() && !isWidgetsFragmentExpanded()) {
                            runOnUiThread {
                                updateStatusBarIcons()
                            }
                        }
                    }
                }
            wallpaperManager.addOnColorsChangedListener(
                wallpaperColorChangeListener!!,
                Handler(Looper.getMainLooper())
            )

            refreshWallpaperSupportsDarkText()
        }
    }

    private fun refreshWallpaperSupportsDarkText() {
        if (!isOreoMr1Plus()) return
        wallpaperSupportsDarkText = WallpaperManager.getInstance(this)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.supportsDarkText()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val wasAnyFragmentOpen = isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()
        if (wasJustPaused) {
            if (isAllAppsFragmentExpanded()) {
                hideFragment(binding.allAppsFragment)
            }
            if (isWidgetsFragmentExpanded()) {
                hideFragment(binding.widgetsFragment)
            }
        } else {
            closeAppDrawer()
            closeWidgetsFragment()
        }

        binding.allAppsFragment.searchBar.closeSearch()

        // scroll to first page when home button is pressed
        val alreadyOnHome = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyOnHome && !wasAnyFragmentOpen) {
            binding.homeScreenGrid.root.skipToPage(0)
        }

        handleIntentAction(intent)
    }

    override fun onStart() {
        super.onStart()
        binding.homeScreenGrid.root.appWidgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()
        wasJustPaused = false
        refreshWallpaperSupportsDarkText()
        Handler(Looper.getMainLooper()).post {
            if (!isFinishing && !isDestroyed) {
                com.design.home.helpers.LeftPanelRegistry.provider?.checkAndOpenMainFlow(this)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()) {
                updateStatusBarIcons(getProperBackgroundColor())
            } else {
                updateStatusBarIcons()
            }
        }, ANIMATION_DURATION)

        with(binding.mainHolder) {
            onGlobalLayout {
                binding.allAppsFragment.root.setupViews()
                binding.widgetsFragment.root.setupViews()
            }
        }

        ensureBackgroundThread {
            if (IconCache.launchers.isEmpty()) {
                val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
                    it.getIconIdentifier()
                }

                IconCache.launchers = launchersDB.getAppLaunchers().filter {
                    val showIcon = !hiddenIcons.contains(it.getLauncherIdentifier())
                    if (!showIcon) {
                        try {
                            launchersDB.deleteById(it.id!!)
                        } catch (_: Exception) {
                        }
                    }
                    showIcon
                }.toMutableList() as ArrayList<AppLauncher>
            }

            binding.allAppsFragment.root.gotLaunchers(IconCache.launchers)
            refreshLaunchers()
        }

        binding.homeScreenGrid.root.resizeGrid(
            newRowCount = config.homeRowCount,
            newColumnCount = config.homeColumnCount
        )
        binding.homeScreenGrid.root.updateColors()
        binding.allAppsFragment.root.onResume()
        if (IconCache.launchers.isNotEmpty()) {
            refreshSidePanelApps()
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            binding.homeScreenGrid.root.appWidgetHost.stopListening()
        } catch (_: Exception) {
        }

        wasJustPaused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isOreoMr1Plus() && wallpaperColorChangeListener != null) {
            WallpaperManager.getInstance(this)
                .removeOnColorsChangedListener(wallpaperColorChangeListener!!)
        }
        try {
            if (launcherAppsCallback != null) {
                val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as? LauncherApps
                launcherApps?.unregisterCallback(launcherAppsCallback!!)
                launcherAppsCallback = null
            }
        } catch (_: Exception) {
        }
        try {
            if (packageChangeReceiver != null) {
                unregisterReceiver(packageChangeReceiver)
                packageChangeReceiver = null
            }
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        wasJustPaused = true
        mOpenActionPopup?.dismiss(animate = false)
        sidePanelAdRefreshHandler.removeCallbacks(sidePanelAdRefreshRunnable)
    }

    override fun onBackPressedCompat(): Boolean {
        if (mOpenActionPopup?.isShowing == true) {
            mOpenActionPopup?.dismiss(animate = true)
            return true
        }
        return  if (isSidePanelExpanded()) {
            closeSidePanel()
            true
        } else if (isAllAppsFragmentExpanded()) {
            if (!binding.allAppsFragment.root.onBackPressed()) {
                hideFragment(binding.allAppsFragment)
                true
            } else {
                true
            }
        } else if (isWidgetsFragmentExpanded()) {
            if (binding.widgetsFragment.searchBar.isSearchOpen) {
                clearWidgetsSearch()
            } else {
                hideFragment(binding.widgetsFragment)
            }
            true
        } else if (binding.homeScreenGrid.resizeFrame.isVisible) {
            binding.homeScreenGrid.root.hideResizeLines()
            true
        } else {
            // this is a home launcher app, prevent back press from doing anything
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        when (requestCode) {
            REQUEST_SET_DEFAULT -> {
                if (!config.hasAddedDefaultSmartWidget) {
                    checkAndAddSmartTopWidget()
                }
                if (!config.hasAddedDefaultBoosterWidget) {
                    checkAndAddTurboBoosterWidget()
                }
            }

            UNINSTALL_APP_REQUEST_CODE -> {
                val pendingPkg = mPendingUninstallPackage
                mPendingUninstallPackage = null
                ensureBackgroundThread {
                    if (pendingPkg != null && !isPackageInstalled(pendingPkg)) {
                        onPackageUninstalled(pendingPkg)
                    } else {
                        refreshLaunchers()
                        Handler(Looper.getMainLooper()).postDelayed({
                            ensureBackgroundThread {
                                if (pendingPkg != null && !isPackageInstalled(pendingPkg)) {
                                    onPackageUninstalled(pendingPkg)
                                } else {
                                    refreshLaunchers()
                                }
                            }
                        }, 1000L)
                    }
                }
            }

            REQUEST_ALLOW_BINDING_WIDGET -> mActionOnCanBindWidget?.invoke(resultCode == RESULT_OK)
            REQUEST_CONFIGURE_WIDGET -> mActionOnWidgetConfiguredWidget?.invoke(resultCode == RESULT_OK)
            REQUEST_CREATE_SHORTCUT -> {
                if (resultCode == RESULT_OK && resultData != null) {
                    val launcherApps =
                        applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                    if (launcherApps.hasShortcutHostPermission()) {
                        val item = launcherApps.getPinItemRequest(resultData)
                        val shortcutInfo = item?.shortcutInfo ?: return
                        if (item.accept()) {
                            val shortcutId = shortcutInfo.id
                            val label = shortcutInfo.getLabel()
                            val icon = launcherApps.getShortcutBadgedIconDrawable(
                                shortcutInfo,
                                resources.displayMetrics.densityDpi
                            )
                            mActionOnAddShortcut?.invoke(shortcutId, label, icon)
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.allAppsFragment.root.onConfigurationChanged()
        binding.widgetsFragment.root.onConfigurationChanged()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            mPanelWasExpandedOnDown = isSidePanelExpanded()
            if (mPanelWasExpandedOnDown) {
                mSidePanelTouchDownX = ev.rawX
                mSidePanelTouchDownY = ev.rawY
                mSidePanelInitialX = binding.sidePanelFragment.root.x
                mSidePanelDragActive = false
                mSidePanelVelocityTracker?.recycle()
                mSidePanelVelocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }
        }

        if (mPanelWasExpandedOnDown) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    mSidePanelVelocityTracker?.addMovement(ev)
                    val dx = ev.rawX - mSidePanelTouchDownX
                    val dy = ev.rawY - mSidePanelTouchDownY
                    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

                    if (!mSidePanelDragActive && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        mSidePanelDragActive = true
                        binding.sidePanelFragment.root.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                    }

                    if (mSidePanelDragActive) {
                        val newX = (mSidePanelInitialX + dx).coerceIn(0f, mScreenWidth.toFloat())
                        binding.sidePanelFragment.root.x = newX
                        updateSidePanelScrim(newX)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = mSidePanelDragActive
                    mSidePanelDragActive = false
                    mPanelWasExpandedOnDown = false

                    if (wasDragging) {
                        mSidePanelVelocityTracker?.addMovement(ev)
                        mSidePanelVelocityTracker?.computeCurrentVelocity(1000)
                        val velX = mSidePanelVelocityTracker?.xVelocity ?: 0f
                        mSidePanelVelocityTracker?.recycle()
                        mSidePanelVelocityTracker = null

                        val flingThreshold = 800 * resources.displayMetrics.density
                        when {
                            velX > flingThreshold -> closeSidePanel()
                            velX < -flingThreshold -> openSidePanel()
                            binding.sidePanelFragment.root.x >= mScreenWidth * 0.5f -> closeSidePanel()
                            else -> openSidePanel()
                        }
                        return true
                    } else {
                        mSidePanelVelocityTracker?.recycle()
                        mSidePanelVelocityTracker = null
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        if (mLongPressedIcon != null && event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            mLastUpEvent = System.currentTimeMillis()
        }

        try {
            mDetector.onTouchEvent(event)
        } catch (_: Exception) {
        }

        when (event.actionMasked) {


            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x.toInt()
                mTouchDownY = event.y.toInt()
                mAllAppsFragmentY = binding.allAppsFragment.root.y.toInt()
                mWidgetsFragmentY = binding.widgetsFragment.root.y.toInt()
                mSidePanelX = binding.sidePanelFragment.root.x.toInt()
                mIgnoreUpEvent = false
                mSidePanelWasDragged = false
                mLeftPanelSwipeActive = false

                mIgnoreXMoveEvents = isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()
                mIgnoreYMoveEvents = isSidePanelExpanded()

                mVelocityTracker?.recycle()
                mVelocityTracker = VelocityTracker.obtain()
                mVelocityTracker?.addMovement(event)
            }

            MotionEvent.ACTION_MOVE -> {
                mVelocityTracker?.addMovement(event)
                val hasFingerMoved = if (mTouchDownX == -1 || mTouchDownY == -1) {
                    mTouchDownX = event.x.toInt()
                    mTouchDownY = event.y.toInt()
                    false
                } else {
                    hasFingerMoved(event)
                }
                if (mLongPressedIcon != null && (mOpenActionPopup?.isShowing == true || mOpenPopupMenu != null) && hasFingerMoved) {
                    mOpenActionPopup?.dismiss(animate = false)
                    mOpenPopupMenu?.dismiss()
                    mOpenPopupMenu = null
                    binding.homeScreenGrid.root.itemDraggingStarted(mLongPressedIcon!!)
                    hideFragment(binding.allAppsFragment)
                }

                if (mLongPressedIcon != null && hasFingerMoved) {
                    binding.homeScreenGrid.root.draggedItemMoved(event.x.toInt(), event.y.toInt())
                }

                if (hasFingerMoved && !mIgnoreMoveEvents && mLongPressedIcon == null) {
                    val diffY = mTouchDownY - event.y
                    val diffX = mTouchDownX - event.x

                    if (abs(diffY) > abs(diffX) && !mIgnoreYMoveEvents) {
                        mIgnoreXMoveEvents = true
                        if (isWidgetsFragmentExpanded()) {
                            val newY = mWidgetsFragmentY - diffY
                            binding.widgetsFragment.root.y = min(
                                a = max(0f, newY), b = mScreenHeight.toFloat()
                            )
                        } else {
                            val newY = mAllAppsFragmentY - diffY
                            binding.allAppsFragment.root.y = min(
                                a = max(0f, newY), b = mScreenHeight.toFloat()
                            )
                        }
                    } else if (abs(diffX) > abs(diffY) && !mIgnoreXMoveEvents) {
                        mIgnoreYMoveEvents = true
                        val draggedLeft = diffX // positive while finger moves right -> left
                        val currentPage = binding.homeScreenGrid.root.getCurrentPage()
                        val maxPage = binding.homeScreenGrid.root.getMaxPage()
                        val isFirstPage = currentPage == 0
                        val isLastPage = currentPage >= maxPage

                        if (isSidePanelExpanded() || (draggedLeft > 0 && isLastPage)) {
                            // Right-to-left drag on LAST page (or panel already open): drag right side panel
                            mSidePanelWasDragged = true
                            val newX = (mSidePanelX - draggedLeft).coerceIn(0f, mScreenWidth.toFloat())
                            binding.sidePanelFragment.root.x = newX
                            updateSidePanelScrim(newX)
                        } else if (draggedLeft < 0 && isFirstPage && LeftPanelRegistry.provider != null) {
                            // Left-to-right drag on FIRST page (page 0): mark left panel swipe active
                            mLeftPanelSwipeActive = true
                        } else {
                            // Between pages: normal page scroll (subtract touch slop for smooth start)
                            val smoothDiffX = if (diffX > 0) diffX - mMoveGestureThreshold else diffX + mMoveGestureThreshold
                            binding.homeScreenGrid.root.setSwipeMovement(smoothDiffX)
                        }
                    }
                }

                mLastTouchCoords = Pair(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                val startX = mTouchDownX
                mTouchDownX = -1
                mTouchDownY = -1
                mIgnoreMoveEvents = false
                mLongPressedIcon = null
                mLastTouchCoords = Pair(-1f, -1f)
                resetFragmentTouches()
                binding.homeScreenGrid.root.itemDraggingStopped()

                if (!mIgnoreUpEvent) {
                    if (!mIgnoreYMoveEvents) {
                        if (binding.allAppsFragment.root.y < mScreenHeight * 0.5) {
                            showFragment(binding.allAppsFragment)
                        } else if (isAllAppsFragmentExpanded()) {
                            hideFragment(binding.allAppsFragment)
                        }

                        if (binding.widgetsFragment.root.y < mScreenHeight * 0.5) {
                            showFragment(binding.widgetsFragment)
                        } else if (isWidgetsFragmentExpanded()) {
                            hideFragment(binding.widgetsFragment)
                        }
                    }

                    if (!mIgnoreXMoveEvents) {
                        val panelWasTouched = isSidePanelExpanded() || mSidePanelWasDragged

                        if (panelWasTouched) {
                            val velocity = mVelocityTracker?.let { tracker ->
                                tracker.addMovement(event)
                                tracker.computeCurrentVelocity(1000)
                                tracker.xVelocity
                            } ?: 0f

                            val flingThreshold = 800 * resources.displayMetrics.density
                            when {
                                velocity < -flingThreshold -> openSidePanel()
                                velocity > flingThreshold -> closeSidePanel()
                                binding.sidePanelFragment.root.x <= mScreenWidth * 0.5f -> openSidePanel()
                                else -> closeSidePanel()
                            }
                        } else {
                            // Check if this was a left-panel swipe (left→right swipe on page 0)
                            val velocity = mVelocityTracker?.let { tracker ->
                                tracker.addMovement(event)
                                tracker.computeCurrentVelocity(1000)
                                tracker.xVelocity
                            } ?: 0f
                            val flingThreshold = 250 * resources.displayMetrics.density
                            val distanceDraggedRight = if (startX != -1) event.x - startX else 0f
                            val distanceThreshold = 50 * resources.displayMetrics.density
                            val isOnFirstPage = binding.homeScreenGrid.root.getCurrentPage() == 0
                            android.util.Log.d("LeftPanel", "ACTION_UP: page=$isOnFirstPage, provider=${LeftPanelRegistry.provider}, swipeActive=$mLeftPanelSwipeActive, dist=$distanceDraggedRight, vel=$velocity")

                            if (isOnFirstPage && LeftPanelRegistry.provider != null &&
                                ((mLeftPanelSwipeActive && distanceDraggedRight > distanceThreshold) || velocity > flingThreshold)
                            ) {
                                mLeftPanelSwipeActive = false
                                LeftPanelRegistry.provider!!.openLeftPanel(this)
                            } else {
                                binding.homeScreenGrid.root.finalizeSwipe(velocity)
                            }
                        }
                    }
                }

                mLeftPanelSwipeActive = false
                mVelocityTracker?.recycle()
                mVelocityTracker = null
                mSidePanelWasDragged = false
            }
        }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(APP_DRAWER_STATE, isAllAppsFragmentExpanded())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState.getBoolean(APP_DRAWER_STATE)) {
            showFragment(binding.allAppsFragment, 0L)
        }
    }

    private fun handleIntentAction(intent: Intent) {
        if (intent.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            val launcherApps =
                applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val item = launcherApps.getPinItemRequest(intent)
            val shortcutInfo = item?.shortcutInfo ?: return

            ensureBackgroundThread {
                val shortcutId = shortcutInfo.id
                val label = shortcutInfo.getLabel()
                val icon = launcherApps.getShortcutBadgedIconDrawable(
                    shortcutInfo,
                    resources.displayMetrics.densityDpi
                )
                val (page, rect) = findFirstEmptyCell()
                val gridItem = HomeScreenGridItem(
                    id = null,
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                    page = page,
                    packageName = shortcutInfo.`package`,
                    activityName = "",
                    title = label,
                    type = ITEM_TYPE_SHORTCUT,
                    className = "",
                    widgetId = -1,
                    shortcutId = shortcutId,
                    icon = icon.toBitmap(),
                    docked = false,
                    parentId = null,
                    drawable = icon
                )

                runOnUiThread {
                    binding.homeScreenGrid.root.skipToPage(page)
                }
                // delay showing the shortcut both to let the user see adding it in realtime and hackily avoid concurrent modification exception at HomeScreenGrid
                Thread.sleep(2000)

                try {
                    item.accept()
                    binding.homeScreenGrid.root.storeAndShowGridItem(gridItem)
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    private fun findFirstEmptyCell(): Pair<Int, Rect> {
        val gridItems = homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
        val maxPage = gridItems.maxOf { it.page }
        val occupiedCells = ArrayList<Triple<Int, Int, Int>>()
        gridItems.toImmutableList().filter { it.parentId == null }.forEach { item ->
            for (xCell in item.left..item.right) {
                for (yCell in item.top..item.bottom) {
                    occupiedCells.add(Triple(item.page, xCell, yCell))
                }
            }
        }

        for (page in 0 until maxPage) {
            for (checkedYCell in 0 until config.homeColumnCount) {
                for (checkedXCell in 0 until config.homeRowCount - 1) {
                    val wantedCell = Triple(page, checkedXCell, checkedYCell)
                    if (!occupiedCells.contains(wantedCell)) {
                        return Pair(
                            first = page,
                            second = Rect(
                                wantedCell.second,
                                wantedCell.third,
                                wantedCell.second,
                                wantedCell.third
                            )
                        )
                    }
                }
            }
        }

        return Pair(maxPage + 1, Rect(0, 0, 0, 0))
    }

    // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
    private fun hasFingerMoved(event: MotionEvent): Boolean {
        return mTouchDownX != -1 && mTouchDownY != -1 &&
            (abs(mTouchDownX - event.x) > mMoveGestureThreshold || abs(mTouchDownY - event.y) > mMoveGestureThreshold)
    }


    private fun refreshLaunchers() {
        val launchers = getAllAppLaunchers()
        binding.allAppsFragment.root.gotLaunchers(launchers)
        binding.widgetsFragment.root.getAppWidgets()

        val installedPackageNames = launchers.map { it.packageName }.toSet()

        IconCache.launchers.map { it.packageName }.forEach { packageName ->
            if (!installedPackageNames.contains(packageName)) {
                launchersDB.deleteApp(packageName)
                homeScreenGridItemsDB.deleteByPackageName(packageName)
            }
        }

        try {
            homeScreenGridItemsDB.getAllItems().forEach { item ->
                if (item.type == ITEM_TYPE_ICON && !installedPackageNames.contains(item.packageName)) {
                    homeScreenGridItemsDB.deleteByPackageName(item.packageName)
                }
            }
        } catch (_: Exception) {
        }

        IconCache.launchers = launchers
        runOnUiThread { refreshSidePanelApps() }

        if (!config.wasHomeScreenInit) {
            ensureBackgroundThread {
                getDefaultAppPackages(launchers)
                config.wasHomeScreenInit = true
                binding.homeScreenGrid.root.fetchGridItems()
            }
        } else {
            binding.homeScreenGrid.root.fetchGridItems()
        }
    }

    fun onPackageUninstalled(packageName: String) {
        ensureBackgroundThread {
            launchersDB.deleteApp(packageName)
            homeScreenGridItemsDB.deleteByPackageName(packageName)
            IconCache.launchers = IconCache.launchers.filter { it.packageName != packageName }

            runOnUiThread {
                binding.allAppsFragment.root.onPackageUninstalled(packageName)
                binding.homeScreenGrid.root.onPackageUninstalled(packageName)
                binding.widgetsFragment.root.getAppWidgets()
                refreshSidePanelApps()
            }
        }
    }

    private var launcherAppsCallback: LauncherApps.Callback? = null
    private var packageChangeReceiver: BroadcastReceiver? = null
    private var mPendingUninstallPackage: String? = null

    private fun setupPackageListeners() {
        try {
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps != null) {
                launcherAppsCallback = object : LauncherApps.Callback() {
                    override fun onPackageRemoved(packageName: String, user: UserHandle) {
                        onPackageUninstalled(packageName)
                    }

                    override fun onPackageAdded(packageName: String, user: UserHandle) {
                        ensureBackgroundThread { refreshLaunchers() }
                    }

                    override fun onPackageChanged(packageName: String, user: UserHandle) {
                        ensureBackgroundThread { refreshLaunchers() }
                    }

                    override fun onPackagesAvailable(
                        packageNames: Array<out String>?,
                        user: UserHandle,
                        replacing: Boolean
                    ) {
                        ensureBackgroundThread { refreshLaunchers() }
                    }

                    override fun onPackagesUnavailable(
                        packageNames: Array<out String>?,
                        user: UserHandle,
                        replacing: Boolean
                    ) {
                        packageNames?.forEach { onPackageUninstalled(it) }
                    }
                }
                launcherApps.registerCallback(launcherAppsCallback!!, Handler(Looper.getMainLooper()))
            }
        } catch (_: Exception) {
        }

        try {
            packageChangeReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val packageName = intent?.data?.schemeSpecificPart ?: return
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    when (intent.action) {
                        Intent.ACTION_PACKAGE_REMOVED,
                        Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                            if (!isReplacing) {
                                onPackageUninstalled(packageName)
                            }
                        }
                        Intent.ACTION_PACKAGE_ADDED,
                        Intent.ACTION_PACKAGE_CHANGED -> {
                            ensureBackgroundThread { refreshLaunchers() }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            registerReceiver(packageChangeReceiver, filter)
        } catch (_: Exception) {
        }
    }


    fun isAllAppsFragmentExpanded() = binding.allAppsFragment.root.y != mScreenHeight.toFloat()

    private fun isWidgetsFragmentExpanded() =
        binding.widgetsFragment.root.y != mScreenHeight.toFloat()

    fun startHandlingTouches(touchDownY: Int) {
        mLongPressedIcon = null
        mTouchDownY = touchDownY
        mAllAppsFragmentY = binding.allAppsFragment.root.y.toInt()
        mWidgetsFragmentY = binding.widgetsFragment.root.y.toInt()
        mIgnoreUpEvent = false
    }

    private fun showFragment(fragment: ViewBinding, animationDuration: Long = ANIMATION_DURATION) {
        ObjectAnimator.ofFloat(fragment.root, "y", 0f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = resources.getColor(R.color.semitransparent_navigation)
        binding.homeScreenGrid.root.fragmentExpanded()
        binding.homeScreenGrid.root.hideResizeLines()

        @SuppressLint("AccessibilityFocus")
        fragment.root.performAccessibilityAction(
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )

        if (
            fragment is AllAppsFragmentBinding
            && config.showSearchBar
            && config.autoShowKeyboardInAppDrawer
        ) {
            fragment.root.post {
                showKeyboard(fragment.searchBar.binding.topToolbarSearch)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatusBarIcons(getProperBackgroundColor())
        }, animationDuration)
    }

    private fun hideFragment(fragment: ViewBinding, animationDuration: Long = ANIMATION_DURATION) {
        ObjectAnimator.ofFloat(fragment.root, "y", mScreenHeight.toFloat()).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = Color.TRANSPARENT
        binding.homeScreenGrid.root.fragmentCollapsed()
        updateStatusBarIcons()
        if (fragment is WidgetsFragmentBinding) {
            clearWidgetsSearch()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (fragment is AllAppsFragmentBinding) {
                fragment.allAppsGrid.scrollToPosition(0)
                fragment.root.touchDownY = -1
            } else if (fragment is WidgetsFragmentBinding) {
                fragment.widgetsList.scrollToPosition(0)
                fragment.root.touchDownY = -1
            }
        }, animationDuration)
    }

    fun homeScreenLongPressed(eventX: Float, eventY: Float) {
        if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()) {
            return
        }

        val (x, y) = binding.homeScreenGrid.root.intoViewSpaceCoords(eventX, eventY)
        mIgnoreMoveEvents = true
        val clickedGridItem = binding.homeScreenGrid.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemLongClick(x, clickedGridItem)
            return
        }

        binding.mainHolder.performHapticFeedback()
        showMainLongPressMenu(x, y)
    }

    fun homeScreenClicked(eventX: Float, eventY: Float) {
        binding.homeScreenGrid.root.hideResizeLines()
        val (x, y) = binding.homeScreenGrid.root.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = binding.homeScreenGrid.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemClick(clickedGridItem)
        }
        if (clickedGridItem?.type != ITEM_TYPE_FOLDER) {
            binding.homeScreenGrid.root.closeFolder(redraw = true)
        }
    }

    fun homeScreenDoubleTapped(eventX: Float, eventY: Float) {
        val (x, y) = binding.homeScreenGrid.root.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = binding.homeScreenGrid.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            return
        }

        val devicePolicyManager =
            getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
            ComponentName(this, LockDeviceAdminReceiver::class.java)
        )
        if (isLockDeviceAdminActive) {
            devicePolicyManager.lockNow()
        }
    }

    fun closeAppDrawer(delayed: Boolean = false) {
        if (isAllAppsFragmentExpanded()) {
            val close = {
                binding.allAppsFragment.root.y = mScreenHeight.toFloat()
                binding.allAppsFragment.allAppsGrid.scrollToPosition(0)
                binding.allAppsFragment.root.touchDownY = -1
                binding.homeScreenGrid.root.fragmentCollapsed()
                updateStatusBarIcons()
            }
            if (delayed) {
                Handler(Looper.getMainLooper()).postDelayed(close, APP_DRAWER_CLOSE_DELAY)
            } else {
                close()
            }
        }
    }

    fun closeWidgetsFragment(delayed: Boolean = false) {
        if (isWidgetsFragmentExpanded()) {
            val close = {
                binding.widgetsFragment.root.y = mScreenHeight.toFloat()
                binding.widgetsFragment.widgetsList.scrollToPosition(0)
                clearWidgetsSearch()
                binding.widgetsFragment.root.touchDownY = -1
                binding.homeScreenGrid.root.fragmentCollapsed()
                updateStatusBarIcons()
            }
            if (delayed) {
                Handler(Looper.getMainLooper()).postDelayed(close, APP_DRAWER_CLOSE_DELAY)
            } else {
                close()
            }
        }
    }

    fun clearWidgetsSearch() {
        binding.widgetsFragment.searchBar.closeSearch()
    }

    private fun performItemClick(clickedGridItem: HomeScreenGridItem) {
        when (clickedGridItem.type) {
            ITEM_TYPE_ICON -> {
                RecentAppsManager.recordAppUsed(
                    applicationContext, clickedGridItem.packageName, clickedGridItem.activityName
                )
                val pkg = clickedGridItem.packageName
                val act = clickedGridItem.activityName
                val provider = LeftPanelRegistry.provider
                if (provider != null) {
                    provider.onLauncherAppLaunched(this) { launchApp(pkg, act) }
                } else {
                    launchApp(pkg, act)
                }
            }
            ITEM_TYPE_FOLDER -> openFolder(clickedGridItem)
            ITEM_TYPE_SHORTCUT -> {
                val id = clickedGridItem.shortcutId
                val packageName = clickedGridItem.packageName
                val userHandle = Process.myUserHandle()
                val shortcutBounds = binding.homeScreenGrid.root.getClickableRect(clickedGridItem)
                val launcherApps =
                    applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.startShortcut(packageName, id, shortcutBounds, null, userHandle)
            }
        }
    }

    private fun openFolder(folder: HomeScreenGridItem) {
        binding.homeScreenGrid.root.openFolder(folder)
    }

    private fun performItemLongClick(x: Float, clickedGridItem: HomeScreenGridItem) {
        if (clickedGridItem.type == ITEM_TYPE_ICON || clickedGridItem.type == ITEM_TYPE_SHORTCUT || clickedGridItem.type == ITEM_TYPE_FOLDER) {
            binding.mainHolder.performHapticFeedback()
        }

        val clickableRect = binding.homeScreenGrid.root.getClickableRect(clickedGridItem)
        showHomeIconMenu(clickableRect.centerX().toFloat(), clickableRect.centerY().toFloat(), clickedGridItem, false)
    }

    fun showHomeIconMenu(
        x: Float,
        y: Float,
        gridItem: HomeScreenGridItem,
        isOnAllAppsFragment: Boolean,
    ) {
        binding.homeScreenGrid.root.hideResizeLines()
        val liveItem = binding.homeScreenGrid.root.gridItems.firstOrNull {
            (gridItem.id != null && it.id == gridItem.id) ||
                (gridItem.widgetId != -1 && it.widgetId == gridItem.widgetId)
        } ?: gridItem
        mLongPressedIcon = liveItem

        val clickableRect = if (isOnAllAppsFragment || gridItem.type == ITEM_TYPE_WIDGET) {
            val iconSize = realScreenSize.x / config.drawerColumnCount.toFloat()
            RectF(x - iconSize / 2f, y - iconSize / 2f, x + iconSize / 2f, y + iconSize / 2f)
        } else {
            val rect = binding.homeScreenGrid.root.getClickableRect(gridItem)
            RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
        }

        val launcherApps =
            applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val shortcuts = if (launcherApps.hasShortcutHostPermission()) {
            try {
                val query = LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                ).setPackage(gridItem.packageName)
                launcherApps.getShortcuts(query, Process.myUserHandle())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val shortcutItems = mutableListOf<PopupActionItem>()
        val iconSize = resources.getDimensionPixelSize(R.dimen.menu_icon_size)
        shortcuts?.forEach { shortcutInfo ->
            val iconDrawable = launcherApps.getShortcutIconDrawable(
                shortcutInfo, resources.displayMetrics.densityDpi
            )
            val shortLabel = shortcutInfo.shortLabel ?: shortcutInfo.longLabel ?: ""
            shortcutItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = shortLabel.toString(),
                    icon = (iconDrawable ?: Color.TRANSPARENT.toDrawable())
                        .toBitmap(width = iconSize, height = iconSize)
                        .toDrawable(resources),
                    isShortcut = true,
                    onClick = {
                        menuListener.onAnyClick()
                        val id = shortcutInfo.id
                        val packageName = shortcutInfo.`package`
                        val userHandle = Process.myUserHandle()
                        launcherApps.startShortcut(packageName, id, Rect(), null, userHandle)
                    }
                )
            )
        }

        val actionItems = mutableListOf<PopupActionItem>()

        if (gridItem.type == ITEM_TYPE_ICON) {
            actionItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(R.string.app_info),
                    icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_info_vector),
                    onClick = {
                        menuListener.onAnyClick()
                        menuListener.appInfo(gridItem)
                    }
                )
            )
        }

        if (gridItem.type == ITEM_TYPE_ICON && isOnAllAppsFragment) {
            actionItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(CommonR.string.hide),
                    icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_hide_vector),
                    onClick = {
                        menuListener.onAnyClick()
                        menuListener.hide(gridItem)
                    }
                )
            )
        }

        if (gridItem.type == ITEM_TYPE_WIDGET) {
            actionItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(CommonR.string.resize),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_resize_vector),
                    onClick = {
                        menuListener.onAnyClick()
                        menuListener.resize(gridItem)
                    }
                )
            )
        }

        if ((gridItem.type == ITEM_TYPE_ICON || gridItem.type == ITEM_TYPE_FOLDER) && !isOnAllAppsFragment) {
            actionItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(CommonR.string.rename),
                    icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_rename_vector),
                    onClick = {
                        menuListener.onAnyClick()
                        menuListener.rename(gridItem)
                    }
                )
            )
        }

        if (!isOnAllAppsFragment) {
            actionItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(CommonR.string.remove),
                    icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_cross_vector),
                    onClick = {
                        menuListener.onAnyClick()
                        menuListener.remove(gridItem)
                    }
                )
            )
        }

        if (gridItem.type == ITEM_TYPE_ICON && canAppBeUninstalled(gridItem.packageName) && gridItem.packageName != packageName) {
            actionItems.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(R.string.uninstall),
                    icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_delete_vector),
                    onClick = {
                        menuListener.onAnyClick()
                        menuListener.uninstall(gridItem)
                    }
                )
            )
        }

        mOpenActionPopup?.show(
            anchorX = clickableRect.centerX(),
            anchorY = clickableRect.centerY(),
            anchorRect = clickableRect,
            items = actionItems,
            shortcutItems = shortcutItems,
            onDismiss = {
                menuListener.onDismiss()
            }
        )
    }

    fun dismissActionPopupSilently() {
        mOpenActionPopup?.dismiss(animate = false)
        mOpenPopupMenu?.dismiss()
        mOpenPopupMenu = null
    }

    fun resetLongPressedIcon() {
        mLongPressedIcon = null
    }

    fun widgetLongPressedOnList(gridItem: HomeScreenGridItem) {
        mLongPressedIcon = gridItem
        hideFragment(binding.widgetsFragment)
        binding.homeScreenGrid.root.itemDraggingStarted(mLongPressedIcon!!)
    }

    fun addWidgetDirectlyToHomeScreen(appWidget: com.design.home.models.AppWidget) {
        closeWidgetsFragment()
        val added = binding.homeScreenGrid.root.addNewWidgetDirectly(appWidget)
        if (added) {
            toast(R.string.widget_added_to_home_screen)
        }
    }

    private fun showMainLongPressMenu(x: Float, y: Float) {
        binding.homeScreenGrid.root.hideResizeLines()
        val items = mutableListOf<PopupActionItem>()

        items.add(
            PopupActionItem(
                id = View.NO_ID,
                title = getString(CommonR.string.widgets),
                icon = ContextCompat.getDrawable(this, R.drawable.ic_widgets_vector),
                onClick = { showWidgetsFragment() }
            )
        )

        items.add(
            PopupActionItem(
                id = View.NO_ID,
                title = getString(CommonR.string.wallpapers),
                icon = ContextCompat.getDrawable(this, R.drawable.ic_wallpaper_vector),
                onClick = { launchWallpapersIntent() }
            )
        )

        items.add(
            PopupActionItem(
                id = View.NO_ID,
                title = getString(R.string.launcher_settings),
                icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_settings_cog_vector),
                onClick = { launchSettings() }
            )
        )

        if (!isDefaultLauncher()) {
            items.add(
                PopupActionItem(
                    id = View.NO_ID,
                    title = getString(CommonR.string.set_as_default),
                    icon = ContextCompat.getDrawable(this, CommonR.drawable.ic_set_as_vector),
                    onClick = { launchSetAsDefaultIntent() }
                )
            )
        }

        mOpenActionPopup?.show(
            anchorX = x,
            anchorY = y,
            items = items,
            onDismiss = {
                resetFragmentTouches()
            }
        )
    }

    private fun resetFragmentTouches() {
        binding.widgetsFragment.root.apply {
            touchDownY = -1
            ignoreTouches = false
        }

        binding.allAppsFragment.root.apply {
            touchDownY = -1
            ignoreTouches = false
        }
    }

    private fun showWidgetsFragment() {
        showFragment(binding.widgetsFragment)
    }

    private fun hideIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            val hiddenIcon = HiddenIcon(null, item.packageName, item.activityName, item.title, null)
            hiddenIconsDB.insert(hiddenIcon)

            runOnUiThread {
                binding.allAppsFragment.root.onIconHidden(item)
            }
        }
    }

    private fun renameItem(homeScreenGridItem: HomeScreenGridItem) {
        RenameItemDialog(this, homeScreenGridItem) {
            binding.homeScreenGrid.root.fetchGridItems()
        }
    }

    private fun updateWallpaperDesiredDimensions() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val screenX = realScreenSize.x
            val screenY = realScreenSize.y
            if (screenX > 0 && screenY > 0) {
                wallpaperManager.suggestDesiredDimensions(screenX, screenY)
            }
        } catch (_: Exception) {
        }
    }

    private fun launchWallpapersIntent() {
        try {
            updateWallpaperDesiredDimensions()
            val screenX = realScreenSize.x
            val screenY = realScreenSize.y
            val intent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
                if (screenX > 0 && screenY > 0) {
                    putExtra("aspectX", screenX)
                    putExtra("aspectY", screenY)
                    putExtra("outputX", screenX)
                    putExtra("outputY", screenY)
                    putExtra("scale", true)
                    putExtra("scaleUpIfNeeded", true)
                }
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(com.design.commons.R.string.no_app_found)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun launchSettings() {
        startActivity(
            Intent(this@LauncherHomeActivity, SettingsActivity::class.java)
        )
    }

    private fun launchSetAsDefaultIntent() {
        if (isQPlus()) {
            requestHomeRole()
            return
        }
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        val intent = intents.firstOrNull { it.resolveActivity(packageManager) != null }
        if (intent != null) {
            startActivity(intent)
        }
    }

    private fun requestHomeRole() {
        if (isQPlus()) {
            startActivityForResult(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                REQUEST_SET_DEFAULT
            )
        }
    }

    val menuListener: ItemMenuListener = object : ItemMenuListener {
        override fun onAnyClick() {
            resetFragmentTouches()
        }

        override fun hide(gridItem: HomeScreenGridItem) {
            hideIcon(gridItem)
        }

        override fun rename(gridItem: HomeScreenGridItem) {
            renameItem(gridItem)
        }

        override fun resize(gridItem: HomeScreenGridItem) {
            binding.homeScreenGrid.root.widgetLongPressed(gridItem)
        }

        override fun appInfo(gridItem: HomeScreenGridItem) {
            launchAppInfo(gridItem.packageName)
        }

        override fun remove(gridItem: HomeScreenGridItem) {
            binding.homeScreenGrid.root.removeAppIcon(gridItem)
        }

        override fun uninstall(gridItem: HomeScreenGridItem) {
            mPendingUninstallPackage = gridItem.packageName
            uninstallApp(gridItem.packageName)
        }

        override fun onDismiss() {
            mOpenPopupMenu = null
            resetFragmentTouches()
        }

        override fun beforeShow(menu: Menu) {
            var visibleMenuItems = 0
            for (item in menu.iterator()) {
                if (item.isVisible) {
                    visibleMenuItems++
                }
            }
            val yOffset =
                resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuItems - 1)
            binding.homeScreenPopupMenuAnchor.y -= yOffset
        }
    }

    private class MyGestureListener(
        private val flingListener: FlingListener,
    ) : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            (flingListener as LauncherHomeActivity).homeScreenClicked(event.x, event.y)
            return super.onSingleTapUp(event)
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            (flingListener as LauncherHomeActivity).homeScreenDoubleTapped(event.x, event.y)
            return super.onDoubleTap(event)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            // ignore fling events just after releasing an icon from dragging
            if (System.currentTimeMillis() - mLastUpEvent < 500L) {
                return true
            }

            if (abs(velocityY) > abs(velocityX)) {
                if (velocityY > 0) {
                    flingListener.onFlingDown()
                } else {
                    flingListener.onFlingUp()
                }
            } else if (abs(velocityX) > abs(velocityY)) {
                if (velocityX > 0) {
                    flingListener.onFlingRight()
                } else {
                    flingListener.onFlingLeft()
                }
            }

            return true
        }

        override fun onLongPress(event: MotionEvent) {
            (flingListener as LauncherHomeActivity).homeScreenLongPressed(event.x, event.y)
        }
    }

    override fun onFlingUp() {
        if (mIgnoreYMoveEvents) {
            return
        }

        if (!isWidgetsFragmentExpanded()) {
            mIgnoreUpEvent = true
            showFragment(binding.allAppsFragment)
        }
    }

    @SuppressLint("WrongConstant")
    override fun onFlingDown() {
        if (mIgnoreYMoveEvents) {
            return
        }

        mIgnoreUpEvent = true
        if (isAllAppsFragmentExpanded()) {
            hideFragment(binding.allAppsFragment)
        } else if (isWidgetsFragmentExpanded()) {
            hideFragment(binding.widgetsFragment)
        } else {
            try {
                Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(getSystemService("statusbar"))
            } catch (_: Exception) {
            }
        }
    }

    override fun onFlingRight() {
        if (mIgnoreXMoveEvents || mLongPressedIcon != null) return
        mIgnoreUpEvent = true
        if (mSidePanelWasDragged || isSidePanelExpanded()) {
            closeSidePanel()
        } else {
            val isFirstPage = binding.homeScreenGrid.root.getCurrentPage() == 0
            android.util.Log.d("LeftPanel", "onFlingRight: page=${binding.homeScreenGrid.root.getCurrentPage()}, provider=${LeftPanelRegistry.provider}")
            if (isFirstPage && LeftPanelRegistry.provider != null) {
                mLeftPanelSwipeActive = false
                LeftPanelRegistry.provider?.openLeftPanel(this)
            } else {
                binding.homeScreenGrid.root.prevPage(redraw = true)
            }
        }
    }

    override fun onFlingLeft() {
        if (mIgnoreXMoveEvents || mLongPressedIcon != null) return
        mIgnoreUpEvent = true
        val currentPage = binding.homeScreenGrid.root.getCurrentPage()
        val maxPage = binding.homeScreenGrid.root.getMaxPage()
        val isLastPage = currentPage >= maxPage

        if (mSidePanelWasDragged || isLastPage) {
            openSidePanel()
        } else {
            binding.homeScreenGrid.root.nextPage(redraw = true)
        }
    }

    @SuppressLint("WrongConstant")
    fun getAllAppLaunchers(): ArrayList<AppLauncher> {
        val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
            it.getIconIdentifier()
        }

        val allApps = ArrayList<AppLauncher>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val simpleLauncher = applicationContext.packageName
        val microG = "com.google.android.gms"
        val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val packageName = componentInfo.packageName
            if (packageName == simpleLauncher || packageName == microG) {
                continue
            }

            val activityName = info.activityInfo.name
            if (hiddenIcons.contains("$packageName/$activityName")) {
                continue
            }

            val label = info.loadLabel(packageManager).toString()
            val drawable = info.loadIcon(packageManager)
                ?: getDrawableForPackageName(packageName)
                ?: continue

            val bitmap = drawable.toBitmap(
                width = max(drawable.intrinsicWidth, 1),
                height = max(drawable.intrinsicHeight, 1),
                config = Bitmap.Config.ARGB_8888
            )
            val placeholderColor = calculateAverageColor(bitmap)
            allApps.add(
                AppLauncher(
                    id = null,
                    title = label,
                    packageName = packageName,
                    activityName = activityName,
                    order = 0,
                    thumbnailColor = placeholderColor,
                    drawable = bitmap.toDrawable(resources)
                )
            )
        }

        launchersDB.insertAll(allApps)
        return allApps
    }

    private fun getDefaultAppPackages(appLaunchers: ArrayList<AppLauncher>) {
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val defaultDialerPackage = packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?: packageManager.queryIntentActivities(dialIntent, 0).firstOrNull()?.activityInfo?.packageName
            if (defaultDialerPackage != null) {
                appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.let { app ->
                    val dialerIcon =
                        HomeScreenGridItem(
                            id = null,
                            left = 0,
                            top = config.homeRowCount - 1,
                            right = 0,
                            bottom = config.homeRowCount - 1,
                            page = 0,
                            packageName = app.packageName,
                            activityName = "",
                            title = app.title,
                            type = ITEM_TYPE_ICON,
                            className = "",
                            widgetId = -1,
                            shortcutId = "",
                            icon = null,
                            docked = true,
                            parentId = null
                        )
                    homeScreenGridItems.add(dialerIcon)
                }
            }
        } catch (_: Exception) {
        }

        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO, "smsto:".toUri())
            val defaultSMSMessengerPackage = packageManager.resolveActivity(smsIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?: packageManager.queryIntentActivities(smsIntent, 0).firstOrNull()?.activityInfo?.packageName
            if (defaultSMSMessengerPackage != null) {
                appLaunchers.firstOrNull { it.packageName == defaultSMSMessengerPackage }?.let { app ->
                    val messengerIcon =
                        HomeScreenGridItem(
                            id = null,
                            left = 1,
                            top = config.homeRowCount - 1,
                            right = 1,
                            bottom = config.homeRowCount - 1,
                            page = 0,
                            packageName = app.packageName,
                            activityName = "",
                            title = app.title,
                            type = ITEM_TYPE_ICON,
                            className = "",
                            widgetId = -1,
                            shortcutId = "",
                            icon = null,
                            docked = true,
                            parentId = null
                        )
                    homeScreenGridItems.add(messengerIcon)
                }
            }
        } catch (_: Exception) {
        }

        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
            val resolveInfo =
                packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultBrowserPackage }?.apply {
                val browserIcon =
                    HomeScreenGridItem(
                        id = null,
                        left = 2,
                        top = config.homeRowCount - 1,
                        right = 2,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = defaultBrowserPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(browserIcon)
            }
        } catch (_: Exception) {
        }

        try {
            val potentialStores = arrayListOf(
                "com.android.vending", "org.fdroid.fdroid", "com.aurora.store"
            )
            val storePackage = potentialStores.firstOrNull {
                isPackageInstalled(it) && appLaunchers.map { it.packageName }.contains(it)
            }
            if (storePackage != null) {
                appLaunchers.firstOrNull { it.packageName == storePackage }?.apply {
                    val storeIcon = HomeScreenGridItem(
                        id = null,
                        left = 3,
                        top = config.homeRowCount - 1,
                        right = 3,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = storePackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                    homeScreenGridItems.add(storeIcon)
                }
            }
        } catch (_: Exception) {
        }

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveInfo =
                packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultCameraPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultCameraPackage }?.apply {
                val cameraIcon =
                    HomeScreenGridItem(
                        id = null,
                        left = 4,
                        top = config.homeRowCount - 1,
                        right = 4,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = defaultCameraPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (_: Exception) {
        }

        homeScreenGridItemsDB.insertAll(homeScreenGridItems)
    }

    fun checkAndAddSmartTopWidget() {
        ensureBackgroundThread {
            synchronized(smartWidgetLock) {
                val allItems = homeScreenGridItemsDB.getAllItems()
                val smartWidgets = allItems.filter {
                    it.type == ITEM_TYPE_WIDGET && it.className == SmartTopWidgetProvider::class.java.name
                }
                if (smartWidgets.isNotEmpty()) {
                    config.hasAddedDefaultSmartWidget = true
                    var needsRedraw = false

                    // If duplicate smart widgets were previously created, delete all extras and keep ONLY one
                    val primaryWidget = smartWidgets.first()
                    if (smartWidgets.size > 1) {
                        for (i in 1 until smartWidgets.size) {
                            val extra = smartWidgets[i]
                            if (extra.id != null) {
                                homeScreenGridItemsDB.deleteById(extra.id!!)
                                try {
                                    binding.homeScreenGrid.root.appWidgetHost.deleteAppWidgetId(extra.widgetId)
                                } catch (_: Exception) {
                                }
                                needsRedraw = true
                            }
                        }
                    }



                    if (needsRedraw) {
                        WeatherManager.refreshWeather(this)
                        runOnUiThread {
                            binding.homeScreenGrid.root.fetchGridItems()
                        }
                    }
                    return@synchronized
                }

                val appWidgetManager = AppWidgetManager.getInstance(this)
                val smartProvider = appWidgetManager.installedProviders.firstOrNull {
                    it.provider.className == SmartTopWidgetProvider::class.java.name
                } ?: return@synchronized

                val widgetId = binding.homeScreenGrid.root.appWidgetHost.allocateAppWidgetId()
                val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, smartProvider.provider)
                if (canBind) {
                    val colCount = config.homeColumnCount
                    val occupiedItemsOnTop = allItems.filter {
                        it.page == 0 && !it.docked && it.top < 1
                    }
                    occupiedItemsOnTop.forEach { item ->
                        val newTop = item.top + 1
                        val newBottom = item.bottom + 1
                        if (newBottom < config.homeRowCount - 1) {
                            item.top = newTop
                            item.bottom = newBottom
                            homeScreenGridItemsDB.updateItemPosition(
                                item.left, item.top, item.right, item.bottom, item.page, item.docked, item.parentId, item.id!!
                            )
                        } else {
                            item.page = 1
                            homeScreenGridItemsDB.updateItemPosition(
                                item.left, item.top, item.right, item.bottom, 1, item.docked, item.parentId, item.id!!
                            )
                        }
                    }

                    val widgetItem = HomeScreenGridItem(
                        id = null,
                        left = 0,
                        top = 0,
                        right = colCount - 1,
                        bottom = 0,
                        page = 0,
                        packageName = packageName,
                        activityName = "",
                        title = getString(R.string.widget_smart_top_title),
                        type = ITEM_TYPE_WIDGET,
                        className = SmartTopWidgetProvider::class.java.name,
                        widgetId = widgetId,
                        shortcutId = "",
                        icon = null,
                        docked = false,
                        parentId = null
                    )
                    homeScreenGridItemsDB.insert(widgetItem)
                    config.hasAddedDefaultSmartWidget = true
                    WeatherManager.refreshWeather(this)
                    runOnUiThread {
                        binding.homeScreenGrid.root.fetchGridItems()
                    }
                } else {
                    binding.homeScreenGrid.root.appWidgetHost.deleteAppWidgetId(widgetId)
                }
            }
        }
    }

    fun checkAndAddTurboBoosterWidget() {
        ensureBackgroundThread {
            synchronized(smartWidgetLock) {
                val allItems = homeScreenGridItemsDB.getAllItems()
                val boosterWidgets = allItems.filter {
                    it.type == ITEM_TYPE_WIDGET && it.className.endsWith("GameBoosterWidgetProvider")
                }
                if (boosterWidgets.isNotEmpty()) {
                    config.hasAddedDefaultBoosterWidget = true
                    var needsRedraw = false

                    // Ensure all booster widgets have at least 2x2 dimensions (perfect square)
                    boosterWidgets.forEach { boosterWidget ->
                        val currentW = boosterWidget.getWidthInCells()
                        val currentH = boosterWidget.getHeightInCells()
                        if (currentW < 2 || currentH < 2) {
                            val newW = 2
                            val newH = 2
                            if (boosterWidget.left + newW > config.homeColumnCount) {
                                boosterWidget.left = (config.homeColumnCount - newW).coerceAtLeast(0)
                            }
                            boosterWidget.right = boosterWidget.left + newW - 1
                            if (boosterWidget.top + newH > config.homeRowCount - 1) {
                                boosterWidget.top = (config.homeRowCount - 1 - newH).coerceAtLeast(0)
                            }
                            boosterWidget.bottom = boosterWidget.top + newH - 1
                            boosterWidget.widthCells = newW
                            boosterWidget.heightCells = newH
                            if (boosterWidget.id != null) {
                                homeScreenGridItemsDB.updateItemPosition(
                                    boosterWidget.left, boosterWidget.top, boosterWidget.right, boosterWidget.bottom,
                                    boosterWidget.page, boosterWidget.docked, boosterWidget.parentId, boosterWidget.id!!
                                )
                                needsRedraw = true
                            }
                        }
                    }

                    try {
                        val appWidgetManager = AppWidgetManager.getInstance(this@LauncherHomeActivity)
                        val boosterProvider = appWidgetManager.installedProviders.firstOrNull {
                            it.provider.className.endsWith("GameBoosterWidgetProvider")
                        }
                        if (boosterProvider != null) {
                            val widgetIds = boosterWidgets.map { it.widgetId }.toIntArray()
                            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                component = boosterProvider.provider
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                            }
                            sendBroadcast(updateIntent)
                        }
                    } catch (_: Exception) {
                    }

                    if (needsRedraw) {
                        runOnUiThread {
                            binding.homeScreenGrid.root.fetchGridItems()
                        }
                    }
                    return@synchronized
                }

                if (config.hasAddedDefaultBoosterWidget) {
                    return@synchronized
                }

                val appWidgetManager = AppWidgetManager.getInstance(this)
                val boosterProvider = appWidgetManager.installedProviders.firstOrNull {
                    it.provider.className.endsWith("GameBoosterWidgetProvider")
                } ?: return@synchronized

                val widgetId = binding.homeScreenGrid.root.appWidgetHost.allocateAppWidgetId()
                val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, boosterProvider.provider)
                if (canBind) {
                    val targetLeft = 0
                    val targetTop = 1
                    val targetRight = 1
                    val targetBottom = 2

                    // Move any items on page 0 that overlap with cells (0..1, 1..2)
                    val overlappingItems = allItems.filter {
                        it.page == 0 && !it.docked &&
                            it.left <= targetRight && it.right >= targetLeft &&
                            it.top <= targetBottom && it.bottom >= targetTop
                    }

                    val shiftY = targetBottom - targetTop + 1
                    overlappingItems.forEach { item ->
                        val newTop = item.top + shiftY
                        val newBottom = item.bottom + shiftY
                        if (newBottom < config.homeRowCount - 1) {
                            item.top = newTop
                            item.bottom = newBottom
                            homeScreenGridItemsDB.updateItemPosition(
                                item.left, item.top, item.right, item.bottom, item.page, item.docked, item.parentId, item.id!!
                            )
                        } else {
                            item.page = 1
                            homeScreenGridItemsDB.updateItemPosition(
                                item.left, item.top, item.right, item.bottom, 1, item.docked, item.parentId, item.id!!
                            )
                        }
                    }

                    val widgetItem = HomeScreenGridItem(
                        id = null,
                        left = targetLeft,
                        top = targetTop,
                        right = targetRight,
                        bottom = targetBottom,
                        page = 0,
                        packageName = boosterProvider.provider.packageName,
                        activityName = "",
                        title = getString(R.string.widget_turbo_booster_title),
                        type = ITEM_TYPE_WIDGET,
                        className = boosterProvider.provider.className,
                        widgetId = widgetId,
                        shortcutId = "",
                        icon = null,
                        docked = false,
                        parentId = null,
                        widthCells = 2,
                        heightCells = 2
                    )
                    homeScreenGridItemsDB.insert(widgetItem)
                    config.hasAddedDefaultBoosterWidget = true

                    try {
                        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                            component = boosterProvider.provider
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                        }
                        sendBroadcast(updateIntent)
                    } catch (_: Exception) {
                    }

                    runOnUiThread {
                        binding.homeScreenGrid.root.fetchGridItems()
                    }
                } else {
                    binding.homeScreenGrid.root.appWidgetHost.deleteAppWidgetId(widgetId)
                }
            }
        }
    }


    fun handleWidgetBinding(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo,
        callback: (canBind: Boolean) -> Unit,
    ) {
        mActionOnCanBindWidget = null
        val canCreateWidget =
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetInfo.provider)
        if (canCreateWidget) {
            callback(true)
        } else {
            mActionOnCanBindWidget = callback
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetInfo.provider)
                startActivityForResult(this, REQUEST_ALLOW_BINDING_WIDGET)
            }
        }
    }

    fun handleWidgetConfigureScreen(
        appWidgetHost: AppWidgetHost,
        appWidgetId: Int,
        callback: (canBind: Boolean) -> Unit,
    ) {
        mActionOnWidgetConfiguredWidget = callback
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            this,
            appWidgetId,
            0,
            REQUEST_CONFIGURE_WIDGET,
            null
        )
    }

    fun handleShorcutCreation(
        activityInfo: ActivityInfo,
        callback: (shortcutId: String, label: String, icon: Drawable) -> Unit,
    ) {
        mActionOnAddShortcut = callback
        val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
        Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            component = componentName
            startActivityForResult(this, REQUEST_CREATE_SHORTCUT)
        }
    }

    private fun updateStatusBarIcons(backgroundColor: Int? = null) {
        val isLightBackground = when {
            backgroundColor != null -> backgroundColor.getContrastColor() == DARK_GREY
            wallpaperSupportsDarkText != null -> wallpaperSupportsDarkText!!
            else -> {
                refreshWallpaperSupportsDarkText()
                wallpaperSupportsDarkText ?: false
            }
        }
//        window.insetsController().apply {
//            isAppearanceLightStatusBars = isLightBackground
//            isAppearanceLightNavigationBars = isLightBackground
//        }
    }

    // taken from https://gist.github.com/maxjvh/a6ab15cbba9c82a5065d
    private fun calculateAverageColor(bitmap: Bitmap): Int {
        var red = 0
        var green = 0
        var blue = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
            n++
            i += 1
        }

        return Color.rgb(red / n, green / n, blue / n)
    }


    private fun isSidePanelExpanded() = binding.sidePanelFragment.root.x < mScreenWidth * 0.95f

    private var mSidePanelAnimator: ObjectAnimator? = null

    private fun animateSidePanel(targetX: Float, onEnd: (() -> Unit)? = null) {
        val root = binding.sidePanelFragment.root
        mSidePanelAnimator?.cancel()

        val startX = root.x
        val distance = kotlin.math.abs(targetX - startX)
        val animDuration = ((distance / mScreenWidth.toFloat()) * 180L).toLong().coerceIn(120L, 200L)

        root.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        mSidePanelAnimator = ObjectAnimator.ofFloat(root, "x", targetX).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator(1.8f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.setLayerType(View.LAYER_TYPE_NONE, null)
                    updateSidePanelScrim(targetX)
                    onEnd?.invoke()
                }
            })
            addUpdateListener {
                updateSidePanelScrim(root.x)
            }
            start()
        }
    }

    private fun updateSidePanelScrim(currentX: Float) {
        val openFraction = (1f - (currentX / mScreenWidth.toFloat())).coerceIn(0f, 1f)
        val alphaInt = (openFraction * 0xCC).toInt()
        binding.sidePanelFragment.root.setBackgroundColor(Color.argb(alphaInt, 0, 0, 0))
    }

    private val sidePanelAdRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sidePanelAdRefreshRunnable = object : Runnable {
        override fun run() {
            if (isSidePanelExpanded()) {
                LeftPanelRegistry.provider?.loadSidePanelNativeAd(
                    this@LauncherHomeActivity,
                    binding.sidePanelFragment.cardSidePanelAd
                )
                sidePanelAdRefreshHandler.postDelayed(this, 30000L)
            }
        }
    }

    private fun openSidePanel() {
        refreshSidePanelApps()
        animateSidePanel(0f)
        LeftPanelRegistry.provider?.loadSidePanelNativeAd(
            this,
            binding.sidePanelFragment.cardSidePanelAd
        )
        sidePanelAdRefreshHandler.removeCallbacks(sidePanelAdRefreshRunnable)
        sidePanelAdRefreshHandler.postDelayed(sidePanelAdRefreshRunnable, 30000L)
    }

    private fun closeSidePanel() {
        animateSidePanel(mScreenWidth.toFloat())
        sidePanelAdRefreshHandler.removeCallbacks(sidePanelAdRefreshRunnable)
    }

    // 4-Tier Deterministic Ranking:
    // Tier 1: True Recents in MRU order (last opened first)
    // Tier 2: Most Frequently / Trending used apps
    // Tier 3: Curated Popular Apps (Chrome, YouTube, WhatsApp, Camera, Settings, etc.)
    // Tier 4: Stable Alphabetical Fallback (Deterministic, Never Shuffled!)
    private fun refreshSidePanelApps() {
        val allApps = IconCache.launchers
        if (allApps.isEmpty()) return

        val appLookup = allApps.associateBy { "${it.packageName}/${it.activityName}" }
        val usedAppIds = mutableSetOf<String>()
        val finalApps = mutableListOf<AppLauncher>()

        // Tier 1: True Recents in MRU order
        val recentIds = RecentAppsManager.getRecentIdentifiers(applicationContext)
        for (id in recentIds) {
            if (finalApps.size >= RecentAppsManager.MAX_RECENT) break
            appLookup[id]?.let { app ->
                if (usedAppIds.add(id)) {
                    finalApps.add(app)
                }
            }
        }

        // Tier 2: Most Frequently / Trending used apps
        if (finalApps.size < RecentAppsManager.MAX_RECENT) {
            val frequentIds = RecentAppsManager.getFrequentIdentifiers(applicationContext)
            for (id in frequentIds) {
                if (finalApps.size >= RecentAppsManager.MAX_RECENT) break
                appLookup[id]?.let { app ->
                    if (usedAppIds.add(id)) {
                        finalApps.add(app)
                    }
                }
            }
        }

        // Tier 3: Curated Popular Apps
        if (finalApps.size < RecentAppsManager.MAX_RECENT) {
            for (pkg in RecentAppsManager.DEFAULT_POPULAR_PACKAGES) {
                if (finalApps.size >= RecentAppsManager.MAX_RECENT) break
                val matched = allApps.firstOrNull { it.packageName == pkg && "${it.packageName}/${it.activityName}" !in usedAppIds }
                if (matched != null) {
                    val id = "${matched.packageName}/${matched.activityName}"
                    if (usedAppIds.add(id)) {
                        finalApps.add(matched)
                    }
                }
            }
        }

        // Tier 4: Stable Alphabetical Fallback
        if (finalApps.size < RecentAppsManager.MAX_RECENT) {
            val remaining = allApps
                .filter { "${it.packageName}/${it.activityName}" !in usedAppIds }
                .sortedBy { it.title.lowercase() }
            for (app in remaining) {
                if (finalApps.size >= RecentAppsManager.MAX_RECENT) break
                finalApps.add(app)
            }
        }

        val iconViews = listOf(
            binding.sidePanelFragment.card1Icon1, binding.sidePanelFragment.card1Icon2,
            binding.sidePanelFragment.card1Icon3, binding.sidePanelFragment.card1Icon4,
            binding.sidePanelFragment.card2Icon1, binding.sidePanelFragment.card2Icon2,
            binding.sidePanelFragment.card2Icon3, binding.sidePanelFragment.card2Icon4
        )

        mSidePanelIconApps.clear()
        iconViews.forEachIndexed { index, imageView ->
            val app = finalApps.getOrNull(index)
            if (app != null) {
                imageView.isVisible = true
                imageView.setImageDrawable(app.drawable)
                imageView.contentDescription = app.title
                mSidePanelIconApps[imageView] = app
            } else {
                imageView.isVisible = false
            }
        }
    }

    private fun setupSidePanelTouch() {
        val icons = listOf(
            binding.sidePanelFragment.card1Icon1,
            binding.sidePanelFragment.card1Icon2,
            binding.sidePanelFragment.card1Icon3,
            binding.sidePanelFragment.card1Icon4,
            binding.sidePanelFragment.card2Icon1,
            binding.sidePanelFragment.card2Icon2,
            binding.sidePanelFragment.card2Icon3,
            binding.sidePanelFragment.card2Icon4
        )

        icons.forEach { imageView ->
            imageView.setOnClickListener {
                mSidePanelIconApps[imageView]?.let { app ->
                    RecentAppsManager.recordAppUsed(
                        applicationContext,
                        app.packageName,
                        app.activityName
                    )
                    val provider = LeftPanelRegistry.provider
                    if (provider != null) {
                        provider.onLauncherAppLaunched(this) { launchApp(app.packageName, app.activityName) }
                    } else {
                        launchApp(app.packageName, app.activityName)
                    }
                }
                closeSidePanel()
            }
        }

        binding.sidePanelFragment.cardGameSpace.setOnClickListener {
            closeSidePanel()
            val provider = LeftPanelRegistry.provider
            if (provider != null) {
                provider.openDownloadList(this)
            } else {
                LeftPanelRegistry.provider?.openGameSpace(this)
            }
        }

        binding.sidePanelFragment.cardGfxTool.setOnClickListener {
            closeSidePanel()
            val provider = LeftPanelRegistry.provider
            if (provider != null) {
                provider.openSearchTab(this)
            } else {
                LeftPanelRegistry.provider?.openGfxTool(this)
            }
        }

        LeftPanelRegistry.provider?.loadSidePanelNativeAd(
            this,
            binding.sidePanelFragment.cardSidePanelAd
        )
    }



}
