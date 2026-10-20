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
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
                e.printStackTrace()
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
                e.printStackTrace()
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
                    startForegroundWithCompat(initialNotification)

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
                        startForegroundWithCompat(notif)

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
        // Keep downloading even when app is cleared from recent tasks
        if (activeDownloadQueue.isEmpty() && pausedItemIds.isEmpty()) {
            stopSelf()
        }
    }

    private fun startForegroundWithCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
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

        val isAudio = item.isAudio || item.title.contains("audio", ignoreCase = true)
        val isVideo = !isAudio && (item.isVideo || url.contains(".mp4") || url.contains(".webm"))
        val ext = when {
            isAudio -> ".m4a"
            isVideo -> ".mp4"
            else -> ".jpg"
        }

        val rawName = item.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val baseName = if (rawName.endsWith(ext, ignoreCase = true)) {
            rawName.substring(0, rawName.length - ext.length)
        } else if (rawName.endsWith(".mp3", ignoreCase = true)) {
            rawName.substring(0, rawName.length - 4)
        } else if (rawName.endsWith(".mp4", ignoreCase = true)) {
            rawName.substring(0, rawName.length - 4)
        } else {
            rawName
        }

        val targetDir = DownloadHelper.getDownloadDirectory(applicationContext)
        val finalTargetFile = if (isAudio) File(targetDir, "${baseName}_raw.mp4") else File(targetDir, "$baseName$ext")

        // For resumable download, write to a .part file until 100% complete
        val partFile = File(targetDir, "${finalTargetFile.name}.part")
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        var outputStream: OutputStream? = null

        try {
            val cleanUrl = DownloadHelper.cleanMediaUrl(url)
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

            // Resume support via HTTP Range header
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
                // Server doesn't support Range; restart from 0
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

            outputStream = FileOutputStream(partFile, isPartial)

            val input = conn.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = existingBytes
            var lastProgressUpdate = 0L
            var currentPercent = 0

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

            outputStream.flush()
            outputStream.close()
            input.close()
            conn.disconnect()

            val isCancelled = cancelledItemIds.contains(item.id)
            if (isVideo && totalBytes < 40_000 && !isCancelled) {
                partFile.delete()
                throw Exception("Video stream incomplete ($totalBytes bytes). Re-try with full video.")
            }

            // Download completed! Rename .part file to final file
            if (finalTargetFile.exists()) {
                finalTargetFile.delete()
            }
            partFile.renameTo(finalTargetFile)

            // If audio was requested, extract pure audio track to a standard .m4a audio file
            var resolvedLocalPath = finalTargetFile.absolutePath
            if (isAudio) {
                val pureAudioFile = File(targetDir, "$baseName.m4a")
                if (pureAudioFile.exists()) {
                    pureAudioFile.delete()
                }
                val extracted = extractAudioTrack(finalTargetFile.absolutePath, pureAudioFile.absolutePath)
                if (extracted && pureAudioFile.exists() && pureAudioFile.length() > 0) {
                    finalTargetFile.delete()
                    resolvedLocalPath = pureAudioFile.absolutePath
                } else {
                    // Fallback: If extraction wasn't possible (e.g. stream was already audio), rename to .mp3
                    val fallbackAudio = File(targetDir, "$baseName.mp3")
                    if (fallbackAudio.exists()) fallbackAudio.delete()
                    finalTargetFile.renameTo(fallbackAudio)
                    resolvedLocalPath = fallbackAudio.absolutePath
                }
            }

            // Register in MediaStore for Android Gallery / Music apps
            val mimeType = when {
                isAudio -> if (resolvedLocalPath.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "audio/mp4"
                isVideo -> "video/mp4"
                else -> "image/jpeg"
            }

            var mediaStoreUri: Uri? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, File(resolvedLocalPath).name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        val folder = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS
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
                    mediaStoreUri = contentResolver.insert(collection, contentValues)
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
                        val thumbFile = File(finalTargetFile.parentFile, "${finalTargetFile.nameWithoutExtension}_thumb.jpg")
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
                title = File(resolvedLocalPath).name,
                sizeBytes = File(resolvedLocalPath).length(),
                sizeFormatted = formatSize(File(resolvedLocalPath).length()),
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

        } catch (e: Exception) {
            e.printStackTrace()
            DownloadProgressManager.notifyFailed(item, e.message ?: "Download failed")
        } finally {
            if (!pausedItemIds.contains(item.id)) {
                activeDownloadQueue.remove(item.id)
                cancelledItemIds.remove(item.id)
                checkStopService()
            }
        }
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

    private fun checkStopService() {
        if (activeDownloadQueue.isEmpty() && pausedItemIds.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
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
        executor.shutdown()
        super.onDestroy()
    }
}
