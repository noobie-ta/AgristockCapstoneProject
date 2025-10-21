package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide

class PostPreviewActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnEdit: Button
    private lateinit var btnPost: Button
    private lateinit var ivPreviewImage: ImageView
    private lateinit var tvPreviewName: TextView
    private lateinit var tvPreviewPrice: TextView
    private lateinit var tvPreviewDescription: TextView
    private lateinit var tvPreviewCategory: TextView
    private lateinit var tvPreviewLocation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_preview)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        loadPreviewData()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back)
        btnEdit = findViewById(R.id.btn_edit)
        btnPost = findViewById(R.id.btn_post)
        ivPreviewImage = findViewById(R.id.iv_preview_image)
        tvPreviewName = findViewById(R.id.tv_preview_name)
        tvPreviewPrice = findViewById(R.id.tv_preview_price)
        tvPreviewDescription = findViewById(R.id.tv_preview_description)
        tvPreviewCategory = findViewById(R.id.tv_preview_category)
        tvPreviewLocation = findViewById(R.id.tv_preview_location)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnEdit.setOnClickListener {
            finish()
        }

        btnPost.setOnClickListener {
            // Return to sell post activity with confirmation
            val resultIntent = Intent()
            resultIntent.putExtra("confirmPost", true)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadPreviewData() {
        val title = intent.getStringExtra("title") ?: ""
        val price = intent.getStringExtra("price") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val location = intent.getStringExtra("location") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl") ?: ""

        tvPreviewName.text = title.ifEmpty { "Item Name" }
        tvPreviewPrice.text = if (price.isNotEmpty()) "₱$price" else "₱0"
        tvPreviewDescription.text = description.ifEmpty { "Description" }
        tvPreviewCategory.text = category.ifEmpty { "Category" }
        tvPreviewLocation.text = location.ifEmpty { "Location" }

        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(ivPreviewImage)
        } else {
            ivPreviewImage.setImageResource(R.drawable.ic_image_placeholder)
        }
    }
}



