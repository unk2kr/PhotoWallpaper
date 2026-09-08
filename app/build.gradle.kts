plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.photowallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.photowallpaper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // OAuth2 Web Client ID — замените на свой из Google Cloud Console
        manifestPlaceholders["appAuthRedirectScheme"] = "com.photowallpaper"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_WEB_CLIENT_ID_HERE\"")
    }

    buildTypes {
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