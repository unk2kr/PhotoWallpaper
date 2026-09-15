package com.photowallpaper

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Wallhaven (wallhaven.cc) — публичная база обоев с бесплатным API, ключ не нужен:
 * https://wallhaven.cc/help/api
 *
 * Берём топл-лист за сегодня (sorting=toplist, topRange=1d) в разрешении
 * не ниже 1920x1080, только SFW (purity=100), без категории "people" (categories=110).
 * Картинки лежат на w.wallhaven.cc — прямые ссылки, отдаются без авторизации.
 * Лимит Wallhaven: 45 запросов в минуту — для приложения одного запроса на галерею хватает.
 */
object WallhavenApi {

    private const val SEARCH_URL =
        "https://wallhaven.cc/api/v1/search" +
            "?sorting=toplist&topRange=1d&atleast=1920x1080&purity=100&categories=110"

    /** Сколько фото забираем в галерею (как у Bing). */
    private const val LIMIT = 8

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .dns(DoHResolver.dohFallbackDns())
        .build()

    /**
     * Топ обоев Wallhaven за сегодня (индекс 0 = лучшее сегодня).
     * Бросает IOException при ошибке сети/ответа.
     */
    suspend fun fetchTop(): List<BingImage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SEARCH_URL)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Wallhaven HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Пустой ответ Wallhaven")
            val root = JSONObject(body.string())
            val arr = root.optJSONArray("data")
                ?: throw IOException("Wallhaven: нет поля data[]")

            val images = mutableListOf<BingImage>()
            for (i in 0 until arr.length()) {
                if (images.size >= LIMIT) break
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "")
                val path = o.optString("path", "")
                if (id.isBlank() || path.isBlank()) continue
                val thumbs = o.optJSONObject("thumbs")
                val uploader = o.optJSONObject("uploader")?.optString("username", "")
                    .orEmpty().ifBlank { "Wallhaven" }
                images += BingImage(
                    startdate = createdYmd(o.optString("created_at", ""), id),
                    urlBase = "wallhaven-$id",
                    copyright = "$uploader via Wallhaven",
                    copyrightLink = o.optString("url", "https://wallhaven.cc/w/$id"),
                    url = path,
                    previewUrl = thumbs?.optString("small", "").orEmpty(),
                    source = BingImage.SOURCE_WALLHAVEN
                )
            }
            if (images.isEmpty()) throw IOException("Wallhaven: пустой список")
            return@withContext images
        }
    }

    /** "2026-09-13 10:22:33" -> "20260913" (для dateLabel()); при неудаче — служебная метка. */
    private fun createdYmd(created: String, id: String): String {
        if (created.isNotBlank()) {
            try {
                val inFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val d = inFmt.parse(created.take(19))
                if (d != null) return SimpleDateFormat("yyyyMMdd", Locale.US).format(d)
            } catch (e: Exception) {
                // не распарсилось — используем метку ниже
            }
        }
        return "wallhaven_$id"
    }
}
