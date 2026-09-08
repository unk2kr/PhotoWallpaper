package com.photowallpaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Lorem Picsum — публичный сервис случайных фотографий БЕЗ ключей.
 * https://picsum.photos/v2/list?page={page}&limit={n}
 * Возвращает JSON: [{ id, author, width, height, url }]
 *
 * Используется как запасной источник когда Bing недоступен.
 */
object LoremPicsumApi {

    private const val LIST_URL = "https://picsum.photos/v2/list"
    /** Десктопный Chrome User-Agent — Bing не блокирует запросы от десктопных браузеров. */
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Создаёт Request.Builder с десктопными браузерными заголовками. */
    private fun browserRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", UA)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.5")
        .header("Cache-Control", "no-cache")

    /** Загружает список фото. Бросает IOException при ошибке сети. */
    suspend fun fetchPhotos(): List<BingImage> = withContext(Dispatchers.IO) {
        val request = browserRequest("$LIST_URL?page=1&limit=8")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Picsum HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Пустой ответ Picsum")
            val arr = JSONArray(body.string())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val id = o.getInt("id")
                val author = o.optString("author", "Picsum")
                // picsum.photos/id/{id}/width/height.jpg
                val rawUrl = "https://picsum.photos/id/$id/1920/1080.jpg"
                BingImage(
                    startdate = "picsum_$id",
                    urlBase = "/picsum/$id",
                    copyright = "$author via Picsum",
                    copyrightLink = "",
                    url = rawUrl
                )
            }
        }
    }

    /** Скачивает изображение по URL в File. Возвращает HTTP-код. */
    suspend fun downloadHttp(url: String, dest: File): Int =
        withContext(Dispatchers.IO) {
            val request = browserRequest(url)
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

    /**
     * Проверочный HTTP-запрос (HEAD) для диагностики.
     * Возвращает HTTP-код или бросает IOException.
     */
    suspend fun checkHttp(url: String): Int = withContext(Dispatchers.IO) {
        val request = browserRequest(url).head().build()
        client.newCall(request).execute().use { resp -> resp.code }
    }
}

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
     * URL превью для галереи — лёгкий размер, чтобы не тратить трафик.
     */
    fun previewUrl(): String = imageUrl("1366x768")

    /**
     * Кандидаты для скачивания обоев — от лучшего к запасному.
     * Для телефонов FullHD+ (Pixel 6 и др.) используем 1920x1080 вместо UHD,
     * чтобы уменьшить размер файла и трафик. UHD остаётся запасным вариантом.
     */
    fun downloadCandidates(context: Context): List<String> {
        val size = DisplayUtils.wallpaperSize(context)
        val isFullHdOrLess = maxOf(size.width, size.height) <= 2400
        val list = if (isFullHdOrLess) {
            mutableListOf(
                imageUrl("1920x1080"), // ~300 KB вместо ~4 MB UHD
                imageUrl("UHD")       // запасной
            )
        } else {
            mutableListOf(
                imageUrl("UHD"),
                imageUrl("1920x1080")
            )
        }
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
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

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

    /** Создаёт Request.Builder с десктопными браузерными заголовками. */
    private fun browserRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", UA)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.5")
        .header("Cache-Control", "no-cache")

    /** Последние 8 фото Bing. Индекс 0 = сегодня. Бросает IOException при ошибке. */
    suspend fun fetchWallpapers(): List<BingImage> = withContext(Dispatchers.IO) {
        val request = browserRequest(archiveUrl())
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
            val request = browserRequest(url).build()
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                if (!resp.isSuccessful) return@withContext code
                val body = resp.body ?: return@withContext code
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                code
            }
        }

    /**
     * Проверочный HTTP-запрос (HEAD) для диагностики.
     * Возвращает HTTP-код или бросает IOException.
     */
    suspend fun checkHttp(url: String): Int = withContext(Dispatchers.IO) {
        val request = browserRequest(url).head().build()
        client.newCall(request).execute().use { resp -> resp.code }
    }
}

/**
 * Кэширование галереи в SharedPreferences (JSON-строка),
 * чтобы приложение работало, даже если сервисы недоступны.
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

// ════════════════════════════════════════════════
// Альтернативные источники фото (fallback цепочка)
// ════════════════════════════════════════════════

/** Обёртка над любым списком изображений, возвращает BingImage[] и причину неудачи при ошибке. */
typealias ImageFetchResult = Pair<List<BingImage>, String?>

/**
 * Пытается загрузить фото из нескольких источников по очереди.
 *  1. Bing Daily Wallpaper (основной)
 *  2. Lorem Picsum (запасной — случайные красивые фото)
 * Возвращает Pair<список, причинаПоследнейОшибки>. Пустой список = все недоступны.
 */
suspend fun fetchWallpaperList(settings: SettingsManager): ImageFetchResult {
    // Попытка 1: Bing
    runCatching { BingApi.fetchWallpapers() }.onSuccess { list ->
        if (list.isNotEmpty()) return list to null
    }
    var lastError = "Bing недоступен"

    // Попытка 2: Lorem Picsum — публичный сервис без ключей
    runCatching { LoremPicsumApi.fetchPhotos() }.onSuccess { list ->
        if (list.isNotEmpty()) return list to null
    }
    lastError += "; Lorem Picsum также недоступен"

    settings.lastError = lastError
    settings.cachedGalleryJson = null
    return emptyList<BingImage>() to lastError
}
