plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Отладочный keystore, закоммиченный в репозиторий:
// SHA-1 у него постоянен, поэтому локальные и CI-сборки
// подписаны одной и той же подписью (повторяемая установка).
val pinnedDebugKeystore = file("debug.keystore")

android {
    namespace = "com.photowallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.photowallpaper"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
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
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Material Design 3
    implementation("com.google.android.material:material:1.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Glide (превью галереи)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JSON
    implementation("org.json:json:20240303")
}
