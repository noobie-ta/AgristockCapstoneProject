package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewUserProfileActivity : AppCompatActivity() {

    data class PostItem(
        val id: String,
        val name: String,
        val price: String,
        val description: String,
        val imageUrl: String?,
        val status: String, // FOR SALE or BIDDING or SOLD
        val datePosted: String,
        val favoriteCount: Long = 0L,
        val type: String = "SELL", // SELL or BID
        val saleTradeType: String = "SALE", // SALE, TRADE, or BOTH
        val category: String = ""
    )

    // Profile Section Views
    private lateinit var postsContainer: LinearLayout
    private lateinit var avatarView: ImageView
    private lateinit var coverPhotoView: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var tvBio: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvContact: TextView
    
    // Rating Views
    private lateinit var tvRatingText: TextView
    private lateinit var tvTotalRatings: TextView
    private lateinit var star1: ImageView
    private lateinit var star2: ImageView
    private lateinit var star3: ImageView
    private lateinit var star4: ImageView
    private lateinit var star5: ImageView
    
    // Badge Chips
    private lateinit var chipVerified: LinearLayout
    private lateinit var chipBidder: LinearLayout
    
    // Posts Section Views
    private lateinit var btnFilter: LinearLayout
    private lateinit var tvFilterText: TextView
    private lateinit var btnGridView: ImageView
    private lateinit var btnListView: ImageView
    
    // Message button
    private lateinit var btnMessage: ImageView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var isListView = true
    private var currentSort = "newest"
    private var currentCategory = "All Categories"
    private var currentPostType = "All" // All, SELL, BID, TRADE
    private var currentStatus = "All" // All, Active, Sold
    private var sellerId: String? = null
    private var blockedUsersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val blockedUserIds = mutableSetOf<String>() // Cache of blocked user IDs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_user_profile)

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        try {
        initializeViews()
        setupClickListeners()
        
        // Get seller ID from intent
        sellerId = intent.getStringExtra("sellerId")
            android.util.Log.d("ViewUserProfile", "Received sellerId from intent: '$sellerId'")
            
        if (sellerId.isNullOrEmpty()) {
                android.util.Log.e("ViewUserProfile", "sellerId is null or empty, finishing activity")
            Toast.makeText(this, "Invalid user profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

            android.util.Log.d("ViewUserProfile", "Loading profile for sellerId: $sellerId")
        loadUserProfile()
        loadBlockedUsers()
        loadPosts()
        updateFilterText()
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfile", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error loading user profile", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun loadBlockedUsers() {
        val currentUser = auth.currentUser ?: return
        
        // Load users that current user has blocked
        blockedUsersListener = firestore.collection("blocks")
            .whereEqualTo("blockerId", currentUser.uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    android.util.Log.e("ViewUserProfile", "Error loading blocked users: ${exception.message}")
                    return@addSnapshotListener
                }
                
                blockedUserIds.clear()
                snapshot?.documents?.forEach { doc ->
                    val blockedUserId = doc.getString("blockedUserId")
                    if (!blockedUserId.isNullOrEmpty()) {
                        blockedUserIds.add(blockedUserId)
                    }
                }
                
                android.util.Log.d("ViewUserProfile", "Loaded ${blockedUserIds.size} blocked users")
                // Refresh posts when blocked users list updates
                loadPosts()
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        blockedUsersListener?.remove()
    }

    private fun initializeViews() {
        // Profile Section
        postsContainer = findViewById(R.id.ll_posts_container)
        avatarView = findViewById(R.id.iv_avatar)
        coverPhotoView = findViewById(R.id.iv_cover_photo)
        tvUsername = findViewById(R.id.tv_username)
        tvBio = findViewById(R.id.tv_bio)
        tvLocation = findViewById(R.id.tv_location)
        tvContact = findViewById(R.id.tv_contact)
        
        // Rating Views
        tvRatingText = findViewById(R.id.tv_rating_text)
        tvTotalRatings = findViewById(R.id.tv_total_ratings)
        star1 = findViewById(R.id.star1)
        star2 = findViewById(R.id.star2)
        star3 = findViewById(R.id.star3)
        star4 = findViewById(R.id.star4)
        star5 = findViewById(R.id.star5)
        
        // Badge Chips
        chipVerified = findViewById(R.id.chip_verified)
        chipBidder = findViewById(R.id.chip_bidder)
        
        // Posts Section Views
        btnFilter = findViewById(R.id.btn_filter)
        tvFilterText = findViewById(R.id.tv_filter_text)
        btnGridView = findViewById(R.id.btn_grid_view)
        btnListView = findViewById(R.id.btn_list_view)
        
        // Message button
        btnMessage = findViewById(R.id.btn_message)
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        // Filter button
        btnFilter.setOnClickListener { showFilterModal() }
        
        // View toggle buttons
        btnGridView.setOnClickListener { setViewMode(false) }
        btnListView.setOnClickListener { setViewMode(true) }
        
        // Message button
        btnMessage.setOnClickListener { 
            contactUser()
        }
    }

    private fun showFilterModal() {
        val dialog = android.app.AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.modal_filter_profile, null)
        dialog.setView(dialogView)
        
        val alertDialog = dialog.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set up modal click listeners
        setupFilterModalListeners(dialogView, alertDialog)
        
        alertDialog.show()
    }
    
    private fun setupFilterModalListeners(dialogView: View, alertDialog: android.app.AlertDialog) {
        // Close button
        dialogView.findViewById<ImageView>(R.id.btn_close_filter).setOnClickListener {
            alertDialog.dismiss()
        }
        
        // Sort buttons
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_newest).setOnClickListener {
            setActiveSort(dialogView, "newest")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_oldest).setOnClickListener {
            setActiveSort(dialogView, "oldest")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_price_high).setOnClickListener {
            setActiveSort(dialogView, "price_high")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_price_low).setOnClickListener {
            setActiveSort(dialogView, "price_low")
        }
        
        // Post Type buttons
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_all).setOnClickListener {
            setActivePostType(dialogView, "All")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_sell).setOnClickListener {
            setActivePostType(dialogView, "SELL")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_bid).setOnClickListener {
            setActivePostType(dialogView, "BID")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_trade).setOnClickListener {
            setActivePostType(dialogView, "TRADE")
        }
        
        // Status buttons
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_all).setOnClickListener {
            setActiveStatus(dialogView, "All")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_active).setOnClickListener {
            setActiveStatus(dialogView, "Active")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_sold).setOnClickListener {
            setActiveStatus(dialogView, "Sold")
        }
        
        // Category buttons
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_all).setOnClickListener {
            setActiveCategory(dialogView, "All Categories")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_carabao).setOnClickListener {
            setActiveCategory(dialogView, "CARABAO")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_chicken).setOnClickListener {
            setActiveCategory(dialogView, "CHICKEN")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_goat).setOnClickListener {
            setActiveCategory(dialogView, "GOAT")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_cow).setOnClickListener {
            setActiveCategory(dialogView, "COW")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_pig).setOnClickListener {
            setActiveCategory(dialogView, "PIG")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_duck).setOnClickListener {
            setActiveCategory(dialogView, "DUCK")
        }
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_other).setOnClickListener {
            setActiveCategory(dialogView, "OTHER")
        }
        
        // Apply button
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_apply_filter).setOnClickListener {
            val selectedSort = getSelectedSort(dialogView)
            val selectedCategory = getSelectedCategory(dialogView)
            val selectedPostType = getSelectedPostType(dialogView)
            val selectedStatus = getSelectedStatus(dialogView)
            
            currentSort = selectedSort
            currentCategory = selectedCategory
            currentPostType = selectedPostType
            currentStatus = selectedStatus
            
            updateFilterText()
        loadPosts()
            alertDialog.dismiss()
        }
        
        // Set initial states
        setActiveSort(dialogView, currentSort)
        setActiveCategory(dialogView, currentCategory)
        setActivePostType(dialogView, currentPostType)
        setActiveStatus(dialogView, currentStatus)
    }
    
    private fun setActiveSort(dialogView: View, sort: String) {
        val newestBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_newest)
        val oldestBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_oldest)
        val priceHighBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_price_high)
        val priceLowBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_price_low)
        
        // Reset all
        newestBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        oldestBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        priceHighBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        priceLowBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        
        newestBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        oldestBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        priceHighBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        priceLowBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        // Set active
        when (sort) {
            "newest" -> {
                newestBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                newestBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "oldest" -> {
                oldestBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                oldestBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "price_high" -> {
                priceHighBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                priceHighBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "price_low" -> {
                priceLowBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                priceLowBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
    }
    
    private fun setActiveCategory(dialogView: View, category: String) {
        val categoryButtons = listOf(
            R.id.btn_category_all to "All Categories",
            R.id.btn_category_carabao to "CARABAO",
            R.id.btn_category_chicken to "CHICKEN",
            R.id.btn_category_goat to "GOAT",
            R.id.btn_category_cow to "COW",
            R.id.btn_category_pig to "PIG",
            R.id.btn_category_duck to "DUCK",
            R.id.btn_category_other to "OTHER"
        )
        
        // Reset all
        categoryButtons.forEach { (buttonId, _) ->
            val button = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(buttonId)
            button.background = ContextCompat.getDrawable(this, R.drawable.modern_category_chip_inactive)
            button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        
        // Set active
        val activeButtonId = categoryButtons.find { it.second == category }?.first
        if (activeButtonId != null) {
            val activeButton = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(activeButtonId)
            activeButton.background = ContextCompat.getDrawable(this, R.drawable.modern_category_chip_active)
            activeButton.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }
    
    private fun getSelectedSort(dialogView: View): String {
        val newestBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_newest)
        val oldestBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_oldest)
        val priceHighBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_price_high)
        val priceLowBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_sort_price_low)
        
        return when {
            newestBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "newest"
            oldestBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "oldest"
            priceHighBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "price_high"
            priceLowBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "price_low"
            else -> "newest"
        }
    }
    
    private fun getSelectedCategory(dialogView: View): String {
        val categoryButtons = listOf(
            R.id.btn_category_all to "All Categories",
            R.id.btn_category_carabao to "CARABAO",
            R.id.btn_category_chicken to "CHICKEN",
            R.id.btn_category_goat to "GOAT",
            R.id.btn_category_cow to "COW",
            R.id.btn_category_pig to "PIG",
            R.id.btn_category_duck to "DUCK",
            R.id.btn_category_other to "OTHER"
        )
        
        for ((buttonId, categoryValue) in categoryButtons) {
            val button = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(buttonId)
            if (button.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_category_chip_active)?.constantState) {
                return categoryValue
            }
        }
        
        return "All Categories"
    }
    
    private fun setActivePostType(dialogView: View, postType: String) {
        val allBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_all)
        val sellBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_sell)
        val bidBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_bid)
        val tradeBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_trade)
        
        // Reset all
        allBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        sellBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        bidBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        tradeBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        
        allBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        sellBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        bidBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tradeBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        // Set active
        when (postType) {
            "All" -> {
                allBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                allBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "SELL" -> {
                sellBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                sellBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "BID" -> {
                bidBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                bidBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "TRADE" -> {
                tradeBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                tradeBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
    }
    
    private fun getSelectedPostType(dialogView: View): String {
        val allBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_all)
        val sellBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_sell)
        val bidBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_bid)
        val tradeBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_type_trade)
        
        return when {
            sellBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "SELL"
            bidBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "BID"
            tradeBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "TRADE"
            else -> "All"
        }
    }
    
    private fun setActiveStatus(dialogView: View, status: String) {
        val allBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_all)
        val activeBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_active)
        val soldBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_sold)
        
        // Reset all
        allBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        activeBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        soldBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        
        allBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        activeBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        soldBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        // Set active
        when (status) {
            "All" -> {
                allBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                allBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "Active" -> {
                activeBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                activeBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "Sold" -> {
                soldBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)
                soldBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
    }
    
    private fun getSelectedStatus(dialogView: View): String {
        val allBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_all)
        val activeBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_active)
        val soldBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_status_sold)
        
        return when {
            activeBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "Active"
            soldBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "Sold"
            else -> "All"
        }
    }
    
    private fun updateFilterText() {
        val parts = mutableListOf<String>()
        
        // Add post type if not "All"
        if (currentPostType != "All") {
            parts.add(currentPostType)
        }
        
        // Add status if not "All"
        if (currentStatus != "All") {
            parts.add(currentStatus)
        }
        
        // Add category if not "All Categories"
        if (currentCategory != "All Categories") {
            parts.add(currentCategory)
        }
        
        // Add sort if not "newest"
        if (currentSort != "newest") {
            parts.add(getSortLabel(currentSort))
        }
        
        // Build filter text
        val filterText = if (parts.isEmpty()) {
            "Filter"
        } else {
            parts.joinToString(" • ")
        }
        
        tvFilterText.text = filterText
    }
    
    private fun getSortLabel(sort: String): String {
        return when (sort) {
            "newest" -> "Newest"
            "oldest" -> "Oldest"
            "price_high" -> "Price High"
            "price_low" -> "Price Low"
            else -> "Newest"
        }
    }

    private fun setViewMode(listView: Boolean) {
        isListView = listView
        updateViewButtons()
        loadPosts()
    }

    private fun updateViewButtons() {
        if (isListView) {
            btnListView.setImageResource(R.drawable.view_toggle_active)
            btnGridView.setImageResource(R.drawable.view_toggle_inactive)
        } else {
            btnListView.setImageResource(R.drawable.view_toggle_inactive)
            btnGridView.setImageResource(R.drawable.view_toggle_active)
        }
    }

    private fun loadUserProfile() {
        if (sellerId.isNullOrEmpty()) return
        
        firestore.collection("users").document(sellerId!!).get()
            .addOnSuccessListener { document ->
                if (document.exists() && document.data != null) {
                    val data = document.data ?: return@addOnSuccessListener
                    
                    // Load profile information
                    val username = data["username"]?.toString() ?: 
                        "${data["firstName"] ?: ""} ${data["lastName"] ?: ""}".trim()
                        .ifEmpty { data["displayName"]?.toString() ?: "User" }
                    val bio = data["bio"]?.toString() ?: ""
                    val location = data["location"]?.toString() ?: ""
                    val contact = data["contact"]?.toString() ?: ""
                    val avatarUrl = data["avatarUrl"]?.toString()
                    val coverUrl = data["coverUrl"]?.toString()
                    val rating = (data["rating"] as? Double) ?: 0.0
                    val totalRatings = (data["totalRatings"] as? Long) ?: 0L
                    val verificationStatus = (data["verificationStatus"] as? String)?.trim()?.lowercase() ?: ""
                    val biddingApprovalStatus = (data["biddingApprovalStatus"] as? String)?.trim()?.lowercase() ?: ""
                    
                    // Update UI
                    tvUsername.text = username
                    tvBio.text = bio.ifEmpty { "Bio not set" }
                    tvLocation.text = location.ifEmpty { "Location not set" }
                    tvContact.text = contact.ifEmpty { "Contact not set" }
                    
                    // Update badge chips visibility
                    updateBadgeChips(verificationStatus, biddingApprovalStatus)
                    
                    // Load profile picture
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(avatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .into(avatarView)
                    } else {
                        avatarView.setImageResource(R.drawable.ic_profile)
                    }
                    
                    // Load cover photo
                    if (!coverUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(coverUrl)
                            .centerCrop()
                            .placeholder(R.color.gray_200)
                            .error(R.color.gray_200)
                            .into(coverPhotoView)
                    } else {
                        coverPhotoView.setImageResource(R.color.gray_200)
                    }
                    
                    // Update rating
                    updateRatingDisplay(rating.toFloat(), totalRatings.toInt())
                } else {
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                    android.util.Log.w("ViewUserProfile", "Document exists but has no data for user: $sellerId")
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ViewUserProfile", "Failed to load profile: ${exception.message}", exception)
                Toast.makeText(this, "Failed to load profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateRatingDisplay(rating: Float, totalRatings: Int = 0) {
        // Update rating text with total ratings count
        if (totalRatings > 0 && rating > 0) {
            tvRatingText.text = String.format("%.1f", rating)
            tvTotalRatings.text = "$totalRatings ratings"
        } else {
            tvRatingText.text = ""
            tvTotalRatings.text = "0 ratings"
        }
        
        // Update star display
        val stars = listOf(star1, star2, star3, star4, star5)
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
    }
    
    private fun updateBadgeChips(verificationStatus: String, biddingApprovalStatus: String) {
        // Show/hide verified badge chip
        if (verificationStatus == "approved") {
            chipVerified.visibility = View.VISIBLE
        } else {
            chipVerified.visibility = View.GONE
        }
        
        // Show/hide bidding approved badge chip
        if (biddingApprovalStatus == "approved") {
            chipBidder.visibility = View.VISIBLE
        } else {
            chipBidder.visibility = View.GONE
        }
    }

    private fun loadPosts() {
        if (sellerId.isNullOrEmpty()) {
            android.util.Log.w("ViewUserProfile", "loadPosts called with empty sellerId")
            renderPosts(emptyList())
            return
        }
        
        postsContainer.removeAllViews()
        
        try {
        firestore.collection("posts")
            .whereEqualTo("userId", sellerId)
            .addSnapshotListener { snapshots, exception ->
                if (exception != null) {
                        android.util.Log.e("ViewUserProfile", "Error in posts listener: ${exception.message}", exception)
                    Toast.makeText(this, "Error loading posts: ${exception.message}", Toast.LENGTH_SHORT).show()
                    renderPosts(emptyList())
                    return@addSnapshotListener
                }

                    if (snapshots == null) {
                        android.util.Log.w("ViewUserProfile", "Snapshots is null in listener")
                        renderPosts(emptyList())
                        return@addSnapshotListener
                    }

                    val currentUserId = auth.currentUser?.uid
                    
                    val list = snapshots.documents
                    ?.mapNotNull { d ->
                        // Filter out posts if viewing blocked user's profile
                        if (currentUserId != null && sellerId != null && blockedUserIds.contains(sellerId)) {
                            return@mapNotNull null
                        }
                        
                        PostItem(
                            id = d.id,
                            name = d.getString("title") ?: "",
                            price = d.getString("price") ?: "",
                            description = d.getString("description") ?: "",
                            imageUrl = d.getString("imageUrl"),
                            status = d.getString("status") ?: "FOR SALE",
                            datePosted = d.getString("datePosted") ?: "Unknown date",
                            favoriteCount = d.getLong("favoriteCount") ?: 0L,
                            type = d.getString("type") ?: "SELL",
                            saleTradeType = d.getString("saleTradeType") ?: "SALE",
                            category = d.getString("category") ?: ""
                        )
                    }
                    ?: emptyList()

                    android.util.Log.d("ViewUserProfile", "Loaded ${list.size} posts from Firestore")
                    android.util.Log.d("ViewUserProfile", "Filters: Status=$currentStatus, Type=$currentPostType, Category=$currentCategory, Sort=$currentSort")

                    // Apply status filter
                    val statusFiltered = when (currentStatus) {
                        "Active" -> list.filter { it.status.uppercase() != "SOLD" }
                        "Sold" -> list.filter { it.status.uppercase() == "SOLD" }
                        else -> list // "All" - show both active and sold
                    }
                    android.util.Log.d("ViewUserProfile", "After status filter: ${statusFiltered.size} posts")

                    // Apply post type filter
                    val typeFiltered = when (currentPostType) {
                        "SELL" -> statusFiltered.filter { it.type.uppercase() == "SELL" }
                        "BID" -> statusFiltered.filter { it.type.uppercase() == "BID" }
                        "TRADE" -> statusFiltered.filter { 
                            it.type.uppercase() == "SELL" && 
                            (it.saleTradeType.uppercase() == "TRADE" || it.saleTradeType.uppercase() == "BOTH")
                        }
                        else -> statusFiltered // "All" - show all types
                    }
                    android.util.Log.d("ViewUserProfile", "After type filter: ${typeFiltered.size} posts")

                    // Apply category filter
                    val categoryFiltered = if (currentCategory == "All Categories") {
                        typeFiltered
                    } else {
                        typeFiltered.filter { it.category == currentCategory }
                    }
                    android.util.Log.d("ViewUserProfile", "After category filter: ${categoryFiltered.size} posts")

                // Apply sorting
                val sortedList = when (currentSort) {
                    "newest" -> categoryFiltered.sortedByDescending { it.datePosted }
                    "oldest" -> categoryFiltered.sortedBy { it.datePosted }
                    "price_high" -> categoryFiltered.sortedByDescending { 
                        it.price.replace("₱", "").replace(",", "").toDoubleOrNull() ?: 0.0 
                    }
                    "price_low" -> categoryFiltered.sortedBy { 
                        it.price.replace("₱", "").replace(",", "").toDoubleOrNull() ?: 0.0 
                    }
                    else -> categoryFiltered
                }
                    android.util.Log.d("ViewUserProfile", "After sorting: ${sortedList.size} posts")

                renderPosts(sortedList)
                }
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfile", "Exception in loadPosts: ${e.message}", e)
            Toast.makeText(this, "Error setting up posts listener", Toast.LENGTH_SHORT).show()
            renderPosts(emptyList())
            }
    }

    private fun renderPosts(posts: List<PostItem>) {
        try {
        postsContainer.removeAllViews()
            
            android.util.Log.d("ViewUserProfile", "renderPosts called with ${posts.size} posts, isListView=$isListView")
        
        if (posts.isEmpty()) {
                android.util.Log.d("ViewUserProfile", "No posts to display, showing empty state")
            showEmptyState()
            return
        }

        if (isListView) {
            // List View - add posts directly
                android.util.Log.d("ViewUserProfile", "Rendering in LIST view mode")
            posts.forEach { post ->
                    android.util.Log.d("ViewUserProfile", "Adding post card: ${post.name}")
                addPostCard(post)
            }
                android.util.Log.d("ViewUserProfile", "List view rendering complete. Container child count: ${postsContainer.childCount}")
        } else {
            // Grid View - create 2-column layout
            val gridContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            for (i in posts.indices step 2) {
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 2f
                }
                
                // First column
                val itemView1 = LayoutInflater.from(this).inflate(R.layout.item_home_post_grid, null, false)
                val layoutParams1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams1.setMargins(0, 0, 6, 0)
                itemView1.layoutParams = layoutParams1
                setupPostItem(itemView1, posts[i])
                rowLayout.addView(itemView1)
                
                // Second column (if exists)
                if (i + 1 < posts.size) {
                    val itemView2 = LayoutInflater.from(this).inflate(R.layout.item_home_post_grid, null, false)
                    val layoutParams2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams2.setMargins(6, 0, 0, 0)
                    itemView2.layoutParams = layoutParams2
                    setupPostItem(itemView2, posts[i + 1])
                    rowLayout.addView(itemView2)
                }
                
                gridContainer.addView(rowLayout)
            }
            
            postsContainer.addView(gridContainer)
            }
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfile", "Error rendering posts: ${e.message}", e)
            showEmptyState()
        }
    }

    private fun addPostCard(post: PostItem) {
        val inflater = LayoutInflater.from(this)
        val postView = inflater.inflate(R.layout.item_home_post, null)
        
        // Set proper layout parameters for the view
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        // Add margin at the bottom for spacing between items
        val marginBottom = (8 * resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, 0, marginBottom)
        postView.layoutParams = layoutParams
        
        val tvName = postView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = postView.findViewById<TextView>(R.id.tv_post_price)
        val ivPostImage = postView.findViewById<ImageView>(R.id.iv_post_image)
        val ivFavorite = postView.findViewById<ImageView>(R.id.iv_favorite)
        val tvFavCount = postView.findViewById<TextView>(R.id.tv_favorite_count)
        val btnView = postView.findViewById<TextView>(R.id.btn_view_post)
        
        // Category and Sale/Trade badges (for list view)
        val tvCategoryBadge = postView.findViewById<TextView>(R.id.tv_category_badge)
        val tvSaleTradeBadge = postView.findViewById<TextView>(R.id.tv_sale_trade_badge)
        
        tvName.text = post.name
        tvPrice.text = formatPrice(post.price)
        
        // Set category and sale/trade type badges
        if (tvCategoryBadge != null) {
            tvCategoryBadge.text = post.category.uppercase()
        }
        if (tvSaleTradeBadge != null) {
            val saleTradeText = when (post.saleTradeType.uppercase()) {
                "SALE" -> "SALE"
                "TRADE" -> "TRADE"
                "BOTH" -> "SALE/TRADE"
                else -> "SALE"
            }
            tvSaleTradeBadge.text = saleTradeText
        }
        
        btnView.setOnClickListener {
            val intent = if (post.type == "BID") {
                Intent(this, ViewBiddingActivity::class.java)
            } else {
                Intent(this, ItemDetailsActivity::class.java)
            }
            intent.putExtra("postId", post.id)
            startActivity(intent)
        }
        
        // Load favorite count and image
        val postRef = firestore.collection("posts").document(post.id)
        postRef.get().addOnSuccessListener { doc ->
            val count = (doc.getLong("favoriteCount") ?: 0L).toInt()
            tvFavCount.text = count.toString()
            
            // Load image from the post data
            val imageUrl = doc.getString("imageUrl") ?: 
                (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
            val imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList()
            
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(ivPostImage)
                
                // Make image clickable for image viewer (works for single or multiple images)
                ivPostImage.setOnClickListener {
                    val imageUrlList = if (imageUrls.isNotEmpty()) {
                        imageUrls.filter { it.isNotEmpty() }
                    } else {
                        listOf(imageUrl).filter { it.isNotEmpty() }
                    }
                    if (imageUrlList.isNotEmpty()) {
                        ImageViewerActivity.start(this, imageUrlList, 0)
                    }
                }
            } else {
                ivPostImage.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
        
        // Set favorite icon (display only)
        ivFavorite.setImageResource(R.drawable.ic_favorite_border)
        ivFavorite.setColorFilter(ContextCompat.getColor(this, R.color.yellow_accent))
        
        postsContainer.addView(postView)
    }


    private fun setupPostItem(itemView: View, post: PostItem) {
        val tvName = itemView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = itemView.findViewById<TextView>(R.id.tv_post_price)
        val ivPostImage = itemView.findViewById<ImageView>(R.id.iv_post_image)
        val tvFavCount = itemView.findViewById<TextView>(R.id.tv_favorites_count)
        
        // Category and Sale/Trade badges (for grid view)
        val tvCategoryBadge = itemView.findViewById<TextView>(R.id.tv_category_badge)
        val tvSaleTradeBadge = itemView.findViewById<TextView>(R.id.tv_sale_trade_badge)
        
        tvName.text = post.name
        tvPrice.text = formatPrice(post.price)
        
        // Set category and sale/trade type badges
        if (tvCategoryBadge != null) {
            tvCategoryBadge.text = post.category.uppercase()
        }
        if (tvSaleTradeBadge != null) {
            val saleTradeText = when (post.saleTradeType.uppercase()) {
                "SALE" -> "SALE"
                "TRADE" -> "TRADE"
                "BOTH" -> "SALE/TRADE"
                else -> "SALE"
            }
            tvSaleTradeBadge.text = saleTradeText
        }
        
        // Load image and favorite count
        val postRef = firestore.collection("posts").document(post.id)
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val count = (doc.getLong("favoriteCount") ?: 0L).toInt()
                tvFavCount.text = count.toString()
                
                // Load image from the post data
                val imageUrl = doc.getString("imageUrl") ?: 
                    (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
                val imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList()
                
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(ivPostImage)
                    
                    // Make image clickable for multiple image viewer
                    ivPostImage.setOnClickListener {
                        val imageUrlList = imageUrls.mapNotNull { it?.toString() }.filter { it.isNotEmpty() }
                        if (imageUrlList.isNotEmpty()) {
                            ImageViewerActivity.start(this, imageUrlList, 0)
                        }
                    }
                } else {
                    ivPostImage.setImageResource(R.drawable.ic_image_placeholder)
                }
            }
        }
        
        // Add click listener for grid items
        itemView.setOnClickListener {
            val intent = if (post.type == "BID") {
                Intent(this, ViewBiddingActivity::class.java)
            } else {
                Intent(this, ItemDetailsActivity::class.java)
            }
            intent.putExtra("postId", post.id)
            startActivity(intent)
        }
    }

    private fun showEmptyState() {
        val emptyView = LayoutInflater.from(this).inflate(R.layout.empty_posts_state, postsContainer, false)
        postsContainer.addView(emptyView)
    }

    private fun contactUser() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to send a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (sellerId.isNullOrEmpty()) {
            Toast.makeText(this, "User information not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if user is trying to contact themselves
        if (sellerId == currentUserId) {
            btnMessage.isEnabled = false
            btnMessage.alpha = 0.5f
            Toast.makeText(this, "You can't message yourself", Toast.LENGTH_SHORT).show()
            return
        }
        
        // ✅ CHECK VERIFICATION: Users must be verified to message other users
        firestore.collection("users").document(currentUserId)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { userDoc ->
                val verificationStatus = userDoc.getString("verificationStatus")?.trim()?.lowercase()
                
                // Debug logging
                android.util.Log.d("ViewUserProfileActivity", "Checking verificationStatus: '$verificationStatus'")
                android.util.Log.d("ViewUserProfileActivity", "Raw document data: ${userDoc.data}")
                
                if (verificationStatus != "approved") {
                    android.util.Log.d("ViewUserProfileActivity", "⚠️ User is not verified, cannot message user")
                    showVerificationRequiredForMessaging(verificationStatus)
                    return@addOnSuccessListener
                }
                
                android.util.Log.d("ViewUserProfileActivity", "✅ User is verified, proceeding with messaging")
                
                // Check if either user has blocked the other (bidirectional)
                com.example.agristockcapstoneproject.utils.BlockingUtils.checkIfBlocked(currentUserId, sellerId!!) { isBlocked ->
                    if (isBlocked) {
                        Toast.makeText(this, "You cannot message this user. They have been blocked.", Toast.LENGTH_LONG).show()
                        return@checkIfBlocked
                    }
                    
                    // User is verified and not blocked, proceed with creating or finding chat
                    createOrFindChat(sellerId!!)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check verification status", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createOrFindChat(userId: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to send a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Check if chat already exists between these users
            firestore.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .get()
                .addOnSuccessListener { chatsSnapshot ->
                    try {
                        var existingChatId: String? = null
                        
                        // Look for existing chat with the user
                        for (chatDoc in chatsSnapshot.documents) {
                            val participants = chatDoc.get("participants") as? List<String>
                            if (participants != null && participants.contains(userId)) {
                                existingChatId = chatDoc.id
                                break
                            }
                        }
                        
                        if (existingChatId != null) {
                            // Chat exists, navigate to it
                            navigateToChat(existingChatId, userId)
                        } else {
                            // Create new chat
                            createNewChat(userId)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ViewUserProfileActivity", "Error processing chat query: ${e.message}")
                        createNewChat(userId)
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ViewUserProfileActivity", "Error querying chats: ${exception.message}")
                    Toast.makeText(this, "Failed to check existing chats", Toast.LENGTH_SHORT).show()
                    createNewChat(userId)
                }
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfileActivity", "Error in createOrFindChat: ${e.message}")
            Toast.makeText(this, "Error creating chat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createNewChat(userId: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to send a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Get user's information for the chat
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { userDoc ->
                    try {
                        val userName = if (userDoc.exists()) {
                            userDoc.getString("username") ?: 
                            "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                                .ifEmpty { userDoc.getString("displayName") ?: "User" }
                        } else {
                            "User"
                        }
                        
                        val userAvatar = userDoc.getString("avatarUrl")
                        
                        android.util.Log.d("ViewUserProfileActivity", "Retrieved user data - userName: $userName, userAvatar: $userAvatar")
                        
                        // Create new chat document
                        val chatData = hashMapOf(
                            "participants" to listOf(currentUserId, userId),
                            "lastMessage" to "",
                            "lastMessageTime" to System.currentTimeMillis(),
                            "lastMessageSender" to "",
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "unreadCount_$currentUserId" to 0,
                            "unreadCount_$userId" to 0
                        )
                        
                        firestore.collection("chats")
                            .add(chatData)
                            .addOnSuccessListener { chatDoc ->
                                navigateToChat(chatDoc.id, userId, userName, userAvatar)
                            }
                            .addOnFailureListener { exception ->
                                android.util.Log.e("ViewUserProfileActivity", "Error creating chat: ${exception.message}")
                                Toast.makeText(this, "Failed to create chat: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        android.util.Log.e("ViewUserProfileActivity", "Error processing user data: ${e.message}")
                        createChatWithMinimalInfo(currentUserId, userId)
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ViewUserProfileActivity", "Error loading user data: ${exception.message}")
                    // Create chat with minimal info if user data can't be loaded
                    createChatWithMinimalInfo(currentUserId, userId)
                }
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfileActivity", "Error in createNewChat: ${e.message}")
            Toast.makeText(this, "Error creating chat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createChatWithMinimalInfo(currentUserId: String, userId: String) {
        try {
            val chatData = hashMapOf(
                "participants" to listOf(currentUserId, userId),
                "lastMessage" to "",
                "lastMessageTime" to System.currentTimeMillis(),
                "lastMessageSender" to "",
                "createdAt" to com.google.firebase.Timestamp.now(),
                "unreadCount_$currentUserId" to 0,
                "unreadCount_$userId" to 0
            )
            
            firestore.collection("chats")
                .add(chatData)
                .addOnSuccessListener { chatDoc ->
                    // Load user data before navigating to ensure we have userName and userAvatar
                    loadUserDataForChat(chatDoc.id, userId)
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ViewUserProfileActivity", "Error creating minimal chat: ${exception.message}")
                    Toast.makeText(this, "Failed to create chat: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfileActivity", "Error in createChatWithMinimalInfo: ${e.message}")
            Toast.makeText(this, "Error creating chat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadUserDataForChat(chatId: String, userId: String) {
        try {
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { userDoc ->
                    try {
                        val userName = if (userDoc.exists()) {
                            userDoc.getString("username") ?: 
                            "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                                .ifEmpty { userDoc.getString("displayName") ?: "User" }
                        } else {
                            "User"
                        }
                        
                        val userAvatar = userDoc.getString("avatarUrl")
                        
                        android.util.Log.d("ViewUserProfileActivity", "Loaded user data for chat - userName: $userName, userAvatar: $userAvatar")
                        
                        // Navigate to chat with complete user data
                        navigateToChat(chatId, userId, userName, userAvatar)
                    } catch (e: Exception) {
                        android.util.Log.e("ViewUserProfileActivity", "Error processing user data for chat: ${e.message}")
                        // Navigate with minimal data as fallback
                        navigateToChat(chatId, userId, "User", null)
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ViewUserProfileActivity", "Error loading user data for chat: ${exception.message}")
                    // Navigate with minimal data as fallback
                    navigateToChat(chatId, userId, "User", null)
                }
        } catch (e: Exception) {
            android.util.Log.e("ViewUserProfileActivity", "Error in loadUserDataForChat: ${e.message}")
            // Navigate with minimal data as fallback
            navigateToChat(chatId, userId, "User", null)
        }
    }
    
    private fun navigateToChat(chatId: String, userId: String, userName: String = "", userAvatar: String? = null) {
        android.util.Log.d("ViewUserProfileActivity", "Navigating to chat - chatId: $chatId, userId: $userId, userName: $userName, userAvatar: $userAvatar")
        
        val intent = Intent(this, ChatRoomActivity::class.java)
        intent.putExtra("chatId", chatId)
        intent.putExtra("otherUserId", userId)
        intent.putExtra("otherUserName", userName)
        intent.putExtra("otherUserAvatar", userAvatar)
        startActivity(intent)
    }

    private fun formatPrice(price: String): String {
        if (price.startsWith("₱") || price.startsWith("PHP")) {
            return price
        }
        return "₱$price"
    }
    
    // ✅ VERIFICATION CHECK FUNCTION FOR MESSAGING
    private fun showVerificationRequiredForMessaging(verificationStatus: String?) {
        val message = when (verificationStatus) {
            "pending" -> "Your verification request is being reviewed by our team. You'll be able to message other users once your account is verified.\n\nThank you for your patience!"
            "rejected" -> "Your previous verification was rejected. Please submit a new verification request with valid documents to message other users."
            else -> "You must verify your account before messaging other users. This helps prevent spam and maintains trust in our community.\n\nWould you like to verify your account now?"
        }
        
        val title = if (verificationStatus == "pending") "Verification Pending" else "Verification Required"
        val positiveButtonText = when (verificationStatus) {
            "pending" -> "OK"
            "rejected" -> "Resubmit"
            else -> "Verify Now"
        }
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ ->
                if (verificationStatus == "pending") {
                    // Just close dialog for pending status
                } else {
                    // Navigate to verification for others
                    startActivity(Intent(this, VerificationActivity::class.java))
                }
            }
        
        // Only add negative button if not pending
        if (verificationStatus != "pending") {
            builder.setNegativeButton("Cancel", null)
        } else {
            builder.setCancelable(false)
        }
        
        builder.show()
    }
}

