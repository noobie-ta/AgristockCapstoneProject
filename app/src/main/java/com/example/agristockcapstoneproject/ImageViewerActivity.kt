package com.example.agristockcapstoneproject

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.view.WindowInsetsController
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var btnDelete: Button
    private lateinit var tvTitle: TextView
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initViews()
        setupClickListeners()
        loadImage()
    }

    private fun initViews() {
        imageView = findViewById(R.id.imageView)
        btnBack = findViewById(R.id.btn_back)
        btnDelete = findViewById(R.id.btn_delete)
        tvTitle = findViewById(R.id.tv_title)

        // Hide system UI for immersive experience
        hideSystemUI()
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        
        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Toggle system UI on image tap
        imageView.setOnClickListener {
            toggleSystemUI()
        }
    }

    private fun loadImage() {
        imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>("imageUri")
        }
        if (imageUri != null) {
            Glide.with(this)
                .load(imageUri)
                .into(imageView)
        } else {
            finish()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Profile Picture")
            .setMessage("Are you sure you want to delete your profile picture? This will reset it to the default avatar.")
            .setPositiveButton("Delete") { _, _ ->
                // Return result to indicate deletion
                val resultIntent = Intent().apply {
                    putExtra("deleteImage", true)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val insetsController = window.insetsController
            insetsController?.hide(android.view.WindowInsets.Type.systemBars())
            insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val insetsController = window.insetsController
            insetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun toggleSystemUI() {
        if (isSystemUIVisible()) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    private fun isSystemUIVisible(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val insets = window.decorView.rootWindowInsets
            insets?.isVisible(android.view.WindowInsets.Type.systemBars()) ?: true
        } else {
            @Suppress("DEPRECATION")
            val flags = window.decorView.systemUiVisibility
            (flags and android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
}


