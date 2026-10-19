package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe global lock to ensure no two full-screen ads (AppOpen, Interstitial, Rewarded)
 * can ever display simultaneously, preventing Google AdMob policy violations.
 *
 * Includes an automatic watchdog timer (8 seconds max duration) so that if an ad
 * or host activity is dismissed or terminated without firing callbacks, the lock
 * automatically self-heals and NEVER blocks future ads from displaying.
 */
object FullscreenAdLock {
    private const val TAG = "FullscreenAdLock"
    private val isShowingFullscreenAd = AtomicBoolean(false)
    private var lockAcquiredTimeMs = 0L
    private const val MAX_LOCK_DURATION_MS = 8_000L

    /**
     * Attempts to acquire the fullscreen lock.
     * @return true if acquired successfully, false if an ad is already presenting.
     */
    fun acquire(caller: String): Boolean {
        checkWatchdogTimeout()
        val acquired = isShowingFullscreenAd.compareAndSet(false, true)
        if (acquired) {
            lockAcquiredTimeMs = System.currentTimeMillis()
            Log.d(TAG, "[$caller] Fullscreen lock ACQUIRED.")
        } else {
            Log.w(TAG, "[$caller] Fullscreen lock DENIED - another fullscreen ad is active.")
        }
        return acquired
    }

    /**
     * Releases the fullscreen lock once the ad is dismissed or fails to show.
     */
    fun release(caller: String) {
        val wasLocked = isShowingFullscreenAd.getAndSet(false)
        lockAcquiredTimeMs = 0L
        if (wasLocked) {
            Log.d(TAG, "[$caller] Fullscreen lock RELEASED.")
        }
    }

    /**
     * Ensures stale locks held beyond [MAX_LOCK_DURATION_MS] are released automatically.
     */
    private fun checkWatchdogTimeout() {
        if (isShowingFullscreenAd.get() && lockAcquiredTimeMs > 0L) {
            val duration = System.currentTimeMillis() - lockAcquiredTimeMs
            if (duration > MAX_LOCK_DURATION_MS) {
                Log.w(TAG, "Fullscreen lock held for ${duration}ms (> ${MAX_LOCK_DURATION_MS}ms). Auto-releasing stale lock to unblock ads.")
                isShowingFullscreenAd.set(false)
                lockAcquiredTimeMs = 0L
            }
        }
    }

    /**
     * Checks if a fullscreen ad is currently actively displaying.
     */
    val isLocked: Boolean
        get() {
            checkWatchdogTimeout()
            return isShowingFullscreenAd.get()
        }

    fun isShowingFullScreenAd(): Boolean = isLocked

    /**
     * Force-clears the lock (e.g. on activity transitions, splash completion).
     */
    fun forceRelease(caller: String) {
        release(caller)
    }
}
