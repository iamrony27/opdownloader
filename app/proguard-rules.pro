# Keep attributes used by Compose, Jackson and Android metadata.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keepclasseswithmembernames class * {
    native <methods>;
}

# yt-dlp-android talks to native Python/FFmpeg through reflection.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# Apache Commons Compress / zip4j discover ZIP implementations reflectively;
# R8 renaming or merging them breaks YoutubeDL.init() at startup.
-keep class org.apache.commons.compress.** { *; }
-keep class net.lingala.zip4j.** { *; }
-keep class org.apache.commons.io.** { *; }

# Data models are bound from JSON and serialized by the download engine.
-keep class com.opdownloader.app.data.** { *; }

# Compose has its own consumer rules; keep our composable entry points intact.
-keep class com.opdownloader.app.ui.** { *; }
-keep class com.opdownloader.app.MainActivity { *; }
-keep class com.opdownloader.app.DownloaderApp { *; }

-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
# Optional XZ codec backend is not bundled with Commons Compress on Android.
-dontwarn org.tukaani.xz.**
# Jackson 2.11 references desktop-only Java classes that are absent on Android.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
