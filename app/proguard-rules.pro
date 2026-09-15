# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep WallpaperWorker
-keep class com.photowallpaper.WallpaperWorker { *; }

# Keep сетевые источники (используются из воркера и диагностики)
-keep class com.photowallpaper.BingApi { *; }
-keep class com.photowallpaper.WallhavenApi { *; }
-keep class com.photowallpaper.LoremPicsumApi { *; }
-keep class com.photowallpaper.DoHResolver { *; }