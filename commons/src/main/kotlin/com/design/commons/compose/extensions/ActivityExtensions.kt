package com.design.commons.compose.extensions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.design.commons.R
import com.design.commons.extensions.baseConfig
import com.design.commons.extensions.checkAppIconColor
import com.design.commons.extensions.getAppIconColors
import com.design.commons.extensions.getCanAppBeUpgraded
import com.design.commons.extensions.getInternalStoragePath
import com.design.commons.extensions.isAProApp
import com.design.commons.extensions.isAppInstalledOnSDCard
import com.design.commons.extensions.isOrWasThankYouInstalled
import com.design.commons.extensions.launchViewIntent
import com.design.commons.extensions.random
import com.design.commons.extensions.toggleAppIconColor
import com.design.commons.extensions.updateSDCardPath
import com.design.commons.helpers.isOreoMr1Plus
import com.design.commons.models.Release

fun ComponentActivity.appLaunchedCompose(
    appId: String,
    showUpgradeDialog: () -> Unit,
    showDonateDialog: () -> Unit,
) {
    baseConfig.internalStoragePath = getInternalStoragePath()
    updateSDCardPath()
    baseConfig.appId = appId
    if (baseConfig.appRunCount == 0) {
        baseConfig.wasOrangeIconChecked = true
        checkAppIconColor()
    } else if (!baseConfig.wasOrangeIconChecked) {
        baseConfig.wasOrangeIconChecked = true
        val primaryColor = ContextCompat.getColor(this, R.color.color_primary)
        if (baseConfig.appIconColor != primaryColor) {
            getAppIconColors().forEachIndexed { index, color ->
                toggleAppIconColor(appId, index, color, false)
            }

            val defaultClassName = "${baseConfig.appId.removeSuffix(".debug")}.activities.SplashActivity"
            packageManager.setComponentEnabledSetting(
                ComponentName(baseConfig.appId, defaultClassName),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )

            val greenClassName = "${baseConfig.appId.removeSuffix(".debug")}.activities.SplashActivity.Green"
            packageManager.setComponentEnabledSetting(
                ComponentName(baseConfig.appId, greenClassName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            baseConfig.appIconColor = primaryColor
            baseConfig.lastIconColor = primaryColor
        }
    }

    baseConfig.appRunCount++
}


fun Context.fakeVersionCheck(
    showConfirmationDialog: () -> Unit
) {
//    if (!packageName.startsWith("org.fossify.", true)) {
//        if ((0..50).random() == 10 || baseConfig.appRunCount % 100 == 0) {
//            showConfirmationDialog()
//        }
//    }
}

fun ComponentActivity.appOnSdCardCheckCompose(
    showConfirmationDialog: () -> Unit
) {
    if (!baseConfig.wasAppOnSDShown && isAppInstalledOnSDCard()) {
        baseConfig.wasAppOnSDShown = true
        showConfirmationDialog()
    }
}

fun Activity.setShowWhenLockedCompat(showWhenLocked: Boolean) {
    if (isOreoMr1Plus()) {
        setShowWhenLocked(showWhenLocked)
    } else {
        val flagsToUpdate =
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        if (showWhenLocked) {
            window.addFlags(flagsToUpdate)
        } else {
            window.clearFlags(flagsToUpdate)
        }
    }
}

fun Activity.setTurnScreenOnCompat(turnScreenOn: Boolean) {
    if (isOreoMr1Plus()) {
        setTurnScreenOn(turnScreenOn)
    } else {
        val flagToUpdate = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (turnScreenOn) {
            window.addFlags(flagToUpdate)
        } else {
            window.clearFlags(flagToUpdate)
        }
    }
}

fun Activity.setFullscreenCompat(fullScreen: Boolean) {
    if (isOreoMr1Plus()) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).hide(WindowInsetsCompat.Type.statusBars())
    } else {
        val flagToUpdate = WindowManager.LayoutParams.FLAG_FULLSCREEN
        if (fullScreen) {
            window.addFlags(flagToUpdate)
        } else {
            window.clearFlags(flagToUpdate)
        }
    }
}
