package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.app.Activity
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Robust Google Play In-App Update manager.
 * - Checks Google Play for available app updates
 * - Supports Flexible and Immediate update flows
 * - Listens for download completion and notifies user to restart/complete update
 * - Resumes pending updates on Activity resume
 */
class InAppUpdateHelper(
    private val activity: AppCompatActivity,
    private val rootView: View? = null,
    private val updateType: Int = AppUpdateType.FLEXIBLE
) {
    companion object {
        private const val TAG = "InAppUpdateHelper"
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private val installStateListener = InstallStateUpdatedListener { state: InstallState ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytes = state.totalBytesToDownload()
                if (totalBytes > 0) {
                    val progress = (bytesDownloaded * 100 / totalBytes).toInt()
                    Log.d(TAG, "Update downloading: $progress%")
                }
            }
            InstallStatus.DOWNLOADED -> {
                Log.d(TAG, "Update download completed! Showing restart prompt.")
                showUpdateDownloadedPrompt()
            }
            InstallStatus.FAILED -> {
                Log.e(TAG, "Update install failed with error code: ${state.installErrorCode()}")
            }
            InstallStatus.CANCELED -> {
                Log.w(TAG, "Update download canceled by user.")
            }
            else -> {}
        }
    }

    init {
        // Register modern ActivityResultLauncher
        updateLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "Update flow cancelled or failed by user. Result code: ${result.resultCode}")
            } else {
                Log.d(TAG, "User accepted the update.")
            }
        }

        appUpdateManager.registerListener(installStateListener)
    }

    /**
     * Checks Google Play Store for new version availability and starts the update flow.
     */
    fun checkForUpdate() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                val availability = appUpdateInfo.updateAvailability()
                Log.d(TAG, "checkForUpdate: availability=$availability, versionCode=${appUpdateInfo.availableVersionCode()}")

                if (availability == UpdateAvailability.UPDATE_AVAILABLE) {
                    val isAllowed = if (updateType == AppUpdateType.FLEXIBLE) {
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    } else {
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    }

                    if (isAllowed) {
                        startUpdate(appUpdateInfo, updateType)
                    } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        startUpdate(appUpdateInfo, AppUpdateType.FLEXIBLE)
                    }
                } else if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // Resume an immediate update in progress
                    startUpdate(appUpdateInfo, AppUpdateType.IMMEDIATE)
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "Failed to check for Google Play updates: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking Google Play update", e)
        }
    }

    private fun startUpdate(appUpdateInfo: AppUpdateInfo, type: Int) {
        val launcher = updateLauncher ?: return
        try {
            val options = AppUpdateOptions.newBuilder(type).build()
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, launcher, options)
            Log.d(TAG, "Started Google Play update flow (type=$type)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Google Play update flow", e)
        }
    }

    /**
     * Called in Activity onResume to handle completed downloads or resume immediate updates.
     */
    fun onResume() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    showUpdateDownloadedPrompt()
                } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    startUpdate(appUpdateInfo, AppUpdateType.IMMEDIATE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume update check", e)
        }
    }

    /**
     * Unregisters the listener when Activity is destroyed to prevent memory leaks.
     */
    fun onDestroy() {
        try {
            appUpdateManager.unregisterListener(installStateListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering installStateListener", e)
        }
    }

    /**
     * Prompts the user that the update has downloaded and prompts them to restart.
     */
    private fun showUpdateDownloadedPrompt() {
        val view = rootView ?: activity.findViewById<View>(android.R.id.content) ?: return
        Snackbar.make(
            view,
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Restart") {
            appUpdateManager.completeUpdate()
        }.show()
    }
}
