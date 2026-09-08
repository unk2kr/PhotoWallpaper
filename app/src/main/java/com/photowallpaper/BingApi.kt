package com.photowallpaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Одно «фото дня» Bing.
 * Список, возвращаемый BingApi, идёт от новых к старым: индекс 0 = сегодня.
 *
 * @param startdate    "20260908"
 * @param urlBase      "/th?id=OHR.BeechEngland_ROW3028721183"
 * @param copyright    подпись/автор
 * @param copyrightLink ссылка Bing на место на фото
 */
data class BingImage(
    val startdate: String,
    val urlBase: String,
    val copyright: String,
    val copyrightLink: String,
    /** Сырой url из ответа Bing (уже с "..._1920x1080.jpg&rf=...&pid=hp"). */
    val url: String = ""
) {
    /**
     * URL изображения. size: "UHD" (3840x2160) — для обоев,
     * "1920x1080" / "1366x768" — для превью.
     */
    fun imageUrl(size: String = "UHD"): String =
        "https://www.bing.com${urlBase}_${size}.jpg"

    /**
     * Кандидаты для скачивания обоев — от лучшего к запасному.
     * Приложение пробует их по очереди, пока один не вернёт картинку.
     * Это спасает, если конкретное разрешение недоступно в регионе.
     */
    fun downloadCandidates(): List<String> {
        val list = mutableListOf(
            imageUrl("UHD"),        // 3840x2160
            imageUrl("1920x1080")   // 1920x1080 — есть почти всегда
        )
        if (url.isNotBlank()) {
            list.add("https://www.bing.com$url")
        }
        return list
    }

    /** "8 сентября 2026" по startdate. */
    fun dateLabel(): String {
        val inFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val d = try { inFmt.parse(startdate) } catch (e: Exception) { null }
            ?: return startdate
        return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(d)
    }
}

/**
 * Bing Daily Wallpaper — публичный эндпоинт БЕЗ ключей и авторизации:
 * https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8
 *
 * Возвращает JSON с последними 8 «фото дня» и URL изображений
 * в нескольких разрешениях (включая UHD 3840x2160).
 */
object BingApi {

    private const val ARCHIVE_BASE =
        "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8"
    private const val UA = "Mozilla/5.0 (Linux; Android 14) PhotoWallpaper/1.3"

    /** Рынок по локали устройства (например, "ru-RU") — региональный фото-день. */
    private fun market(): String {
        val loc = Locale.getDefault()
        val lang = loc.language.ifBlank { "en" }
        val country = loc.country.ifBlank { "US" }
        return "$lang-$country"
    }

    private fun archiveUrl(): String = "$ARCHIVE_BASE&mkt=${market()}"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Последние 8 фото Bing. Индекс 0 = сегодня. Бросает IOException при ошибке. */
    suspend fun fetchWallpapers(): List<BingImage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(archiveUrl())
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Пустой ответ")
            val json = JSONObject(body.string())
            val arr = json.getJSONArray("images")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BingImage(
                    startdate = o.getString("startdate"),
                    urlBase = o.getString("urlbase"),
                    copyright = o.optString("copyright", ""),
                    copyrightLink = o.optString("copyrightlink", ""),
                    url = o.optString("url", "")
                )
            }
        }
    }

    /**
     * Скачивает изображение в файл. Возвращает HTTP-код:
     * 200 = успех; иначе — код ответа сервера. Бросает IOException при сетевом сбое.
     */
    suspend fun downloadHttp(url: String, dest: File): Int =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .build()
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                if (!resp.isSuccessful) return@withContext code
                val body = resp.body ?: return@withContext code
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                code
            }
        }
}

/**
 * Кэширование галереи в SharedPreferences (JSON-строка),
 * чтобы приложение работало, даже если Bing недоступен.
 */
object GalleryCodec {

    fun encode(list: List<BingImage>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("startdate", it.startdate)
                    .put("urlbase", it.urlBase)
                    .put("copyright", it.copyright)
                    .put("copyrightlink", it.copyrightLink)
                    .put("url", it.url)
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<BingImage> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BingImage(
                    startdate = o.getString("startdate"),
                    urlBase = o.getString("urlbase"),
                    copyright = o.optString("copyright", ""),
                    copyrightLink = o.optString("copyrightlink", ""),
                    url = o.optString("url", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}