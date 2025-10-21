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
    private lateinit var sellerAvatar: ImageView
    private lateinit var sellerNameText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var countdownText: TextView
    private lateinit var startingBidText: TextView
    private lateinit var highestBidText: TextView
    private lateinit var totalBiddersText: TextView
    private lateinit var showLiveBiddingButton: Button
    
    // Rating star views
    private lateinit var star1: ImageView
    private lateinit var star2: ImageView
    private lateinit var star3: ImageView
    private lateinit var star4: ImageView
    private lateinit var star5: ImageView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    private var countdownTimer: CountDownTimer? = null
    private var itemId: String? = null
    private var biddingEndTime: Long = 0
    private var currentPostTitle: String? = null
    private var currentPostImageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_bidding)

        // Configure status bar - transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

        initializeViews()
        setupClickListeners()
        loadItemDetails()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.btn_back)
        favoriteButton = findViewById(R.id.btn_favorite)
        itemImageView = findViewById(R.id.iv_item_image)
        itemNameText = findViewById(R.id.tv_item_name)
        sellerAvatar = findViewById(R.id.iv_seller_avatar)
        sellerNameText = findViewById(R.id.tv_seller_name)
        descriptionText = findViewById(R.id.tv_description)
        countdownText = findViewById(R.id.tv_countdown)
        startingBidText = findViewById(R.id.tv_starting_bid)
        highestBidText = findViewById(R.id.tv_highest_bid)
        totalBiddersText = findViewById(R.id.tv_total_bidders)
        showLiveBiddingButton = findViewById(R.id.btn_show_live_bidding)
        
        // Initialize star rating views
        star1 = findViewById(R.id.star1)
        star2 = findViewById(R.id.star2)
        star3 = findViewById(R.id.star3)
        star4 = findViewById(R.id.star4)
        star5 = findViewById(R.id.star5)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        
        // Favorite button functionality
        favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        
        showLiveBiddingButton.setOnClickListener {
            val intent = Intent(this, LiveBiddingActivity::class.java)
            intent.putExtra("postId", itemId)
            startActivity(intent)
            // Modern Android handles transitions automatically
        }
    }

    private fun loadItemDetails() {
        itemId = intent.getStringExtra("postId")
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
                    val title = data["title"]?.toString() ?: "Unknown Item"
                    val postImageUrl = data["imageUrl"]?.toString()
                    itemNameText.text = title
                    descriptionText.text = data["description"]?.toString() ?: "No description available"
                    
                    // Store post data for chat creation
                    currentPostTitle = title
                    currentPostImageUrl = postImageUrl
                    
                    // Display location
                    val location = data["location"]?.toString() ?: "Location not specified"
                    val address = data["address"]?.toString() ?: ""
                    val displayLocation = if (address.isNotEmpty()) address else location
                    findViewById<TextView>(R.id.tv_item_location).text = displayLocation
                    
                    // Load seller information
                    val sellerId = data["userId"]?.toString()
                    val sellerName = data["sellerName"]?.toString() ?: ""
                    
                    // Debug logging
                    android.util.Log.d("ViewBiddingActivity", "Post data - sellerId: $sellerId, sellerName: $sellerName")
                    android.util.Log.d("ViewBiddingActivity", "All post fields: ${data.keys}")
                    
                    if (!sellerId.isNullOrEmpty()) {
                        loadSellerInfo(sellerId, sellerName)
                    } else {
                        sellerNameText.text = "Seller: Unknown"
                    }
                    
                    
                    // Load image - try multiple sources
                    val imageUrl = data["imageUrls"]?.let { 
                        if (it is List<*> && it.isNotEmpty()) it[0].toString() else null
                    } ?: data["imageUrl"]?.toString()
                    
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_placeholder)
                            .into(itemImageView)
                        
                        // Get image URLs for indicators
                        val imageUrls = data["imageUrls"] as? List<String> ?: listOf(imageUrl)
                        
                        // Show image indicators if multiple images
                        setupImageIndicators(imageUrls)
                        
                        // Make image clickable for image viewer
                        itemImageView.setOnClickListener {
                            val imageUrlList = imageUrls.filter { it.isNotEmpty() }
                            if (imageUrlList.isNotEmpty()) {
                                ImageViewerActivity.start(this, imageUrlList, 0)
                            }
                        }
                    } else {
                        itemImageView.setImageResource(R.drawable.ic_image_placeholder)
                    }
                    
                    // Set bidding details with proper data type handling
                    val startingBidValue = when (val startingBidData = data["startingBid"]) {
                        is Number -> startingBidData.toDouble()
                        is String -> startingBidData.replace("₱", "").replace(",", "").toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    
                    val highestBidValue = when (val highestBidData = data["highestBid"]) {
                        is Number -> highestBidData.toDouble()
                        is String -> highestBidData.replace("₱", "").replace(",", "").toDoubleOrNull() ?: startingBidValue
                        else -> startingBidValue
                    }
                    
                    val totalBiddersValue = when (val totalBiddersData = data["totalBidders"]) {
                        is Number -> totalBiddersData.toInt()
                        is String -> totalBiddersData.toIntOrNull() ?: 0
                        else -> 0
                    }
                    
                    startingBidText.text = "Starting Bid: ₱${String.format("%.2f", startingBidValue)}"
                    highestBidText.text = "Highest Bid: ₱${String.format("%.2f", highestBidValue)}"
                    totalBiddersText.text = "Total Bidders: $totalBiddersValue"
                    
                    // Set bidding end time and start countdown
                    val biddingEndTimeString = data["endTime"]?.toString() ?: data["biddingEndTime"]?.toString()
                    if (!biddingEndTimeString.isNullOrEmpty()) {
                        biddingEndTime = biddingEndTimeString.toLongOrNull() ?: System.currentTimeMillis() + 86400000
                    } else {
                        // If no endTime is set, calculate it from createdAt + 24 hours
                        val createdAt = data["createdAt"]?.let { 
                            if (it is com.google.firebase.Timestamp) {
                                it.toDate().time
                            } else {
                                it.toString().toLongOrNull()
                            }
                        } ?: System.currentTimeMillis()
                        biddingEndTime = createdAt + 86400000 // 24 hours from creation
                    }
                    startCountdown()
                    
                    // Check favorite status
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

    private fun setupImageIndicators(imageUrls: List<String>) {
        try {
            val tvImageCount = findViewById<TextView>(R.id.tv_image_count)
            val llImageIndicators = findViewById<LinearLayout>(R.id.ll_image_indicators)
            
            if (imageUrls.size > 1) {
                // Show image count indicator
                tvImageCount?.let {
                    it.text = "+${imageUrls.size - 1}"
                    it.visibility = View.VISIBLE
                }
                
                // Show dot indicators
                llImageIndicators?.let { container ->
                    container.removeAllViews()
                    container.visibility = View.VISIBLE
                    
                    for (i in imageUrls.indices) {
                        val indicator = ImageView(this)
                        val layoutParams = LinearLayout.LayoutParams(
                            resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 3,
                            resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 3
                        )
                        layoutParams.setMargins(6, 0, 6, 0)
                        indicator.layoutParams = layoutParams
                        indicator.setImageResource(R.drawable.indicator_dot)
                        container.addView(indicator)
                    }
                }
            } else {
                // Hide indicators for single image
                tvImageCount?.visibility = View.GONE
                llImageIndicators?.visibility = View.GONE
            }
        } catch (e: Exception) {
            android.util.Log.e("ViewBiddingActivity", "Error setting up image indicators: ${e.message}")
        }
    }

    private fun startCountdown() {
        val timeLeft = biddingEndTime - System.currentTimeMillis()
        
        if (timeLeft > 0) {
            countdownTimer = object : CountDownTimer(timeLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val days = millisUntilFinished / (1000 * 60 * 60 * 24)
                    val hours = (millisUntilFinished % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                    val seconds = (millisUntilFinished % (1000 * 60)) / 1000
                    
                    countdownText.text = when {
                        days > 0 -> {
                            if (days == 1L) {
                                String.format("1 day, %02d:%02d:%02d", hours, minutes, seconds)
                            } else {
                                String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds)
                            }
                        }
                        hours > 0 -> {
                            String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        }
                        else -> {
                            String.format("%02d:%02d", minutes, seconds)
                        }
                    }
                }
                
                override fun onFinish() {
                    countdownText.text = "Bidding Ended"
                    countdownText.setTextColor(ContextCompat.getColor(this@ViewBiddingActivity, android.R.color.holo_red_dark))
                    // Disable bidding when time expires
                    disableBidding()
                }
            }.start()
        } else {
            countdownText.text = "Bidding Ended"
            countdownText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            // Disable bidding if time has already expired
            disableBidding()
        }
    }
    
    private fun disableBidding() {
        // Disable the live bidding button when time expires
        showLiveBiddingButton.isEnabled = false
        showLiveBiddingButton.alpha = 0.5f
        showLiveBiddingButton.text = "Bidding Ended"
    }
    
    private fun toggleFavorite() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please log in to save favorites", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (itemId.isNullOrEmpty()) return
        
        favoriteButton.isEnabled = false // Prevent rapid clicks
        
        val userFavRef = firestore.collection("users").document(uid).collection("favorites").document(itemId!!)
        val postRef = firestore.collection("posts").document(itemId!!)
        
        // Check current favorite status
        userFavRef.get().addOnSuccessListener { favDoc ->
            val isCurrentlyFav = favDoc.exists()
            
            if (isCurrentlyFav) {
                // Remove from favorites
                userFavRef.delete()
                    .addOnSuccessListener {
                        postRef.get().addOnSuccessListener { postDoc ->
                            if (postDoc.exists()) {
                                val currentCount = (postDoc.getLong("favoriteCount") ?: 0L).toInt()
                                val newCount = maxOf(0, currentCount - 1)
                                postRef.update("favoriteCount", newCount)
                                    .addOnSuccessListener {
                                        updateFavoriteUI(false)
                                        Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                        favoriteButton.isEnabled = true
                                    }
                                    .addOnFailureListener { exception ->
                                        Toast.makeText(this, "Failed to update count: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        favoriteButton.isEnabled = true
                                    }
                            } else {
                                updateFavoriteUI(false)
                                Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                favoriteButton.isEnabled = true
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to remove favorite: ${exception.message}", Toast.LENGTH_SHORT).show()
                        favoriteButton.isEnabled = true
                    }
            } else {
                // Add to favorites
                val data = hashMapOf(
                    "postId" to itemId,
                    "title" to itemNameText.text.toString(),
                    "price" to startingBidText.text.toString().replace("Starting Bid: ", ""),
                    "imageUrl" to "", // Will be updated if needed
                    "date" to SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                
                userFavRef.set(data)
                    .addOnSuccessListener {
                        postRef.get().addOnSuccessListener { postDoc ->
                            if (postDoc.exists()) {
                                val currentCount = (postDoc.getLong("favoriteCount") ?: 0L).toInt()
                                val newCount = currentCount + 1
                                postRef.update("favoriteCount", newCount)
                                    .addOnSuccessListener {
                                        updateFavoriteUI(true)
                                        Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
                                        favoriteButton.isEnabled = true
                                    }
                                    .addOnFailureListener { exception ->
                                        Toast.makeText(this, "Failed to update count: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        favoriteButton.isEnabled = true
                                    }
                            } else {
                                updateFavoriteUI(true)
                                Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
                                favoriteButton.isEnabled = true
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to add favorite: ${exception.message}", Toast.LENGTH_SHORT).show()
                        favoriteButton.isEnabled = true
                    }
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(this, "Failed to check favorite status: ${exception.message}", Toast.LENGTH_SHORT).show()
            favoriteButton.isEnabled = true
        }
    }
    
    private fun updateFavoriteUI(isFavorited: Boolean) {
        if (isFavorited) {
            favoriteButton.setImageResource(R.drawable.ic_favorite_filled_red)
        } else {
            favoriteButton.setImageResource(R.drawable.ic_favorite_border)
        }
    }
    
    private fun checkFavoriteStatus() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            updateFavoriteUI(false)
            return
        }
        
        if (itemId.isNullOrEmpty()) return
        
        firestore.collection("users").document(uid)
            .collection("favorites").document(itemId!!)
            .get()
            .addOnSuccessListener { doc ->
                val isFavorited = doc.exists()
                updateFavoriteUI(isFavorited)
            }
            .addOnFailureListener {
                updateFavoriteUI(false)
            }
    }
    
    private fun loadSellerInfo(sellerId: String, sellerName: String = "") {
        // Debug logging
        android.util.Log.d("ViewBiddingActivity", "loadSellerInfo called with sellerId: $sellerId, sellerName: $sellerName")
        
        if (sellerId.isEmpty()) {
            sellerNameText.text = "Seller: Unknown"
            sellerAvatar.setImageResource(R.drawable.ic_profile)
            return
        }
        
        // Always try to load the user's profile first for the most up-to-date information
        android.util.Log.d("ViewBiddingActivity", "Loading seller profile for sellerId: $sellerId")
        loadSellerProfile(sellerId, sellerName)
    }
    
    private fun loadSellerProfile(sellerId: String, fallbackName: String = "") {
        firestore.collection("users").document(sellerId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    android.util.Log.d("ViewBiddingActivity", "User document found: ${document.data}")
                    
                    val username = document.getString("username") ?: 
                        "${document.getString("firstName") ?: ""} ${document.getString("lastName") ?: ""}".trim()
                        ?.ifEmpty { document.getString("displayName") ?: "User" }
                        ?: "User"
                    
                    val rating = document.getDouble("rating") ?: 0.0
                    val totalRatings = document.getLong("totalRatings") ?: 0L
                    val avatarUrl = document.getString("avatarUrl")
                    
                    android.util.Log.d("ViewBiddingActivity", "User data - username: $username, avatarUrl: $avatarUrl, rating: $rating, totalRatings: $totalRatings")
                    
                    // Check verification status
                    val verificationStatus = document.getString("verificationStatus")
                    val isVerified = verificationStatus == "approved"
                    val verifiedTag = if (isVerified) " ✅ Verified Seller" else ""
                    
                    // Display username with rating (show rating if there are actual ratings)
                    val displayText = if (totalRatings > 0 && rating > 0) {
                        "Seller: $username$verifiedTag (${String.format("%.1f", rating)}★)"
                    } else {
                        "Seller: $username$verifiedTag (No ratings yet ⭐)"
                    }
                    sellerNameText.text = displayText
                    updateStarRatingDisplay(rating.toFloat(), totalRatings.toInt())
                    
                    // Load profile picture with circular background
                    if (!avatarUrl.isNullOrEmpty()) {
                        android.util.Log.d("ViewBiddingActivity", "Loading avatar from URL: $avatarUrl")
                        Glide.with(this)
                            .load(avatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .into(sellerAvatar)
                    } else {
                        android.util.Log.d("ViewBiddingActivity", "No avatar URL found, using default")
                        sellerAvatar.setImageResource(R.drawable.ic_profile)
                    }
                    
                    // Set up click listeners to view seller profile
                    val profileClickListener = View.OnClickListener {
                        android.util.Log.d("ViewBiddingActivity", "Seller info clicked, navigating to profile for sellerId: $sellerId")
                        val intent = Intent(this, ViewUserProfileActivity::class.java)
                        intent.putExtra("sellerId", sellerId)
                        startActivity(intent)
                    }
                    
                    sellerNameText.setOnClickListener(profileClickListener)
                    sellerAvatar.setOnClickListener(profileClickListener)
                } else {
                    sellerNameText.text = "Seller: Unknown"
                    sellerAvatar.setImageResource(R.drawable.ic_profile)
                    
                    // Set up click listeners even when user document doesn't exist
                    if (sellerId.isNotEmpty()) {
                        val profileClickListener = View.OnClickListener {
                            val intent = Intent(this, ViewUserProfileActivity::class.java)
                            intent.putExtra("sellerId", sellerId)
                            startActivity(intent)
                        }
                        
                        sellerNameText.setOnClickListener(profileClickListener)
                        sellerAvatar.setOnClickListener(profileClickListener)
                    }
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ViewBiddingActivity", "Failed to load seller: ${exception.message}")
                if (fallbackName.isNotEmpty()) {
                    sellerNameText.text = "Seller: $fallbackName"
                } else {
                    sellerNameText.text = "Seller: Unknown"
                }
                sellerAvatar.setImageResource(R.drawable.ic_profile)
                
                // Set up click listeners even in fallback case
                if (sellerId.isNotEmpty()) {
                    val profileClickListener = View.OnClickListener {
                        val intent = Intent(this, ViewUserProfileActivity::class.java)
                        intent.putExtra("sellerId", sellerId)
                        startActivity(intent)
                    }
                    
                    sellerNameText.setOnClickListener(profileClickListener)
                    sellerAvatar.setOnClickListener(profileClickListener)
                }
            }
    }

    private fun formatPrice(price: String): String {
        if (price.startsWith("₱") || price.startsWith("PHP")) {
            return price
        }
        return "₱$price"
    }

    private fun updateStarRatingDisplay(rating: Float, totalRatings: Int) {
        val stars = listOf(star1, star2, star3, star4, star5)
        val ratingContainer = findViewById<LinearLayout>(R.id.ll_rating_stars)
        
        if (totalRatings > 0 && rating > 0) {
            // Show actual rating stars
            ratingContainer.visibility = View.VISIBLE
            stars.forEachIndexed { index, star ->
                val starValue = index + 1
                when {
                    starValue <= rating -> {
                        star.setImageResource(R.drawable.ic_star_filled)
                        star.setColorFilter(ContextCompat.getColor(this, R.color.yellow_accent))
                    }
                    starValue - 0.5f <= rating -> {
                        star.setImageResource(R.drawable.ic_star_half)
                        star.setColorFilter(ContextCompat.getColor(this, R.color.yellow_accent))
                    }
                    else -> {
                        star.setImageResource(R.drawable.ic_star_filled)
                        star.setColorFilter(ContextCompat.getColor(this, R.color.gray_300))
                    }
                }
            }
        } else {
            // Hide stars when no ratings
            ratingContainer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
