package com.photowallpaper

import android.content.Context
import android.content.SharedPreferences

/**
 * Настройки приложения:
 * интервал смены, точка старта ротации, состояние,
 * кэш последней галереи Bing (для офлайн-работы).
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ──────────────────────────────────────────
    // Интервал смены обоев в минутах (часы × 60)
    // ──────────────────────────────────────────

    var intervalMinutes: Long
        get() = prefs.getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putLong(KEY_INTERVAL_MINUTES, value).apply()

    /** Интервал в часах (1..24) — удобная обёртка для слайдера в UI. */
    var intervalHours: Int
        get() = (intervalMinutes / 60L).toInt().coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)
        set(value) {
            intervalMinutes = value.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS) * 60L
        }

    // ──────────────────────────────────────────
    // Включена ли автоматическая смена
    // ──────────────────────────────────────────

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    // ──────────────────────────────────────────
    // Часовой режим: с какого из 8 фото начать (0 = сегодня)
    // ──────────────────────────────────────────

    var startOffset: Int
        get() = prefs.getInt(KEY_START_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_START_OFFSET, value).apply()

    // ──────────────────────────────────────────
    // Сколько ротаций уже произошло (часовой режим)
    // ──────────────────────────────────────────

    var rotationCount: Int
        get() = prefs.getInt(KEY_ROTATION_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_ROTATION_COUNT, value).apply()

    // ──────────────────────────────────────────
    // Текущие обои: "дата • автор" (для показа в UI)
    // ──────────────────────────────────────────

    var lastWallpaperInfo: String?
        get() = prefs.getString(KEY_LAST_WALLPAPER, null)
        set(value) = prefs.edit().putString(KEY_LAST_WALLPAPER, value).apply()

    // ──────────────────────────────────────────
    // Кэш галереи (последний успешный ответ Bing)
    // ──────────────────────────────────────────

    var cachedGalleryJson: String?
        get() = prefs.getString(KEY_CACHED_GALLERY, null)
        set(value) = prefs.edit().putString(KEY_CACHED_GALLERY, value).apply()

    // ──────────────────────────────────────────
    // Последняя ошибка скачивания (для показа в UI)
    // ──────────────────────────────────────────
    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "photo_wallpaper_prefs"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_ENABLED = "wallpaper_enabled"
        private const val KEY_START_OFFSET = "start_offset"
        private const val KEY_ROTATION_COUNT = "rotation_count"
        private const val KEY_LAST_WALLPAPER = "last_wallpaper_info"
        private const val KEY_CACHED_GALLERY = "cached_gallery_json"
        private const val KEY_LAST_ERROR = "last_error"

        const val DEFAULT_INTERVAL_MINUTES = 60L // 1 час по умолчанию
        const val INTERVAL_DAILY_MINUTES = 1440L
        const val MIN_INTERVAL_HOURS = 1
        const val MAX_INTERVAL_HOURS = 24
    }
}