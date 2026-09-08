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
4. Настройте **OAuth consent screen** (`APIs & Services` → `OAuth consent screen`):
   - Тип: **External**, заполните название и email
   - Добавьте scope `.../auth/photoslibrary.readonly`
   - Пока приложение в статусе **Testing** — добавьте свой Google-аккаунт
     в список **Test users**, иначе Google вернёт `access_denied`
5. Создайте **OAuth 2.0 Client ID**:
   - `APIs & Services` → `Credentials` → `Create Credentials` → `OAuth client ID`
   - Тип: **⚠️ Android** (не «Web application»!)
   - **Package name**: `com.photowallpaper`
   - **SHA-1 certificate fingerprint**: отпечаток ключа, которым подписан APK
     (см. ниже — его показывает workflow «Generate Debug Keystore» и само
     приложение в блоке «🔧 Диагностика OAuth»)
6. Скопируйте **Client ID** вида `123456789-abc...apps.googleusercontent.com`

> **Почему тип Android, а не Web?**
> Для клиента типа «Web application» Google требует client_secret при обмене
> кода на токен и регистрацию redirect URI вручную — из мобильного приложения
> это даёт ошибки `400 redirect_uri_mismatch` / `unauthorized_client`.
> Android-клиент — публичный: секрет не нужен, а redirect URI формируется
> автоматически как `com.googleusercontent.apps.<ID>:/oauth2redirect`.

### 2. Зафиксируйте debug-ключ подписи

SHA-1 подписи должен совпадать с зарегистрированным в консоли. Чтобы SHA-1
не менялся при каждой CI-сборке, используется закреплённый keystore:

1. GitHub → **Actions** → **«Generate Debug Keystore»** → **Run workflow**
2. В логе — **SHA-1** (впишите его в пункт 1.5 выше)
3. Скачайте артефакт `pinned-debug-keystore`, положите файл в `app/debug.keystore`
   и закоммитьте (он исключён из `.gitignore` нарочно — отладочные ключи не секретны)

### 3. Передайте Client ID сборке

Локально — в `local.properties` (не коммитится):

```properties
GOOGLE_CLIENT_ID=123456789-abc....apps.googleusercontent.com
```

На CI — в секретах репозитория:
**Settings → Secrets and variables → Actions → New repository secret** →
`GOOGLE_CLIENT_ID` = ваш Client ID. Workflow сам впишет его в сборку.

Проверить, что вшьётся в APK:

```bash
./gradlew :app:printOAuthConfig
```

### 4. Соберите и запустите

```bash
./gradlew assembleDebug
```

Или просто запушьте — **GitHub Actions** соберёт APK в артефакты.

## 🩺 Ошибка 400 при авторизации — диагностика

Откройте в приложении блок **«🔧 Диагностика OAuth»** — там видно Client ID,
тип клиента, Redirect URI и SHA-1 подписи именно вашей сборки.

| Текст ошибки Google | Причина | Лечение |
|---------------------|---------|---------|
| `400: invalid_request` | Client ID пустой/заглушка | Шаги 3 выше |
| `400: redirect_uri_mismatch` | redirect URI не совпадает | Для Android-клиента URI фиксирован — проверьте, что клиент именно типа **Android** |
| `400: unauthorized_client` | Тип клиента «Web application» | Пересоздайте клиент типа **Android** |
| Страница «unregistered/invalid» | SHA-1 подписи ≠ зарегистрированному | Выполните шаг 2, впишите SHA-1 из диагностики приложения |
| `access_denied` / приложение «заблокировано» | Consent screen в Testing, аккаунт не в Test users | Добавьте себя в **Test users** |

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