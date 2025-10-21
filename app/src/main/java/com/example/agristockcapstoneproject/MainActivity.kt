package com.example.agristockcapstoneproject

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.agristockcapstoneproject.databinding.ActivityMainBinding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sharedPreferences: SharedPreferences
    private var currentFilter = "ALL" // ALL, SELL, BID
    private var currentCategory = "All Categories" // Category filter
    private var postsListener: ListenerRegistration? = null
    private var isRefreshing = false
    private var isListView = true
    private var notificationsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make status bar transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        drawerLayout = binding.drawerLayout
        sharedPreferences = getSharedPreferences("homepage_filters", MODE_PRIVATE)
        
        // Load saved filter state
        loadFilterState()
        
        setupClickListeners()
        setupNavigation()
        setupDrawerMenu()
        setupSwipeRefresh()
        setupNotificationBadge()
        setupMessagesBadge()
        setupWelcomeUsername()
        setupModernFilters()
        displayPosts()
        
        // Initialize badges to hidden state
        initializeBadges()

        // Ensure initial active state reflects Home
        setActiveNavItem(binding.navHome)
    }

    private fun setupSwipeRefresh() {
        val srl = findViewById<SwipeRefreshLayout>(R.id.srl_home_refresh)
        srl.setOnRefreshListener {
            // Re-attach the listener to force refresh and stop the spinner
            isRefreshing = true
            displayPosts()
            srl.isRefreshing = false
            isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Only refresh if we're coming from another activity, not from within the app
        if (shouldRefreshOnResume()) {
            displayPosts()
        }
        // Refresh badges when returning to the activity
        refreshBadges()
        // Ensure bottom nav highlights Home when returning via back navigation
        setActiveNavItem(binding.navHome)
    }
    
    private fun shouldRefreshOnResume(): Boolean {
        // Only refresh if we haven't refreshed recently (within last 30 seconds)
        val currentTime = System.currentTimeMillis()
        val lastRefreshTime = sharedPreferences.getLong("last_refresh_time", 0)
        return (currentTime - lastRefreshTime) > 30000 // 30 seconds
    }

    override fun onStop() {
        super.onStop()
        // Detach listener to avoid leaks or duplicate callbacks
        postsListener?.remove()
        postsListener = null
        notificationsListener?.remove()
        notificationsListener = null
        messagesListener?.remove()
        messagesListener = null
    }

    private fun setupClickListeners() {
        // SELL and BID buttons
        binding.btnSell.setOnClickListener {
            startActivity(Intent(this, SellPostActivity::class.java))
        }

        binding.btnBid.setOnClickListener {
            startActivity(Intent(this, BidPostActivity::class.java))
        }

        // Filter functionality is now handled in setupModernFilters()

        // Hamburger menu button
        binding.btnHamburger.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Search functionality - navigate to search activity with query
        // Search icon button - trigger search with current input
        findViewById<ImageView>(R.id.btn_search_icon).setOnClickListener {
            val query = binding.etSearch.text.toString().trim()
            val intent = Intent(this, SearchActivity::class.java)
            if (query.isNotEmpty()) {
                intent.putExtra("search_query", query)
            }
            startActivity(intent)
        }

        // Add search functionality to search input field
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.putExtra("search_query", query)
                    startActivity(intent)
                }
                true
            } else {
                false
            }
        }

        // View toggle buttons
        findViewById<ImageView>(R.id.btn_list_view_home).setOnClickListener {
            setViewOption(true)
        }

        findViewById<ImageView>(R.id.btn_grid_view_home).setOnClickListener {
            setViewOption(false)
        }
        
        // Remove real-time navigation to avoid duplicate search pages; tap opens search instead
        
        // Make search bar focusable for better UX
        binding.etSearch.isFocusable = true
        binding.etSearch.isClickable = true
    }

    private fun setupNavigation() {
        binding.navHome.setOnClickListener {
            // Already on home, just highlight
            setActiveNavItem(binding.navHome)
        }

        binding.navNotifications.setOnClickListener {
            setActiveNavItem(binding.navNotifications)
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        binding.navMessages.setOnClickListener {
            setActiveNavItem(binding.navMessages)
            startActivity(Intent(this, MessagesActivity::class.java))
        }

        binding.navSoldItems.setOnClickListener {
            setActiveNavItem(binding.navSoldItems)
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        binding.navProfile.setOnClickListener {
            setActiveNavItem(binding.navProfile)
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupDrawerMenu() {
        // Edit Profile button removed

        // Menu items
        findViewById<LinearLayout>(R.id.menu_verification).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, IdVerificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_settings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_apply_bidding).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Apply for Bidding feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.menu_my_purchases).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, MyPurchasesActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_logout).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showLogoutDialog()
        }

        // Load user info
        loadUserInfo()
        
        // Refresh user info when drawer is opened
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerClosed(drawerView: android.view.View) {}
            override fun onDrawerOpened(drawerView: android.view.View) {
                // Refresh user info when drawer opens
                loadUserInfo()
            }
        })
    }

    private fun loadUserInfo() {
        val user = auth.currentUser
        val usernameText = findViewById<TextView>(R.id.tv_username)
        val profilePicture = findViewById<ImageView>(R.id.iv_profile_picture)
        
        if (user != null) {
            // First set the username from Firebase Auth
            usernameText.text = user.displayName ?: "User"
            
            // Load user data from Firestore
            firestore.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username")
                        val avatarUrl = document.getString("avatarUrl")
                        
                        // Update username if available from Firestore
                        if (!username.isNullOrEmpty()) {
                            usernameText.text = username
                        }
                        
                        // Load profile picture
                        if (!avatarUrl.isNullOrEmpty()) {
                            android.util.Log.d("MainActivity", "Loading profile picture: $avatarUrl")
                            Glide.with(this)
                                .load(avatarUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .into(profilePicture)
                        } else {
                            android.util.Log.d("MainActivity", "No avatar URL found, using default")
                            profilePicture.setImageResource(R.drawable.ic_profile)
                        }
                    } else {
                        android.util.Log.w("MainActivity", "User document not found in Firestore")
                        profilePicture.setImageResource(R.drawable.ic_profile)
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("MainActivity", "Error loading user info: ${exception.message}")
                    profilePicture.setImageResource(R.drawable.ic_profile)
                }
        } else {
            usernameText.text = "Guest"
            profilePicture.setImageResource(R.drawable.ic_profile)
        }
    }


    private fun setViewOption(isList: Boolean) {
        isListView = isList
        
        val btnListView = findViewById<ImageView>(R.id.btn_list_view_home)
        val btnGridView = findViewById<ImageView>(R.id.btn_grid_view_home)
        
        if (isList) {
            btnListView.background = ContextCompat.getDrawable(this, R.drawable.view_toggle_active)
            btnListView.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
            btnGridView.background = ContextCompat.getDrawable(this, R.drawable.view_toggle_inactive)
            btnGridView.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            btnGridView.background = ContextCompat.getDrawable(this, R.drawable.view_toggle_active)
            btnGridView.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
            btnListView.background = ContextCompat.getDrawable(this, R.drawable.view_toggle_inactive)
            btnListView.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
        }
        
        // Refresh posts with new view
        displayPosts()
    }

    private fun displayPosts() {
        // Record refresh time to prevent unnecessary refreshes
        sharedPreferences.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()
        
        // Load posts from Firebase; avoid inequality filter to prevent failed precondition
        val postsRef = firestore.collection("posts")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
        
        // Use real-time listener to automatically update when new posts are added
        // Remove previous listener before attaching a new one
        postsListener?.remove()
        postsListener = postsRef.addSnapshotListener { documents, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error loading posts: ${exception.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
                return@addSnapshotListener
            }
            
            if (documents != null) {
                val currentTime = System.currentTimeMillis()
                
                // Filter out old SOLD items and expired bid items client-side
                val availableDocs = documents.documents.filter { doc ->
                    val status = doc.getString("status") ?: ""
                    val type = doc.getString("type") ?: "SELL"
                    
                    // Filter out SOLD items older than 24 hours
                    if (status.uppercase() == "SOLD") {
                        val soldAt = doc.getLong("soldAt") ?: 0L
                        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L // 24 hours
                        
                        // If soldAt is not set (old sold items), filter them out
                        if (soldAt == 0L) return@filter false
                        
                        // Filter out if sold more than 24 hours ago
                        if (currentTime - soldAt > twentyFourHoursInMillis) {
                            return@filter false
                        }
                        // Keep sold items that are less than 24 hours old
                    }
                    
                    // Filter out expired bid items (1 hour after bidding end)
                    if (type == "BID") {
                        val endTime = doc.getLong("endTime") ?: 0L
                        val oneHourAfterEnd = endTime + (60 * 60 * 1000) // 1 hour in milliseconds
                        if (currentTime > oneHourAfterEnd) {
                            return@filter false
                        }
                    }
                    
                    true
                }
                
                val posts = availableDocs.map { doc ->
                    Post(
                        id = doc.id,
                        name = doc.getString("title") ?: "",
                        price = doc.getString("price") ?: "",
                        time = doc.getString("datePosted") ?: "",
                        type = doc.getString("type") ?: "SELL",
                        sellerId = doc.getString("userId") ?: "",
                        sellerName = doc.getString("sellerName") ?: "",
                        saleTradeType = doc.getString("saleTradeType") ?: "SALE",
                        category = doc.getString("category") ?: ""
                    )
                }
                
                // Apply type filter (ALL, SELL, BID)
                val typeFilteredPosts = if (currentFilter == "ALL") {
                    posts
                } else {
                    posts.filter { it.type == currentFilter }
                }
                
                // Apply category filter
                val filteredPosts = if (currentCategory == "All Categories") {
                    typeFilteredPosts
                } else {
                    typeFilteredPosts.filter { post ->
                        // Get category from Firestore document
                        val doc = availableDocs.find { it.id == post.id }
                        val category = doc?.getString("category") ?: ""
                        category == currentCategory
                    }
                }
                
                // Clear existing views before adding new ones
                binding.llPostsContainer.removeAllViews()
                
                if (filteredPosts.isEmpty()) {
                    showEmptyState()
                } else {
                    if (isListView) {
                        // List View - add posts directly
                        filteredPosts.forEach { post ->
                            addPostCard(post)
                        }
                    } else {
                        // Grid View - create 2-column layout
                        val gridContainer = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                        }
                        
                        for (i in filteredPosts.indices step 2) {
                            val rowLayout = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                weightSum = 2f
                            }
                            
                            // First column
                            val itemView1 = LayoutInflater.from(this).inflate(R.layout.item_home_post_grid, null, false)
                            val layoutParams1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            layoutParams1.setMargins(0, 0, 6, 0)
                            itemView1.layoutParams = layoutParams1
                            setupPostItem(itemView1, filteredPosts[i])
                            rowLayout.addView(itemView1)
                            
                            // Second column (if exists)
                            if (i + 1 < filteredPosts.size) {
                                val itemView2 = LayoutInflater.from(this).inflate(R.layout.item_home_post_grid, null, false)
                                val layoutParams2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                layoutParams2.setMargins(6, 0, 0, 0)
                                itemView2.layoutParams = layoutParams2
                                setupPostItem(itemView2, filteredPosts[i + 1])
                                rowLayout.addView(itemView2)
                            }
                            
                            gridContainer.addView(rowLayout)
                        }
                        
                        binding.llPostsContainer.addView(gridContainer)
                    }
                }
            } else {
                showEmptyState()
            }
        }
    }

    private fun addPostCard(post: Post) {
        val inflater = LayoutInflater.from(this)
        val postView = if (isListView) {
            inflater.inflate(R.layout.item_home_post, binding.llPostsContainer, false)
        } else {
            inflater.inflate(R.layout.item_home_post_grid, binding.llPostsContainer, false)
        }
        
        val tvName = postView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = postView.findViewById<TextView>(R.id.tv_post_price)
        val ivPostImage = postView.findViewById<ImageView>(R.id.iv_post_image)
        val ivFavorite = postView.findViewById<ImageView>(R.id.iv_favorite)
        val tvFavCount = postView.findViewById<TextView>(R.id.tv_favorite_count)
        val btnView = postView.findViewById<TextView>(R.id.btn_view_post)
        
        // Category and Sale/Trade badges (for list view)
        val tvCategoryBadge = postView.findViewById<TextView>(R.id.tv_category_badge)
        val tvSaleTradeBadge = postView.findViewById<TextView>(R.id.tv_sale_trade_badge)
        val tvBidBadge = postView.findViewById<TextView>(R.id.tv_bid_badge)
        val tvSoldBadge = postView.findViewById<TextView>(R.id.tv_sold_badge)
        
        tvName.text = post.name
        tvPrice.text = if (post.price.startsWith("₱")) post.price else "₱${post.price}"
        
        // Set category and sale/trade type badges
        if (tvCategoryBadge != null) {
            tvCategoryBadge.text = post.category.uppercase()
        }
        
        // Show bid badge for bid posts, sale/trade badge for sell posts
        if (post.type.uppercase() == "BID") {
            tvSaleTradeBadge?.visibility = View.GONE
            tvBidBadge?.visibility = View.VISIBLE
        } else {
            tvBidBadge?.visibility = View.GONE
            if (tvSaleTradeBadge != null) {
                val saleTradeText = when (post.saleTradeType.uppercase()) {
                    "SALE" -> "SALE"
                    "TRADE" -> "TRADE"
                    "BOTH" -> "SALE/TRADE"
                    else -> "SALE"
                }
                tvSaleTradeBadge.text = saleTradeText
                tvSaleTradeBadge.visibility = View.VISIBLE
            }
        }
        
        // 'View' is a small yellow pill for consistency
        
        btnView.setOnClickListener {
            val intent = if (post.type == "BID") {
                Intent(this, ViewBiddingActivity::class.java)
            } else {
                Intent(this, ItemDetailsActivity::class.java)
            }
            intent.putExtra("postId", post.id)
            startActivity(intent)
        }
        
        // Initialize favorite state and count with real-time updates
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("posts").document(post.id)
        
        // Load favorite count and image
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val count = (doc.getLong("favoriteCount") ?: 0L).toInt()
                tvFavCount.text = count.toString()
                
                // Check if item is sold and show SOLD badge
                val status = doc.getString("status")?.uppercase() ?: ""
                if (tvSoldBadge != null) {
                    if (status == "SOLD") {
                        tvSoldBadge.visibility = View.VISIBLE
                        // Optionally dim the image to indicate sold status
                        ivPostImage.alpha = 0.6f
                    } else {
                        tvSoldBadge.visibility = View.GONE
                        ivPostImage.alpha = 1.0f
                    }
                }
                
                // Load image from the post data
                val imageUrl = doc.getString("imageUrl") ?: 
                    (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
                val imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList()
                
                if (!imageUrl.isNullOrEmpty()) {
                    com.bumptech.glide.Glide.with(this)
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
        }
        if (uid != null) {
            db.collection("users").document(uid).collection("favorites").document(post.id)
                .get()
                .addOnSuccessListener { fdoc ->
                    val isFav = fdoc.exists()
                    ivFavorite.setImageResource(if (isFav) R.drawable.ic_favorite_filled_red else R.drawable.ic_favorite_border)
                    if (!isFav) ivFavorite.setColorFilter(ContextCompat.getColor(this, R.color.yellow_accent)) else ivFavorite.clearColorFilter()
                }
        }

        // Favorite button is display-only on homepage - no click functionality
        // Users must go to the View Item page to add/remove favorites

        binding.llPostsContainer.addView(postView)
    }
    
    private fun loadSellerRating(sellerId: String, ratingTextView: TextView) {
        if (sellerId.isEmpty()) {
            ratingTextView.text = "Seller: Unknown"
            return
        }
        
        firestore.collection("users").document(sellerId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rating = document.getDouble("rating") ?: 0.0
                    val totalRatings = document.getLong("totalRatings") ?: 0L
                    val verificationStatus = document.getString("verificationStatus")
                    val sellerName = document.getString("username") ?: 
                        "${document.getString("firstName") ?: ""} ${document.getString("lastName") ?: ""}".trim()
                        ?.ifEmpty { document.getString("displayName") ?: "User" }
                        ?: "User"
                    
                    val isVerified = verificationStatus == "approved"
                    val verifiedTag = if (isVerified) " ✅ Verified Seller" else ""
                    
                    val displayText = if (totalRatings > 0 && rating > 0) {
                        "Seller: $sellerName$verifiedTag (${String.format("%.1f", rating)}★)"
                    } else {
                        "Seller: $sellerName$verifiedTag (No rating yet ⭐)"
                    }
                    ratingTextView.text = displayText
                } else {
                    ratingTextView.text = "Seller: Unknown"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MainActivity", "Failed to load seller rating: ${e.message}")
                ratingTextView.text = "Seller: Unknown"
            }
    }

    private fun setupPostItem(itemView: View, post: Post) {
        val tvName = itemView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = itemView.findViewById<TextView>(R.id.tv_post_price)
        val ivPostImage = itemView.findViewById<ImageView>(R.id.iv_post_image)
        val tvFavCount = itemView.findViewById<TextView>(R.id.tv_favorites_count)
        
        // Category and Sale/Trade badges (for grid view)
        val tvCategoryBadge = itemView.findViewById<TextView>(R.id.tv_category_badge)
        val tvSaleTradeBadge = itemView.findViewById<TextView>(R.id.tv_sale_trade_badge)
        val tvBidBadge = itemView.findViewById<TextView>(R.id.tv_bid_badge)
        
        tvName.text = post.name
        tvPrice.text = if (post.price.startsWith("₱")) post.price else "₱${post.price}"
        
        // Set category and sale/trade type badges
        if (tvCategoryBadge != null) {
            tvCategoryBadge.text = post.category.uppercase()
        }
        
        // Show bid badge for bid posts, sale/trade badge for sell posts
        if (post.type.uppercase() == "BID") {
            tvSaleTradeBadge?.visibility = View.GONE
            tvBidBadge?.visibility = View.VISIBLE
        } else {
            tvBidBadge?.visibility = View.GONE
            if (tvSaleTradeBadge != null) {
                val saleTradeText = when (post.saleTradeType.uppercase()) {
                    "SALE" -> "SALE"
                    "TRADE" -> "TRADE"
                    "BOTH" -> "SALE/TRADE"
                    else -> "SALE"
                }
                tvSaleTradeBadge.text = saleTradeText
                tvSaleTradeBadge.visibility = View.VISIBLE
            }
        }
        
        
        // Load image and favorite count with real-time updates
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("posts").document(post.id)
        
        // Load favorite count and image
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val count = (doc.getLong("favoriteCount") ?: 0L).toInt()
                tvFavCount.text = count.toString()
                
                // Load image from the post data
                val imageUrl = doc.getString("imageUrl") ?: 
                    (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
                val imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList()
                
                if (!imageUrl.isNullOrEmpty()) {
                    com.bumptech.glide.Glide.with(this)
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
        val emptyView = TextView(this).apply {
            text = "No posts available.\nBe the first to SELL or BID!"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }
        binding.llPostsContainer.addView(emptyView)
    }

    private fun setActiveNavItem(activeItem: LinearLayout) {
        // Reset all nav items to inactive style
        val navItems = listOf(
            binding.navHome,
            binding.navNotifications,
            binding.navMessages,
            binding.navSoldItems,
            binding.navProfile
        )

        navItems.forEach { item ->
            val icon = item.getChildAt(0) as? android.widget.ImageView
            val text = item.getChildAt(1) as? TextView
            // Remove active circle background and set gray tint
            icon?.background = null
            icon?.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            // Inactive label: gray, normal weight, no shadow/underline
            text?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            text?.setTypeface(text.typeface, android.graphics.Typeface.NORMAL)
            text?.paintFlags = text?.paintFlags?.and(android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()) ?: 0
            text?.setShadowLayer(0f, 0f, 0f, 0)
        }

        // Highlight active item
        val activeIcon = activeItem.getChildAt(0) as? android.widget.ImageView
        val activeText = activeItem.getChildAt(1) as? TextView

        // Active icon inside bold yellow circle, icon dark for contrast
        activeIcon?.background = ContextCompat.getDrawable(this, R.drawable.bg_nav_item_active_circle)
        activeIcon?.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))

        // Active label: bold black with subtle yellow glow
        activeText?.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        activeText?.setTypeface(activeText.typeface, android.graphics.Typeface.BOLD)
        activeText?.setShadowLayer(6f, 0f, 0f, ContextCompat.getColor(this, R.color.yellow_light))
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                finishAffinity()
                startActivity(Intent(this, SplashActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Data class for posts
    data class Post(
        val id: String,
        val name: String,
        val price: String,
        val time: String,
        val type: String, // SELL or BID
        val sellerId: String = "",
        val sellerName: String = "",
        val sellerRating: Double = 0.0,
        val sellerTotalRatings: Long = 0L,
        val saleTradeType: String = "SALE", // SALE, TRADE, or BOTH
        val category: String = ""
    )
    
    private fun setupNotificationBadge() {
        val uid = auth.currentUser?.uid ?: return
        
        notificationsListener = firestore.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    android.util.Log.e("MainActivity", "Error loading notifications badge: ${exception.message}")
                    return@addSnapshotListener
                }
                
                val unreadCount = snapshot?.size() ?: 0
                
                runOnUiThread {
                    val badge = findViewById<TextView>(R.id.badge_notifications)
                    if (unreadCount > 0) {
                        badge.visibility = View.VISIBLE
                        badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                    } else {
                        badge.visibility = View.GONE
                    }
                }
            }
    }
    
    private fun setupMessagesBadge() {
        val uid = auth.currentUser?.uid ?: return
        
        messagesListener = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    android.util.Log.e("MainActivity", "Error loading messages badge: ${exception.message}")
                    return@addSnapshotListener
                }
                
                var unreadCount = 0
                snapshot?.documents?.forEach { chatDoc ->
                    try {
                        // Skip hidden chats (deleted by user)
                        val isHiddenFor = chatDoc.get("isHiddenFor") as? Map<String, Boolean> ?: emptyMap()
                        if (isHiddenFor[uid] == true) {
                            android.util.Log.d("MainActivity", "Skipping hidden chat ${chatDoc.id} for badge count")
                            return@forEach
                        }
                        
                        // Get unread count for this specific user
                        val userUnreadCount = chatDoc.getLong("unreadCount_$uid") ?: 0L
                        unreadCount += userUnreadCount.toInt()
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error processing chat for badge: ${e.message}")
                    }
                }
                
                runOnUiThread {
                    val badge = findViewById<TextView>(R.id.badge_messages)
                    if (unreadCount > 0) {
                        badge.visibility = View.VISIBLE
                        badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                    } else {
                        badge.visibility = View.GONE
                    }
                }
            }
    }
    
    private fun initializeBadges() {
        // Initialize notification badge
        val notificationBadge = findViewById<TextView>(R.id.badge_notifications)
        notificationBadge.visibility = View.GONE
        
        // Initialize messages badge
        val messagesBadge = findViewById<TextView>(R.id.badge_messages)
        messagesBadge.visibility = View.GONE
    }
    
    private fun refreshBadges() {
        // Re-setup badges to get fresh data
        setupNotificationBadge()
        setupMessagesBadge()
    }
    
    fun clearNotificationBadge() {
        runOnUiThread {
            val badge = findViewById<TextView>(R.id.badge_notifications)
            badge.visibility = View.GONE
        }
    }
    
    fun clearMessagesBadge() {
        runOnUiThread {
            val badge = findViewById<TextView>(R.id.badge_messages)
            badge.visibility = View.GONE
        }
    }
    
    private fun setupWelcomeUsername() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Try to get username from Firestore
            firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username") ?: 
                            document.getString("displayName") ?: 
                            "${document.getString("firstName") ?: ""} ${document.getString("lastName") ?: ""}".trim()
                            .ifEmpty { "User" }
                        
                        val welcomeText = "Welcome, $username"
                        findViewById<TextView>(R.id.tv_welcome_username).text = welcomeText
                    } else {
                        findViewById<TextView>(R.id.tv_welcome_username).text = "Welcome, User"
                    }
                }
                .addOnFailureListener {
                    findViewById<TextView>(R.id.tv_welcome_username).text = "Welcome, User"
                }
        } else {
            findViewById<TextView>(R.id.tv_welcome_username).text = "Welcome, Guest"
        }
    }
    
    private fun setupModernFilters() {
        // Set up filter button click listener
        findViewById<LinearLayout>(R.id.btn_filter).setOnClickListener {
            showFilterModal()
        }
        
        // Update filter text display
        updateFilterText()
    }
    
    private fun showFilterModal() {
        val dialog = android.app.AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.modal_filter, null)
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
        
        // Type filter buttons
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_all).setOnClickListener {
            setActiveTypeFilter(dialogView, "ALL")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_sell).setOnClickListener {
            setActiveTypeFilter(dialogView, "SELL")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_bid).setOnClickListener {
            setActiveTypeFilter(dialogView, "BID")
        }
        
        // Category filter buttons
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_all).setOnClickListener {
            setActiveCategoryFilter(dialogView, "All Categories")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_carabao).setOnClickListener {
            setActiveCategoryFilter(dialogView, "CARABAO")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_chicken).setOnClickListener {
            setActiveCategoryFilter(dialogView, "CHICKEN")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_goat).setOnClickListener {
            setActiveCategoryFilter(dialogView, "GOAT")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_cow).setOnClickListener {
            setActiveCategoryFilter(dialogView, "COW")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_pig).setOnClickListener {
            setActiveCategoryFilter(dialogView, "PIG")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_duck).setOnClickListener {
            setActiveCategoryFilter(dialogView, "DUCK")
        }
        
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_category_other).setOnClickListener {
            setActiveCategoryFilter(dialogView, "OTHER")
        }
        
        // Apply button
        dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_apply_filter).setOnClickListener {
            // Get the selected filter and category from the active buttons
            val selectedFilter = getSelectedFilter(dialogView)
            val selectedCategory = getSelectedCategory(dialogView)
            
            currentFilter = selectedFilter
            currentCategory = selectedCategory
            
            // Save the filter state
            saveFilterState()
            
            updateFilterText()
            displayPosts()
            alertDialog.dismiss()
        }
        
        // Set initial states
        setActiveTypeFilter(dialogView, currentFilter)
        setActiveCategoryFilter(dialogView, currentCategory)
    }
    
    private fun setActiveTypeFilter(dialogView: View, selectedType: String) {
        val allBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_all)
        val sellBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_sell)
        val bidBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_bid)
        
        // Reset all buttons
        allBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        allBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        sellBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        sellBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        bidBtn.background = ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_inactive)
        bidBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        // Set active button
        when (selectedType) {
            "ALL" -> {
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
        }
    }
    
    private fun setActiveCategoryFilter(dialogView: View, selectedCategory: String) {
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
        
        // Reset all buttons
        categoryButtons.forEach { (buttonId, _) ->
            val button = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(buttonId)
            button.background = ContextCompat.getDrawable(this, R.drawable.modern_category_chip_inactive)
            button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        
        // Set active button
        val activeButtonId = categoryButtons.find { it.second == selectedCategory }?.first
        if (activeButtonId != null) {
            val activeButton = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(activeButtonId)
            activeButton.background = ContextCompat.getDrawable(this, R.drawable.modern_category_chip_active)
            activeButton.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }
    
    private fun updateFilterText() {
        val filterText = when {
            currentCategory == "All Categories" && currentFilter == "ALL" -> "All Items"
            currentCategory == "All Categories" -> currentFilter
            currentFilter == "ALL" -> currentCategory
            else -> "$currentFilter - $currentCategory"
        }
        
        findViewById<TextView>(R.id.tv_filter_text).text = filterText
    }
    
    private fun getSelectedFilter(dialogView: View): String {
        val allBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_all)
        val sellBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_sell)
        val bidBtn = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_bid)
        
        return when {
            allBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "ALL"
            sellBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "SELL"
            bidBtn.background.constantState == ContextCompat.getDrawable(this, R.drawable.modern_filter_chip_active)?.constantState -> "BID"
            else -> "ALL"
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
    
    private fun loadFilterState() {
        currentFilter = sharedPreferences.getString("current_filter", "ALL") ?: "ALL"
        currentCategory = sharedPreferences.getString("current_category", "All Categories") ?: "All Categories"
    }
    
    private fun saveFilterState() {
        sharedPreferences.edit().apply {
            putString("current_filter", currentFilter)
            putString("current_category", currentCategory)
            apply()
        }
    }
    
}