package com.design.home.interfaces

import android.app.Activity
import android.view.ViewGroup

/**
 * SDK-side callback interface. The app module implements this and registers it with
 * [com.design.home.helpers.LeftPanelRegistry.provider]. When the user swipes
 * left-to-right on the first home page the launcher calls [openLeftPanel].
 *
 * This pattern is the standard way to bridge sdk → app without creating a circular dependency.
 */
interface LeftPanelProvider {
    /** Called by the launcher when the left-swipe gesture fires on page 0. */
    fun openLeftPanel(activity: Activity)

    /** Called when the Download List card is clicked in the side panel. */
    fun openDownloadList(activity: Activity) { }

    /** Called when the Search Tab card is clicked in the side panel. */
    fun openSearchTab(activity: Activity) { }

    /** Backwards-compatible aliases */
    fun openGameSpace(activity: Activity) { openDownloadList(activity) }
    fun openGfxTool(activity: Activity) { openSearchTab(activity) }

    /** Load small/large native ad into the side panel's ad container */
    fun loadSidePanelNativeAd(activity: Activity, container: ViewGroup) { }

    /** Load small native ad into the settings screen's ad container */
    fun loadSettingsNativeAd(activity: Activity, container: ViewGroup) { }

    /**
     * Called just before the left/side panel opens (swipe or fling right).
     */
    fun onLauncherPanelOpen(activity: Activity) { }

    /**
     * Called just before an app is launched from the home grid or side panel.
     * [onComplete] MUST always be called — it triggers the actual app launch.
     */
    fun onLauncherAppLaunched(activity: Activity, onComplete: () -> Unit) { onComplete() }
}
