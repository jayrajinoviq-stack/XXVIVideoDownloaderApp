package com.inoviq.videodownloader.videosaver.storysaver.ads

import android.util.Log
import com.inoviq.videodownloader.videosaver.storysaver.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Direct Firebase Remote Config Helper.
 * Fetches and activates live parameters on startup without reflection.
 */
object RemoteConfigHelper {
    private const val TAG = "RemoteConfigHelper"

    private val stringConfigMap = mutableMapOf<String, String>()
    private val longConfigMap = mutableMapOf<String, Long>()
    private val booleanConfigMap = mutableMapOf<String, Boolean>()

    private var firebaseRemoteConfigInstance: FirebaseRemoteConfig? = null

    private var lastFetchTimeMs: Long = 0L
    private const val MIN_BACKGROUND_FETCH_INTERVAL_MS: Long = 15 * 60 * 1000L // 15 minutes throttle

    /**
     * Initializes and fetches the latest Remote Config values from Firebase.
     */
    fun init(onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "Initializing RemoteConfigHelper with FirebaseRemoteConfig...")
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            firebaseRemoteConfigInstance = remoteConfig

            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0L) // Instant fetch for real-time updates
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                lastFetchTimeMs = System.currentTimeMillis()
                Log.d(TAG, "Firebase Remote Config fetchAndActivate finished. isSuccessful=${task.isSuccessful}")
                onComplete?.invoke()
            }
            return
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing FirebaseRemoteConfig: ${e.message}", e)
        }

        onComplete?.invoke()
    }

    /**
     * Silently fetches updated Firebase config in the background when Launcher resumes.
     * Throttled to avoid unnecessary network calls (default: every 15 minutes).
     * Automatically refreshes AdsResponse upon successful fetch.
     */
    fun fetchInBackgroundIfStale(context: android.content.Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchTimeMs < MIN_BACKGROUND_FETCH_INTERVAL_MS) {
            return
        }
        lastFetchTimeMs = now
        Log.d(TAG, "fetchInBackgroundIfStale: Checking Firebase Remote Config for updates in background...")
        try {
            val remoteConfig = firebaseRemoteConfigInstance ?: FirebaseRemoteConfig.getInstance().also {
                firebaseRemoteConfigInstance = it
            }
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Background fetchAndActivate complete. Config updated=$updated")
                    AdsResponse.initialize(context)
                } else {
                    Log.w(TAG, "Background fetchAndActivate failed: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in background fetchInBackgroundIfStale", e)
        }
    }

    /**
     * Waits for Remote Config fetch with an enforced safety timeout to avoid hanging startup.
     */
    fun waitForFetch(timeoutMs: Long = 3500L, onComplete: () -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var isDone = false
        val finishAction = {
            if (!isDone) {
                isDone = true
                onComplete()
            }
        }
        handler.postDelayed({ finishAction() }, timeoutMs)
        init {
            handler.post { finishAction() }
        }
    }

    fun getString(key: String, default: String = ""): String {
        firebaseRemoteConfigInstance?.let { instance ->
            try {
                val value = instance.getString(key)
                if (value.isNotBlank()) return value
            } catch (e: Exception) {
                Log.e(TAG, "Error querying Firebase Remote Config for $key", e)
            }
        }
        return stringConfigMap[key] ?: default
    }

    fun getLong(key: String, default: Long = 1L): Long {
        firebaseRemoteConfigInstance?.let { instance ->
            try {
                // Check if stored as string in Remote Config
                val strVal = instance.getString(key)
                if (strVal.isNotBlank()) {
                    strVal.toLongOrNull()?.let { return it }
                }
                val value = instance.getLong(key)
                if (value != 0L) return value
            } catch (e: Exception) {
                Log.e(TAG, "Error querying Firebase Remote Config for $key", e)
            }
        }
        return longConfigMap[key] ?: default
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        firebaseRemoteConfigInstance?.let { instance ->
            try {
                val strVal = instance.getString(key)
                if (strVal.isNotBlank()) {
                    if (strVal.equals("true", ignoreCase = true)) return true
                    if (strVal.equals("false", ignoreCase = true)) return false
                }
                return instance.getBoolean(key)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying Firebase Remote Config for $key", e)
            }
        }
        return booleanConfigMap[key] ?: default
    }

    fun setString(key: String, value: String) {
        stringConfigMap[key] = value
    }

    fun setLong(key: String, value: Long) {
        longConfigMap[key] = value
    }

    fun setBoolean(key: String, value: Boolean) {
        booleanConfigMap[key] = value
    }
}
