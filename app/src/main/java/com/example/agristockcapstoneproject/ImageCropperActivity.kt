package com.example.agristockcapstoneproject

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.view.MotionEvent
import java.io.File
import java.io.FileOutputStream

class ImageCropperActivity : AppCompatActivity() {

    private lateinit var cropImageView: ImageView
    private lateinit var btnCrop: Button
    private lateinit var btnCancel: Button
    private lateinit var btnBack: ImageView
    private lateinit var tvTitle: TextView
    private var originalBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_cropper)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        initViews()
        setupClickListeners()
        loadImage()
    }

    private fun initViews() {
        cropImageView = findViewById(R.id.cropImageView)
        btnCrop = findViewById(R.id.btn_crop)
        btnCancel = findViewById(R.id.btn_cancel)
        btnBack = findViewById(R.id.btn_back)
        tvTitle = findViewById(R.id.tv_title)

        // Configure image view
        cropImageView.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        
        btnCancel.setOnClickListener { finish() }
        
        btnCrop.setOnClickListener {
            cropImage()
        }
    }

    private fun loadImage() {
        val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>("imageUri")
        }
        if (imageUri != null) {
            try {
                Glide.with(this).load(imageUri).into(cropImageView)
                // Load bitmap for cropping
                val inputStream = contentResolver.openInputStream(imageUri)
                originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } catch (e: Exception) {
                showError("Failed to load image: ${e.message}")
                finish()
            }
        } else {
            finish()
        }
    }

    private fun cropImage() {
        try {
            val croppedBitmap = cropBitmapToSquare(originalBitmap)
            if (croppedBitmap != null) {
                // Save cropped bitmap to a temporary file
                val tempFile = File.createTempFile("cropped_image", ".jpg", cacheDir)
                val outputStream = FileOutputStream(tempFile)
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.close()
                
                val croppedUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    tempFile
                )
                val resultIntent = Intent().apply {
                    putExtra("croppedImageUri", croppedUri)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                showError("Failed to crop image")
            }
        } catch (e: Exception) {
            showError("Failed to crop image: ${e.message}")
        }
    }

    private fun cropBitmapToSquare(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
