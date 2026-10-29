package com.inoviq.videodownloader.videosaver.storysaver.model

import androidx.annotation.DrawableRes

data class FeatureWalkthroughItem(
    @param:DrawableRes val iconRes: Int,
    val title: String,
    val description: String
)
