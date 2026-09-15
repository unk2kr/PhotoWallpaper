package com.photowallpaper

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * DNS-over-HTTPS как запасной путь разрешения имён, когда системный DNS сломан
 * (нет DNS-сервера, DNS заблокирован, captive portal и т.п.).
 *
 * Провайдеры заданы ПИННЫМИ IP-адресами, чтобы сам запрос к DoH не зависел
 * от системного DNS (сертификаты провайдеров содержат эти IP в SAN,
 * поэтому проверка TLS по IP-литералу проходит штатно).
 * Формат ответа: dns-json (RFC 8484).
 */
object DoHResolver {

    private const val TAG = "PhotoWallpaperDoH"

    /** Пинные эндпоинты DoH. Порядок = приоритет. */
    private val PROVIDERS = listOf(
        "https://1.1.1.1/dns-query",          // Cloudflare
        "https://104.16.249.249/dns-query",   // Cloudflare
        "https://104.16.249.249/dns-query",   // Cloudflare
        "https://8.8.8.8/resolve",            // Google
        "https://8.8.4.4/resolve"             // Google
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Разрешает хост через пинные DoH-провайдеры.
     * Возвращает первый найденный IPv4 или null, если все провайдеры недоступны.
     */
    fun resolve(host: String): String? {
        if (host.isBlank()) return null
        for (base in PROVIDERS) {
            try {
                val url = "$base?name=${URLEncoder.encode(host, "UTF-8")}&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "DoH $base: HTTP ${resp.code} для $host")
                        return@use
                    }
                    val body = resp.body?.string() ?: return@use
                    val ip = parseFirstARecord(body) ?: return@use
                    Log.i(TAG, "DoH OK: $host -> $ip ($base)")
                    return ip
                }
            } catch (e: Exception) {
                Log.w(TAG, "DoH $base: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        Log.w(TAG, "Все DoH-провайдеры недоступны для $host")
        return null
    }

    /** Достаёт первую A-запись (type=1) с IPv4 из ответа dns-json. */
    fun parseFirstARecord(json: String): String? {
        return try {
            val o = JSONObject(json)
            if (o.optInt("Status", -1) != 0) return null
            val answers = o.optJSONArray("Answer") ?: return null
            for (i in 0 until answers.length()) {
                val a = answers.getJSONObject(i)
                if (a.optInt("type", -1) != 1) continue
                val data = a.optString("data", "")
                if (isIpv4(data)) return data
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isIpv4(s: String): Boolean {
        val parts = s.split(".")
        if (parts.size != 4) return false
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return false
            if ((p.toIntOrNull() ?: return false) > 255) return false
        }
        return true
    }

    /**
     * OkHttp Dns: сначала системный DNS (быстрый путь), при сбое или пустом
     * ответе — пинные DoH-провайдеры. Подключается через Builder().dns(...).
     */
    fun dohFallbackDns(): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // 1) Системный DNS.
            try {
                val addrs = InetAddress.getAllByName(hostname)
                if (addrs.isNotEmpty()) return addrs.toList()
                Log.w(TAG, "Системный DNS: пустой ответ для $hostname")
            } catch (e: Exception) {
                Log.w(TAG, "Системный DNS не смог разрешить $hostname: ${e.message}")
            }
            // 2) DoH fallback (пинные IP, не зависят от системного DNS).
            val ip = DoHResolver.resolve(hostname) ?: throw UnknownHostException(
                "Не удалось разрешить $hostname (системный DNS и DoH недоступны)"
            )
            return try {
                listOf(InetAddress.getByName(ip))
            } catch (e: Exception) {
                throw UnknownHostException("Некорректный IP от DoH: $ip")
            }
        }
    }
}
