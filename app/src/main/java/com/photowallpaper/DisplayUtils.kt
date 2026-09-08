package com.photowallpaper

import android.content.Context
import android.util.Size

/**
 * Разрешение экрана устройства.
 * Для обоев обычно нужна картинка немного больше реального разрешения —
 * берём округление до «стандартных» размеров, чтобы не перегружать сеть
 * гигантскими UHD-файлами на обычных телефонах.
 */
object DisplayUtils {

    /** Реальные пиксели экрана. */
    fun screenSize(context: Context): Size {
        val dm = context.resources.displayMetrics
        return Size(dm.widthPixels, dm.heightPixels)
    }

    /**
     * Оптимальный размер для скачивания обоев.
     * Для FullHD+ телефонов (Pixel 6 и т.п.) возвращает 1080×2400,
     * для более крупных — 1440×3200, иначе UHD.
     */
    fun wallpaperSize(context: Context): Size {
        val (w, h) = screenSize(context).let { it.width to it.height }
        val maxPx = maxOf(w, h)
        val minPx = minOf(w, h)
        return when {
            maxPx <= 2400 && minPx <= 1080 -> Size(1080, 2400)
            maxPx <= 3200 && minPx <= 1440 -> Size(1440, 3200)
            else -> Size(2160, 3840) // UHD 4K
        }
    }
}
