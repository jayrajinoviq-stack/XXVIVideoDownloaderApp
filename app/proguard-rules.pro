# Add project specific R8 rules here.
# AGP will combine all keep rule files in src/main/keepRules to pass to R8
#
# For more details, see
#   https://d.android.com/r/tools/r8/keep-rules

# ==============================================================================
# Kotlin metadata / annotations
# ==============================================================================
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlin.jvm.JvmField <fields>;
}
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

# ==============================================================================
# WebView JavaScript Interface (CRITICAL for web video sniffer callbacks)
# ==============================================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==============================================================================
# App models & data classes
# ==============================================================================
-keep class com.inoviq.videodownloader.videosaver.storysaver.model.** { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.models.** { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.ads.model.** { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse$* { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.data.model.** { *; }

-keep class com.inoviq.videodownloader.model.** { *; }
-keep class com.inoviq.videodownloader.models.** { *; }
-keep class com.inoviq.videodownloader.data.model.** { *; }
-keep class com.inoviq.videodownloader.repository.DownloadResult { *; }
-keep class com.inoviq.videodownloader.repository.DownloadResult$* { *; }

-keepclassmembers class com.inoviq.videodownloader.videosaver.storysaver.model.** { *; }
-keepclassmembers class com.inoviq.videodownloader.videosaver.storysaver.models.** { *; }
-keepclassmembers class com.inoviq.videodownloader.videosaver.storysaver.ads.model.** { *; }
-keepclassmembers class com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse { *; }
-keepclassmembers class com.inoviq.videodownloader.videosaver.storysaver.ads.AdsResponse$* { *; }

-keepclassmembers class com.inoviq.videodownloader.model.** { *; }
-keepclassmembers class com.inoviq.videodownloader.models.** { *; }
-keepclassmembers class com.inoviq.videodownloader.data.model.** { *; }

# ==============================================================================
# App Services & Lifecycle Components
# ==============================================================================
-keep class com.inoviq.videodownloader.videosaver.storysaver.service.DownloadService { *; }
-keep class com.inoviq.videodownloader.videosaver.storysaver.service.** { *; }
-keep class com.inoviq.videodownloader.service.PlaybackService { *; }
-keep class com.inoviq.videodownloader.service.DownloadForegroundService { *; }
-keep class com.inoviq.videodownloader.service.** { *; }

# ==============================================================================
# youtubedl-android (yt-dlp wrapper) — CRITICAL
# Gson deserializes JSON straight into VideoInfo/StreamInfo/etc via reflection.
# Without this, getInfo()/fetchInfo() silently returns null/garbage fields in release.
# ==============================================================================
-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.** { *; }
-dontwarn com.yausername.**
-dontwarn org.apache.commons.compress.**

# ==============================================================================
# Media3 / ExoPlayer (playback + MediaSessionService)
# ==============================================================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ==============================================================================
# OkHttp / Okio
# ==============================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ==============================================================================
# Jsoup
# ==============================================================================
-dontwarn org.jsoup.**

# ==============================================================================
# Gson (JSON parser)
# ==============================================================================
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    protected <init>();
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep no-arg constructors of anything Gson deserializes into (covers classes
# that don't have @SerializedName annotations)
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName *;
}

# ==============================================================================
# WorkManager — Workers are instantiated by class name via reflection
# ==============================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger

# ==============================================================================
# Custom Views & Layout Inflation (AnimatedBottomBar, etc.)
# ==============================================================================
-keep class nl.joery.animatedbottombar.** { *; }
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ==============================================================================
# ViewBinding & DataBinding
# ==============================================================================
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}
-keep class androidx.databinding.** { *; }
-dontwarn androidx.databinding.**

# ==============================================================================
# Room Database
# ==============================================================================
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.migration.bundle.**
-dontwarn androidx.room.limits.LimitDeclaration
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# ==============================================================================
# Glide (Image Loading)
# ==============================================================================
-keep public class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule {
    <init>(...);
}
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl {
    <init>(...);
}
-keep @com.bumptech.glide.annotation.GlideModule class *
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# ==============================================================================
# Facebook Shimmer
# ==============================================================================
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# ==============================================================================
# Firebase & Google Play Services (Ads, App Update, Crashlytics, Firestore)
# ==============================================================================
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn com.android.installreferrer.**

# Play Store In-App Update
-keep class com.google.android.play.core.** { *; }

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Firestore — keeps @PropertyName-annotated members and POJO structure.
-keepattributes Signature
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keepclassmembers class * {
    public <init>();
}

# ==============================================================================
# Coroutines
# ==============================================================================
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepclassmembernames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==============================================================================
# Parcelable / Enums (safe defaults, harmless if unused)
# ==============================================================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==============================================================================
# Native / JNI (ffmpeg, aria2c, python binaries invoked via JNI)
# ==============================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# ==============================================================================
# Lottie & Coil
# ==============================================================================
-keep class com.airbnb.lottie.** { *; }
-dontwarn coil.**

# ==============================================================================
# Commons & Home Modules
# ==============================================================================
-keep class com.design.commons.** { *; }
-keep class com.design.home.** { *; }
-dontwarn com.design.commons.**
-dontwarn com.design.home.**

# ==============================================================================
# YoutubeDL / YT-DLP
# ==============================================================================
-keep class com.yausername.youtubedl_android.** { *; }
-keep interface com.yausername.youtubedl_android.** { *; }
-dontwarn com.yausername.youtubedl_android.**
