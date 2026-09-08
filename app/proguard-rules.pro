# AppAuth
-keep class net.openid.appauth.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep WallpaperWorker
-keep class com.photowallpaper.WallpaperWorker { *; }