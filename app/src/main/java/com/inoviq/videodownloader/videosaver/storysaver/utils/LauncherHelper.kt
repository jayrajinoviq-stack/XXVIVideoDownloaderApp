package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityOptionsCompat
import com.design.home.activities.LauncherHomeActivity
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse
import com.inoviq.videodownloader.videosaver.storysaver.ads.RemoteConfigHelper
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity

object LauncherHelper {

    private const val PREFS_NAME = "app_launcher_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
    private const val KEY_SHOULD_OPEN_MAIN = "key_should_open_main_on_launcher"
    const val KEY_SHOW_ONBOARDING_EVERY_TIME = AdsResponse.KEY_SHOW_ONBOARDING_EVERY_TIME

    fun isShowOnboardingEveryTime(): Boolean {
        return AdsResponse.ShowOnboardingEveryTime
    }

    fun shouldShowOnboarding(context: Context): Boolean {
        if (isDefaultLauncher(context)) {
            return !isOnboardingCompleted(context)
        }
        if (isShowOnboardingEveryTime()) {
            return true
        }
        return !isOnboardingCompleted(context)
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        try {
            com.inoviq.videodownloader.videosaver.storysaver.repository.UserPreferencesRepository(context).isOnboardingCompleted = completed
        } catch (_: Exception) {}
    }

    fun shouldOpenMainOnNextLauncherHome(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_OPEN_MAIN, false)
    }

    fun setShouldOpenMainOnNextLauncherHome(context: Context, shouldOpen: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOULD_OPEN_MAIN, shouldOpen).apply()
    }

    /**
     * Checks if current app is set as the default HOME / Launcher application.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        return try {
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
            // If it resolves to android / ResolverActivity (meaning no default set), or to another app, we are NOT default
            if (currentPackage == null || currentPackage == "android" || currentPackage != context.packageName) {
                return false
            }

            // On Android Q+, also verify RoleManager confirms ROLE_HOME is held
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }

            true
        } catch (_: Exception) {
            false
        }
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
     * Opens MainActivity with standard slide animation.
     */
    fun openMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.slide_in_left, R.anim.no_anim)
        context.startActivity(intent, options.toBundle())
        if (context is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_left,
                R.anim.no_anim
            )
        }
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
        val options = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.slide_in_left, R.anim.no_anim)
        context.startActivity(intent, options.toBundle())
        if (context is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_left,
                R.anim.no_anim
            )
        }
    }


}
