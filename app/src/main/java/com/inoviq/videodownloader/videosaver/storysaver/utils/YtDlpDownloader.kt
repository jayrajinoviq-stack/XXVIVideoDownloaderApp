package com.inoviq.videodownloader.videosaver.storysaver.utils

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.Executors

object YtDlpDownloader {

    private val executor = Executors.newCachedThreadPool()
    @Volatile private var initialized = false

    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            initialized = true
        } catch (_: Exception) {
            // Already initialized or handled
        }
    }

    /**
     * Downloads ONE file that already has video + audio (no merge, no FFmpeg).
     * Also supports audio-only downloads.
     */
    fun download(
        context: Context,
        pageUrl: String,
        outDir: File,
        fileName: String,
        isAudio: Boolean = false,
        onProgress: (Float) -> Unit,
        onDone: (File?) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                ensureInit(context)
                outDir.mkdirs()

                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val request = YoutubeDLRequest(pageUrl).apply {
                    // KEY: "b" = best single file that has BOTH video and audio.
                    // Never use "bv*+ba" (that needs FFmpeg to merge).
                    if (isAudio) {
                        addOption("-f", "ba[ext=m4a]/ba/b")
                    } else {
                        addOption("-f", "b[ext=mp4][vcodec!=none][acodec!=none]/b[vcodec!=none][acodec!=none]/b")
                    }
                    addOption("-o", "${outDir.absolutePath}/$safeName.%(ext)s")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--no-part")
                    addOption("--retries", "3")
                    addOption("--user-agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")

                    // Reuse WebView login session (Facebook/Instagram private or login-walled posts)
                    val cookie = CookieManager.getInstance().getCookie(pageUrl)
                    if (!cookie.isNullOrBlank()) {
                        addOption("--add-header", "Cookie:$cookie")
                    }
                    addOption("--add-header", "Referer:$pageUrl")
                }

                YoutubeDL.getInstance().execute(request, safeName) { progress, _, _ ->
                    onProgress(progress)
                }

                val result = outDir.listFiles()
                    ?.filter { it.name.startsWith(safeName) && !it.name.endsWith(".part") }
                    ?.maxByOrNull { it.lastModified() }
                onDone(result)
            } catch (e: Exception) {
                onError(e.message ?: "Download failed")
            }
        }
    }
}
