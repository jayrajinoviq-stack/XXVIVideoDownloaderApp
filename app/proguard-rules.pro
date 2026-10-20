# Proguard rules for XXVI Video Downloader

-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep models for Gson / Serialization
-keep class com.inoviq.videodownloader.videosaver.storysaver.model.** { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.ads.model.** { *; }
