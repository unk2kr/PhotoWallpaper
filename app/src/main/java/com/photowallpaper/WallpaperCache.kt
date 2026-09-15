package com.photowallpaper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * LRU-кеш скачанных обоев: последние [MAX_ENTRIES] изображений.
 *
 * Хранение — внутренний кеш приложения (context.cacheDir), разрешения не нужны:
 *  - файлы:  cacheDir/wallpapers/<hash>.jpg
 *  - индекс: SharedPreferences (последовательность ключей: новый в конец).
 *
 * Ключ — SHA-256 от URL изображения (существует только для Bing; для
 * Wallhaven/Picsum ключ строится из urlBase), то есть «фото дня» не
 * зависит от выбранного разрешения (1920x1080 / UHD).
 *
 * Обои, уже сохранившиеся в кеше, в следующий раз ставятся без сети.
 */
object WallpaperCache {

    private const val TAG = "PhotoWallpaper"
    private const val PREFS_NAME = "wallpaper_cache"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 8

    /** Ключ кеша для фото: у Bing — urlBase (стабилен, не зависит от разрешения). */
    fun cacheKey(image: BingImage): String =
        if (image.source == BingImage.SOURCE_BING) image.urlBase
            else image.urlBase.ifBlank { image.url }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "wallpapers").apply { mkdirs() }

    private fun fileFor(context: Context, key: String): File =
        File(cacheDir(context), sha256Hex(key).take(40) + ".jpg")

    // ──────────────────────────────────────────
    // Запись индекса
    // ──────────────────────────────────────────

    private fun readEntries(context: Context): List<String> {
        val json = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(context: Context, entries: List<String>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    // ──────────────────────────────────────────
    // Публичный API
    // ──────────────────────────────────────────

    /** Файл с обоями, если они уже в кеше, иначе null. Порядок индекса при чтении не меняется — обновляется только [put]. */
    suspend fun get(context: Context, key: String): File? =
        withContext(Dispatchers.IO) {
            val entries = readEntries(context)
            if (key !in entries) return@withContext null
            val file = fileFor(context, key)
            if (!file.exists() || file.length() == 0L) {
                // Индекс есть, файла нет — чистим и пропускаем.
                remove(context, key)
                return@withContext null
            }
            file
        }

    /**
     * Сохраняет только что скачанный файл в кеш и вытесняет самые старые
     * записи, пока их не станет [MAX_ENTRIES]. Повторное сохранение
     * одного и того же ключа не дублирует запись — просто «обновляет» её.
     */
    suspend fun put(context: Context, key: String, file: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val target = fileFor(context, key)
                if (file.absolutePath != target.absolutePath) {
                    if (!file.renameTo(target)) {
                        file.copyTo(target, overwrite = true)
                        file.delete()
                    }
                }
                val entries = readEntries(context).toMutableList()
                entries.remove(key)
                entries.add(key)
                while (entries.size > MAX_ENTRIES) {
                    val evicted = entries.removeAt(0)
                    fileFor(context, evicted).delete()
                    Log.i(TAG, "Кеш: вытеснен старый файл ($evicted)")
                }
                writeEntries(context, entries)
                Log.i(TAG, "Кеш: сохранено записей: ${entries.size} (лимит $MAX_ENTRIES)")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Кеш: не удалось сохранить файл", e)
                false
            }
        }

    /** Удаляет одну запись (например, если файл оказался битым). */
    fun remove(context: Context, key: String) {
        val entries = readEntries(context)
        if (key !in entries) return
        fileFor(context, key).delete()
        writeEntries(context, entries.filter { it != key })
    }

    /** Полный сброс кеша (файлы + индекс). */
    fun clear(context: Context) {
        val entries = readEntries(context)
        entries.forEach { fileFor(context, it).delete() }
        writeEntries(context, emptyList())
    }

    /** Сколько записей сейчас в кеше (для диагностики/теста). */
    fun size(context: Context): Int = readEntries(context).size

    // ──────────────────────────────────────────
    // Вспомогательное
    // ──────────────────────────────────────────

    /** SHA-256 в hex — устойчивый идентификатор файла. */
    private fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
