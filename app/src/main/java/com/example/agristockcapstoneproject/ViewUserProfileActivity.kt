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
        val type: String = "SELL" // SELL or BID
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
    
    // Posts Section Views
    private lateinit var btnSortNewest: TextView
    private lateinit var btnSortOldest: TextView
    private lateinit var btnSortPriceHigh: TextView
    private lateinit var btnSortPriceLow: TextView
    private lateinit var btnGridView: ImageView
    private lateinit var btnListView: ImageView
    
    // Message button
    private lateinit var btnMessage: ImageView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var isListView = true
    private var currentSort = "newest"
    private var sellerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_user_profile)

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        
        // Get seller ID from intent
        sellerId = intent.getStringExtra("sellerId")
        if (sellerId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid user profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadUserProfile()
        loadPosts()
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
        
        // Posts Section Views
        btnSortNewest = findViewById(R.id.btn_sort_newest)
        btnSortOldest = findViewById(R.id.btn_sort_oldest)
        btnSortPriceHigh = findViewById(R.id.btn_sort_price_high)
        btnSortPriceLow = findViewById(R.id.btn_sort_price_low)
        btnGridView = findViewById(R.id.btn_grid_view)
        btnListView = findViewById(R.id.btn_list_view)
        
        // Message button
        btnMessage = findViewById(R.id.btn_message)
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        // Sort buttons
        btnSortNewest.setOnClickListener { setSort("newest") }
        btnSortOldest.setOnClickListener { setSort("oldest") }
        btnSortPriceHigh.setOnClickListener { setSort("price_high") }
        btnSortPriceLow.setOnClickListener { setSort("price_low") }
        
        // View toggle buttons
        btnGridView.setOnClickListener { setViewMode(false) }
        btnListView.setOnClickListener { setViewMode(true) }
        
        // Message button
        btnMessage.setOnClickListener { 
            contactUser()
        }
    }

    private fun setSort(sort: String) {
        currentSort = sort
        updateSortButtons()
        loadPosts()
    }

    private fun setViewMode(listView: Boolean) {
        isListView = listView
        updateViewButtons()
        loadPosts()
    }

    private fun updateSortButtons() {
        val buttons = listOf(btnSortNewest, btnSortOldest, btnSortPriceHigh, btnSortPriceLow)
        buttons.forEach { it.setBackgroundResource(R.drawable.filter_toggle_default_ripple) }
        
        when (currentSort) {
            "newest" -> btnSortNewest.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
            "oldest" -> btnSortOldest.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
            "price_high" -> btnSortPriceHigh.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
            "price_low" -> btnSortPriceLow.setBackgroundResource(R.drawable.filter_toggle_active_ripple)
        }
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
                if (document.exists()) {
                    val data = document.data!!
                    
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
                    
                    // Update UI
                    tvUsername.text = username
                    tvBio.text = bio.ifEmpty { "Bio not set" }
                    tvLocation.text = location.ifEmpty { "Location not set" }
                    tvContact.text = contact.ifEmpty { "Contact not set" }
                    
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
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateRatingDisplay(rating: Float, totalRatings: Int = 0) {
        // Update rating text with total ratings count
        if (totalRatings > 0) {
            tvRatingText.text = String.format("%.1f", rating)
            tvTotalRatings.text = "$totalRatings ratings"
        } else {
            tvRatingText.text = "No ratings"
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

    private fun loadPosts() {
        if (sellerId.isNullOrEmpty()) return
        
        postsContainer.removeAllViews()
        
        firestore.collection("posts")
            .whereEqualTo("userId", sellerId)
            .addSnapshotListener { snapshots, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error loading posts: ${exception.message}", Toast.LENGTH_SHORT).show()
                    renderPosts(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshots?.documents
                    ?.map { d ->
                        PostItem(
                            id = d.id,
                            name = d.getString("title") ?: "",
                            price = d.getString("price") ?: "",
                            description = d.getString("description") ?: "",
                            imageUrl = d.getString("imageUrl"),
                            status = d.getString("status") ?: "FOR SALE",
                            datePosted = d.getString("datePosted") ?: "Unknown date",
                            favoriteCount = d.getLong("favoriteCount") ?: 0L,
                            type = d.getString("type") ?: "SELL"
                        )
                    }
                    // Filter out SOLD items client-side to avoid Firestore '!=' constraints
                    ?.filter { it.status.uppercase() != "SOLD" }
                    ?: emptyList()

                // Apply sorting
                val sortedList = when (currentSort) {
                    "newest" -> list.sortedByDescending { it.datePosted }
                    "oldest" -> list.sortedBy { it.datePosted }
                    "price_high" -> list.sortedByDescending { 
                        it.price.replace("₱", "").replace(",", "").toDoubleOrNull() ?: 0.0 
                    }
                    "price_low" -> list.sortedBy { 
                        it.price.replace("₱", "").replace(",", "").toDoubleOrNull() ?: 0.0 
                    }
                    else -> list
                }

                renderPosts(sortedList)
            }
    }

    private fun renderPosts(posts: List<PostItem>) {
        postsContainer.removeAllViews()
        
        if (posts.isEmpty()) {
            showEmptyState()
            return
        }

        if (isListView) {
            // List View - add posts directly
            posts.forEach { post ->
                addPostCard(post)
            }
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
    }

    private fun addPostCard(post: PostItem) {
        val inflater = LayoutInflater.from(this)
        val postView = if (isListView) {
            inflater.inflate(R.layout.item_home_post, postsContainer, false)
        } else {
            inflater.inflate(R.layout.item_home_post_grid, postsContainer, false)
        }
        
        val tvName = postView.findViewById<TextView>(R.id.tv_post_name)
        val tvPrice = postView.findViewById<TextView>(R.id.tv_post_price)
        val tvTime = postView.findViewById<TextView>(R.id.tv_post_time)
        val ivPostImage = postView.findViewById<ImageView>(R.id.iv_post_image)
        val ivFavorite = postView.findViewById<ImageView>(R.id.iv_favorite)
        val tvFavCount = postView.findViewById<TextView>(R.id.tv_favorite_count)
        val btnView = postView.findViewById<TextView>(R.id.btn_view_post)
        
        tvName.text = post.name
        tvPrice.text = formatPrice(post.price)
        tvTime.text = post.datePosted
        
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
            
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(ivPostImage)
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
        
        tvName.text = post.name
        tvPrice.text = formatPrice(post.price)
        
        // Load image and favorite count
        val postRef = firestore.collection("posts").document(post.id)
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val count = (doc.getLong("favoriteCount") ?: 0L).toInt()
                tvFavCount.text = count.toString()
                
                // Load image from the post data
                val imageUrl = doc.getString("imageUrl") ?: 
                    (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
                
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this)
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
        
        // Create or find chat with user
        createOrFindChat(sellerId!!)
    }
    
    private fun createOrFindChat(userId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Check if chat already exists between these users
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { chatsSnapshot ->
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
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check existing chats", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createNewChat(userId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Get user's information for the chat
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                val userName = if (userDoc.exists()) {
                    userDoc.getString("username") ?: 
                    "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                        .ifEmpty { userDoc.getString("displayName") ?: "User" }
                } else {
                    "User"
                }
                
                val userAvatar = userDoc.getString("avatarUrl")
                
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
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create chat", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                // Create chat with minimal info if user data can't be loaded
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
                        navigateToChat(chatDoc.id, userId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create chat", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    
    private fun navigateToChat(chatId: String, userId: String, userName: String = "", userAvatar: String? = null) {
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
}
