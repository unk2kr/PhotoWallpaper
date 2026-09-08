package com.photowallpaper

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.photowallpaper.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Главный экран: галерея «фото дня» Bing + настройки автосмены обоев.
 * Авторизация не нужна — используется публичный эндпоинт Bing.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager
    private var gallery: List<BingImage> = emptyList()

    /** Защита от срабатывания слушателей во время восстановления состояния. */
    private var restoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsManager(this)

        setupListeners()
        loadState()
        loadGallery()
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

        binding.radioGroupInterval.setOnCheckedChangeListener { _, checkedId ->
            if (restoring) return@setOnCheckedChangeListener
            settings.intervalMinutes = when (checkedId) {
                R.id.radioDaily -> SettingsManager.INTERVAL_DAILY_MINUTES
                else -> SettingsManager.DEFAULT_INTERVAL_MINUTES
            }
            if (settings.isEnabled) {
                WallpaperWorker.schedulePeriodic(this, settings.intervalMinutes)
            }
        }

        binding.buttonChangeNow.setOnClickListener { changeNow() }
    }

    private fun loadState() {
        restoring = true
        binding.switchEnable.isChecked = settings.isEnabled
        val daily = settings.intervalMinutes >= SettingsManager.INTERVAL_DAILY_MINUTES
        binding.radioHourly.isChecked = !daily
        binding.radioDaily.isChecked = daily
        binding.textLastWallpaper.text =
            settings.lastWallpaperInfo ?: getString(R.string.current_none)
        restoring = false
    }

    private fun loadGallery() {
        binding.textGalleryStatus.setText(R.string.gallery_status_loading)
        binding.textGalleryStatus.isVisible = true
        lifecycleScope.launch {
            val images = try {
                val fresh = BingApi.fetchWallpapers()
                if (fresh.isNotEmpty()) {
                    settings.cachedGalleryJson = GalleryCodec.encode(fresh)
                }
                fresh
            } catch (e: Exception) {
                GalleryCodec.decode(settings.cachedGalleryJson)
            }
            gallery = images
            if (images.isEmpty()) {
                binding.textGalleryStatus.setText(R.string.gallery_status_error)
                binding.textGalleryStatus.isVisible = true
            } else {
                binding.textGalleryStatus.isVisible = false
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
                .load(img.imageUrl("1366x768"))
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
            Toast.makeText(this, R.string.gallery_status_error, Toast.LENGTH_SHORT).show()
            return
        }
        val index = if (settings.intervalMinutes >= SettingsManager.INTERVAL_DAILY_MINUTES) {
            0
        } else {
            val i = (settings.startOffset + settings.rotationCount) % gallery.size
            settings.rotationCount += 1
            i
        }
        applyImage(gallery[index])
    }

    /** Скачивает UHD и ставит обоями (с прогресс-состоянием кнопки). */
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
                Toast.makeText(this@MainActivity, R.string.toast_applied_ok, Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(this@MainActivity, R.string.toast_applied_fail, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}