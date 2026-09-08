package com.photowallpaper

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Фоновая задача WorkManager: периодическая смена обоев
 * на «фото дня» Bing.
 *
 * Интервал задаётся пользователем в часах (1..24) и хранится
 * в SettingsManager.intervalMinutes.
 *  - интервал < 24 ч — ротация по 8 последним «фото дня»;
 *  - интервал = 24 ч — всегда сегодняшнее «фото дня».
 */
class WallpaperWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "PhotoWallpaperWorker"
        private const val WORK_NAME_PERIODIC = "photo_wallpaper_periodic"
        private const val WORK_NAME_ONCE = "photo_wallpaper_once"
        private const val KEY_FORCE = "force"

        /** Периодическая смена (интервал в минутах). */
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

        /** Однократная смена по кнопке — выполняется всегда,
         *  даже если авто-смена выключена. */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
            val work = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(workDataOf(KEY_FORCE to true))
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE, ExistingWorkPolicy.REPLACE, work
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(applicationContext)
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!force && !settings.isEnabled) return Result.success()

        // Галерея: по очереди Bing → Lorem Picsum (fallback).
        val (images, _) = fetchWallpaperList(settings)
        if (images.isEmpty()) return Result.retry()

        val index = if (settings.intervalHours >= SettingsManager.MAX_INTERVAL_HOURS) {
            // Суточный режим: всегда «фото дня» (индекс 0).
            0
        } else {
            // Интервал < 24 ч: ротация по галерее, чтобы обои менялись заметно.
            val i = (settings.startOffset + settings.rotationCount) % images.size
            settings.rotationCount += 1
            i
        }
        val image = images[index]

        return if (WallpaperApplier.applyImage(applicationContext, image)) {
            settings.lastWallpaperInfo =
                "${image.dateLabel()} • ${image.copyright}"
            Result.success()
        } else {
            Result.retry()
        }
    }
}