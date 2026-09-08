package com.photowallpaper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * Расширенная диагностика сети для отладки.
 * Показывает техническую информацию: DNS, HTTP-статус, User-Agent, заголовки ответа.
 */
object NetworkDiagnostics {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val UA_CHROME =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    private const val UA_OKHTTP = "Mozilla/5.0 (compatible; PhotoWallpaper/1.6)"

    /** Формирует полный отчёт диагностики. */
    suspend fun fullReport(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════")
        sb.appendLine("  ДИАГНОСТИКА СЕТИ")
        sb.appendLine("═══════════════════════════════")
        sb.appendLine()
        sb.appendLine("📱 Устройство:")
        sb.appendLine("  ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("  App: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("  Время: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine()
        appendNetworkInfo(context, sb)
        sb.appendLine()
        sb.appendLine("🔎 DNS:")
        sb.appendLine("  bing.com → ${resolveDns("www.bing.com")}")
        sb.appendLine("  picsum.photos → ${resolveDns("picsum.photos")}")
        sb.appendLine()
        sb.appendLine("🌐 Bing (Chrome UA):")
        sb.append(checkUrl("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1", UA_CHROME))
        sb.appendLine()
        sb.appendLine("🌐 Bing (OkHttp UA):")
        sb.append(checkUrl("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1", UA_OKHTTP))
        sb.appendLine()
        sb.appendLine("🖼️ Bing UHD:")
        sb.append(checkUrl("https://www.bing.com/th?id=OHR.BeechEngland_ROW3028721183_UHD.jpg", UA_CHROME))
        sb.appendLine()
        sb.appendLine("🖼️ Bing 1920x1080:")
        sb.append(checkUrl("https://www.bing.com/th?id=OHR.BeechEngland_ROW3028721183_1920x1080.jpg", UA_CHROME))
        sb.appendLine()
        sb.appendLine("🌐 Picsum:")
        sb.append(checkUrl("https://picsum.photos/v2/list?page=1&limit=1", UA_CHROME))
        sb.appendLine()
        sb.appendLine("🔧 URLConnection (системный):")
        sb.append(checkUrlConnection("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1"))
        sb.toString()
        }

    private fun appendNetworkInfo(context: Context, sb: StringBuilder) {
        sb.appendLine("📡 Сеть:")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            sb.appendLine("  ✗ ConnectivityManager недоступен")
            return
        }
        val network = cm.activeNetwork
        sb.appendLine("  Активная сеть: ${if (network != null) "✓" else "✗"}")
        if (network != null) {
            val caps = cm.getNetworkCapabilities(network)
            if (caps != null) {
                sb.appendLine("  INTERNET: ${if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "✓" else "✗"}")
                sb.appendLine("  VALIDATED: ${if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "✓" else "✗"}")
                if (Build.VERSION.SDK_INT >= 28) {
                    sb.appendLine("  NOT_SUSPENDED: ${if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) "✓" else "✗"}")
                }
                val type = try {
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                        else -> "Unknown"
                    }
                } catch (e: Exception) { "Error: ${e.message}" }
                sb.appendLine("  Тип: $type")
                sb.appendLine("  Downstream: ${caps.linkDownstreamBandwidthKbps} kbps")
                sb.appendLine("  Upstream: ${caps.linkUpstreamBandwidthKbps} kbps")
            } else {
                sb.appendLine("  ✗ NetworkCapabilities недоступен")
            }
        }
    }

    private fun checkUrl(url: String, userAgent: String): String {
        val sb = StringBuilder()
        sb.appendLine("  URL: $url")
        sb.appendLine("  UA: $userAgent")
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/html, */*")
                .build()
            client.newCall(request).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                sb.appendLine("  HTTP ${resp.code} ${resp.message} (${elapsed}ms)")
                sb.appendLine("  Type: ${resp.header("Content-Type") ?: "—"}")
                sb.appendLine("  Server: ${resp.header("Server") ?: "—"}")
                sb.appendLine("  Size: ${resp.header("Content-Length") ?: "?"} bytes")
                if (!resp.isSuccessful) {
                    val body = try { resp.body?.string()?.take(500) ?: "—" } catch (e: Exception) { "?" }
                    sb.appendLine("  Body: $body")
                }
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            sb.appendLine("  ✗ ОШИБКА (${elapsed}ms)")
            sb.appendLine("  ${e.javaClass.simpleName}: ${e.message}")
            e.cause?.let { sb.appendLine("  Cause: ${it.javaClass.simpleName}: ${it.message}") }
        }
        return sb.toString()
    }

    private fun checkUrlConnection(url: String): String {
        val sb = StringBuilder()
        sb.appendLine("  URL: $url")
        val start = System.currentTimeMillis()
        try {
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA_CHROME)
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.connect()
            val elapsed = System.currentTimeMillis() - start
            sb.appendLine("  HTTP ${conn.responseCode} ${conn.responseMessage} (${elapsed}ms)")
            sb.appendLine("  Type: ${conn.contentType ?: "—"}")
            sb.appendLine("  Server: ${conn.getHeaderField("Server") ?: "—"}")
            conn.disconnect()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            sb.appendLine("  ✗ ОШИБКА (${elapsed}ms)")
            sb.appendLine("  ${e.javaClass.simpleName}: ${e.message}")
            e.cause?.let { sb.appendLine("  Cause: ${it.javaClass.simpleName}: ${it.message}") }
        }
        return sb.toString()
    }

    private fun resolveDns(host: String): String {
        return try {
            val addrs = InetAddress.getAllByName(host)
            if (addrs.isEmpty()) "✗" else addrs.joinToString(", ") { it.hostAddress ?: "?" }
        } catch (e: Exception) {
            "✗ ${e.message}"
        }
    }
}