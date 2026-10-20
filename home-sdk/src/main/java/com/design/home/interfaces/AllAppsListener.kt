package com.design.home.interfaces

import com.design.home.models.AppLauncher

interface AllAppsListener {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher)
}
