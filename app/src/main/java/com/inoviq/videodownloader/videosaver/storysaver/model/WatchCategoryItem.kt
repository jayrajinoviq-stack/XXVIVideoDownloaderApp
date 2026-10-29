package com.inoviq.videodownloader.videosaver.storysaver.model

import androidx.annotation.DrawableRes

data class WatchCategoryItem(
    val id: String,
    val title: String,
    @param:DrawableRes val logoRes: Int,
    val isSelected: Boolean = false
)
