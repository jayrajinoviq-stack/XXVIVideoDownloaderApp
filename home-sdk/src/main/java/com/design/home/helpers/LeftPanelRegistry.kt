package com.design.home.helpers

import com.design.home.interfaces.LeftPanelProvider

/**
 * Global registry that decouples the app module from LauncherHomeActivity's class hierarchy.
 *
 * The app module sets [provider] once (e.g. in LauncherActivity or Application).
 * LauncherHomeActivity reads it on every left-swipe gesture.
 *
 * No circular dependency: app only imports home-sdk interfaces, never LauncherHomeActivity.
 */
object LeftPanelRegistry {
    @Volatile
    var provider: LeftPanelProvider? = null
}
