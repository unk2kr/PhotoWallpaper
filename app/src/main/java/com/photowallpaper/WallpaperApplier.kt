package com.photowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Скачивает фото Bing в полном разрешении (UHD) и ставит его обоями.
 * Общий код для фонового воркера и кнопки «Сменить сейчас».
 */
object WallpaperApplier {

    private const val TAG = "PhotoWallpaper"

    /** true — обои установлены. */
    suspend fun applyImage(context: Context, image: BingImage): Boolean {
        val cacheDir = File(context.cacheDir, "wallpaper_cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "current_wallpaper.jpg")
        return try {
            val downloaded = BingApi.download(image.imageUrl("UHD"), tempFile)
            if (!downloaded) {
                Log.w(TAG, "Не удалось скачать: ${image.imageUrl("UHD")}")
                return false
            }
            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                ?: run {
                    Log.w(TAG, "Не удалось декодировать изображение")
                    return false
                }
            WallpaperManager.getInstance(context).setBitmap(bitmap)
            bitmap.recycle()
            Log.i(TAG, "Обои: ${image.startdate} | ${image.copyright}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка установки обоев", e)
            false
        } finally {
            tempFile.delete()
        }
    }
}