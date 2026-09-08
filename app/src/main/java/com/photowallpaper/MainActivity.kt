package com.photowallpaper

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var settings: SettingsManager
    private val api = GooglePhotosApi()

    private lateinit var btnSignIn: MaterialButton
    private lateinit var btnSignOut: MaterialButton
    private lateinit var cardAlbum: MaterialCardView
    private lateinit var spinnerAlbum: Spinner
    private lateinit var tvAlbumStatus: TextView
    private lateinit var radioGroupInterval: RadioGroup
    private lateinit var radioHour: RadioButton
    private lateinit var radioDay: RadioButton
    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var btnApply: MaterialButton
    private lateinit var btnChangeNow: MaterialButton
    private lateinit var tvStatus: TextView

    private val oauthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        lifecycleScope.launch {
            authManager.handleAuthorizationResponse(data) { success, error ->
                if (success) {
                    tvStatus.text = "✅ Авторизация успешна!"
                    updateUI()
                    loadAlbums()
                } else {
                    tvStatus.text = "❌ Ошибка: $error"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = SettingsManager(this)
        authManager = AuthManager(this)
        bindViews()
        setupListeners()
        updateUI()
    }

    private fun bindViews() {
        btnSignIn = findViewById(R.id.btnSignIn)
        btnSignOut = findViewById(R.id.btnSignOut)
        cardAlbum = findViewById(R.id.cardAlbum)
        spinnerAlbum = findViewById(R.id.spinnerAlbum)
        tvAlbumStatus = findViewById(R.id.tvAlbumStatus)
        radioGroupInterval = findViewById(R.id.radioGroupInterval)
        radioHour = findViewById(R.id.radioHour)
        radioDay = findViewById(R.id.radioDay)
        switchEnabled = findViewById(R.id.switchEnabled)
        btnApply = findViewById(R.id.btnApply)
        btnChangeNow = findViewById(R.id.btnChangeNow)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupListeners() {
        btnSignIn.setOnClickListener {
            try {
                val intent = authManager.createAuthorizationIntent()
                oauthLauncher.launch(intent)
            } catch (e: Exception) {
                tvStatus.text = "❌ Ошибка: ${e.message}"
            }
        }
        btnSignOut.setOnClickListener {
            authManager.signOut()
            updateUI()
            tvStatus.text = "👋 Вы вышли из аккаунта"
        }
        btnApply.setOnClickListener { applySettings() }
        btnChangeNow.setOnClickListener { changeWallpaperNow() }
    }

    private fun updateUI() {
        val loggedIn = settings.isLoggedIn
        btnSignIn.visibility = if (loggedIn) View.GONE else View.VISIBLE
        btnSignOut.visibility = if (loggedIn) View.VISIBLE else View.GONE
        cardAlbum.visibility = if (loggedIn) View.VISIBLE else View.GONE
        radioGroupInterval.visibility = if (loggedIn) View.VISIBLE else View.GONE
        switchEnabled.visibility = if (loggedIn) View.VISIBLE else View.GONE
        btnApply.visibility = if (loggedIn) View.VISIBLE else View.GONE
        btnChangeNow.visibility = if (loggedIn) View.VISIBLE else View.GONE
        if (loggedIn) {
            when (settings.intervalMinutes) {
                60L -> radioHour.isChecked = true
                1440L -> radioDay.isChecked = true
            }
            switchEnabled.isChecked = settings.isEnabled
            settings.selectedAlbumTitle?.let {
                tvAlbumStatus.text = "📁 Альбом: $it"
            }
        }
    }

    private fun loadAlbums() {
        tvStatus.text = "⏳ Загружаем альбомы..."
        authManager.getAccessToken { token ->
            if (token == null) {
                runOnUiThread { tvStatus.text = "❌ Не удалось получить токен" }
                return@getAccessToken
            }
            lifecycleScope.launch {
                val result = api.getAlbums(token)
                result.fold(
                    onSuccess = { albums ->
                        if (albums.isEmpty()) {
                            runOnUiThread { tvStatus.text = "📭 Альбомы не найдены" }
                            return@fold
                        }
                        val titles = albums.map {
                            "${it.title} (${it.mediaItemsCount} фото)"
                        }
                        val adapter = ArrayAdapter(
                            this@MainActivity,
                            android.R.layout.simple_spinner_item, titles
                        )
                        adapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item
                        )
                        runOnUiThread {
                            spinnerAlbum.adapter = adapter
                            spinnerAlbum.onItemSelectedListener =
                                object : AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        p: AdapterView<*>?, v: View?,
                                        pos: Int, id: Long
                                    ) {
                                        val a = albums[pos]
                                        settings.selectedAlbumId = a.id
                                        settings.selectedAlbumTitle = a.title
                                        tvAlbumStatus.text = "📁 ${a.title}"
                                    }
                                    override fun onNothingSelected(p: AdapterView<*>?) {}
                                }
                            tvStatus.text = "✅ Найдено ${albums.size} альбомов"
                        }
                    },
                    onFailure = { e ->
                        runOnUiThread {
                            tvStatus.text = "❌ Ошибка: ${e.message}"
                        }
                    }
                )
            }
        }
    }

    private fun applySettings() {
        val interval = if (radioDay.isChecked) 1440L else 60L
        settings.intervalMinutes = interval
        settings.isEnabled = switchEnabled.isChecked
        if (switchEnabled.isChecked) {
            WallpaperWorker.schedulePeriodic(this, interval)
            val desc = if (interval == 60L) "1 час" else "24 часа"
            tvStatus.text = "✅ Обои включены. Смена каждые $desc"
        } else {
            WallpaperWorker.cancelPeriodic(this)
            tvStatus.text = "⏸ Автоматическая смена выключена"
        }
    }

    private fun changeWallpaperNow() {
        tvStatus.text = "⏳ Меняем обои..."
        WallpaperWorker.runOnce(this)
        tvStatus.text = "✅ Задача смены обоев запущена!"
    }

    override fun onDestroy() {
        super.onDestroy()
        authManager.dispose()
    }
}