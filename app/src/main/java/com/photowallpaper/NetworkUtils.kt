package com.photowallpaper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Утилиты для проверки состояния сети.
 */
object NetworkUtils {

    /**
     * Проверяет, есть ли активное интернет-соединение.
     * Использует ConnectivityManager и NetworkCapabilities (Android 6+).
     * 
     * ВАЖНО: НЕ используем NET_CAPABILITY_VALIDATED — на многих устройствах
     * (особенно в РФ/Китае) эта проверка возвращает false, потому что система
     * не может пинговать connectivitycheck.gstatic.com, но интернет при этом работает.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        // Проверяем только INTERNET без VALIDATED — это более мягкая проверка
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Возвращает тип соединения: "Wi-Fi", "Mobile", "Ethernet" или "Unknown".
     */
    fun getConnectionType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"

        val network = cm.activeNetwork ?: return "Unknown"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "Unknown"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }
}