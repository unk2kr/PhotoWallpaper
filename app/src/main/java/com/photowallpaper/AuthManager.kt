package com.photowallpaper

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.*
import net.openid.appauth.browser.BrowserAllowList
import net.openid.appauth.browser.VersionedBrowserMatcher
import org.json.JSONObject

/**
 * Менеджер OAuth2 авторизации через AppAuth.
 * Использует Authorization Code Flow с PKCE для Google OAuth2.
 */
class AuthManager(private val context: Context) {

    private val settings = SettingsManager(context)

    private val authServiceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    private val authService: AuthorizationService by lazy {
        val appAuthConfig = AppAuthConfiguration.Builder()
            .setBrowserMatcher(
                BrowserAllowList(
                    VersionedBrowserMatcher.CHROME_BROWSER,
                    VersionedBrowserMatcher.CHROME_CUSTOM_TAB
                )
            )
            .build()
        AuthorizationService(context, appAuthConfig)
    }

    /**
     * Redirect URI вычисляется на этапе сборки из Client ID (см. build.gradle.kts):
     *  • Android-клиент  → com.googleusercontent.apps.<ID>:/oauth2redirect
     *  • Web-клиент      → com.photowallpaper:/oauth2callback
     */
    private val redirectUri: Uri = Uri.parse(OAuthConfig.redirectUri)

    /**
     * Создаёт Intent для запуска OAuth2 авторизации.
     */
    fun createAuthorizationIntent(): Intent {
        OAuthConfig.fatalProblem()?.let { throw IllegalStateException(it) }

        val authRequest = AuthorizationRequest.Builder(
            authServiceConfig,
            OAuthConfig.clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScope("openid https://www.googleapis.com/auth/photoslibrary.readonly")
            .setPrompt("consent")
            .build()

        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        return authIntent
    }

    /**
     * Обрабатывает результат авторизации.
     */
    suspend fun handleAuthorizationResponse(
        intent: Intent,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (response != null) {
            // Обмениваем authorization code на токены
            val tokenRequest = response.createTokenExchangeRequest()
            authService.performTokenRequest(tokenRequest) { tokenResponse, tokenException ->
                if (tokenResponse != null) {
                    val authState = AuthState(response, tokenResponse, tokenException)
                    settings.authStateJson = authState.jsonSerializeString()
                    onResult(true, null)
                } else {
                    onResult(false, "Обмен кода на токен не удался: ${describe(tokenException)}")
                }
            }
        } else {
            onResult(false, "Авторизация не завершена: ${describe(exception)}")
        }
    }

    /** Развёрнутое описание ошибки AppAuth (тип / код / описание). */
    private fun describe(e: AuthorizationException?): String =
        e?.let { (it.message ?: "").ifBlank { it.toString() } } ?: "неизвестная ошибка"

    /**
     * Получает актуальный access token (с автоматическим обновлением).
     */
    fun getAccessToken(onToken: (token: String?) -> Unit) {
        val authStateJson = settings.authStateJson ?: return onToken(null)

        val authState = try {
            AuthState.jsonDeserialize(authStateJson)
        } catch (e: Exception) {
            return onToken(null)
        }

        if (authState.isAuthorized) {
            authState.performActionWithFreshTokens(authService) { accessToken, _, _ ->
                onToken(accessToken)
            }
        } else {
            onToken(null)
        }
    }

    /**
     * Сохраняет обновлённое состояние AuthState.
     */
    fun updateAuthState(authState: AuthState) {
        settings.authStateJson = authState.jsonSerializeString()
    }

    /**
     * Выход — очистка токенов.
     */
    fun signOut() {
        settings.clearAuth()
    }

    fun dispose() {
        authService.dispose()
    }
}