package com.photowallpaper

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Фоновая задача WorkManager: периодическая смена обоев
 * на «фото дня» Bing.
 *
 * Режимы:
 *  - «Раз в сутки» — всегда ставит фото сегодняшнего дня;
 *  - «Раз в час»   — ротация по последним 8 фото
 *                    (точка старта настраивается в приложении).
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

        // Галерея: свежая с Bing; при сбое сети — кэш предыдущего ответа.
        val images = try {
            val fresh = BingApi.fetchWallpapers()
            if (fresh.isNotEmpty()) settings.cachedGalleryJson = GalleryCodec.encode(fresh)
            fresh
        } catch (e: Exception) {
            Log.w(TAG, "Bing недоступен, пробуем кэш", e)
            GalleryCodec.decode(settings.cachedGalleryJson)
        }
        if (images.isEmpty()) return Result.retry()

        val index = if (settings.intervalMinutes >= SettingsManager.INTERVAL_DAILY_MINUTES) {
            0 // суточный режим: всегда фото дня
        } else {
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