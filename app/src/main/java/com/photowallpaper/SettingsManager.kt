package com.photowallpaper

import android.content.Context
import android.content.SharedPreferences

/**
 * Менеджер настроек приложения.
 * Хранит: выбранный альбом, интервал смены, состояние авторизации, OAuth токены.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ──────────────────────────────────────────
    // OAuth / Auth State
    // ──────────────────────────────────────────

    var authStateJson: String?
        get() = prefs.getString(KEY_AUTH_STATE, null)
        set(value) = prefs.edit().putString(KEY_AUTH_STATE, value).apply()

    val isLoggedIn: Boolean
        get() = authStateJson != null

    // ──────────────────────────────────────────
    // Выбранный альбом
    // ──────────────────────────────────────────

    var selectedAlbumId: String?
        get() = prefs.getString(KEY_ALBUM_ID, null)
        set(value) = prefs.edit().putString(KEY_ALBUM_ID, value).apply()

    var selectedAlbumTitle: String?
        get() = prefs.getString(KEY_ALBUM_TITLE, null)
        set(value) = prefs.edit().putString(KEY_ALBUM_TITLE, value).apply()

    // ──────────────────────────────────────────
    // Интервал смены обоев
    // ──────────────────────────────────────────

    var intervalMinutes: Long
        get() = prefs.getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putLong(KEY_INTERVAL_MINUTES, value).apply()

    // ──────────────────────────────────────────
    // Активность (включены ли обои)
    // ──────────────────────────────────────────

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    // ──────────────────────────────────────────
    // Индекс последнего показанного фото (для ротации)
    // ──────────────────────────────────────────

    var lastPhotoIndex: Int
        get() = prefs.getInt(KEY_LAST_PHOTO_INDEX, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_PHOTO_INDEX, value).apply()

    // ──────────────────────────────────────────
    // Очистка
    // ──────────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_AUTH_STATE)
            .remove(KEY_ALBUM_ID)
            .remove(KEY_ALBUM_TITLE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "photo_wallpaper_prefs"
        private const val KEY_AUTH_STATE = "auth_state_json"
        private const val KEY_ALBUM_ID = "selected_album_id"
        private const val KEY_ALBUM_TITLE = "selected_album_title"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_ENABLED = "wallpaper_enabled"
        private const val KEY_LAST_PHOTO_INDEX = "last_photo_index"

        const val DEFAULT_INTERVAL_MINUTES = 60L // 1 час по умолчанию
    }
}