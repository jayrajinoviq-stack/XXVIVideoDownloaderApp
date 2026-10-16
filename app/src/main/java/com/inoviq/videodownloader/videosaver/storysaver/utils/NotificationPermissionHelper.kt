package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Window
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogNotificationPermissionBinding

object NotificationPermissionHelper {

    private const val PREFS_NAME = "xxvi_notification_prefs"
    private const val KEY_PROMPT_COUNT = "notification_system_prompt_count"
    const val REQUEST_CODE_NOTIFICATION = 1088

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getPromptCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PROMPT_COUNT, 0)
    }

    private fun incrementPromptCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_PROMPT_COUNT, 0) + 1
        prefs.edit().putInt(KEY_PROMPT_COUNT, count).apply()
        return count
    }

    /**
     * Checks and requests notification permission:
     * - System dialog is shown up to 2 times.
     * - After 2 system prompts (or if permanently denied), shows custom explanation dialog
     *   navigating directly to App Notification Settings.
     */
    fun checkAndRequestPermission(
        activity: Activity,
        onPermissionResult: ((granted: Boolean) -> Unit)? = null
    ) {
        if (isNotificationPermissionGranted(activity)) {
            onPermissionResult?.invoke(true)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onPermissionResult?.invoke(true)
            return
        }

        val promptCount = getPromptCount(activity)

        if (promptCount < 2) {
            // Trigger Android system permission dialog (up to 2 times)
            incrementPromptCount(activity)
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION
            )
            onPermissionResult?.invoke(false)
        } else {
            // Already asked 2 times via system dialog, show custom dialog directing to Settings
            showCustomPermissionDialog(activity, onPermissionResult)
        }
    }

    /**
     * Custom dialog explaining necessity of notification permissions with Settings shortcut.
     */
    fun showCustomPermissionDialog(
        activity: Activity,
        onDismiss: ((granted: Boolean) -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogNotificationPermissionBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        binding.btnCancelPermission.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke(false)
        }

        binding.btnOpenSettings.setOnClickListener {
            dialog.dismiss()
            openNotificationSettings(activity)
            onDismiss?.invoke(false)
        }

        dialog.show()
    }

    private fun openNotificationSettings(activity: Activity) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
