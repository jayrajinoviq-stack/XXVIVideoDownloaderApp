package com.inoviq.videodownloader.videosaver.storysaver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import com.inoviq.videodownloader.videosaver.storysaver.ui.activity.MainActivity
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadHelper
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadProgressManager
import com.inoviq.videodownloader.videosaver.storysaver.utils.DownloadStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.inoviq.videodownloader.ACTION_START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.inoviq.videodownloader.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.inoviq.videodownloader.ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.inoviq.videodownloader.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_MEDIA_ITEM = "extra_media_item"
        const val EXTRA_ITEM_ID = "extra_item_id"
        private const val CHANNEL_PROGRESS_ID = "xxvi_download_progress_channel"
        private const val CHANNEL_COMPLETE_ID = "xxvi_download_complete_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 202410

        private const val TAG = "DownloadService"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        fun startDownload(context: Context, item: MediaItem) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MEDIA_ITEM, item)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }

        fun pauseDownload(context: Context, itemId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun resumeDownload(context: Context, itemId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }

        fun cancelDownload(context: Context, itemId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val activeDownloadQueue = ConcurrentHashMap<String, MediaItem>()
    private val pausedItemIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledItemIds = ConcurrentHashMap.newKeySet<String>()
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_MEDIA_ITEM, MediaItem::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_MEDIA_ITEM) as? MediaItem
                }

                if (item != null) {
                    pausedItemIds.remove(item.id)
                    cancelledItemIds.remove(item.id)
                    activeDownloadQueue[item.id] = item
                    DownloadProgressManager.notifyStarted(item)

                    val initialNotification = buildProgressNotification(item.title, 0, item.id, false, false)
                    startForegroundServiceWithNotification(initialNotification)

                    executor.execute {
                        performDownload(item)
                    }
                }
            }

            ACTION_PAUSE_DOWNLOAD -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                if (!itemId.isNullOrBlank()) {
                    pausedItemIds.add(itemId)
                    val item = activeDownloadQueue[itemId] ?: DownloadProgressManager.getItem(itemId)
                    if (item != null) {
                        val pausedItem = item.copy(isPaused = true)
                        activeDownloadQueue[itemId] = pausedItem
                        DownloadProgressManager.notifyPaused(pausedItem)
                        val notif = buildProgressNotification(pausedItem.title, pausedItem.downloadProgress, itemId, true, true)
                        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notif)
                    }
                }
            }

            ACTION_RESUME_DOWNLOAD -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                if (!itemId.isNullOrBlank()) {
                    pausedItemIds.remove(itemId)
                    val item = activeDownloadQueue[itemId] ?: DownloadProgressManager.getItem(itemId)
                    if (item != null) {
                        val resumedItem = item.copy(isPaused = false)
                        activeDownloadQueue[itemId] = resumedItem
                        DownloadProgressManager.notifyResumed(resumedItem)

                        val notif = buildProgressNotification(resumedItem.title, resumedItem.downloadProgress, itemId, true, false)
                        startForegroundServiceWithNotification(notif)

                        executor.execute {
                            performDownload(resumedItem)
                        }
                    }
                }
            }

            ACTION_CANCEL_DOWNLOAD -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                if (!itemId.isNullOrBlank()) {
                    cancelledItemIds.add(itemId)
                    pausedItemIds.remove(itemId)
                    try {
                        YoutubeDL.getInstance().destroyProcessById(itemId)
                    } catch (_: Exception) {}
                    val item = activeDownloadQueue.remove(itemId) ?: DownloadProgressManager.getItem(itemId)
                    if (item != null) {
                        item.localPath?.let { path ->
                            try {
                                val f = File(path)
                                if (f.exists()) f.delete()
                                val part = File("$path.part")
                                if (part.exists()) part.delete()
                            } catch (_: Exception) {}
                        }
                        DownloadProgressManager.notifyCancelled(item)
                    }
                    checkStopService()
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep downloading in foreground even when app is cleared from recent tasks
        if (activeDownloadQueue.isEmpty() && pausedItemIds.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }



    private fun performDownload(item: MediaItem) {
        val url = item.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            activeDownloadQueue.remove(item.id)
            DownloadProgressManager.notifyFailed(item, "Invalid download URL")
            checkStopService()
            return
        }

        val isAudio = item.isAudio
        val cleanUrl = DownloadHelper.cleanMediaUrl(url)
        val cleanUrlNoQuery = cleanUrl.substringBefore("?")
        val hasImageExtension = cleanUrlNoQuery.endsWith(".jpg", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".jpeg", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".png", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".webp", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".gif", ignoreCase = true) ||
                cleanUrl.contains(".jpg?", ignoreCase = true) ||
                cleanUrl.contains(".jpeg?", ignoreCase = true) ||
                cleanUrl.contains(".png?", ignoreCase = true) ||
                cleanUrl.contains(".webp?", ignoreCase = true) ||
                cleanUrl.contains(".jpg&", ignoreCase = true) ||
                cleanUrl.contains(".png&", ignoreCase = true) ||
                cleanUrl.contains(".webp&", ignoreCase = true) ||
                cleanUrl.contains(".jpg/", ignoreCase = true) ||
                cleanUrl.contains(".png/", ignoreCase = true) ||
                item.title.endsWith(".jpg", ignoreCase = true) ||
                item.title.endsWith(".jpeg", ignoreCase = true) ||
                item.title.endsWith(".png", ignoreCase = true) ||
                item.title.endsWith(".webp", ignoreCase = true)

        val hasVideoExtension = cleanUrlNoQuery.endsWith(".mp4", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".webm", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".mkv", ignoreCase = true) ||
                cleanUrlNoQuery.endsWith(".mov", ignoreCase = true) ||
                cleanUrl.contains(".mp4?", ignoreCase = true) ||
                cleanUrl.contains(".webm?", ignoreCase = true)

        val isImage = !isAudio && (hasImageExtension || (!hasVideoExtension && (!item.isVideo || item.title.contains("_photo", true) || item.title.contains("_img", true))))
        val isVideo = !isAudio && !isImage

        try {
            var downloaded = false
            // 1. Try YoutubeDL for video, audio, or web platform links (YouTube, Instagram, TikTok, Facebook, etc.)
            // Skip YoutubeDL for static direct image assets (.jpg, .png, etc.)
            if (!isImage) {
                try {
                    downloaded = downloadWithYoutubeDL(item, isAudio, isVideo)
                } catch (e: Exception) {
                    if (cancelledItemIds.contains(item.id)) {
                        activeDownloadQueue.remove(item.id)
                        cancelledItemIds.remove(item.id)
                        DownloadProgressManager.notifyCancelled(item)
                        checkStopService()
                        return
                    }
                    android.util.Log.w(TAG, "YoutubeDL download failed or not supported for url: ${item.url}, falling back to direct stream download", e)
                    downloaded = false
                }
            }

            // 2. If YoutubeDL was not applicable or threw an unsupported URL exception, download directly
            if (!downloaded && !cancelledItemIds.contains(item.id)) {
                downloadDirectStream(item, isAudio, isVideo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!cancelledItemIds.contains(item.id)) {
                DownloadProgressManager.notifyFailed(item, e.message ?: "Download failed")
            }
        } finally {
            if (!pausedItemIds.contains(item.id)) {
                activeDownloadQueue.remove(item.id)
                cancelledItemIds.remove(item.id)
                checkStopService()
            }
        }
    }

    private fun ensureYoutubeDLInitialized() {
        try {
            YoutubeDL.getInstance().init(applicationContext)
        } catch (_: Exception) {
            // Already initialized in MyApp or idempotent
        }
    }

    private fun downloadWithYoutubeDL(item: MediaItem, isAudio: Boolean, isVideo: Boolean): Boolean {
        ensureYoutubeDLInitialized()

        // Prefer the canonical page URL for social platforms so YT-DLP can properly
        // resolve streams. CDN/blob URLs are temporary and YT-DLP often mixes up
        // video vs audio tracks when given a raw CDN URL directly.
        val pageUrl = item.pageUrl
        val isSocialPlatform = !pageUrl.isNullOrBlank() && isSocialPlatformUrl(pageUrl)
        val extractionUrl = if (isSocialPlatform) pageUrl else DownloadHelper.cleanMediaUrl(item.url)

        val targetDir = DownloadHelper.getDownloadDirectory(applicationContext, createIfMissing = true)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val rawName = item.title.substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .ifBlank { "media_${System.currentTimeMillis()}" }

        val request = YoutubeDLRequest(extractionUrl)
        request.addOption("-o", "${targetDir.absolutePath}/$rawName.%(ext)s")
        request.addOption("--no-mtime")
        request.addOption("--no-playlist")
        request.addOption("--no-part")
        request.addOption("--retries", "3")
        request.addOption("--user-agent", USER_AGENT)
        request.addOption("--cache-dir", "${applicationContext.cacheDir.absolutePath}/ytdlp_cache")

        // Forward WebView login session cookie and referer
        val cookie = CookieManager.getInstance().getCookie(extractionUrl)
        if (!cookie.isNullOrBlank()) {
            request.addOption("--add-header", "Cookie:$cookie")
        }
        request.addOption("--add-header", "Referer:$extractionUrl")

        // Format selection WITHOUT FFMPEG:
        // b = best single file that has BOTH video and audio
        if (isAudio) {
            request.addOption("-f", "ba[ext=m4a]/ba/b")
        } else {
            request.addOption("-f", "b[ext=mp4][vcodec!=none][acodec!=none]/b[vcodec!=none][acodec!=none]/b")
        }

        var lastProgressUpdate = 0L
        YoutubeDL.getInstance().execute(request, item.id) { progress: Float, _: Long, _: String ->
            if (cancelledItemIds.contains(item.id)) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(item.id)
                } catch (_: Exception) {}
                return@execute
            }
            val now = System.currentTimeMillis()
            val pct = progress.toInt().coerceIn(0, 100)
            if (now - lastProgressUpdate > 250 || pct == 100) {
                lastProgressUpdate = now
                val formatted = if (pct > 0) "${pct}%" else ""
                updateProgress(item.title, pct, item.id, 0L, formatted)
            }
        }

        if (cancelledItemIds.contains(item.id)) {
            return false
        }

        // Find the completed file written by yt-dlp
        val downloadedFile = targetDir.listFiles { _, name ->
            name.startsWith(rawName) && !name.endsWith(".part") && !name.endsWith(".ytdl") && !name.endsWith(".temp")
        }?.maxByOrNull { it.lastModified() }

        if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
            var finalPath = downloadedFile.absolutePath

            // If audio was requested and the downloaded stream is in a video container,
            // extract pure audio using native Android MediaExtractor/MediaMuxer (zero ffmpeg needed)
            if (isAudio && (finalPath.endsWith(".mp4", ignoreCase = true) || finalPath.endsWith(".mkv", ignoreCase = true) || finalPath.endsWith(".webm", ignoreCase = true))) {
                val pureAudioFile = File(targetDir, "${rawName}.m4a")
                val extracted = extractAudioTrack(finalPath, pureAudioFile.absolutePath)
                if (extracted && pureAudioFile.exists() && pureAudioFile.length() > 0) {
                    try { File(finalPath).delete() } catch (_: Exception) {}
                    finalPath = pureAudioFile.absolutePath
                }
            }

            finalizeDownloadedFile(item, finalPath, isAudio, isVideo)
            return true
        }

        return false
    }

    /**
     * Returns true for canonical social-media page URLs (Instagram, Facebook, TikTok, Twitter/X,
     * YouTube, Pinterest, Snapchat) — meaning YT-DLP should use this URL for extraction rather
     * than a raw CDN stream URL.
     */
    private fun isSocialPlatformUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val clean = url.lowercase().trim()
        if (clean.startsWith("blob:") || clean.startsWith("about:") || clean.startsWith("home")) return false
        return (clean.contains("instagram.com") && (clean.contains("/p/") || clean.contains("/reel/") || clean.contains("/reels/") || clean.contains("/tv/") || clean.contains("/stories/"))) ||
               ((clean.contains("facebook.com") || clean.contains("fb.watch")) && (clean.contains("/watch") || clean.contains("/reel") || clean.contains("/videos/") || clean.contains("video.php") || clean.contains("v=") || clean.contains("story_fbid="))) ||
               ((clean.contains("youtube.com") || clean.contains("youtu.be")) && (clean.contains("watch") || clean.contains("youtu.be/") || clean.contains("/shorts/") || clean.contains("/embed/"))) ||
               (clean.contains("tiktok.com") && (clean.contains("/video/") || clean.contains("/v/") || clean.contains("vt.tiktok.com") || clean.contains("vm.tiktok.com"))) ||
               ((clean.contains("twitter.com") || clean.contains("x.com")) && clean.contains("/status/")) ||
               (clean.contains("pinterest.com") && clean.contains("/pin/")) ||
               (clean.contains("reddit.com") && clean.contains("/comments/")) ||
               (clean.contains("vimeo.com") && clean.matches(Regex(".*/\\d+.*"))) ||
               (clean.contains("dailymotion.com") && clean.contains("/video/"))
    }

    private fun downloadDirectStream(item: MediaItem, isAudio: Boolean, isVideo: Boolean) {

        val url = item.url
        val cleanUrl = DownloadHelper.cleanMediaUrl(url)
        val cleanUrlNoQuery = cleanUrl.substringBefore("?")
        val ext = when {
            isAudio -> if (item.title.endsWith(".mp3", true)) ".mp3" else ".m4a"
            isVideo -> ".mp4"
            cleanUrlNoQuery.endsWith(".png", true) || cleanUrl.contains(".png", true) || item.title.endsWith(".png", true) -> ".png"
            cleanUrlNoQuery.endsWith(".webp", true) || cleanUrl.contains(".webp", true) || item.title.endsWith(".webp", true) -> ".webp"
            else -> ".jpg"
        }

        val targetDir = DownloadHelper.getDownloadDirectory(applicationContext, createIfMissing = true)
        val finalTargetFile = if (!item.localPath.isNullOrBlank()) {
            val existing = File(item.localPath!!)
            if (!isVideo && !isAudio && (existing.name.endsWith(".mp4", true) || existing.name.endsWith(".webm", true) || existing.name.endsWith(".m4a", true) || existing.name.endsWith(".mp3", true))) {
                File(existing.parentFile ?: targetDir, "${existing.nameWithoutExtension}$ext")
            } else {
                existing
            }
        } else {
            val rawName = item.title.substringBeforeLast(".")
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            File(targetDir, "$rawName$ext")
        }

        val downloadTargetFile = if (isAudio) {
            File(finalTargetFile.parentFile ?: targetDir, "${finalTargetFile.nameWithoutExtension}_raw.mp4")
        } else {
            finalTargetFile
        }

        var partFile = File(downloadTargetFile.parentFile ?: targetDir, "${downloadTargetFile.name}.part")
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        var outputStream: OutputStream? = null

        val referer = when {
            cleanUrl.contains("instagram", ignoreCase = true) || cleanUrl.contains("cdninstagram", ignoreCase = true) -> "https://www.instagram.com/"
            cleanUrl.contains("fbcdn.net", ignoreCase = true) || cleanUrl.contains("facebook.com", ignoreCase = true) -> "https://www.facebook.com/"
            cleanUrl.contains("pinimg.com", ignoreCase = true) || cleanUrl.contains("pinterest.com", ignoreCase = true) -> "https://www.pinterest.com/"
            cleanUrl.contains("twimg.com", ignoreCase = true) || cleanUrl.contains("twitter.com", ignoreCase = true) || cleanUrl.contains("x.com", ignoreCase = true) -> "https://twitter.com/"
            cleanUrl.contains("tiktokcdn.com", ignoreCase = true) || cleanUrl.contains("tiktok.com", ignoreCase = true) -> "https://www.tiktok.com/"
            cleanUrl.contains("whatsapp.net", ignoreCase = true) || cleanUrl.contains("whatsapp.com", ignoreCase = true) -> "https://web.whatsapp.com/"
            cleanUrl.contains("sc-cdn.net", ignoreCase = true) || cleanUrl.contains("snapchat.com", ignoreCase = true) -> "https://www.snapchat.com/"
            else -> {
                try {
                    val parsed = Uri.parse(cleanUrl)
                    "${parsed.scheme}://${parsed.host}/"
                } catch (_: Exception) {
                    "https://www.google.com/"
                }
            }
        }

        var currentUrl = cleanUrl
        var conn = URL(currentUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Referer", referer)

        val cm = CookieManager.getInstance()
        val urlCookie = cm.getCookie(currentUrl)
        val parentCookie = if (referer.startsWith("https://")) cm.getCookie(referer) else null
        val instaCookie = if (referer.contains("instagram")) cm.getCookie("https://www.instagram.com/") else null
        val finalCookie = listOfNotNull(urlCookie, parentCookie, instaCookie).filter { it.isNotBlank() }.distinct().joinToString("; ")
        if (finalCookie.isNotBlank()) {
            conn.setRequestProperty("Cookie", finalCookie)
        }

        if (existingBytes > 0) {
            conn.setRequestProperty("Range", "bytes=$existingBytes-")
        }

        conn.connect()

        var responseCode = conn.responseCode
        var redirects = 0
        while ((responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308) && redirects < 5) {
            val newLoc = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            currentUrl = newLoc
            conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", referer)
            if (finalCookie.isNotBlank()) {
                conn.setRequestProperty("Cookie", finalCookie)
            }
            if (existingBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            conn.connect()
            responseCode = conn.responseCode
            redirects++
        }
        val isPartial = responseCode == 206
        if (responseCode !in 200..299 && !isPartial) {
            throw Exception("Server returned HTTP $responseCode")
        }

        if (!isPartial && existingBytes > 0) {
            existingBytes = 0L
            partFile.delete()
        }

        val totalContentLength = if (isPartial) {
            conn.contentLengthLong + existingBytes
        } else {
            conn.contentLengthLong
        }

        val parentDir = partFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        outputStream = try {
            FileOutputStream(partFile, isPartial)
        } catch (e: Exception) {
            val fallbackDir = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: applicationContext.filesDir, "XXVI_Downloader")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            partFile = File(fallbackDir, partFile.name)
            FileOutputStream(partFile, isPartial)
        }

        val input = conn.inputStream
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytes = existingBytes
        var lastProgressUpdate = 0L
        var currentPercent = 0

        try {
            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (cancelledItemIds.contains(item.id)) {
                    outputStream.flush()
                    outputStream.close()
                    input.close()
                    conn.disconnect()
                    partFile.delete()
                    activeDownloadQueue.remove(item.id)
                    cancelledItemIds.remove(item.id)
                    DownloadProgressManager.notifyCancelled(item)
                    checkStopService()
                    return
                }

                if (pausedItemIds.contains(item.id)) {
                    outputStream.flush()
                    outputStream.close()
                    input.close()
                    conn.disconnect()
                    val pausedItem = item.copy(
                        downloadProgress = currentPercent,
                        sizeBytes = totalBytes,
                        sizeFormatted = formatSize(totalBytes),
                        isPaused = true,
                        isDownloaded = false,
                        localPath = partFile.absolutePath
                    )
                    activeDownloadQueue[item.id] = pausedItem
                    DownloadProgressManager.notifyPaused(pausedItem)
                    val notif = buildProgressNotification(pausedItem.title, currentPercent, item.id, true, true)
                    notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notif)
                    return
                }

                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead

                if (totalContentLength > 0) {
                    val pct = ((totalBytes * 100) / totalContentLength).toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    if (pct != currentPercent && (now - lastProgressUpdate > 250 || pct == 100)) {
                        currentPercent = pct
                        lastProgressUpdate = now
                        val formatted = formatSize(totalBytes)
                        updateProgress(finalTargetFile.name, currentPercent, item.id, totalBytes, formatted)
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 400) {
                        lastProgressUpdate = now
                        val formatted = formatSize(totalBytes)
                        DownloadProgressManager.updateProgress(item.id, 0, totalBytes, formatted)
                    }
                }
            }
        } finally {
            try { outputStream.flush() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
            try { input.close() } catch (_: Exception) {}
            try { conn.disconnect() } catch (_: Exception) {}
        }

        val isCancelled = cancelledItemIds.contains(item.id)
        if (totalBytes <= 0L && !isCancelled) {
            partFile.delete()
            throw Exception("Downloaded file is empty (0 bytes)")
        }

        val activeDestination = File(partFile.parentFile ?: targetDir, downloadTargetFile.name)
        if (activeDestination.exists()) {
            activeDestination.delete()
        }
        partFile.renameTo(activeDestination)

        var resolvedLocalPath = activeDestination.absolutePath
        if (isAudio) {
            val pureAudioFile = File(activeDestination.parentFile ?: targetDir, "${finalTargetFile.nameWithoutExtension}.m4a")
            if (pureAudioFile.exists()) {
                pureAudioFile.delete()
            }
            val extracted = extractAudioTrack(activeDestination.absolutePath, pureAudioFile.absolutePath)
            if (extracted && pureAudioFile.exists() && pureAudioFile.length() > 0) {
                activeDestination.delete()
                resolvedLocalPath = pureAudioFile.absolutePath
            } else {
                val fallbackAudio = File(activeDestination.parentFile ?: targetDir, "${finalTargetFile.nameWithoutExtension}.mp3")
                if (fallbackAudio.exists()) fallbackAudio.delete()
                activeDestination.renameTo(fallbackAudio)
                resolvedLocalPath = fallbackAudio.absolutePath
            }
        } else {
            resolvedLocalPath = activeDestination.absolutePath
            if (!isVideo && !isAudio && activeDestination.exists() && activeDestination.length() >= 12) {
                try {
                    val header = ByteArray(12)
                    java.io.FileInputStream(activeDestination).use { it.read(header) }
                    val detectedExt = when {
                        header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> ".png"
                        header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> ".jpg"
                        header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                                header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() -> ".webp"
                        else -> null
                    }
                    if (detectedExt != null && !activeDestination.name.endsWith(detectedExt, ignoreCase = true)) {
                        val correctedFile = File(activeDestination.parentFile ?: targetDir, "${activeDestination.nameWithoutExtension}$detectedExt")
                        if (correctedFile.exists()) correctedFile.delete()
                        if (activeDestination.renameTo(correctedFile)) {
                            resolvedLocalPath = correctedFile.absolutePath
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        finalizeDownloadedFile(item, resolvedLocalPath, isAudio, isVideo)
    }

    private fun finalizeDownloadedFile(
        item: MediaItem,
        resolvedLocalPath: String,
        isAudio: Boolean,
        isVideo: Boolean
    ) {
        val targetDir = DownloadHelper.getDownloadDirectory(applicationContext, createIfMissing = true)
        val file = File(resolvedLocalPath)
        val finalSize = if (file.exists()) file.length() else 0L

        // Register in MediaStore for Android Gallery / Music apps
        val mimeType = when {
            isAudio -> if (resolvedLocalPath.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "audio/mp4"
            isVideo -> "video/mp4"
            resolvedLocalPath.endsWith(".png", ignoreCase = true) -> "image/png"
            resolvedLocalPath.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    val folder = when {
                        isAudio -> Environment.DIRECTORY_MUSIC
                        isVideo -> Environment.DIRECTORY_DOWNLOADS
                        else -> Environment.DIRECTORY_PICTURES
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/XXVI_Downloader")
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                val collection = if (isAudio) {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                } else if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                contentResolver.insert(collection, contentValues)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Generate thumbnail if video (seek into video to avoid black starting frame!)
        var savedThumbPath: String? = null
        if (isVideo) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(resolvedLocalPath)
                val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                if (bitmap != null) {
                    val thumbFile = File(file.parentFile ?: targetDir, "${file.nameWithoutExtension}_thumb.jpg")
                    FileOutputStream(thumbFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    savedThumbPath = thumbFile.absolutePath
                    MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(thumbFile.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                }
                retriever.release()
            } catch (_: Exception) {}
        }

        val finalThumb = savedThumbPath
            ?: if (isVideo) (if (!item.thumbnailUrl.isNullOrBlank() && item.thumbnailUrl.startsWith("http")) item.thumbnailUrl else "")
            else if (isAudio) ""
            else resolvedLocalPath

        val completedItem = item.copy(
            title = file.name,
            sizeBytes = finalSize,
            sizeFormatted = formatSize(finalSize),
            downloadProgress = 100,
            isDownloaded = true,
            isPaused = false,
            isAudio = isAudio,
            isVideo = isVideo,
            localPath = resolvedLocalPath,
            thumbnailUrl = finalThumb
        )

        DownloadStore.saveDownloadedItem(applicationContext, completedItem)

        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(resolvedLocalPath),
            arrayOf(mimeType),
            null
        )

        showCompletedNotification(completedItem)
        DownloadProgressManager.notifyCompleted(completedItem)
    }

    private fun extractAudioTrack(sourceVideoPath: String, outputAudioPath: String): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(sourceVideoPath)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex == -1) return false

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            muxer = MediaMuxer(outputAudioPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            extractor.release()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            return false
        }
    }

    private fun updateProgress(fileName: String, progress: Int, itemId: String, bytes: Long, formattedSize: String) {
        val notification = buildProgressNotification(fileName, progress, itemId, true, false)
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        DownloadProgressManager.updateProgress(itemId, progress, bytes, formattedSize)
    }

    private fun buildProgressNotification(
        fileName: String,
        progress: Int,
        itemId: String,
        determinate: Boolean,
        isPaused: Boolean
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_DOWNLOADS", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, DownloadService::class.java).apply {
            action = if (isPaused) ACTION_RESUME_DOWNLOAD else ACTION_PAUSE_DOWNLOAD
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val pauseResumePending = PendingIntent.getService(
            this,
            itemId.hashCode() + 1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val cancelPending = PendingIntent.getService(
            this,
            itemId.hashCode() + 2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(if (isPaused) "Paused: $fileName" else "Downloading $fileName")
            .setContentText(if (isPaused) "Download paused • $progress%" else if (determinate) "$progress% completed" else "Downloading...")
            .setProgress(100, progress, !determinate)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isPaused) {
            builder.addAction(R.drawable.ic_play_arrow, "Resume", pauseResumePending)
        } else {
            builder.addAction(R.drawable.ic_pause, "Pause", pauseResumePending)
        }
        builder.addAction(R.drawable.ic_cancel_circle, "Cancel", cancelPending)

        return builder.build()
    }

    private fun showCompletedNotification(item: MediaItem) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_DOWNLOADS", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            item.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = when {
            item.isAudio -> "Audio"
            item.isVideo -> "Video"
            else -> "Photo"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("$typeLabel Downloaded")
            .setContentText("${item.title} saved to XXVI_Downloader")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    private fun startForegroundServiceWithNotification(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun checkStopService() {
        if (activeDownloadQueue.isEmpty() && pausedItemIds.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val kb = bytes / 1024.0
        return if (mb >= 1.0) String.format("%.2f MB", mb) else String.format("%.1f kB", kb)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active video, audio, and image downloads"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                "Download Completed",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when downloads finish successfully"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }
}
