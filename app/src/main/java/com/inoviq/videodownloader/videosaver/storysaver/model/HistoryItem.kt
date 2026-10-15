package com.inoviq.videodownloader.videosaver.storysaver.model

data class HistoryItem(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)
