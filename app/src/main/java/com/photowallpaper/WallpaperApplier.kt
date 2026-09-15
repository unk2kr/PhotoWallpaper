package com.photowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Стадии загрузки обоев для индикатора в UI.
 * [Downloading.progress]: 0..100 при известном размере файла, null — стадия идёт,
 * но общий размер ответа неизвестен.
 */
sealed class WallpaperProgress {
    /** Обои уже скачаны ранее — устанавливаем из кеша, без сети. */
    object CacheHit : WallpaperProgress()

    /** Идёт скачивание. */
    data class Downloading(val progress: Int?) : WallpaperProgress()

    /** Скачано, идёт декодирование/даунсэмплинг. */
    object Decoding : WallpaperProgress()

    /** Изображение готово, идёт установка обоев. */
    object Setting : WallpaperProgress()
}

/** Колбэк прогресса. Вызывается с фонового диспетчера — обновлять UI только через main. */
fun interface WallpaperProgressListener {
    fun onProgress(progress: WallpaperProgress)
}

/**
 * Скачивает фото (Bing → Wallhaven → Picsum, пробуя несколько разрешений)
 * и ставит его обоями. Битмап даунсэмплится под экран — защита от OutOfMemory
 * на UHD-фото. Причина неудачи записывается в settings.lastError (видна в UI).
 *
 * Кеш: последние скачанные обои сохраняются во внутреннем хранилище
 * ([WallpaperCache], LRU) — повторная установка идёт без сети.
 */
object WallpaperApplier {

    private const val TAG = "PhotoWallpaper"

    /** true — обои установлены. Иначе причина записана в settings.lastError. */
    suspend fun applyImage(
        context: Context,
        image: BingImage,
        progressListener: WallpaperProgressListener? = null
    ): Boolean {
        val settings = SettingsManager(context)
        settings.lastError = null
        val appContext = context.applicationContext

        val cacheDir = File(context.cacheDir, "wallpaper_cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "current_wallpaper.jpg")
        val key = WallpaperCache.cacheKey(image)

        // 0) Быстрый путь: обои уже скачивались ранее — берём из кеша, без сети.
        val cachedFile = WallpaperCache.get(appContext, key)
        if (cachedFile != null) {
            progressListener?.onProgress(WallpaperProgress.CacheHit)
            Log.i(TAG, "Кеш: используем сохранённое фото ($key)")
            progressListener?.onProgress(WallpaperProgress.Decoding)
            val bitmap = withContext(Dispatchers.IO) { decodeSampled(appContext, cachedFile) }
            if (bitmap != null) {
                progressListener?.onProgress(WallpaperProgress.Setting)
                return try {
                    WallpaperManager.getInstance(appContext).setBitmap(bitmap)
                    Log.i(TAG, "Обои установлены из кеша: ${image.startdate}")
                    true
                } catch (e: Exception) {
                    settings.lastError = "Ошибка установки обоев: ${e.message}"
                    ErrorLogger.log(appContext, settings.lastError ?: "set wallpaper failed", e)
                    Log.e(TAG, "Ошибка установки обоев", e)
                    false
                } finally {
                    bitmap.recycle()
                }
            }
            // Файл в кеше битый — убираем запись и скачиваем заново.
            Log.w(TAG, "Кеш: файл $key битый, скачиваем заново")
            WallpaperCache.remove(appContext, key)
        }

        // 1) Скачиваем Bing: пробуем кандидатов по очереди.
        var lastCode = -1
        var lastException: String? = null
        var downloaded = false
        val candidates = image.downloadCandidates(context)
        for (url in candidates) {
            try {
                val code = BingApi.downloadHttp(url, tempFile) { p ->
                    progressListener?.onProgress(WallpaperProgress.Downloading(p))
                }
                if (code == 200 && tempFile.length() > 0) {
                    downloaded = true
                    Log.i(TAG, "Bing скачан ($code): $url")
                    break
                }
                lastCode = code
                Log.w(TAG, "Bing HTTP $code для $url — пробуем следующий")
            } catch (e: Exception) {
                lastException = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Bing сбой загрузки $url: $lastException")
            }
        }

        // Если Bing не помог и это фото от Picsum — пробуем их API.
        if (!downloaded && image.urlBase.startsWith("/picsum/")) {
            Log.i(TAG, "Пикасум-фото, пробуем запасной источник")
            try {
                val code = LoremPicsumApi.downloadHttp(image.url, tempFile) { p ->
                    progressListener?.onProgress(WallpaperProgress.Downloading(p))
                }
                if (code == 200 && tempFile.length() > 0) {
                    downloaded = true
                    Log.i(TAG, "Picsum скачан ($code): ${image.url}")
                } else {
                    Log.w(TAG, "Picsum HTTP $code")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Picsum сбой: ${e.message}")
            }
        }

        if (!downloaded) {
            val reason = if (image.urlBase.startsWith("/picsum/")) {
                "Не удалось скачать фото: все источники недоступны"
            } else {
                "Бинг недоступен (${if (lastException != null) lastException else "HTTP $lastCode"})"
            }
            settings.lastError = "Не удалось скачать фото ($reason)"
            ErrorLogger.log(appContext, settings.lastError ?: "download failed")
            tempFile.delete()
            return false
        }

        // Сохраняем в LRU-кеш (последние 8), чтобы повторная установка шла без сети.
        WallpaperCache.put(appContext, key, tempFile)

        // 2) Декодируем с даунсэмплингом под экран (защита от OOM на UHD).
        progressListener?.onProgress(WallpaperProgress.Decoding)
        val bitmap = withContext(Dispatchers.IO) { decodeSampled(appContext, tempFile) }
        tempFile.delete()
        if (bitmap == null) {
            settings.lastError = "Не удалось декодировать изображение"
            ErrorLogger.log(appContext, settings.lastError ?: "decode failed")
            return false
        }

        // 3) Ставим обоями.
        progressListener?.onProgress(WallpaperProgress.Setting)
        return try {
            WallpaperManager.getInstance(appContext).setBitmap(bitmap)
            Log.i(TAG, "Обои установлены: ${image.startdate}")
            true
        } catch (e: Exception) {
            settings.lastError = "Ошибка установки обоев: ${e.message}"
            ErrorLogger.log(appContext, settings.lastError ?: "set wallpaper failed", e)
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