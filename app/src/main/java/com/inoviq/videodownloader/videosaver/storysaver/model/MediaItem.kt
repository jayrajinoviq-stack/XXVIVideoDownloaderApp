package com.inoviq.videodownloader.videosaver.storysaver.model

import java.io.Serializable

data class MediaItem(
    val id: String,
    var title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val sizeBytes: Long = 0L,
    val sizeFormatted: String = "15.67kB",
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    var isChecked: Boolean = true,
    var downloadProgress: Int = 0,
    var isDownloaded: Boolean = false,
    var isPaused: Boolean = false,
    var localPath: String? = null,
    val hdUrl: String? = null,
    val sdUrl: String? = null,
    var quality: String? = null
) : Serializable
