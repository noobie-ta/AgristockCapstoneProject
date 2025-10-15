package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ViewBiddingActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var favoriteButton: ImageView
    private lateinit var itemImageView: ImageView
    private lateinit var itemNameText: TextView
    private lateinit var sellerNameText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var countdownText: TextView
    private lateinit var startingBidText: TextView
    private lateinit var highestBidText: TextView
    private lateinit var totalBiddersText: TextView
    private lateinit var showLiveBiddingButton: Button
    private lateinit var contactSellerButton: Button

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    private var countdownTimer: CountDownTimer? = null
    private var isFavorite = false
    private var itemId: String? = null
    private var biddingEndTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_bidding)

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        loadItemDetails()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.btn_back)
        favoriteButton = findViewById(R.id.btn_favorite)
        itemImageView = findViewById(R.id.iv_item_image)
        itemNameText = findViewById(R.id.tv_item_name)
        sellerNameText = findViewById(R.id.tv_seller_name)
        descriptionText = findViewById(R.id.tv_description)
        countdownText = findViewById(R.id.tv_countdown)
        startingBidText = findViewById(R.id.tv_starting_bid)
        highestBidText = findViewById(R.id.tv_highest_bid)
        totalBiddersText = findViewById(R.id.tv_total_bidders)
        showLiveBiddingButton = findViewById(R.id.btn_show_live_bidding)
        contactSellerButton = findViewById(R.id.btn_contact_seller)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        
        favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        
        showLiveBiddingButton.setOnClickListener {
            val intent = Intent(this, LiveBiddingActivity::class.java)
            intent.putExtra("itemId", itemId)
            startActivity(intent)
            // Modern Android handles transitions automatically
        }
        
        contactSellerButton.setOnClickListener {
            // TODO: Implement contact seller functionality
            Toast.makeText(this, "Contact seller feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadItemDetails() {
        itemId = intent.getStringExtra("itemId")
        if (itemId.isNullOrEmpty()) {
            Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("posts").document(itemId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data!!
                    
                    // Load item details
                    itemNameText.text = data["title"]?.toString() ?: "Unknown Item"
                    sellerNameText.text = "Seller: ${data["sellerName"]?.toString() ?: "Unknown"}"
                    descriptionText.text = data["description"]?.toString() ?: "No description available"
                    
                    // Load image
                    val imageUrl = data["imageUrls"]?.let { 
                        if (it is List<*> && it.isNotEmpty()) it[0].toString() else null
                    }
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(imageUrl).into(itemImageView)
                    }
                    
                    // Set bidding details
                    val startingBid = data["startingBid"]?.toString() ?: "₱0"
                    val highestBid = data["highestBid"]?.toString() ?: startingBid
                    val totalBidders = data["totalBidders"]?.toString() ?: "0"
                    
                    startingBidText.text = "Starting Bid: $startingBid"
                    highestBidText.text = "Highest Bid: $highestBid"
                    totalBiddersText.text = "Total Bidders: $totalBidders"
                    
                    // Set bidding end time and start countdown
                    biddingEndTime = data["biddingEndTime"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis() + 86400000 // Default 24 hours
                    startCountdown()
                    
                    // Check if item is in favorites
                    checkFavoriteStatus()
                } else {
                    Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load item details", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun startCountdown() {
        val timeLeft = biddingEndTime - System.currentTimeMillis()
        
        if (timeLeft > 0) {
            countdownTimer = object : CountDownTimer(timeLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val hours = millisUntilFinished / (1000 * 60 * 60)
                    val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                    val seconds = (millisUntilFinished % (1000 * 60)) / 1000
                    
                    countdownText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                }
                
                override fun onFinish() {
                    countdownText.text = "Bidding Ended"
                    countdownText.setTextColor(ContextCompat.getColor(this@ViewBiddingActivity, android.R.color.holo_red_dark))
                }
            }.start()
        } else {
            countdownText.text = "Bidding Ended"
            countdownText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun checkFavoriteStatus() {
        val user = auth.currentUser
        if (user != null) {
            firestore.collection("favorites")
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("itemId", itemId)
                .get()
                .addOnSuccessListener { documents ->
                    isFavorite = !documents.isEmpty
                    updateFavoriteButton()
                }
        }
    }

    private fun toggleFavorite() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to save favorites", Toast.LENGTH_SHORT).show()
            return
        }

        if (isFavorite) {
            // Remove from favorites
            firestore.collection("favorites")
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("itemId", itemId)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.reference.delete()
                    }
                    isFavorite = false
                    updateFavoriteButton()
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Add to favorites
            val favoriteData = hashMapOf(
                "userId" to user.uid,
                "itemId" to itemId,
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection("favorites")
                .add(favoriteData)
                .addOnSuccessListener {
                    isFavorite = true
                    updateFavoriteButton()
                    Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateFavoriteButton() {
        if (isFavorite) {
            favoriteButton.setImageResource(R.drawable.ic_favorite_filled)
            favoriteButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            favoriteButton.setImageResource(R.drawable.ic_favorite_border)
            favoriteButton.setColorFilter(ContextCompat.getColor(this, android.R.color.black))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
