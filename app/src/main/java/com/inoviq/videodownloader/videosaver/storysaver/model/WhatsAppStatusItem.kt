package com.inoviq.videodownloader.videosaver.storysaver.model

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class WhatsAppStatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val documentFile: DocumentFile? = null,
    val file: File? = null,
    var isSaved: Boolean = false
)
