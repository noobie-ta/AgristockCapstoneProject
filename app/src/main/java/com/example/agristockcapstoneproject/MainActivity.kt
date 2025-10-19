package com.example.agristockcapstoneproject

import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private var currentFilter = "ALL" // ALL, SELL, BID
    private var postsListener: ListenerRegistration? = null
    private var isRefreshing = false
    private var isListView = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        drawerLayout = binding.drawerLayout
        setupClickListeners()
        setupNavigation()
        setupDrawerMenu()
        setupSwipeRefresh()
        displayPosts()

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
        // Always refresh posts when returning to the activity to get updated counters
        displayPosts()
        // Ensure bottom nav highlights Home when returning via back navigation
        setActiveNavItem(binding.navHome)
    }

    override fun onStop() {
        super.onStop()
        // Detach listener to avoid leaks or duplicate callbacks
        postsListener?.remove()
        postsListener = null
    }

    private fun setupClickListeners() {
        // SELL and BID buttons
        binding.btnSell.setOnClickListener {
            startActivity(Intent(this, SellPostActivity::class.java))
        }

        binding.btnBid.setOnClickListener {
            startActivity(Intent(this, BidPostActivity::class.java))
        }

        // Filter buttons
        binding.btnFilterAll.setOnClickListener {
            setFilter("ALL")
        }

        binding.btnFilterSell.setOnClickListener {
            setFilter("SELL")
        }

        binding.btnFilterBid.setOnClickListener {
            setFilter("BID")
        }

        // Hamburger menu button
        binding.btnHamburger.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Search functionality - navigate to search activity
        binding.etSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // View toggle buttons
        findViewById<ImageView>(R.id.btn_list_view_home).setOnClickListener {
            setViewOption(true)
        }

        findViewById<ImageView>(R.id.btn_grid_view_home).setOnClickListener {
            setViewOption(false)
        }
        
        // Add text change listener for real-time search
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    // Navigate to search activity with query
                    val intent = Intent(this@MainActivity, SearchActivity::class.java)
                    intent.putExtra("search_query", query)
                    startActivity(intent)
                }
            }
        })
        
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
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        binding.navMessages.setOnClickListener {
            setActiveNavItem(binding.navMessages)
            Toast.makeText(this, "Messages coming soon!", Toast.LENGTH_SHORT).show()
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
            startActivity(Intent(this, VerificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_settings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_subscription).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Subscriptions coming soon!", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.menu_sold_items).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SoldItemsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_logout).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showLogoutDialog()
        }

        // Load user info
        loadUserInfo()
    }

    private fun loadUserInfo() {
        val user = auth.currentUser
        val usernameText = findViewById<TextView>(R.id.tv_username)
        val profilePicture = findViewById<ImageView>(R.id.iv_profile_picture)
        
        if (user != null) {
            usernameText.text = user.displayName ?: "User"
            // Load profile picture from Firebase if available
            // For now, using default image
        } else {
            usernameText.text = "Guest"
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        
        // Update button backgrounds and text colors
        when (filter) {
            "ALL" -> {
                binding.btnFilterAll.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
                binding.btnFilterAll.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                binding.btnFilterSell.setBackgroundResource(R.drawable.filter_toggle_default_ripple)
                binding.btnFilterSell.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnFilterBid.setBackgroundResource(R.drawable.filter_toggle_default_ripple)
                binding.btnFilterBid.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            "SELL" -> {
                binding.btnFilterAll.setBackgroundResource(R.drawable.filter_toggle_default_ripple)
                binding.btnFilterAll.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnFilterSell.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
                binding.btnFilterSell.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                binding.btnFilterBid.setBackgroundResource(R.drawable.filter_toggle_default_ripple)
                binding.btnFilterBid.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            "BID" -> {
                binding.btnFilterAll.setBackgroundResource(R.drawable.filter_toggle_default_ripple)
                binding.btnFilterAll.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnFilterSell.setBackgroundResource(R.drawable.filter_toggle_default_ripple)
                binding.btnFilterSell.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnFilterBid.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
                binding.btnFilterBid.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
        
        displayPosts()
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
                // Filter out SOLD items client-side to avoid Firestore '!=' constraints
                val availableDocs = documents.documents.filter { doc ->
                    (doc.getString("status") ?: "").uppercase() != "SOLD"
                }
                val posts = availableDocs.map { doc ->
                    Post(
                        id = doc.id,
                        name = doc.getString("title") ?: "",
                        price = doc.getString("price") ?: "",
                        time = doc.getString("datePosted") ?: "",
                        type = doc.getString("type") ?: "SELL"
                    )
                }
                
                val filteredPosts = if (currentFilter == "ALL") {
                    posts
                } else {
                    posts.filter { it.type == currentFilter }
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
        val tvTime = postView.findViewById<TextView>(R.id.tv_post_time)
        val ivPostImage = postView.findViewById<ImageView>(R.id.iv_post_image)
        val ivFavorite = postView.findViewById<ImageView>(R.id.iv_favorite)
        val tvFavCount = postView.findViewById<TextView>(R.id.tv_favorite_count)
        val btnView = postView.findViewById<TextView>(R.id.btn_view_post)
        
        tvName.text = post.name
        tvPrice.text = post.price
        tvTime.text = post.time
        
        // 'View' is a small yellow pill for consistency
        
        btnView.setOnClickListener {
            val intent = Intent(this, ItemDetailsActivity::class.java)
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
                
                // Load image from the post data
                val imageUrl = doc.getString("imageUrl") ?: 
                    (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
                
                if (!imageUrl.isNullOrEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(ivPostImage)
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

    private fun setupPostItem(itemView: View, post: Post) {
        val tvName = itemView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = itemView.findViewById<TextView>(R.id.tv_post_price)
        val ivPostImage = itemView.findViewById<ImageView>(R.id.iv_post_image)
        val tvFavCount = itemView.findViewById<TextView>(R.id.tv_favorites_count)
        
        tvName.text = post.name
        tvPrice.text = post.price
        
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
                
                if (!imageUrl.isNullOrEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(ivPostImage)
                } else {
                    ivPostImage.setImageResource(R.drawable.ic_image_placeholder)
                }
            }
        }
        
        // Add click listener for grid items
        itemView.setOnClickListener {
            val intent = Intent(this, ItemDetailsActivity::class.java)
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
        val type: String // SELL or BID
    )
}