package com.photowallpaper

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Логгер ошибок. Сохраняет в папку приложения на внешнем хранилище
 * (Android/data/com.photowallpaper/files/Download/errors.log).
 *
 * Это не требует разрешений WRITE_EXTERNAL_STORAGE и работает на любом Android.
 * Файл доступен пользователю через файловый менеджер.
 */
object ErrorLogger {

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

                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                File(dir, FILE_NAME).appendBytes(line.toByteArray())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    /** Возвращает путь к лог-файлу (для показа в UI). */
    fun logUriDescription(context: Context): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(dir, FILE_NAME).absolutePath
    }
}

