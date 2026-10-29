package com.inoviq.videodownloader.videosaver.storysaver.model

import androidx.annotation.DrawableRes

data class CategoryItem(
    val id: String,
    val title: String,
    @param:DrawableRes val iconRes: Int,
    val isSelected: Boolean = false
)
