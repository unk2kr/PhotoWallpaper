package com.photowallpaper

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Диагностика OAuth-конфигурации, вшитой в APK на этапе сборки.
 *
 * Нужна для того, чтобы на устройстве сразу видеть, почему Google
 * отвечает 400 на шаге авторизации: не задан Client ID, выбран не тот
 * тип клиента или не совпадает redirect URI / SHA-1 подписи.
 */
object OAuthConfig {

    private const val PLACEHOLDER_MARK = "YOUR_"

    val clientId: String = BuildConfig.GOOGLE_CLIENT_ID
    val redirectUri: String = BuildConfig.OAUTH_REDIRECT_URI
    val packageName: String = BuildConfig.APPLICATION_ID
    val clientType: String = BuildConfig.OAUTH_CLIENT_TYPE
    val isAndroidClient: Boolean = clientType == "android"

    /** Client ID задан и не является заглушкой. */
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && !clientId.contains(PLACEHOLDER_MARK, ignoreCase = true)

    /**
     * Фатальная проблема — запускать OAuth-флоу бессмысленно
     * (Google гарантированно вернёт 400).
     */
    fun fatalProblem(): String? = if (!isConfigured) {
        "Client ID не вшит в сборку. Создайте OAuth Client ID типа «Android» в " +
            "Google Cloud Console и передайте его сборке: local.properties → " +
            "GOOGLE_CLIENT_ID=<id> (или secret GOOGLE_CLIENT_ID в GitHub Actions)."
    } else {
        null
    }

    /** Предупреждение — попробовать можно, но 400 вероятен. */
    fun warning(): String? = when {
        !isConfigured -> fatalProblem()
        !isAndroidClient ->
            "Тип клиента — «$clientType», а не Android. Google отклоняет такие " +
                "запросы из установленного приложения ошибкой 400. Нужен Client ID " +
                "типа «Android» с Package name + SHA-1 подписи APK (см. ниже)."
        else -> null
    }

    /** Человекочитаемая сводка для экрана диагностики. */
    fun summary(context: Context): String = buildString {
        appendLine(
            if (isConfigured) "✅ Client ID вшит: …${clientId.takeLast(28)}"
            else "❌ Client ID: не настроен ($clientType)"
        )
        appendLine(
            if (isAndroidClient) "✅ Тип клиента: Android"
            else "⚠️ Тип клиента: $clientType"
        )
        appendLine("Package name: $packageName")
        appendLine("Redirect URI: $redirectUri")
        append("SHA-1 подписи: ")
        append(signingCertificateFingerprints(context).joinToString().ifBlank { "не удалось прочитать" })
    }

    /**
     * Отпечатки сертификата, которым подписан установленный APK.
     * Именно SHA-1 просит поле «SHA-1 and certificate fingerprints»
     * в настройках Android OAuth-клиента Google Cloud Console.
     */
    @Suppress("DEPRECATION")
    fun signingCertificateFingerprints(context: Context): List<String> = try {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        val signatures = info.signatures ?: emptyArray()
        signatures.mapNotNull { signature ->
            runCatching {
                MessageDigest.getInstance("SHA-1")
                    .digest(signature.toByteArray())
                    .joinToString(":") { "%02X".format(it) }
            }.getOrNull()
        }
    } catch (e: Exception) {
        emptyList()
    }
}
