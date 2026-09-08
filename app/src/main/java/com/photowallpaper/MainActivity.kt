package com.photowallpaper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.photowallpaper.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Главный экран: галерея «фото дня» Bing + настройки автосмены обоев.
 * Авторизация не нужна — используется публичный эндпоинт Bing.
 *
 * Интервал смены задаётся слайдером: любое целое число часов от 1 до 24.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager
    private var gallery: List<BingImage> = emptyList()

    /** Защита от срабатывания слушателей во время восстановления состояния. */
    private var restoring = false

    /** Запрос разрешения на запись в хранилище (Android 9 и ниже). */
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Без разрешения логи ошибок не будут сохраняться в Downloads",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsManager(this)

        ensurePermissions()
        checkNetworkOnStart()
        setupListeners()
        loadState()
        loadGallery()
    }

    /** Проверяет доступность сети при запуске и показывает диагностику. */
    private fun checkNetworkOnStart() {
        val hasNetwork = NetworkUtils.isNetworkAvailable(this)
        val connectionType = NetworkUtils.getConnectionType(this)
        
        if (!hasNetwork) {
            val msg = "⚠️ Нет доступа к интернету\n\n" +
                    "Тип соединения: $connectionType\n\n" +
                    "Проверьте:\n" +
                    "• Включён ли мобильный интернет или Wi-Fi\n" +
                    "• Не заблокирован ли доступ к bing.com и picsum.photos\n" +
                    "• Настройки прокси/VPN"
            binding.textGalleryStatus.text = msg
            binding.textGalleryStatus.isVisible = true
            binding.textGalleryStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            // Сеть есть, но покажем тип соединения для диагностики
            binding.textGalleryStatus.text = "✓ Сеть: $connectionType"
            binding.textGalleryStatus.isVisible = true
            binding.textGalleryStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            // Скрываем через 2 секунды
            binding.root.postDelayed({
                if (gallery.isNotEmpty()) {
                    binding.textGalleryStatus.isVisible = false
                }
            }, 2000)
        }
    }

    /** Проверяет и запрашивает нужные разрешения. */
    private fun ensurePermissions() {
        // На Android 10+ разрешение не нужно — используется MediaStore.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Показываем ошибку, если фоновый воркер не смог скачать фото.
        renderError(settings.lastError)
    }

    private fun setupListeners() {
        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            if (restoring) return@setOnCheckedChangeListener
            settings.isEnabled = checked
            if (checked) {
                WallpaperWorker.schedulePeriodic(this, settings.intervalMinutes)
            } else {
                WallpaperWorker.cancelPeriodic(this)
            }
        }

        // Слайдер: progress 0..23 -> часы 1..24.
        binding.seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textIntervalValue.text = intervalLabel(progress + 1)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (restoring) return
                val hours = (binding.seekInterval.progress + 1)
                    .coerceIn(
                        SettingsManager.MIN_INTERVAL_HOURS,
                        SettingsManager.MAX_INTERVAL_HOURS
                    )
                settings.intervalHours = hours
                if (settings.isEnabled) {
                    WallpaperWorker.schedulePeriodic(this@MainActivity, settings.intervalMinutes)
                }
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_interval_saved, intervalLabel(hours)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        binding.buttonChangeNow.setOnClickListener { changeNow() }
    }
    private fun loadState() {
        restoring = true
        binding.switchEnable.isChecked = settings.isEnabled
        val hours = settings.intervalHours
        binding.seekInterval.progress = (hours - 1)
            .coerceIn(0, SettingsManager.MAX_INTERVAL_HOURS - 1)
        binding.textIntervalValue.text = intervalLabel(hours)
        binding.textLastWallpaper.text =
            settings.lastWallpaperInfo ?: getString(R.string.current_none)
        restoring = false
    }

    /** Текстовое представление интервала в часах. */
    private fun intervalLabel(hours: Int): String = when (hours) {
        1 -> getString(R.string.interval_value_1)
        SettingsManager.MAX_INTERVAL_HOURS -> getString(R.string.interval_value_24)
        else -> getString(R.string.interval_value_hours, hours)
    }

    /** Показ/скрытие блока с последней ошибкой скачивания. */
    private fun renderError(error: String?) {
        if (error.isNullOrBlank()) {
            binding.textError.isVisible = false
        } else {
            binding.textError.text = "${getString(R.string.error_title)}: $error"
            binding.textError.isVisible = true
        }
    }

    private fun loadGallery() {
        binding.textGalleryStatus.setText(R.string.gallery_status_loading)
        binding.textGalleryStatus.isVisible = true
        lifecycleScope.launch {
            val (images, error) = fetchWallpaperList(settings)
            gallery = images
            renderError(error)
            if (images.isEmpty()) {
                binding.textGalleryStatus.text =
                    getString(R.string.gallery_status_error) + "\n" + (error ?: "")
                binding.textGalleryStatus.isVisible = true
                binding.textTapHint.isVisible = false
            } else {
                // Сохраняем кэш для офлайн-работы
                settings.cachedGalleryJson = GalleryCodec.encode(images)
                binding.textGalleryStatus.isVisible = false
                binding.textTapHint.isVisible = true
            }
            renderGallery(images)
        }
    }

    /** Горизонтальная лента превью + список стартового фото. */
    private fun renderGallery(images: List<BingImage>) {
        binding.previewContainer.removeAllViews()
        if (images.isEmpty()) return

        val density = resources.displayMetrics.density
        val thumbW = (96 * density).toInt()
        val thumbH = (72 * density).toInt()
        val endMargin = (8 * density).toInt()

        images.forEach { img ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbW, thumbH).apply {
                    this.marginEnd = endMargin
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = img.copyright
                setOnClickListener { applyImage(img) }
            }
            Glide.with(this)
                .load(img.previewUrl())
                .placeholder(android.R.color.darker_gray)
                .into(iv)
            binding.previewContainer.addView(iv)
        }

        fillStartSpinner(images)
    }

    private fun fillStartSpinner(items: List<BingImage>) {
        val labels = items.mapIndexed { i, img ->
            getString(R.string.start_item_format, i + 1, img.dateLabel())
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStart.adapter = adapter
        binding.spinnerStart.setSelection(settings.startOffset.coerceIn(0, items.size - 1))
        // Слушатель вешаем ПОСЛЕ начальной установки, чтобы не сбросить сохранённый offset.
        binding.spinnerStart.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                settings.startOffset = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    /** Кнопка «Сменить сейчас» — берёт следующее фото по логике воркера. */
    private fun changeNow() {
        if (gallery.isEmpty()) {
            // Возможно, галерея не загрузилась при старте — пробуем ещё раз.
            lifecycleScope.launch {
                val (fresh, error) = fetchWallpaperList(settings)
                if (fresh.isNotEmpty()) {
                    gallery = fresh
                    settings.cachedGalleryJson = GalleryCodec.encode(fresh)
                    renderGallery(gallery)
                    applyImage(pickNextImage())
                } else {
                    renderError(error ?: getString(R.string.gallery_status_error))
                    Toast.makeText(
                        this@MainActivity,
                        R.string.gallery_status_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }
        applyImage(pickNextImage())
    }

    /** Выбирает следующее фото из галереи по текущим настройкам. */
    private fun pickNextImage(): BingImage {
        val index = if (settings.intervalHours >= SettingsManager.MAX_INTERVAL_HOURS) {
            0 // суточный режим: фото дня
        } else {
            val i = (settings.startOffset + settings.rotationCount) % gallery.size
            settings.rotationCount += 1
            i
        }
        return gallery[index]
    }

    /** Скачивает фото (с запасными разрешениями) и ставит его обоями. */
    private fun applyImage(image: BingImage) {
        Toast.makeText(this, R.string.toast_applying, Toast.LENGTH_SHORT).show()
        binding.buttonChangeNow.isEnabled = false
        lifecycleScope.launch {
            val ok = WallpaperApplier.applyImage(this@MainActivity, image)
            binding.buttonChangeNow.isEnabled = true
            if (ok) {
                val info = "${image.dateLabel()} • ${image.copyright}"
                settings.lastWallpaperInfo = info
                binding.textLastWallpaper.text = info
                renderError(null)
                Toast.makeText(this@MainActivity, R.string.toast_applied_ok, Toast.LENGTH_SHORT)
                    .show()
            } else {
                renderError(settings.lastError)
                Toast.makeText(this@MainActivity, R.string.toast_applied_fail, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}
