package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.inoviq.videodownloader.videosaver.storysaver.databinding.DialogCustomAlertBinding

/**
 * Lifecycle-aware Network Live Observer.
 * Observes connectivity changes in real-time, displaying a non-intrusive "No Internet Connection"
 * dialog when offline, and automatically dismissing it upon network recovery.
 */
class NetworkLiveObserver(private val activity: Activity) : DefaultLifecycleObserver {

    private var noInternetDialog: Dialog? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        registerNetworkMonitor()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        checkAndShowNoInternetDialog()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dismissDialog()
        networkCallback?.let {
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            try {
                cm?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
        super.onDestroy(owner)
    }

    private fun registerNetworkMonitor() {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                activity.runOnUiThread {
                    checkAndShowNoInternetDialog()
                }
            }

            override fun onAvailable(network: Network) {
                activity.runOnUiThread {
                    dismissDialog()
                }
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(builder.build(), callback)
        } catch (_: Exception) {}
    }

    fun isNetworkAvailable(): Boolean {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkAndShowNoInternetDialog() {
        if (!isNetworkAvailable()) {
            showNoInternetDialog()
        } else {
            dismissDialog()
        }
    }

    private fun showNoInternetDialog() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (noInternetDialog?.isShowing == true) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dBinding = DialogCustomAlertBinding.inflate(activity.layoutInflater)
        dialog.setContentView(dBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val density = activity.resources.displayMetrics.density
        dialog.window?.setLayout((320 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(false)

        dBinding.tvAlertTitle.text = "No Internet Connection"
        dBinding.tvAlertMsg.text = "Please check your network settings and try again."
        dBinding.btnAlertNegative.visibility = View.GONE
        dBinding.btnAlertPositive.text = "RETRY"
        dBinding.btnAlertPositive.setOnClickListener {
            if (isNetworkAvailable()) {
                dismissDialog()
            } else {
                Toast.makeText(activity, "No internet connection detected", Toast.LENGTH_SHORT).show()
            }
        }

        noInternetDialog = dialog
        dialog.show()
    }

    private fun dismissDialog() {
        try {
            if (noInternetDialog?.isShowing == true) {
                noInternetDialog?.dismiss()
            }
        } catch (_: Exception) {}
        noInternetDialog = null
    }
}
