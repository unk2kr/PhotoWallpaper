import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ─────────────────────────────────────────────────────────────────
// OAuth-конфигурация.
//
// Client ID НЕ хранится в git. Источники (по приоритету):
//   1) -PGOOGLE_CLIENT_ID=...        (GitHub Actions берёт из secrets)
//   2) local.properties: GOOGLE_CLIENT_ID=...   (локальная сборка)
//
// Тип клиента: **Android**. Для него Google фиксирует redirect URI как
//   com.googleusercontent.apps.<CLIENT_ID_БЕЗ_.apps.googleusercontent.com>:/oauth2redirect
// Scheme/redirect считаются здесь автоматически — это исключает
// 400 redirect_uri_mismatch из-за опечатки при ручном вводе.
// ─────────────────────────────────────────────────────────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secretProp(name: String): String =
    (providers.gradleProperty(name).orNull ?: localProps.getProperty(name) ?: "")
        .trim().trim('"', ' ', '\t')

val googleClientId: String = secretProp("GOOGLE_CLIENT_ID")
val googleAndroidSuffix: String = ".apps.googleusercontent.com"
val googleIsAndroidClient: Boolean = googleClientId.endsWith(googleAndroidSuffix)
val googleRedirectScheme: String =
    if (googleIsAndroidClient) {
        "com.googleusercontent.apps." + googleClientId.removeSuffix(googleAndroidSuffix)
    } else {
        "com.photowallpaper"
    }
val googleRedirectUri: String =
    if (googleIsAndroidClient) "$googleRedirectScheme:/oauth2redirect"
    else "$googleRedirectScheme:/oauth2callback"
val googleClientType: String =
    if (googleIsAndroidClient) "android"
    else if (googleClientId.isBlank()) "none" else "web"

// Отладочный keystore, закоммиченный в репозиторий: SHA-1 у него постоянен,
// поэтому его достаточно один раз вписать в Google Cloud Console — и локальные,
// и CI-сборки будут подписаны одной и той же подписью.
val pinnedDebugKeystore = file("debug.keystore")

android {
    namespace = "com.photowallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.photowallpaper"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        manifestPlaceholders["appAuthRedirectScheme"] = googleRedirectScheme

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "OAUTH_REDIRECT_URI", "\"$googleRedirectUri\"")
        buildConfigField(
            "String",
            "OAUTH_CLIENT_TYPE",
            "\"$googleClientType\""
        )
    }

    signingConfigs {
        create("pinnedDebug") {
            storeFile = pinnedDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            if (pinnedDebugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("pinnedDebug")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")

    // Material Design 3
    implementation("com.google.android.material:material:1.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // OAuth2 — AppAuth
    implementation("net.openid:appauth:0.11.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JSON
    implementation("org.json:json:20240303")
}

/**
 * ./gradlew :app:printOAuthConfig — показывает, что будет вшито в APK.
 * Полезно, чтобы заранее знать Redirect URI для Google Cloud Console.
 */
tasks.register("printOAuthConfig") {
    group = "oauth"
    description = "Печатает Client ID / тип клиента / Redirect URI текущей сборки"
    doLast {
        println("GOOGLE_CLIENT_ID  = ${if (googleClientId.isBlank()) "<не задан>" else googleClientId}")
        println("OAUTH_CLIENT_TYPE = $googleClientType")
        println("REDIRECT_URI      = $googleRedirectUri")
        println(
            "DEBUG_KEYSTORE    = " +
                if (pinnedDebugKeystore.exists()) pinnedDebugKeystore.absolutePath
                else "<нет app/debug.keystore — подпись стандартным debug-ключом>"
        )
    }
}
