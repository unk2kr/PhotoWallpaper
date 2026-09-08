package com.photowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker — фоновая задача для смены обоев.
 * Скачивает фото из альбома Google Photos и ставит как обои.
 */
class WallpaperWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "PhotoWallpaperWorker"
        const val WORK_NAME_PERIODIC = "photo_wallpaper_periodic"

        fun schedulePeriodic(context: Context, intervalMinutes: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
            val work = PeriodicWorkRequestBuilder<WallpaperWorker>(
                intervalMinutes, TimeUnit.MINUTES
            ).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, work
            )
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
            val work = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "photo_wallpaper_once", ExistingWorkPolicy.REPLACE, work
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(context)
        if (!settings.isEnabled) return Result.success()
        val albumId = settings.selectedAlbumId ?: return Result.failure()

        val api = GooglePhotosApi()
        val authManager = AuthManager(context)
        try {
            val token = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                authManager.getAccessToken { cont.resumeWith(kotlin.Result.success(it)) }
            } ?: return Result.retry()

            val mediaResult = api.getMediaItems(token, albumId)
            val mediaItems = mediaResult.getOrNull()
            if (mediaItems.isNullOrEmpty()) return Result.failure()

            val nextIndex = (settings.lastPhotoIndex + 1) % mediaItems.size
            val photo = mediaItems[nextIndex]

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val cacheDir = File(context.cacheDir, "wallpaper_cache").apply { mkdirs() }
            val tempFile = File(cacheDir, "next_wallpaper.jpg")
            val url = photo.getSizedUrl(metrics.widthPixels, metrics.heightPixels)
            api.downloadImage(url, tempFile).getOrNull() ?: return Result.retry()

            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            if (bitmap != null) {
                WallpaperManager.getInstance(context).setBitmap(bitmap)
                bitmap.recycle()
            } else return Result.retry()

            settings.lastPhotoIndex = nextIndex
            tempFile.delete()
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker error", e)
            return Result.retry()
        } finally {
            authManager.dispose()
        }
    }
}