package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.os.Handler
import android.os.Looper
import com.inoviq.videodownloader.videosaver.storysaver.model.MediaItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object DownloadProgressManager {

    interface DownloadProgressListener {
        fun onDownloadStarted(item: MediaItem) {}
        fun onProgressUpdated(item: MediaItem) {}
        fun onDownloadPaused(item: MediaItem) {}
        fun onDownloadResumed(item: MediaItem) {}
        fun onDownloadCompleted(item: MediaItem) {}
        fun onDownloadFailed(item: MediaItem, error: String) {}
        fun onDownloadCancelled(item: MediaItem) {}
    }

    private val activeDownloads = ConcurrentHashMap<String, MediaItem>()
    private val listeners = CopyOnWriteArrayList<DownloadProgressListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun registerListener(listener: DownloadProgressListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: DownloadProgressListener) {
        listeners.remove(listener)
    }

    fun getActiveDownloads(): List<MediaItem> {
        return activeDownloads.values.toList()
    }

    fun isDownloading(id: String): Boolean {
        return activeDownloads.containsKey(id)
    }

    fun getItem(id: String): MediaItem? {
        return activeDownloads[id]
    }

    fun notifyStarted(item: MediaItem) {
        activeDownloads[item.id] = item
        mainHandler.post {
            listeners.forEach { it.onDownloadStarted(item) }
        }
    }

    fun updateProgress(id: String, progress: Int, bytesDownloaded: Long, formattedSize: String) {
        val current = activeDownloads[id] ?: return
        val updated = current.copy(
            downloadProgress = progress,
            sizeBytes = bytesDownloaded,
            sizeFormatted = formattedSize,
            isDownloaded = false,
            isPaused = false
        )
        activeDownloads[id] = updated
        mainHandler.post {
            listeners.forEach { it.onProgressUpdated(updated) }
        }
    }

    fun notifyPaused(item: MediaItem) {
        val updated = item.copy(isPaused = true)
        activeDownloads[item.id] = updated
        mainHandler.post {
            listeners.forEach { it.onDownloadPaused(updated) }
        }
    }

    fun notifyResumed(item: MediaItem) {
        val updated = item.copy(isPaused = false)
        activeDownloads[item.id] = updated
        mainHandler.post {
            listeners.forEach { it.onDownloadResumed(updated) }
        }
    }

    fun notifyCompleted(completedItem: MediaItem) {
        activeDownloads.remove(completedItem.id)
        mainHandler.post {
            listeners.forEach { it.onDownloadCompleted(completedItem) }
        }
    }

    fun notifyFailed(item: MediaItem, error: String) {
        activeDownloads.remove(item.id)
        mainHandler.post {
            listeners.forEach { it.onDownloadFailed(item, error) }
        }
    }

    fun notifyCancelled(item: MediaItem) {
        activeDownloads.remove(item.id)
        mainHandler.post {
            listeners.forEach { it.onDownloadCancelled(item) }
        }
    }
}
