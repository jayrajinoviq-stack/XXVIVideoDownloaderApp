package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.min
import kotlin.math.pow

class AdLoader(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 2000L,
    private val maxDelayMs: Long = 30000L
) {
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())

    val canRetry: Boolean
        get() = retryCount < maxRetries

    fun scheduleRetry(tag: String, action: () -> Unit) {
        if (!canRetry) {
            Log.w(TAG, "[$tag] Reached maximum retry limit ($maxRetries). Halting requests.")
            return
        }

        val delay = min(
            maxDelayMs,
            (baseDelayMs * 2.0.pow(retryCount.toDouble())).toLong()
        )
        retryCount++
        Log.d(TAG, "[$tag] Scheduling retry #$retryCount in ${delay}ms")

        handler.postDelayed({
            action.invoke()
        }, delay)
    }

    fun reset() {
        retryCount = 0
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "AdLoader"
    }
}
