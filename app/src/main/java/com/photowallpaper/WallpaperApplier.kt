package com.photowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Скачивает фото Bing (пробуя несколько разрешений) и ставит его обоями.
 * Битмап даунсэмплится под экран — защита от OutOfMemory на UHD-фото.
 * Причина неудачи записывается в settings.lastError (видна в интерфейсе).
 */
object WallpaperApplier {

    private const val TAG = "PhotoWallpaper"

    /** true — обои установлены. Иначе причина записана в settings.lastError. */
    suspend fun applyImage(context: Context, image: BingImage): Boolean {
        val settings = SettingsManager(context)
        settings.lastError = null

        val cacheDir = File(context.cacheDir, "wallpaper_cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "current_wallpaper.jpg")

        // 1) Скачиваем: пробуем кандидатов по очереди (UHD -> 1920x1080 -> raw url).
        var lastCode = -1
        var lastException: String? = null
        var downloaded = false
        for (url in image.downloadCandidates()) {
            try {
                val code = BingApi.downloadHttp(url, tempFile)
                if (code == 200 && tempFile.length() > 0) {
                    downloaded = true
                    Log.i(TAG, "Скачано ($code): $url")
                    break
                }
                lastCode = code
                Log.w(TAG, "HTTP $code для $url — пробуем следующий")
            } catch (e: Exception) {
                lastException = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Сбой загрузки $url: $lastException")
            }
        }
        if (!downloaded) {
            val reason = if (lastException != null) {
                "сеть: $lastException"
            } else {
                "Bing вернул HTTP $lastCode"
            }
            settings.lastError = "Не удалось скачать фото ($reason)"
            tempFile.delete()
            return false
        }

        // 2) Декодируем с даунсэмплингом под экран (защита от OOM на UHD).
        val bitmap = decodeSampled(context, tempFile)
        tempFile.delete()
        if (bitmap == null) {
            settings.lastError = "Не удалось декодировать изображение"
            return false
        }

        // 3) Ставим обоями.
        return try {
            WallpaperManager.getInstance(context).setBitmap(bitmap)
            Log.i(TAG, "Обои установлены: ${image.startdate}")
            true
        } catch (e: Exception) {
            settings.lastError = "Ошибка установки обоев: ${e.message}"
            Log.e(TAG, "Ошибка установки обоев", e)
            false
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Декодирует файл, уменьшая его так, чтобы большая сторона была
     * не больше ~1.5 * большей стороны экрана (хватает для обоев и бережёт память).
     */
    private fun decodeSampled(context: Context, file: File): Bitmap? {
        return try {
            val dm = context.resources.displayMetrics
            val target = (maxOf(dm.widthPixels, dm.heightPixels) * 1.5f)
                .toInt().coerceAtLeast(1080)

            // Сначала читаем только размеры (не сам битмап).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                // Размеры не определились — пробуем как есть.
                return BitmapFactory.decodeFile(file.absolutePath)
            }
            // Подбираем степень уменьшения (степень двойки).
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка декодирования", e)
            null
        }
    }
}