package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import com.design.home.activities.LauncherHomeActivity
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity

object LauncherHelper {

    /**
     * Checks if current app is set as the default HOME / Launcher application.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val currentPackage = resolveInfo?.activityInfo?.packageName
        return currentPackage != null && currentPackage == context.packageName
    }

    /**
     * Launches the system role dialog or Home settings screen to request default launcher.
     */
    fun requestDefaultLauncher(activity: Activity, roleLauncher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    try {
                        roleLauncher.launch(intent)
                        return
                    } catch (e: Exception) {
                        // Fall back to settings
                    }
                }
            }
        }
        val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS)
        try {
            roleLauncher.launch(homeSettingsIntent)
        } catch (e: Exception) {
            try {
                roleLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Opens the Launcher Home screen.
     */
    fun openLauncherHome(context: Context) {
        val intent = Intent(context, LauncherHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Opens the main downloader application, optionally focusing Downloads or Search.
     */
    fun openMainActivity(
        context: Context,
        showDownloads: Boolean = false,
        focusSearch: Boolean = false
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (showDownloads) putExtra("SHOW_DOWNLOADS", true)
            if (focusSearch) putExtra("FOCUS_SEARCH", true)
        }
        if (context is Activity) {
            val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                context,
                com.inoviq.videodownloader.videosaver.storysaver.R.anim.slide_in_left,
                com.inoviq.videodownloader.videosaver.storysaver.R.anim.no_anim
            )
            context.startActivity(intent, options.toBundle())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    com.inoviq.videodownloader.videosaver.storysaver.R.anim.slide_in_left,
                    com.inoviq.videodownloader.videosaver.storysaver.R.anim.no_anim
                )
            } else {
                @Suppress("DEPRECATION")
                context.overridePendingTransition(
                    com.inoviq.videodownloader.videosaver.storysaver.R.anim.slide_in_left,
                    com.inoviq.videodownloader.videosaver.storysaver.R.anim.no_anim
                )
            }
        } else {
            context.startActivity(intent)
        }
    }
}
