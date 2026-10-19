package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.app.Activity
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

object AdPreCondition {
    private const val TAG = "AdPreCondition"
    fun canShowOnActivity(activity: Activity?): Boolean {
        if (activity == null) {
            Log.w(TAG, "Cannot show ad: Activity is null.")
            return false
        }
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot show ad: Activity is finishing or destroyed.")
            return false
        }
        return true
    }

    fun isAppInForeground(): Boolean {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        val inForeground = state.isAtLeast(Lifecycle.State.STARTED)
        if (!inForeground) {
            Log.w(TAG, "Operation cancelled: App is in background (State: $state).")
        }
        return inForeground
    }

    fun isAdFresh(loadTimeMs: Long, expiryDurationMs: Long): Boolean {
        if (loadTimeMs <= 0L) return false
        val ageMs = System.currentTimeMillis() - loadTimeMs
        val isFresh = ageMs < expiryDurationMs
        if (!isFresh) {
            Log.w(TAG, "Ad expired: age=${ageMs / 1000}s, max=${expiryDurationMs / 1000}s.")
        }
        return isFresh
    }
}
