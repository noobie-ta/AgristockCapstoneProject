package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import java.text.NumberFormat
import java.util.*

class BidPostPreviewActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnEdit: Button
    private lateinit var btnPost: Button
    private lateinit var ivPreviewImage: ImageView
    private lateinit var tvPreviewName: TextView
    private lateinit var tvPreviewStartingBid: TextView
    private lateinit var tvPreviewDescription: TextView
    private lateinit var tvPreviewCategory: TextView
    private lateinit var tvPreviewLocation: TextView
    private lateinit var tvPreviewEndTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bid_post_preview)

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
        tvPreviewStartingBid = findViewById(R.id.tv_preview_starting_bid)
        tvPreviewDescription = findViewById(R.id.tv_preview_description)
        tvPreviewCategory = findViewById(R.id.tv_preview_category)
        tvPreviewLocation = findViewById(R.id.tv_preview_location)
        tvPreviewEndTime = findViewById(R.id.tv_preview_end_time)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnEdit.setOnClickListener {
            finish()
        }

        btnPost.setOnClickListener {
            // Return to bid post activity with confirmation
            val resultIntent = Intent()
            resultIntent.putExtra("confirmPost", true)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadPreviewData() {
        val itemName = intent.getStringExtra("itemName") ?: ""
        val startingBid = intent.getStringExtra("startingBid") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val location = intent.getStringExtra("location") ?: ""
        val endTime = intent.getStringExtra("endTime") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl") ?: ""

        tvPreviewName.text = itemName.ifEmpty { "Item Name" }
        
        // Format starting bid with currency
        val formattedBid = if (startingBid.isNotEmpty()) {
            try {
                val bidAmount = startingBid.toDouble()
                val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
                formatter.currency = Currency.getInstance("PHP")
                formatter.format(bidAmount)
            } catch (e: NumberFormatException) {
                "₱$startingBid"
            }
        } else {
            "₱0"
        }
        tvPreviewStartingBid.text = formattedBid
        
        tvPreviewDescription.text = description.ifEmpty { "Description" }
        tvPreviewCategory.text = category.ifEmpty { "Category" }
        tvPreviewLocation.text = location.ifEmpty { "Location" }
        tvPreviewEndTime.text = endTime.ifEmpty { "End Time" }

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

