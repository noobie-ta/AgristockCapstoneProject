package com.example.agristockcapstoneproject

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

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
	private lateinit var uploadIcon: ImageView
	private lateinit var coverPhotoView: ImageView
	private lateinit var btnEditCover: LinearLayout
	private lateinit var tvUsername: TextView
	private lateinit var tvBio: TextView
	private lateinit var tvLocation: TextView
	private lateinit var tvContact: TextView
	private lateinit var btnEditProfile: ImageView
	
	// Rating Views
	private lateinit var tvRatingText: TextView
	private lateinit var tvTotalRatings: TextView
	private lateinit var star1: ImageView
	private lateinit var star2: ImageView
	private lateinit var star3: ImageView
	private lateinit var star4: ImageView
	private lateinit var star5: ImageView
	
	// Posts Section Views
	private lateinit var btnFilter: LinearLayout
	private lateinit var btnListView: ImageView
	private lateinit var btnGridView: ImageView
	
	private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
	private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
	private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
	private var currentRating = 5.0f
	private var totalRatings = 0
	
	// Filter state with save state
	private var filterSortBy = "NEWEST" // NEWEST or OLDEST
	private var filterStatus = "ALL" // ALL, ACTIVE, SOLD
	private var filterType = "ALL" // ALL, SELL, BID
	private var isListView = true // List or Grid view
	
	private var currentUserData: Map<String, Any>? = null
	private val postListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
	private var allPosts = listOf<PostItem>() // Cache all posts to prevent duplicates

	private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		uri?.let { selectedUri ->
			// Upload as profile picture
			showUploadConfirmationDialog(selectedUri, isCoverPhoto = false)
		}
	}
	
	private val pickCoverPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		uri?.let { selectedUri ->
			// Upload as cover photo
			showUploadConfirmationDialog(selectedUri, isCoverPhoto = true)
		}
	}

    // Cropper removed: images are uploaded as selected

	private val imageViewerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			val deleteImage = result.data?.getBooleanExtra("deleteImage", false)
			if (deleteImage == true) {
				deleteProfilePicture()
			}
		}
	}

	private val editPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		loadPosts()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_profile)

        // Configure status bar - transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

		initializeViews()
		setupClickListeners()
		setupRatingSystem()
		
		// Check if viewing another user's profile
		val sellerId = intent.getStringExtra("sellerId")
		if (sellerId != null && sellerId != FirebaseAuth.getInstance().currentUser?.uid) {
			// Viewing another user's profile - read-only mode
			loadOtherUserProfile(sellerId)
		} else {
			// Viewing own profile - editable mode
			loadUserProfile()
			loadPosts() // Only load posts for own profile here
		}
	}
	
	
	
	
	
	
	
	override fun onResume() {
		super.onResume()
	}
	
	override fun onPause() {
		super.onPause()
	}
	
	override fun onStop() {
		super.onStop()
		// Clean up all post listeners to prevent memory leaks
		postListeners.values.forEach { it.remove() }
		postListeners.clear()
	}
	
	override fun onDestroy() {
		super.onDestroy()
	}

	private fun initializeViews() {
		// Profile Section
		postsContainer = findViewById(R.id.ll_posts_container)
		avatarView = findViewById(R.id.iv_avatar)
		uploadIcon = findViewById(R.id.iv_upload)
		coverPhotoView = findViewById(R.id.iv_cover_photo)
		btnEditCover = findViewById(R.id.btn_edit_cover)
		tvUsername = findViewById(R.id.tv_username)
		tvBio = findViewById(R.id.tv_bio)
		tvLocation = findViewById(R.id.tv_location)
		tvContact = findViewById(R.id.tv_contact)
		btnEditProfile = findViewById(R.id.btn_edit_profile)
		
		// Rating Views
		tvRatingText = findViewById(R.id.tv_rating_text)
		tvTotalRatings = findViewById(R.id.tv_total_ratings)
		star1 = findViewById(R.id.star_1)
		star2 = findViewById(R.id.star_2)
		star3 = findViewById(R.id.star_3)
		star4 = findViewById(R.id.star_4)
		star5 = findViewById(R.id.star_5)
		
		// Posts Section
		btnFilter = findViewById(R.id.btn_filter)
		btnListView = findViewById(R.id.btn_list_view)
		btnGridView = findViewById(R.id.btn_grid_view)
		
		// Load saved filter state
		loadFilterState()
	}

	private fun setupClickListeners() {
		findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

		// Edit Profile Button
		btnEditProfile.setOnClickListener {
			showEditProfileDialog()
		}
		
		// Cover Photo Edit Button
		btnEditCover.setOnClickListener {
			showCoverPhotoOptions()
		}

		// Avatar click - open image viewer if image exists, otherwise pick new image
		avatarView.setOnClickListener {
			val currentImageUri = getCurrentAvatarUri()
			if (currentImageUri != null) {
				openImageViewer(currentImageUri)
			} else {
				pickImageLauncher.launch("image/*")
			}
		}
		
		// Upload icon click - always pick new image
		uploadIcon.setOnClickListener {
			pickImageLauncher.launch("image/*")
		}

		// Filter Button
		btnFilter.setOnClickListener {
			showFilterDialog()
		}
		
		// View Toggle Buttons
		btnListView.setOnClickListener {
			setViewOption(true)
		}
		
		btnGridView.setOnClickListener {
			setViewOption(false)
		}
	}

	private fun setupRatingSystem() {
		val stars = listOf(star1, star2, star3, star4, star5)
		val currentUser = auth.currentUser
		val sellerId = intent.getStringExtra("sellerId")
		
		// Check if user is viewing their own profile
		val isOwnProfile = sellerId == null || sellerId == currentUser?.uid
		
		if (isOwnProfile) {
			// Disable rating system for own profile
			stars.forEach { star ->
				star.isEnabled = false
				star.alpha = 0.5f
			}
			tvRatingText.text = "Your Rating"
			tvTotalRatings.text = "Cannot rate yourself"
		} else {
			// Enable rating system for other users
			stars.forEachIndexed { index, star ->
				star.setOnClickListener {
					currentRating = (index + 1).toFloat()
					updateStarDisplay()
					updateRatingInDatabase()
				}
			}
		}
		updateStarDisplay()
	}

	private fun updateStarDisplay() {
		val stars = listOf(star1, star2, star3, star4, star5)
		stars.forEachIndexed { index, star ->
			val starValue = index + 1
			when {
				starValue <= currentRating -> {
					star.setImageResource(R.drawable.ic_star_filled)
					star.setColorFilter(ContextCompat.getColor(this, R.color.yellow_accent))
				}
				starValue - 0.5f <= currentRating -> {
					star.setImageResource(R.drawable.ic_star_half)
					star.setColorFilter(ContextCompat.getColor(this, R.color.yellow_accent))
				}
				else -> {
					star.setImageResource(R.drawable.ic_star_filled)
					star.setColorFilter(ContextCompat.getColor(this, R.color.gray_300))
				}
			}
		}
		tvRatingText.text = "${currentRating}/5"
		tvTotalRatings.text = "$totalRatings ratings"
	}

	private fun updateRatingInDatabase() {
		val currentUser = auth.currentUser ?: return
		val sellerId = intent.getStringExtra("sellerId")
		
		// Check if user is trying to rate their own profile
		if (sellerId == null || sellerId == currentUser.uid) {
			Toast.makeText(this, "You cannot rate your own profile", Toast.LENGTH_SHORT).show()
			return
		}
		
		// Get current rating data and calculate new average
		firestore.collection("users").document(sellerId)
			.get()
			.addOnSuccessListener { document ->
				if (document.exists()) {
					val currentAverageRating = document.getDouble("rating") ?: 0.0
					val currentTotalRatings = document.getLong("totalRatings") ?: 0L
					
					// Calculate new average rating
					val newTotalRatings = currentTotalRatings + 1
					val newAverageRating = ((currentAverageRating * currentTotalRatings) + currentRating) / newTotalRatings
					
					// Update the rating of the user being viewed
					firestore.collection("users").document(sellerId)
						.update("rating", newAverageRating, "totalRatings", newTotalRatings)
						.addOnSuccessListener {
							totalRatings = newTotalRatings.toInt()
							currentRating = newAverageRating.toFloat()
							updateStarDisplay()
							Toast.makeText(this, "Rating submitted successfully", Toast.LENGTH_SHORT).show()
						}
						.addOnFailureListener { e ->
							Toast.makeText(this, "Failed to submit rating: ${e.message}", Toast.LENGTH_SHORT).show()
						}
				} else {
					Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
				}
			}
			.addOnFailureListener { e ->
				Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
			}
	}

	private fun loadPosts() {
		postsContainer.removeAllViews()
		
		// Check if viewing another user's profile
		val sellerId = intent.getStringExtra("sellerId")
		val targetUserId = if (sellerId != null && sellerId != FirebaseAuth.getInstance().currentUser?.uid) {
			// Viewing another user's profile - load their posts
			android.util.Log.d("ProfileActivity", "Loading posts for other user: $sellerId")
			sellerId
		} else {
			// Viewing own profile - load current user's posts
			val user = auth.currentUser
			if (user == null) {
				renderPosts(emptyList())
				return
			}
			android.util.Log.d("ProfileActivity", "Loading posts for current user: ${user.uid}")
			user.uid
		}
		
		firestore.collection("posts")
			.whereEqualTo("userId", targetUserId)
			.addSnapshotListener { snapshots, exception ->
				if (exception != null) {
					Toast.makeText(
						this,
						"Error loading posts: ${exception.message}",
						android.widget.Toast.LENGTH_SHORT
					).show()
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
							type = d.getString("type") ?: "SELL",
							saleTradeType = d.getString("saleTradeType") ?: "SALE",
							category = d.getString("category") ?: ""
						)
					}
					?: emptyList()

				android.util.Log.d("ProfileActivity", "Loaded ${list.size} posts for user: $targetUserId")
				
				// Cache all posts to prevent duplicates
				allPosts = list
				
				// Apply current filters
				applyFilters()
			}
	}

	private fun loadUserProfile() {
		val user = auth.currentUser ?: return
		
		// Load user data from Firestore
		firestore.collection("users").document(user.uid).get()
			.addOnSuccessListener { document ->
				currentUserData = document.data
				
				// Load username
				val firstName = document.getString("firstName") ?: ""
				val lastName = document.getString("lastName") ?: ""
				val fullName = if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
					"$firstName $lastName".trim()
				} else {
					user.displayName ?: "User"
				}
				tvUsername.text = fullName
				
				// Load bio
				val bio = document.getString("bio") ?: ""
				if (bio.isNotEmpty()) {
					tvBio.text = bio
				} else {
					tvBio.text = "Bio not set"
				}
				
				// Load location
				val location = document.getString("location") ?: ""
				val latitude = document.getDouble("latitude") ?: 0.0
				val longitude = document.getDouble("longitude") ?: 0.0
				
				if (location.isNotEmpty()) {
					tvLocation.text = location
				} else {
					tvLocation.text = "Location not set"
				}
				
				// Load contact
				val contact = document.getString("contact") ?: ""
				if (contact.isNotEmpty()) {
					tvContact.text = contact
				} else {
					tvContact.text = "Contact not set"
				}
				
				// Load rating data
				currentRating = document.getDouble("rating")?.toFloat() ?: 0.0f
				totalRatings = document.getLong("totalRatings")?.toInt() ?: 0
				updateRatingDisplay(currentRating, totalRatings)
				
				// Load avatar
				val avatarUrl = document.getString("avatarUrl")
				if (!avatarUrl.isNullOrEmpty()) {
					Glide.with(this)
						.load(avatarUrl)
						.circleCrop()
						.into(avatarView)
				}
				
				// Load cover photo
				val coverUrl = document.getString("coverUrl")
				if (!coverUrl.isNullOrEmpty()) {
					Glide.with(this).load(coverUrl).into(coverPhotoView)
				}
				
			}
			.addOnFailureListener {
				tvUsername.text = user.displayName ?: "User"
			}
	}
	
	private fun loadOtherUserProfile(sellerId: String) {
		// Debug logging
		android.util.Log.d("ProfileActivity", "loadOtherUserProfile called with sellerId: $sellerId")
		
		// Hide edit buttons for read-only mode
		btnEditProfile.visibility = View.GONE
		btnEditCover.visibility = View.GONE
		uploadIcon.visibility = View.GONE
		
		val userRef = FirebaseFirestore.getInstance().collection("users").document(sellerId)
		userRef.get()
			.addOnSuccessListener { document ->
				android.util.Log.d("ProfileActivity", "User document exists: ${document.exists()}")
				if (document.exists()) {
					android.util.Log.d("ProfileActivity", "User document data: ${document.data}")
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
					
					// Update UI
					tvUsername.text = username
					tvBio.text = bio
					tvLocation.text = location
					tvContact.text = contact
					
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
					
					// Update rating with total ratings count
					val totalRatings = (data["totalRatings"] as? Long) ?: 0L
					updateRatingDisplay(rating.toFloat(), totalRatings.toInt())
					
					// Load the other user's posts
					loadPosts()
				}
			}
			.addOnFailureListener { exception ->
				android.util.Log.e("ProfileActivity", "Failed to load other user profile", exception)
				Toast.makeText(this, "Failed to load profile: ${exception.message}", Toast.LENGTH_SHORT).show()
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

	private fun loadExistingAvatar() {
		// This method is now handled by loadUserProfile()
	}

	private fun uploadAvatarAndSave(uri: Uri) {
		val user = auth.currentUser ?: return
		val ref = storage.reference.child("user_uploads/${user.uid}/avatar.jpg")
		ref.putFile(uri)
			.continueWithTask { task ->
				if (!task.isSuccessful) {
					throw task.exception ?: RuntimeException("Upload failed")
				}
				ref.downloadUrl
			}
			.addOnSuccessListener { downloadUri ->
				firestore.collection("users").document(user.uid)
					.set(mapOf("avatarUrl" to downloadUri.toString()), com.google.firebase.firestore.SetOptions.merge())
					.addOnSuccessListener {
						// Update avatar immediately after successful upload with circular crop
						Glide.with(this)
							.load(downloadUri.toString())
							.circleCrop()
							.into(avatarView)
						Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
					}
			}
			.addOnFailureListener { exception ->
				Toast.makeText(this, "Failed to upload: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
			}
	}
	
	private fun uploadCoverPhotoAndSave(uri: Uri) {
		val user = auth.currentUser ?: return
		val ref = storage.reference.child("user_uploads/${user.uid}/cover.jpg")
		ref.putFile(uri)
			.continueWithTask { task ->
				if (!task.isSuccessful) {
					throw task.exception ?: RuntimeException("Upload failed")
				}
				ref.downloadUrl
			}
			.addOnSuccessListener { downloadUri ->
				firestore.collection("users").document(user.uid)
					.set(mapOf("coverUrl" to downloadUri.toString()), com.google.firebase.firestore.SetOptions.merge())
					.addOnSuccessListener {
						// Update cover photo immediately after successful upload
						Glide.with(this).load(downloadUri.toString()).into(coverPhotoView)
						Toast.makeText(this, "Cover photo updated!", Toast.LENGTH_SHORT).show()
					}
			}
			.addOnFailureListener { exception ->
				Toast.makeText(this, "Failed to upload cover photo: ${exception.message}", Toast.LENGTH_SHORT).show()
			}
	}

	private fun renderPosts(posts: List<PostItem>) {
		postsContainer.removeAllViews()
		if (posts.isEmpty()) {
			val placeholder: View = layoutInflater.inflate(R.layout.view_empty_posts_placeholder, postsContainer, false)
			postsContainer.addView(placeholder)
			return
		}

		val inflater = LayoutInflater.from(this)
		
		if (isListView) {
			// List View - full width cards
			posts.forEach { post ->
			val itemView = inflater.inflate(R.layout.item_profile_post, postsContainer, false)
				setupPostItem(itemView, post)
				postsContainer.addView(itemView)
			}
		} else {
			// Grid View - 2 columns
			val gridContainer = LinearLayout(this).apply {
				orientation = LinearLayout.VERTICAL
			}
			
			for (i in posts.indices step 2) {
				val rowLayout = LinearLayout(this).apply {
					orientation = LinearLayout.HORIZONTAL
					weightSum = 2f
				}
				
				// First column
				val itemView1 = inflater.inflate(R.layout.item_profile_post_grid, null, false)
				val layoutParams1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
				layoutParams1.setMargins(0, 0, 6, 0)
				itemView1.layoutParams = layoutParams1
				setupPostItem(itemView1, posts[i])
				rowLayout.addView(itemView1)
				
				// Second column (if exists)
				if (i + 1 < posts.size) {
					val itemView2 = inflater.inflate(R.layout.item_profile_post_grid, null, false)
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
	
	private fun displayPost(post: PostItem) {
		val inflater = LayoutInflater.from(this)
		val layoutRes = if (isListView) R.layout.item_profile_post else R.layout.item_profile_post_grid
		val itemView = inflater.inflate(layoutRes, postsContainer, false)
		setupPostItem(itemView, post)
		postsContainer.addView(itemView)
	}
	
	private fun setViewOption(isList: Boolean) {
		isListView = isList
		
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
		
		// Re-apply filters with new view
		applyFilters()
	}
	
	private fun setupPostItem(itemView: View, post: PostItem) {
			val tvName = itemView.findViewById<TextView>(R.id.tv_post_name)
			val tvPrice = itemView.findViewById<TextView>(R.id.tv_post_price)
			val tvBadge = itemView.findViewById<TextView>(R.id.tv_status_badge)
		val tvDate = itemView.findViewById<TextView>(R.id.tv_post_date)
		val tvFavoritesCount = itemView.findViewById<TextView>(R.id.tv_favorites_count)
		val btnMenu = itemView.findViewById<ImageView>(R.id.btn_menu)
			val ivImage = itemView.findViewById<ImageView>(R.id.iv_post_image)
		val llLoadingShimmer = itemView.findViewById<LinearLayout>(R.id.ll_loading_shimmer)

		// Set post data
			tvName.text = post.name
			tvPrice.text = formatPrice(post.price)
		tvDate?.text = post.datePosted  // Make date optional for grid view
		tvFavoritesCount.text = post.favoriteCount.toString()
		
		// Category and Sale/Trade badges
		val tvCategoryBadge = itemView.findViewById<TextView>(R.id.tv_category_badge)
		val tvSaleTradeBadge = itemView.findViewById<TextView>(R.id.tv_sale_trade_badge)
		
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
		
		// Set up real-time listener for favorite count updates
		val postRef = FirebaseFirestore.getInstance().collection("posts").document(post.id)
		
		// Remove existing listener for this post if any
		postListeners[post.id]?.remove()
		
		val listener = postRef.addSnapshotListener { doc, exception ->
			if (exception != null) {
				return@addSnapshotListener
			}
			
			if (doc != null && doc.exists()) {
				val count = (doc.getLong("favoriteCount") ?: 0L).toInt()
				tvFavoritesCount.text = count.toString()
			}
		}
		
		// Store the listener for cleanup
		postListeners[post.id] = listener
		
			// Set status text and background based on status
			when (post.status.uppercase()) {
				"FOR SALE", "BIDDING" -> {
					tvBadge.text = "Available"
					tvBadge.setBackgroundResource(R.drawable.status_chip_available)
				}
				"SOLD" -> {
					tvBadge.text = "Sold"
					tvBadge.setBackgroundResource(R.drawable.status_chip_sold)
				}
				else -> {
					tvBadge.text = "Available"
					tvBadge.setBackgroundResource(R.drawable.status_chip_available)
				}
			}
			
		// Load image with loading effect and make clickable
		val imagePostRef = firestore.collection("posts").document(post.id)
		imagePostRef.get().addOnSuccessListener { doc ->
			if (doc.exists()) {
				val imageUrl = doc.getString("imageUrl") ?: 
					(doc.get("imageUrls") as? List<String>)?.firstOrNull()?.toString()
				val imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList()
				
				if (!imageUrl.isNullOrEmpty()) {
					Glide.with(this)
						.load(imageUrl)
						.centerCrop()
						.placeholder(R.drawable.ic_image_placeholder)
						.error(R.drawable.ic_image_placeholder)
						.into(ivImage)
					
					// Make image clickable for image viewer (works for single or multiple images)
					ivImage.setOnClickListener {
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
					ivImage.setImageResource(R.drawable.ic_image_placeholder)
				}
			}
			
			// Hide loading shimmer after image loads
			llLoadingShimmer.visibility = View.GONE
		}

		// 3-dot menu click
		btnMenu.setOnClickListener { view ->
			view.isClickable = true
			animateCard(view)
			showPostMenu(post)
		}

		// Add card animation and navigation on click
		itemView.setOnClickListener { view ->
			animateCard(view)
			// Navigate to appropriate activity based on post type
			val intent = if (post.type == "BID") {
				Intent(this, ViewBiddingActivity::class.java)
			} else {
				Intent(this, ItemDetailsActivity::class.java)
			}
			intent.putExtra("postId", post.id)
			startActivity(intent)
		}
		
		// Make sure the item view is clickable
		itemView.isClickable = true
		itemView.isFocusable = true
	}


	private fun showDeleteConfirmDialog(postId: String) {
		val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
		val dialog = AlertDialog.Builder(this)
			.setView(dialogView)
			.setCancelable(true)
			.create()

		dialogView.findViewById<TextView>(R.id.btn_confirm).setOnClickListener {
			// Prevent double clicks
			dialogView.findViewById<TextView>(R.id.btn_confirm).isEnabled = false
			firestore.collection("posts").document(postId)
				.delete()
				.addOnSuccessListener {
					Toast.makeText(this, "Post deleted", android.widget.Toast.LENGTH_SHORT).show()
					dialog.dismiss()
				}
				.addOnFailureListener { e ->
					Toast.makeText(this, "Failed to delete: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
					dialogView.findViewById<TextView>(R.id.btn_confirm).isEnabled = true
				}
		}
		dialogView.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
			dialog.dismiss()
		}

		dialog.show()
	}

    // Cropper removed: images are uploaded as selected

	private fun openImageViewer(imageUri: Uri) {
		val intent = Intent(this, ImageViewerActivity::class.java).apply {
			putExtra("imageUri", imageUri)
		}
		imageViewerLauncher.launch(intent)
	}

	private fun showUploadConfirmationDialog(imageUri: Uri, isCoverPhoto: Boolean = false) {
		val title = if (isCoverPhoto) "Upload Cover Photo" else "Upload Profile Picture"
		val message = if (isCoverPhoto) "Do you want to upload this image as your cover photo?" else "Do you want to upload this image as your profile picture?"
		
		// Create custom dialog with preview
		val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
		val previewImage = dialogView.findViewById<ImageView>(R.id.iv_preview)
		val btnUpload = dialogView.findViewById<TextView>(R.id.btn_upload)
		val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
		
		// Load preview image
		if (isCoverPhoto) {
			Glide.with(this).load(imageUri).into(previewImage)
		} else {
			Glide.with(this)
				.load(imageUri)
				.circleCrop()
				.into(previewImage)
		}
		
		val dialog = AlertDialog.Builder(this)
			.setTitle(title)
			.setView(dialogView)
			.setCancelable(true)
			.create()
		
		btnUpload.setOnClickListener {
			if (isCoverPhoto) {
				// Show the image immediately in cover photo
				Glide.with(this).load(imageUri).into(coverPhotoView)
				uploadCoverPhotoAndSave(imageUri)
			} else {
				// Show the image immediately in profile picture with circular crop
				Glide.with(this)
					.load(imageUri)
					.circleCrop()
					.into(avatarView)
				uploadAvatarAndSave(imageUri)
			}
			dialog.dismiss()
		}
		
		btnCancel.setOnClickListener {
			dialog.dismiss()
		}
		
		dialog.show()
	}

	private fun getCurrentAvatarUri(): Uri? {
		// This is a simplified approach - in a real app you might want to store the URI
		// For now, we'll check if the avatar is not the default one
		return if (avatarView.drawable != null && avatarView.drawable != ContextCompat.getDrawable(this, R.drawable.ic_profile)) {
			// Return a placeholder URI - in a real implementation you'd store the actual URI
			null // This will trigger the image picker
		} else {
			null
		}
	}

	private fun deleteProfilePicture() {
		val user = auth.currentUser ?: return
		
		// Update Firestore to remove avatar URL
		firestore.collection("users").document(user.uid)
			.update(mapOf("avatarUrl" to null))
			.addOnSuccessListener {
				// Reset to default avatar
				avatarView.setImageResource(R.drawable.ic_profile)
				Toast.makeText(this, "Profile picture deleted", android.widget.Toast.LENGTH_SHORT).show()
			}
			.addOnFailureListener { exception ->
				Toast.makeText(this, "Failed to delete profile picture: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
			}
	}

	// New Enhanced Methods
	

	private fun showEditProfileDialog() {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
		val etUsername = dialogView.findViewById<EditText>(R.id.et_username)
		val etBio = dialogView.findViewById<EditText>(R.id.et_bio)
		val etLocation = dialogView.findViewById<EditText>(R.id.et_location)
		val etContact = dialogView.findViewById<EditText>(R.id.et_contact)
		val btnPickLocation = dialogView.findViewById<Button>(R.id.btn_pick_location)
		
		// Pre-fill current values
		etUsername.setText(tvUsername.text.toString())
		etBio.setText(tvBio.text.toString().takeIf { it != "Bio not set" } ?: "")
		etLocation.setText(tvLocation.text.toString().takeIf { it != "Location not set" } ?: "")
		etContact.setText(tvContact.text.toString().takeIf { it != "Contact not set" } ?: "")
		
		// Set up location picker button
		btnPickLocation.setOnClickListener {
			openLocationPickerForEdit(etLocation)
		}
		
		val dialog = AlertDialog.Builder(this)
			.setTitle("Edit Profile")
			.setView(dialogView)
			.setPositiveButton("Save") { _, _ ->
				saveProfileData(
					etUsername.text.toString(),
					etBio.text.toString(),
					etLocation.text.toString(),
					etContact.text.toString()
				)
			}
			.setNegativeButton("Cancel", null)
			.create()
		
		// Make dialog background transparent to show rounded corners
		dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
		dialog.show()
	}

	private fun openLocationPickerForEdit(etLocation: EditText) {
		val intent = Intent(this, LocationPickerActivity::class.java)
		// Pass current location if available
		val currentLocation = etLocation.text.toString()
		if (currentLocation.isNotEmpty() && currentLocation != "Location not set") {
			// You could parse the current location to get coordinates if needed
			// For now, just open the location picker
		}
		// Store reference to the EditText for updating
		currentLocationEditText = etLocation
		locationPickerLauncherForEdit.launch(intent)
	}

	private var currentLocationEditText: EditText? = null

	private val locationPickerLauncherForEdit = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == RESULT_OK) {
			val data = result.data
			val latitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
			val longitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
			val address = data?.getStringExtra("address") ?: ""
			
			if (latitude != 0.0 && longitude != 0.0 && address.isNotEmpty()) {
				// Update the location field in the dialog
				currentLocationEditText?.setText(address)
				Toast.makeText(this, "Location selected: $address", Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun saveProfileData(username: String, bio: String, location: String, contact: String) {
		val user = auth.currentUser ?: return
		
		// Validate mobile number format
		if (contact.isNotEmpty()) {
			val cleanedContact = contact.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
			// Check if it's a valid Philippine mobile number (11 digits starting with 09)
			val mobilePattern = Regex("^09\\d{9}$")
			if (!mobilePattern.matches(cleanedContact)) {
				Toast.makeText(this, "Please enter a valid mobile number (09XXXXXXXXX)", Toast.LENGTH_LONG).show()
				return
			}
		}
		
		val updates = mapOf(
			"username" to username,
			"bio" to bio,
			"location" to location,
			"contact" to contact
		)
		
		firestore.collection("users").document(user.uid)
			.update(updates)
			.addOnSuccessListener {
				// Update UI
				tvUsername.text = username.ifEmpty { "Username not set" }
				tvBio.text = bio.ifEmpty { "Bio not set" }
				tvLocation.text = location.ifEmpty { "Location not set" }
				tvContact.text = contact.ifEmpty { "Contact not set" }
				Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
			}
			.addOnFailureListener { exception ->
				Toast.makeText(this, "Failed to update profile: ${exception.message}", Toast.LENGTH_SHORT).show()
			}
	}

	private fun showCoverPhotoOptions() {
		AlertDialog.Builder(this)
			.setTitle("Cover Photo")
			.setItems(arrayOf("Change Cover Photo", "Remove Cover Photo")) { _, which ->
				when (which) {
					0 -> pickCoverPhotoLauncher.launch("image/*")
					1 -> removeCoverPhoto()
				}
			}
			.show()
	}

	private fun removeCoverPhoto() {
		val user = auth.currentUser ?: return
		
		firestore.collection("users").document(user.uid)
			.update(mapOf("coverUrl" to null))
			.addOnSuccessListener {
				coverPhotoView.setImageResource(R.drawable.ic_image_placeholder)
				Toast.makeText(this, "Cover photo removed", Toast.LENGTH_SHORT).show()
			}
			.addOnFailureListener { exception ->
				Toast.makeText(this, "Failed to remove cover photo: ${exception.message}", Toast.LENGTH_SHORT).show()
			}
	}

	private fun showFilterDialog() {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_posts, null)
		val dialog = AlertDialog.Builder(this)
			.setView(dialogView)
			.create()
		
		dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
		
		// Get views from dialog
		val rgSort = dialogView.findViewById<RadioGroup>(R.id.rg_sort)
		val rbNewest = dialogView.findViewById<RadioButton>(R.id.rb_newest)
		val rbOldest = dialogView.findViewById<RadioButton>(R.id.rb_oldest)
		
		val rgStatus = dialogView.findViewById<RadioGroup>(R.id.rg_status)
		val rbAllItems = dialogView.findViewById<RadioButton>(R.id.rb_all_items)
		val rbActiveItems = dialogView.findViewById<RadioButton>(R.id.rb_active_items)
		val rbSoldItems = dialogView.findViewById<RadioButton>(R.id.rb_sold_items)
		
		val rgType = dialogView.findViewById<RadioGroup>(R.id.rg_type)
		val rbAllTypes = dialogView.findViewById<RadioButton>(R.id.rb_all_types)
		val rbSellPosts = dialogView.findViewById<RadioButton>(R.id.rb_sell_posts)
		val rbBidPosts = dialogView.findViewById<RadioButton>(R.id.rb_bid_posts)
		
		val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
		val btnApply = dialogView.findViewById<TextView>(R.id.btn_apply)
		
		// Set current filter values
		when (filterSortBy) {
			"NEWEST" -> rbNewest.isChecked = true
			"OLDEST" -> rbOldest.isChecked = true
		}
		
		when (filterStatus) {
			"ALL" -> rbAllItems.isChecked = true
			"ACTIVE" -> rbActiveItems.isChecked = true
			"SOLD" -> rbSoldItems.isChecked = true
		}
		
		when (filterType) {
			"ALL" -> rbAllTypes.isChecked = true
			"SELL" -> rbSellPosts.isChecked = true
			"BID" -> rbBidPosts.isChecked = true
		}
		
		btnCancel.setOnClickListener {
			dialog.dismiss()
		}
		
		btnApply.setOnClickListener {
			// Get selected values
			filterSortBy = when (rgSort.checkedRadioButtonId) {
				R.id.rb_newest -> "NEWEST"
				R.id.rb_oldest -> "OLDEST"
				else -> "NEWEST"
			}
			
			filterStatus = when (rgStatus.checkedRadioButtonId) {
				R.id.rb_all_items -> "ALL"
				R.id.rb_active_items -> "ACTIVE"
				R.id.rb_sold_items -> "SOLD"
				else -> "ALL"
			}
			
			filterType = when (rgType.checkedRadioButtonId) {
				R.id.rb_all_types -> "ALL"
				R.id.rb_sell_posts -> "SELL"
				R.id.rb_bid_posts -> "BID"
				else -> "ALL"
			}
			
			// Save filter state
			saveFilterState()
			
			// Apply filter with smooth transition
			applyFilters()
			
			dialog.dismiss()
		}
		
		dialog.show()
	}
	
	private fun applyFilters() {
		// Use cached posts to prevent duplicates
		var filteredPosts = allPosts.toList()
		
		// Apply status filter
		when (filterStatus) {
			"ACTIVE" -> filteredPosts = filteredPosts.filter { it.status != "SOLD" }
			"SOLD" -> filteredPosts = filteredPosts.filter { it.status == "SOLD" }
		}
		
		// Apply type filter
		when (filterType) {
			"SELL" -> filteredPosts = filteredPosts.filter { it.type == "SELL" }
			"BID" -> filteredPosts = filteredPosts.filter { it.type == "BID" }
		}
		
		// Apply sort
		filteredPosts = when (filterSortBy) {
			"NEWEST" -> filteredPosts.sortedByDescending { it.datePosted }
			"OLDEST" -> filteredPosts.sortedBy { it.datePosted }
			else -> filteredPosts
		}
		
		// Smooth fade transition
		postsContainer.animate().alpha(0f).setDuration(150).withEndAction {
			displayFilteredPosts(filteredPosts)
			postsContainer.animate().alpha(1f).setDuration(150).start()
		}.start()
	}
	
	private fun displayFilteredPosts(posts: List<PostItem>) {
		renderPosts(posts)
	}
	
	private fun loadFilterState() {
		val prefs = getSharedPreferences("profile_filters", MODE_PRIVATE)
		filterSortBy = prefs.getString("sortBy", "NEWEST") ?: "NEWEST"
		filterStatus = prefs.getString("status", "ALL") ?: "ALL"
		filterType = prefs.getString("type", "ALL") ?: "ALL"
	}
	
	private fun saveFilterState() {
		val prefs = getSharedPreferences("profile_filters", MODE_PRIVATE)
		prefs.edit().apply {
			putString("sortBy", filterSortBy)
			putString("status", filterStatus)
			putString("type", filterType)
			apply()
		}
	}

	private fun showPostMenu(post: PostItem) {
		val options = arrayOf("Edit", "Delete")
		
		AlertDialog.Builder(this)
			.setTitle("Post Options")
			.setItems(options) { _, which ->
				when (which) {
					0 -> editPost(post)
					1 -> showDeleteConfirmDialog(post.id)
				}
			}
			.show()
	}

	private fun editPost(post: PostItem) {
		val intent = Intent(this, EditPostActivity::class.java)
		intent.putExtra("postId", post.id)
		intent.putExtra("title", post.name)
		intent.putExtra("price", post.price)
		intent.putExtra("description", post.description)
		intent.putExtra("imageUrl", post.imageUrl ?: "")
		editPostLauncher.launch(intent)
	}


	private fun animateCard(view: View) {
		val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f)
		val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f)
		scaleX.duration = 200
		scaleY.duration = 200
		scaleX.start()
		scaleY.start()
	}
}

private fun formatPrice(raw: String): String {
	val trimmed = raw.trim()
	return if (trimmed.startsWith("₱") || trimmed.startsWith("PHP", ignoreCase = true)) {
		trimmed
	} else if (trimmed.isEmpty()) {
		"₱0"
	} else {
		"₱$trimmed"
	}
}


