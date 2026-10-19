package com.inoviq.videodownloader.videosaver.storysaver.ads.model

/**
 * Represents the current operational state of an individual ad format.
 */
sealed class AdState {
    object Idle : AdState()
    object Loading : AdState()
    object Ready : AdState()
    object Showing : AdState()
    data class Failed(val error: String) : AdState()
}
