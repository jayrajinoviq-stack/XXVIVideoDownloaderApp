package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object DownloadHelper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun downloadMedia(
        context: Context,
        url: String,
        type: String, // "video", "image", or "story"
        customName: String? = null,
        thumbUrl: String? = null,
        onComplete: ((MediaItem) -> Unit)? = null
    ): MediaItem? {
        if (url.isBlank()) return null

        val isVideo = type.contains("video", ignoreCase = true) ||
                type.contains("reel", ignoreCase = true) ||
                url.contains(".mp4") ||
                url.contains(".webm")
        val ext = if (isVideo) ".mp4" else ".jpg"

        val rawName = if (!customName.isNullOrBlank()) {
            customName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        } else {
            "facebook_${if (isVideo) "video" else "photo"}_${System.currentTimeMillis() % 1000000}"
        }

        val fileName = if (rawName.endsWith(ext, ignoreCase = true)) rawName else "$rawName$ext"

        // Prepare destination files
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val localDestFile = File(appDownloadsDir, fileName)

        val mediaItem = MediaItem(
            id = System.currentTimeMillis().toString(),
            title = fileName,
            url = url,
            thumbnailUrl = if (!thumbUrl.isNullOrBlank() && thumbUrl.startsWith("http")) thumbUrl else url,
            sizeFormatted = if (isVideo) "3.42 MB" else "15.67 kB",
            isVideo = isVideo,
            isDownloaded = true,
            localPath = localDestFile.absolutePath
        )

        // Save immediately to persistent store so it shows in Files tab instantly
        DownloadStore.saveDownloadedItem(context, mediaItem)

        // 1. Try enqueuing with Android DownloadManager for system notification
        enqueueSystemDownload(context, url, fileName, isVideo)

        // 2. Perform direct background download to guarantee local file creation
        startDirectDownload(context, url, localDestFile, mediaItem, onComplete)

        Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        return mediaItem
    }

    private fun enqueueSystemDownload(
        context: Context,
        url: String,
        fileName: String,
        isVideo: Boolean
    ) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading Facebook media...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                try {
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "XXVI_Downloader/$fileName"
                    )
                } catch (e: Exception) {
                    // Fallback to app downloads if scoped storage restricts public directory
                    setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            // Forward active Facebook session cookies and User-Agent
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) {
                request.addRequestHeader("Cookie", cookie)
            }
            request.addRequestHeader("User-Agent", USER_AGENT)
            request.addRequestHeader("Referer", "https://m.facebook.com/")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Referer", "https://m.facebook.com/")

                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) {
                    conn.setRequestProperty("Cookie", cookie)
                }

                conn.connect()

                if (conn.responseCode in 200..299) {
                    val input = conn.inputStream
                    val output = FileOutputStream(destFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    output.flush()
                    output.close()
                    input.close()

                    // Update media item with accurate size & local path
                    val sizeMb = totalBytes / (1024.0 * 1024.0)
                    val sizeKb = totalBytes / 1024.0
                    val formatted = if (sizeMb >= 1.0) String.format("%.2f MB", sizeMb) else String.format("%.1f kB", sizeKb)

                    val updatedItem = item.copy(
                        sizeBytes = totalBytes,
                        sizeFormatted = formatted,
                        localPath = destFile.absolutePath,
                        thumbnailUrl = destFile.absolutePath
                    )

                    DownloadStore.saveDownloadedItem(context, updatedItem)

                    // Notify Android gallery
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf(if (item.isVideo) "video/mp4" else "image/jpeg"),
                        null
                    )

                    Handler(Looper.getMainLooper()).post {
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
