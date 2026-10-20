package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.CookieManager
import android.widget.Toast
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object DownloadHelper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /**
     * Returns the dedicated download folder /storage/emulated/0/Download/XXVI_Downloader.
     * If the folder already exists, it is reused and not recreated.
     */
    fun getDownloadDirectory(context: Context): File {
        val pubDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(pubDownloadDir, "XXVI_Downloader")
        if (!targetDir.exists()) {
            try {
                targetDir.mkdirs()
            } catch (_: Exception) {}
        }
        return targetDir
    }

    fun derivePlatformPrefix(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("instagram") || lower.contains("cdninstagram") -> "instagram"
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fbcdn.net") -> "facebook"
            lower.contains("tiktok.com") || lower.contains("tiktokcdn.com") || lower.contains("musical.ly") -> "tiktok"
            lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("twimg.com") -> "twitter"
            lower.contains("whatsapp.com") || lower.contains("whatsapp.net") -> "whatsapp"
            lower.contains("snapchat.com") || lower.contains("sc-cdn.net") -> "snapchat"
            lower.contains("pinterest.com") || lower.contains("pinimg.com") -> "pinterest"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "youtube"
            lower.contains("reddit.com") || lower.contains("redd.it") -> "reddit"
            else -> {
                try {
                    val host = Uri.parse(url).host ?: ""
                    val clean = host.removePrefix("www.").removePrefix("m.").split(".").firstOrNull()
                    if (!clean.isNullOrBlank() && clean.length in 3..15) clean else "download"
                } catch (_: Exception) {
                    "download"
                }
            }
        }
    }

    /**
     * Strips range/DASH chunk parameters (bytestart/byteend) and unescapes unicode escapes (\u0026, \/).
     */
    fun cleanMediaUrl(rawUrl: String): String {
        var u = rawUrl.trim()
        u = u.replace("\\u0026", "&")
        u = u.replace("&amp;", "&")
        u = u.replace("\\/", "/")
        u = u.replace(Regex("([?&])bytestart=\\d+(&|$)"), "$1")
        u = u.replace(Regex("([?&])byteend=\\d+(&|$)"), "$1")
        u = u.replace("?&", "?")
        if (u.endsWith("?") || u.endsWith("&")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    /**
     * Checks if a URL represents a user profile avatar, thumbnail, or tracking pixel
     * rather than downloadable post content.
     */
    fun isProfileOrAvatarUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val lower = url.lowercase()
        return lower.contains("2885-19") ||
                lower.contains("/s150x150/") ||
                lower.contains("/s320x320/") ||
                lower.contains("/s50x50/") ||
                lower.contains("/s64x64/") ||
                lower.contains("/s100x100/") ||
                lower.contains("profile_pic") ||
                lower.contains("profilepic") ||
                lower.contains("profile_photo") ||
                lower.contains("avatar") ||
                lower.contains("p50x50") ||
                lower.contains("p100x100") ||
                lower.contains("p160x160") ||
                lower.contains("rsrc.php") ||
                lower.contains("emoji.php") ||
                lower.contains("blank.gif") ||
                lower.contains("spacer.gif")
    }


    fun downloadMedia(
        context: Context,
        url: String,
        type: String, // "video", "image", or "story"
        customName: String? = null,
        thumbUrl: String? = null,
        onComplete: ((MediaItem) -> Unit)? = null
    ): MediaItem? {
        if (url.isBlank()) return null
        val cleanUrl = cleanMediaUrl(url)

        val isAudio = type.contains("audio", ignoreCase = true) || customName?.contains("audio", ignoreCase = true) == true
        val isVideo = !isAudio && (type.contains("video", ignoreCase = true) ||
                type.contains("reel", ignoreCase = true) ||
                cleanUrl.contains(".mp4") ||
                cleanUrl.contains(".webm"))
        val ext = when {
            isAudio -> ".mp3"
            isVideo -> ".mp4"
            else -> ".jpg"
        }

        val platform = derivePlatformPrefix(cleanUrl)
        val mediaTag = when {
            isAudio -> "_audio"
            isVideo -> "_video"
            else -> "_img"
        }

        var cleanCustom = customName?.trim() ?: ""

        // Strip known file extensions if present
        cleanCustom = cleanCustom.replace(Regex("(?i)\\.(mp4|mp3|m4a|jpg|jpeg|png|webp|webm)$"), "")

        // Strip known format or duplicate type suffixes
        val formatSuffixRegex = Regex("(?i)_(mp4|mp3|m4a|jpg|jpeg|png|webp|webm|photo|image|img|video|audio)(_\\d+)?$")
        while (formatSuffixRegex.containsMatchIn(cleanCustom)) {
            cleanCustom = cleanCustom.replace(formatSuffixRegex, "")
        }

        val rawName = if (cleanCustom.isNotBlank() &&
            !cleanCustom.equals("facebook", true) &&
            !cleanCustom.equals("instagram", true) &&
            !cleanCustom.equals("pinterest", true) &&
            !cleanCustom.equals("tiktok", true) &&
            !cleanCustom.equals("media", true) &&
            !cleanCustom.equals("download", true)) {
            cleanCustom.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        } else {
            "${platform}_${System.currentTimeMillis() % 1000000}"
        }

        val baseName = "${rawName.trimEnd('_')}$mediaTag"

        // Dedicated target directory: /storage/emulated/0/Download/XXVI_Downloader
        val targetDir = getDownloadDirectory(context)
        var destFile = File(targetDir, "$baseName$ext")
        if (destFile.exists()) {
            var counter = 1
            while (File(targetDir, "${baseName}_$counter$ext").exists()) {
                counter++
            }
            destFile = File(targetDir, "${baseName}_$counter$ext")
        }

        val fileName = destFile.name

        val mediaItem = MediaItem(
            id = "${System.currentTimeMillis()}_${(100..999).random()}",
            title = fileName,
            url = cleanUrl,
            thumbnailUrl = if (!thumbUrl.isNullOrBlank() && thumbUrl.startsWith("http")) thumbUrl else cleanUrl,
            sizeFormatted = if (isAudio) "165 kB" else if (isVideo) "3.42 MB" else "15.67kB",
            isVideo = isVideo,
            isAudio = isAudio,
            downloadProgress = 0,
            isDownloaded = false,
            localPath = destFile.absolutePath
        )

        // Save to persistent store so it appears in Files tab immediately with progress indicator
        DownloadStore.saveDownloadedItem(context, mediaItem)

        // Register completion callback if provided
        if (onComplete != null) {
            val listener = object : DownloadProgressManager.DownloadProgressListener {
                override fun onDownloadStarted(item: MediaItem) {}
                override fun onProgressUpdated(item: MediaItem) {}
                override fun onDownloadCompleted(item: MediaItem) {
                    if (item.id == mediaItem.id) {
                        DownloadProgressManager.unregisterListener(this)
                        onComplete.invoke(item)
                    }
                }
                override fun onDownloadFailed(item: MediaItem, error: String) {
                    if (item.id == mediaItem.id) {
                        DownloadProgressManager.unregisterListener(this)
                    }
                }
            }
            DownloadProgressManager.registerListener(listener)
        }

        // Delegate to Foreground DownloadService
        com.inoviq.videodownloader.videosaver.storysaver.service.DownloadService.startDownload(context, mediaItem)

        Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        return mediaItem
    }

    private fun startDirectDownload(
        context: Context,
        url: String,
        destFile: File,
        item: MediaItem,
        onComplete: ((MediaItem) -> Unit)?
    ) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return

        thread(start = true, name = "XXVI_Downloader_${item.id}") {
            var activeFile = destFile
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                val referer = when {
                    url.contains("instagram", ignoreCase = true) || url.contains("cdninstagram", ignoreCase = true) -> "https://www.instagram.com/"
                    url.contains("fbcdn.net", ignoreCase = true) || url.contains("facebook.com", ignoreCase = true) -> "https://www.facebook.com/"
                    url.contains("pinimg.com", ignoreCase = true) || url.contains("pinterest.com", ignoreCase = true) -> "https://www.pinterest.com/"
                    url.contains("twimg.com", ignoreCase = true) || url.contains("twitter.com", ignoreCase = true) || url.contains("x.com", ignoreCase = true) -> "https://twitter.com/"
                    url.contains("tiktokcdn.com", ignoreCase = true) || url.contains("tiktok.com", ignoreCase = true) -> "https://www.tiktok.com/"
                    url.contains("whatsapp.net", ignoreCase = true) || url.contains("whatsapp.com", ignoreCase = true) -> "https://web.whatsapp.com/"
                    url.contains("sc-cdn.net", ignoreCase = true) || url.contains("snapchat.com", ignoreCase = true) -> "https://www.snapchat.com/"
                    else -> {
                        try {
                            val parsed = Uri.parse(url)
                            "${parsed.scheme}://${parsed.host}/"
                        } catch (_: Exception) {
                            "https://www.google.com/"
                        }
                    }
                }
                conn.setRequestProperty("Referer", referer)

                val cm = CookieManager.getInstance()
                val urlCookie = cm.getCookie(url)
                val parentCookie = if (referer.startsWith("https://")) cm.getCookie(referer) else null
                val finalCookie = listOfNotNull(urlCookie, parentCookie).filter { it.isNotBlank() }.distinct().joinToString("; ")
                if (finalCookie.isNotBlank()) {
                    conn.setRequestProperty("Cookie", finalCookie)
                }

                conn.connect()

                if (conn.responseCode in 200..299) {
                    var mediaStoreUri: Uri? = null
                    var outputStream: OutputStream? = null

                    // 1. On Android 10+ (API 29+), use MediaStore.Downloads to save directly into Download/XXVI_Downloader
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, activeFile.name)
                                put(MediaStore.MediaColumns.MIME_TYPE, if (item.isVideo) "video/mp4" else "image/jpeg")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/XXVI_Downloader")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            if (uri != null) {
                                val stream = context.contentResolver.openOutputStream(uri)
                                if (stream != null) {
                                    mediaStoreUri = uri
                                    outputStream = stream
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 2. Direct FileOutputStream into /storage/emulated/0/Download/XXVI_Downloader (API <= 28 or fallback)
                    if (outputStream == null) {
                        try {
                            val parent = activeFile.parentFile
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs()
                            }
                            outputStream = FileOutputStream(activeFile)
                        } catch (e: Exception) {
                            // 3. Fallback to app external directory if public path is strictly denied
                            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "XXVI_Downloader")
                            if (!fallbackDir.exists()) fallbackDir.mkdirs()
                            activeFile = File(fallbackDir, activeFile.name)
                            outputStream = FileOutputStream(activeFile)
                        }
                    }

                    val input = conn.inputStream
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    outputStream.flush()
                    outputStream.close()
                    input.close()

                    // Mark download finished in MediaStore
                    if (mediaStoreUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            }
                            context.contentResolver.update(mediaStoreUri, updateValues, null, null)
                        } catch (_: Exception) {}
                    }

                    // Resolve final local path on disk
                    var localPath = activeFile.absolutePath
                    if (mediaStoreUri != null) {
                        try {
                            context.contentResolver.query(mediaStoreUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                    if (colIdx != -1) {
                                        val resolved = cursor.getString(colIdx)
                                        if (!resolved.isNullOrBlank()) {
                                            localPath = resolved
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Update media item with accurate size & local path
                    val sizeMb = totalBytes / (1024.0 * 1024.0)
                    val sizeKb = totalBytes / 1024.0
                    val formatted = if (sizeMb >= 1.0) String.format("%.2f MB", sizeMb) else String.format("%.1f kB", sizeKb)

                    var savedThumbPath: String? = null
                    if (item.isVideo) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            if (File(localPath).exists()) {
                                retriever.setDataSource(localPath)
                            } else if (mediaStoreUri != null) {
                                retriever.setDataSource(context, mediaStoreUri)
                            }
                            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: retriever.frameAtTime
                            if (bitmap != null) {
                                val thumbFile = File(activeFile.parentFile, "${activeFile.nameWithoutExtension}_thumb.jpg")
                                val outStream: OutputStream = FileOutputStream(thumbFile)
                                outStream.use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                savedThumbPath = thumbFile.absolutePath
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(thumbFile.absolutePath),
                                    arrayOf("image/jpeg"),
                                    null
                                )
                            }
                            retriever.release()
                        } catch (_: Exception) {}
                    }

                    val finalThumb = savedThumbPath
                        ?: if (item.isVideo) (if (!item.thumbnailUrl.isNullOrBlank() && item.thumbnailUrl.startsWith("http")) item.thumbnailUrl else "")
                        else localPath

                    val updatedItem = item.copy(
                        sizeBytes = totalBytes,
                        sizeFormatted = formatted,
                        localPath = localPath,
                        thumbnailUrl = finalThumb
                    )

                    DownloadStore.saveDownloadedItem(context, updatedItem)

                    // Notify Android gallery and system media scanner for the downloaded media
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(localPath),
                        arrayOf(if (item.isVideo) "video/mp4" else "image/jpeg"),
                        null
                    )

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Saved to ${activeFile.name}", Toast.LENGTH_SHORT).show()
                        onComplete?.invoke(updatedItem)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
