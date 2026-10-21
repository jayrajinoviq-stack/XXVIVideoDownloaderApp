package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import android.os.Environment
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DownloadStore {

    private const val PREFS_NAME = "xxvi_download_store"
    private const val KEY_DOWNLOADS = "saved_downloads"
    private const val KEY_CLEANED_VERSION = "clean_v4_purge_defaults"

    fun saveDownloadedItem(context: Context, item: MediaItem) {
        if (isMockItem(item)) return

        val list = getDownloadedItems(context).toMutableList()
        val existingIndex = list.indexOfFirst {
            it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath)
        }
        if (existingIndex != -1) {
            list[existingIndex] = item
        } else {
            list.add(0, item)
        }
        saveList(context, list)
    }

    fun removeDownloadedItem(context: Context, item: MediaItem) {
        val list = getDownloadedItems(context).toMutableList()
        list.removeAll {
            it.id == item.id || (it.localPath != null && item.localPath != null && it.localPath == item.localPath)
        }
        saveList(context, list)

        item.localPath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) {
                    f.delete()
                }
                val thumb = File(f.parentFile, "${f.nameWithoutExtension}_thumb.jpg")
                if (thumb.exists()) thumb.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cleanMockItems(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleaned = prefs.getBoolean(KEY_CLEANED_VERSION, false)
        if (!cleaned) {
            // One-time complete purge of any old mock/default download items
            prefs.edit().remove(KEY_DOWNLOADS).putBoolean(KEY_CLEANED_VERSION, true).apply()

            // Delete any legacy dummy files from disk
            try {
                val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                appDir?.listFiles()?.forEach { file ->
                    val name = file.name.lowercase()
                    if (name.contains("170085") || name.contains("dummy") || name.contains("example") || name.contains("picsum")) {
                        file.delete()
                    }
                }
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XXVI_Downloader")
                if (publicDir.exists()) {
                    publicDir.listFiles()?.forEach { file ->
                        val name = file.name.lowercase()
                        if (name.contains("170085") || name.contains("dummy") || name.contains("example") || name.contains("picsum")) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val list = getDownloadedItems(context).filterNot { isMockItem(it) }
            saveList(context, list)
        }
    }

    fun isMockItem(item: MediaItem): Boolean {
        val url = item.url.lowercase()
        val thumb = (item.thumbnailUrl ?: "").lowercase()
        val title = item.title.lowercase()
        return url.contains("example.com") ||
                url.contains("picsum.photos") ||
                thumb.contains("picsum.photos") ||
                thumb.contains("example.com") ||
                title.contains("dummy") ||
                title.contains("mock")
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        val kb = bytes / 1024.0
        return if (mb >= 1.0) String.format("%.2f MB", mb) else String.format("%.1f kB", kb)
    }

    fun getDownloadedItems(context: Context): List<MediaItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DOWNLOADS, null)
        val items = mutableListOf<MediaItem>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val url = obj.optString("url", "")
                    val thumb = obj.optString("thumbnailUrl", "")
                    val title = obj.optString("title", "downloaded_media")

                    // Filter out any mock items
                    if (url.contains("example.com") || thumb.contains("picsum.photos") || title.contains("dummy") || title.contains("mock")) {
                        continue
                    }

                    val localPath = if (obj.has("localPath") && !obj.isNull("localPath")) obj.getString("localPath") else null
                    val sizeBytes = obj.optLong("sizeBytes", 0L)
                    val isAudio = obj.optBoolean("isAudio", false) ||
                            title.endsWith(".mp3", ignoreCase = true) ||
                            title.endsWith(".m4a", ignoreCase = true) ||
                            title.contains("audio", ignoreCase = true) ||
                            (localPath != null && (localPath.endsWith(".mp3", ignoreCase = true) || localPath.endsWith(".m4a", ignoreCase = true)))
                    val isVideo = !isAudio && (obj.optBoolean("isVideo", false) || title.endsWith(".mp4", ignoreCase = true) || (localPath != null && localPath.endsWith(".mp4", ignoreCase = true)))
                    val defaultSizeStr = if (isAudio) "165 kB" else if (isVideo) "3.42 MB" else "15.67kB"
                    val sizeStr = obj.optString("sizeFormatted", defaultSizeStr)
                    val isDownloaded = if (obj.has("isDownloaded")) obj.getBoolean("isDownloaded") else true
                    val downloadProgress = obj.optInt("downloadProgress", if (isDownloaded) 100 else 0)
                    val quality = if (obj.has("quality") && !obj.isNull("quality")) obj.getString("quality") else null

                    // If marked downloaded and has localPath, verify file exists; otherwise drop ghost item
                    if (isDownloaded && !localPath.isNullOrBlank() && !File(localPath).exists()) {
                        continue
                    }

                    items.add(
                        MediaItem(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            title = title,
                            url = url,
                            thumbnailUrl = if (!thumb.isNullOrBlank()) thumb else null,
                            sizeBytes = sizeBytes,
                            sizeFormatted = sizeStr,
                            isVideo = isVideo,
                            isAudio = isAudio,
                            downloadProgress = downloadProgress,
                            isDownloaded = isDownloaded,
                            localPath = localPath,
                            quality = quality
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Auto-Discovery: Scan XXVI_Downloader on disk for existing files (e.g. after reinstall)
        val discoveredNewItems = scanDiskForDiscoveredFiles(context, items)
        if (discoveredNewItems) {
            saveList(context, items)
        }

        return items
    }

    private fun scanDiskForDiscoveredFiles(context: Context, currentList: MutableList<MediaItem>): Boolean {
        var addedAny = false
        try {
            val downloadDir = DownloadHelper.getDownloadDirectory(context, createIfMissing = false)
            val dirsToScan = mutableListOf<File>()
            if (downloadDir.exists() && downloadDir.isDirectory) {
                dirsToScan.add(downloadDir)
            }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                val appDir = File(it, "XXVI_Downloader")
                if (appDir.exists() && appDir.isDirectory && appDir.absolutePath != downloadDir.absolutePath) {
                    dirsToScan.add(appDir)
                }
            }

            for (dir in dirsToScan) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (!file.isFile || file.length() <= 0L) continue
                    val name = file.name
                    val lower = name.lowercase()
                    if (lower.endsWith(".part") || lower.endsWith("_thumb.jpg") || lower.endsWith("_raw.mp4") || lower.startsWith(".")) {
                        continue
                    }

                    val isAudio = lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".wav")
                    val isVideo = !isAudio && (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".mov"))
                    val isImage = !isAudio && !isVideo && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))

                    if (!isAudio && !isVideo && !isImage) continue

                    // Check if already in list
                    val alreadyKnown = currentList.any { it.localPath == file.absolutePath || it.title == file.name }
                    if (!alreadyKnown) {
                        val siblingThumb = File(file.parentFile, "${file.nameWithoutExtension}_thumb.jpg")
                        val thumbPath = if (siblingThumb.exists()) siblingThumb.absolutePath else if (isImage) file.absolutePath else null
                        currentList.add(
                            MediaItem(
                                id = "disk_${file.lastModified()}_${file.name.hashCode()}",
                                title = file.name,
                                url = file.absolutePath,
                                thumbnailUrl = thumbPath,
                                sizeBytes = file.length(),
                                sizeFormatted = formatSize(file.length()),
                                isVideo = isVideo,
                                isAudio = isAudio,
                                downloadProgress = 100,
                                isDownloaded = true,
                                localPath = file.absolutePath
                            )
                        )
                        addedAny = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return addedAny
    }

    private fun saveList(context: Context, list: List<MediaItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            if (isMockItem(item)) continue
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("thumbnailUrl", item.thumbnailUrl ?: "")
                put("sizeBytes", item.sizeBytes)
                put("sizeFormatted", item.sizeFormatted)
                put("isVideo", item.isVideo)
                put("isAudio", item.isAudio)
                put("isDownloaded", item.isDownloaded)
                put("downloadProgress", item.downloadProgress)
                put("localPath", item.localPath ?: "")
                put("quality", item.quality ?: "")
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_DOWNLOADS, jsonArray.toString()).apply()
    }
}
