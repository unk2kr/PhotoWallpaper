# 📸 Photo Wallpaper

Android-приложение, которое автоматически меняет обои рабочего стола, используя фотографии из ваших альбомов **Google Photos**.

## ✨ Возможности

- 🔐 Авторизация через Google (OAuth2)
- 📸 Выбор альбома из Google Photos
- ⏰ Настраиваемый интервал смены обоев: **каждый час** или **раз в сутки**
- 🔄 Ротация фотографий из альбома (по очереди)
- 📱 Material Design 3 интерфейс
- 💾 Токены хранятся локально на устройстве

## 🛠 Технологии

| Компонент | Технология |
|-----------|-----------|
| Язык | Kotlin |
| UI | Material Design 3 + XML |
| Авторизация | AppAuth (OAuth2 + PKCE) |
| API | Google Photos Library API |
| Фоновая работа | WorkManager |
| HTTP | OkHttp |
| Изображения | BitmapFactory |

## 📋 Требования

- Android 8.0+ (API 26+)
- Google-аккаунт с альбомами в Google Photos

## 🚀 Как запустить

### 1. Настройте Google Cloud проект

1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте новый проект
3. Включите **Google Photos Library API**:
   - `APIs & Services` → `Library` → найдите `Photos Library API` → `Enable`
4. Создайте **OAuth 2.0 Client ID**:
   - `APIs & Services` → `Credentials` → `Create Credentials` → `OAuth client ID`
   - Тип: **Web application** (для AppAuth)
   - Authorized redirect URIs: `com.photowallpaper://oauth2callback`
5. Скопируйте **Client ID**

### 2. Вставьте Client ID в проект

Откройте `app/build.gradle.kts` и замените:

```kotlin
buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_WEB_CLIENT_ID_HERE\"")
```

На ваш Client ID:

```kotlin
buildConfigField("String", "GOOGLE_CLIENT_ID", "\"123456789-abc.apps.googleusercontent.com\"")
```

### 3. Соберите и запустите

```bash
./gradlew assembleDebug
```

Или откройте проект в **Android Studio** и нажмите Run.

## 📱 Использование

1. **Войдите через Google** — нажмите кнопку авторизации
2. **Выберите альбом** — из выпадающего списка
3. **Настройте интервал** — каждый час или раз в сутки
4. **Включите** — переключатель «Автоматическая смена»
5. **Примените** — нажмите «Применить настройки»
6. Или нажмите **«Сменить обои сейчас»** для немедленной смены

## 📁 Структура проекта

```
app/src/main/java/com/photowallpaper/
├── MainActivity.kt       — Экран настроек
├── AuthManager.kt        — OAuth2 авторизация (AppAuth)
├── GooglePhotosApi.kt    — Работа с Google Photos API
├── WallpaperWorker.kt    — WorkManager: фоновая смена обоев
└── SettingsManager.kt    — Хранение настроек (SharedPreferences)
```

## ⚠️ Ограничения Google Photos API

- `baseUrl` фотографий живёт **60 минут** — приложение запрашивает свежий URL при каждой смене
- API возвращает максимум **100 фото** за запрос (пагинация реализована)
- Для чтения нужен scope `photoslibrary.readonly`

## 📄 Лицензия

MIT