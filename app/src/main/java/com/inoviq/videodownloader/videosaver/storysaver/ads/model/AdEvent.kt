package com.inoviq.videodownloader.videosaver.storysaver.ads.model

/**
 * Lifecycle and interaction events emitted across all ad operations.
 */
sealed class AdEvent {
    data class Loaded(val adType: AdType) : AdEvent()
    data class Showed(val adType: AdType) : AdEvent()
    data class Dismissed(val adType: AdType) : AdEvent()
    data class Clicked(val adType: AdType) : AdEvent()
    data class Failed(val adType: AdType, val errorCode: Int, val message: String) : AdEvent()
    data class Skipped(val adType: AdType, val reason: String) : AdEvent()
    data class RewardEarned(val type: String, val amount: Int) : AdEvent()
    data class NotReady(val adType: AdType) : AdEvent()
}
