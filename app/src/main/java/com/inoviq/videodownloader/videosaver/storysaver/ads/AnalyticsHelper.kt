package com.inoviq.videodownloader.videosaver.storysaver.ads

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Enterprise-grade Analytics Helper for Google Firebase Analytics.
 * Handles tracking for:
 * - Confirmed impressions across all formats (ad_impression_confirmed)
 * - Specific failure tags for main vs fallback ads (fail_main_*, fail_sub_*)
 * - Request control auditing and performance metrics
 */
object AnalyticsHelper {

    private const val TAG = "AnalyticsHelper"

    // GA4 hard limits
    private const val MAX_EVENT_NAME_LEN = 40
    private const val MAX_PARAM_NAME_LEN = 40
    private const val MAX_PARAM_VALUE_LEN = 100

    private var firebaseAnalytics: FirebaseAnalytics? = null

    // Session-level metrics counters
    private val totalConfirmedImpressions = AtomicLong(0L)
    private val formatConfirmedCounts = ConcurrentHashMap<String, AtomicLong>()

    private val totalFailCount = AtomicLong(0L)
    private val formatFailCounts = ConcurrentHashMap<String, AtomicLong>()
    private val tagFailCounts = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Initializes Firebase Analytics with Application context.
     */
    fun init(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
            Log.d(TAG, "FirebaseAnalytics initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseAnalytics: ${e.message}")
        }
    }

    private fun getAnalytics(): FirebaseAnalytics? {
        if (firebaseAnalytics == null) {
            try {
                // Attempt lazy fallback if initialized in myApp without calling init here
                val appClass = Class.forName("com.google.firebase.FirebaseApp")
                val getInstanceMethod = appClass.getMethod("getInstance")
                val appInstance = getInstanceMethod.invoke(null)
                if (appInstance != null) {
                    val getContextMethod = appClass.getMethod("getApplicationContext")
                    val ctx = getContextMethod.invoke(appInstance) as? Context
                    if (ctx != null) {
                        firebaseAnalytics = FirebaseAnalytics.getInstance(ctx)
                    }
                }
            } catch (e: Exception) {
                // Silent catch, will log warning if null
            }
        }
        return firebaseAnalytics
    }

    /**
     * Internal safe event logger enforcing GA4 constraints.
     */
    @Suppress("DEPRECATION")
    private fun logEvent(name: String, params: Bundle? = null) {
        try {
            val safeName = name.trim()
            if (safeName.isBlank()) {
                Log.w(TAG, "Dropped event: blank name")
                return
            }
            if (safeName.length > MAX_EVENT_NAME_LEN) {
                Log.w(TAG, "Dropped event '$safeName': name exceeds $MAX_EVENT_NAME_LEN chars")
                return
            }

            params?.keySet()?.forEach { key ->
                if (key.length > MAX_PARAM_NAME_LEN) {
                    Log.w(TAG, "Event '$safeName': param '$key' exceeds limit, removing.")
                    params.remove(key)
                    return@forEach
                }
                val value = params.get(key)
                if (value is String && value.length > MAX_PARAM_VALUE_LEN) {
                    params.putString(key, value.take(MAX_PARAM_VALUE_LEN))
                }
            }

            Log.d(TAG, "[$safeName] -> ${params?.let { bundleToString(it) } ?: "{}"}")
            getAnalytics()?.logEvent(safeName, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event '$name'", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun bundleToString(bundle: Bundle): String =
        bundle.keySet().joinToString(prefix = "{", postfix = "}") { key -> "$key=${bundle.get(key)}" }

    // =========================================================================
    // CONFIRMED IMPRESSION EVENTS (All Formats)
    // =========================================================================

    /**
     * Logs confirmed impression for any ad format.
     * Increments running totals so you can evaluate which ad units/formats confirm more.
     *
     * @param adFormat format name: "interstitial", "app_open", "rewarded", "rewarded_interstitial", "banner", "native"
     * @param adUnitId the actual ad unit ID
     * @param isSub true if served via fallback/sub ID, false if primary/main ID
     * @param adType "admob" or "web_custom"
     */
    fun logAdImpressionConfirmed(
        adFormat: String,
        adUnitId: String,
        isSub: Boolean = false,
        adType: String = "admob"
    ) {
        val total = totalConfirmedImpressions.incrementAndGet()
        val formatCount = formatConfirmedCounts
            .computeIfAbsent(adFormat) { AtomicLong(0L) }
            .incrementAndGet()

        val bundle = Bundle().apply {
            putString("ad_format", adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString("ad_tier", if (isSub) "sub" else "main")
            putLong("is_sub", if (isSub) 1L else 0L)
            putString("ad_type", adType)
            putLong("session_confirmed_total", total)
            putLong("format_confirmed_total", formatCount)
        }

        // Standard confirmed event
        logEvent("ad_impression_confirmed", bundle)

        // Format-specific confirmed event (e.g., confirm_interstitial, confirm_app_open)
        val formatConfirmEvent = "confirm_${adFormat.lowercase()}"
        if (formatConfirmEvent.length <= MAX_EVENT_NAME_LEN) {
            logEvent(formatConfirmEvent, bundle)
        }
    }

    // =========================================================================
    // FAILURE TRACKING (Main vs Sub Specific Events)
    // =========================================================================

    /**
     * Logs failure for primary/main ad.
     * Emits: fail_main_<format> (e.g. fail_main_interstitial) + generic ad_load_failed.
     */
    fun logMainAdFailed(
        adFormat: String,
        adUnitId: String,
        errorCode: Int,
        errorMessage: String
    ) {
        val total = totalFailCount.incrementAndGet()
        val formatCount = formatFailCounts
            .computeIfAbsent(adFormat) { AtomicLong(0L) }
            .incrementAndGet()

        val eventName = "fail_main_${adFormat.lowercase()}"
        tagFailCounts.computeIfAbsent(eventName) { AtomicLong(0L) }.incrementAndGet()

        val bundle = Bundle().apply {
            putString("ad_format", adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString("fail_tier", "main")
            putInt("error_code", errorCode)
            putString("error_message", errorMessage.take(MAX_PARAM_VALUE_LEN))
            putLong("total_fail_count", total)
            putLong("format_fail_count", formatCount)
        }

        // 1. Specific tag event requested: fail_main_interstitial, etc.
        logEvent(eventName, bundle)

        // 2. Aggregate ad_load_failed event
        val generalBundle = Bundle(bundle).apply {
            putString("fail_tag", eventName)
        }
        logEvent("ad_load_failed", generalBundle)
    }

    /**
     * Logs failure for fallback/sub ad.
     * Emits: fail_sub_<format> (e.g. fail_sub_interstitial) + generic ad_load_failed.
     */
    fun logSubAdFailed(
        adFormat: String,
        adUnitId: String,
        errorCode: Int,
        errorMessage: String
    ) {
        val total = totalFailCount.incrementAndGet()
        val formatCount = formatFailCounts
            .computeIfAbsent(adFormat) { AtomicLong(0L) }
            .incrementAndGet()

        val eventName = "fail_sub_${adFormat.lowercase()}"
        tagFailCounts.computeIfAbsent(eventName) { AtomicLong(0L) }.incrementAndGet()

        val bundle = Bundle().apply {
            putString("ad_format", adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString("fail_tier", "sub")
            putInt("error_code", errorCode)
            putString("error_message", errorMessage.take(MAX_PARAM_VALUE_LEN))
            putLong("total_fail_count", total)
            putLong("format_fail_count", formatCount)
        }

        // 1. Specific tag event requested: fail_sub_interstitial, etc.
        logEvent(eventName, bundle)

        // 2. Aggregate ad_load_failed event
        val generalBundle = Bundle(bundle).apply {
            putString("fail_tag", eventName)
        }
        logEvent("ad_load_failed", generalBundle)
    }

    // =========================================================================
    // INTERACTION & REVENUE TRACKING
    // =========================================================================

    fun logAdClicked(
        adFormat: String,
        adUnitId: String,
        isSub: Boolean = false
    ) {
        val bundle = Bundle().apply {
            putString("ad_format", adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString("ad_tier", if (isSub) "sub" else "main")
        }
        logEvent("ad_clicked", bundle)
    }

    fun logAdRevenue(
        adPlatform: String,
        adSource: String,
        adFormat: String,
        adUnitId: String,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int? = null
    ) {
        if (valueMicros <= 0L || currencyCode.isBlank()) return

        val revenue = valueMicros / 1_000_000.0
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, adPlatform)
            putString(FirebaseAnalytics.Param.AD_SOURCE, adSource)
            putString(FirebaseAnalytics.Param.AD_FORMAT, adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putDouble(FirebaseAnalytics.Param.VALUE, revenue)
            putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)

            precisionType?.let { putInt("ad_precision_type", it) }
        }
        logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
    }

    // =========================================================================
    // STATS & QUERY HELPERS
    // =========================================================================

    fun getTotalConfirmedImpressions(): Long = totalConfirmedImpressions.get()

    fun getFormatConfirmedCount(format: String): Long =
        formatConfirmedCounts[format]?.get() ?: 0L

    fun getTotalFailCount(): Long = totalFailCount.get()

    fun getTagFailCount(tag: String): Long =
        tagFailCounts[tag]?.get() ?: 0L
}
