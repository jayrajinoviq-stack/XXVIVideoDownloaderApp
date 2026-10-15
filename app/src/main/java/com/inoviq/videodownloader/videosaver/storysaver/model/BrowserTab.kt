package com.inoviq.videodownloader.videosaver.storysaver.model

import java.io.Serializable

data class BrowserTab(
    val id: String,
    var title: String,
    var url: String,
    var isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
