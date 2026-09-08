package com.photowallpaper

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Логгер ошибок. Сохраняет текстовый лог в папку Загрузки (Downloads/PhotoWallpaper).
 *
 * На Android 10+ используется MediaStore — разрешения не требуются.
 * На Android 9 и ниже пишем напрямую в общую папку Downloads
 * (для этого нужен WRITE_EXTERNAL_STORAGE, который запрашивается в MainActivity).
 */
object ErrorLogger {

    private const val LOG_DIR = "PhotoWallpaper"
    private const val FILE_NAME = "errors.log"

    /** Записывает одну ошибку в лог. */
    suspend fun log(context: Context, message: String, throwable: Throwable? = null) =
        withContext(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                val stack = throwable?.let {
                    "\nException: ${it.javaClass.simpleName}: ${it.message}" +
                            "\n${it.stackTraceToString()}"
                } ?: ""
                val line = "[$timestamp] $message$stack\n"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appendViaMediaStore(context, line)
                } else {
                    appendViaFile(line)
                }
            } catch (e: Exception) {
                // Если даже логгер сломался — ничего не поделаешь.
                e.printStackTrace()
            }
        }

    /** Возвращает путь к лог-файлу (для показа в UI). */
    fun logUriDescription(): String = "Downloads/$LOG_DIR/$FILE_NAME"

    private fun appendViaFile(line: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            LOG_DIR
        )
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME).appendBytes(line.toByteArray())
    }

    private fun appendViaMediaStore(context: Context, line: String) {
        val resolver = context.applicationContext.contentResolver
        val existing = findExistingLog(resolver)
        if (existing != null) {
            // Дописываем в существующий файл.
            val uri = existing.second
            context.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                out.write(line.toByteArray())
            }
        } else {
            // Создаём новый файл.
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/$LOG_DIR")
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    out.write(line.toByteArray())
                }
            }
        }
    }

    private fun findExistingLog(resolver: android.content.ContentResolver): Pair<Long, android.net.Uri>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val args = arrayOf(FILE_NAME, "Download/$LOG_DIR/")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return id to android.net.Uri.withAppendedPath(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()
                )
            }
        }
        return null
    }
}
