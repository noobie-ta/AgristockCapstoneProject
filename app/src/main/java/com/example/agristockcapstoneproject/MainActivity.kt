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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure status bar to blend with white top bar (non-deprecated APIs)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        drawerLayout = binding.drawerLayout
        setupClickListeners()
        setupNavigation()
        setupDrawerMenu()
        displayPosts()

        // Ensure initial active state reflects Home
        setActiveNavItem(binding.navHome)
    }

    override fun onResume() {
        super.onResume()
        // Refresh posts when returning to the activity
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
        // Edit Profile button
        findViewById<TextView>(R.id.btn_edit_profile).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Menu items
        findViewById<LinearLayout>(R.id.menu_verification).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, VerificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_settings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menu_notifications).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, NotificationActivity::class.java))
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
                    filteredPosts.forEach { post ->
                        addPostCard(post)
                    }
                }
            } else {
                showEmptyState()
            }
        }
    }

    private fun addPostCard(post: Post) {
        val inflater = LayoutInflater.from(this)
        val postView = inflater.inflate(R.layout.item_home_post, binding.llPostsContainer, false)
        
        val tvName = postView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = postView.findViewById<TextView>(R.id.tv_post_price)
        val tvTime = postView.findViewById<TextView>(R.id.tv_post_time)
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
        
        binding.llPostsContainer.addView(postView)
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