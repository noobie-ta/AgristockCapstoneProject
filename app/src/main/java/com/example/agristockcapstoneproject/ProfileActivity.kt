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
		val type: String = "SELL" // SELL or BID
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
	private lateinit var progressCompletion: ProgressBar
	private lateinit var tvCompletionPercentage: TextView
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
	private lateinit var btnSortNewest: TextView
	private lateinit var btnSortOldest: TextView
	private lateinit var btnListView: ImageView
	private lateinit var btnGridView: ImageView
	
	private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
	private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
	private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
	private var currentRating = 5.0f
	private var totalRatings = 0
	private var isSortNewest = true
	private var isListView = true
	private var currentUserData: Map<String, Any>? = null
	private val postListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

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

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

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
	
	override fun onStop() {
		super.onStop()
		// Clean up all post listeners to prevent memory leaks
		postListeners.values.forEach { it.remove() }
		postListeners.clear()
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
		progressCompletion = findViewById(R.id.progress_completion)
		tvCompletionPercentage = findViewById(R.id.tv_completion_percentage)
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
		btnSortNewest = findViewById(R.id.btn_sort_newest)
		btnSortOldest = findViewById(R.id.btn_sort_oldest)
		btnListView = findViewById(R.id.btn_list_view)
		btnGridView = findViewById(R.id.btn_grid_view)
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

		// Sort Buttons
		btnSortNewest.setOnClickListener {
			setSortOption(true)
		}
		
		btnSortOldest.setOnClickListener {
			setSortOption(false)
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
		stars.forEachIndexed { index, star ->
			star.setOnClickListener {
				currentRating = (index + 1).toFloat()
				updateStarDisplay()
				updateRatingInDatabase()
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
		val user = auth.currentUser ?: return
		firestore.collection("users").document(user.uid)
			.update("rating", currentRating, "totalRatings", totalRatings + 1)
			.addOnSuccessListener {
				totalRatings++
				updateStarDisplay()
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
							type = d.getString("type") ?: "SELL"
						)
					}
					// Filter out SOLD items client-side to avoid Firestore '!=' constraints
					?.filter { it.status.uppercase() != "SOLD" }
					?: emptyList()

				android.util.Log.d("ProfileActivity", "Loaded ${list.size} posts for user: $targetUserId")
				renderPosts(list)
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
				
				// Update profile completion
				updateProfileCompletion()
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
		progressCompletion.visibility = View.GONE
		tvCompletionPercentage.visibility = View.GONE
		
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

	private fun loadExistingAvatar() {
		// This method is now handled by loadUserProfile()
	}

	private fun uploadAvatarAndSave(uri: Uri) {
		val user = auth.currentUser ?: return
		val ref = storage.reference.child("avatars/${user.uid}.jpg")
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
		val ref = storage.reference.child("covers/${user.uid}.jpg")
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

		// Sort posts based on current sort option
		val sortedPosts = if (isSortNewest) {
			posts.sortedByDescending { it.datePosted }
		} else {
			posts.sortedBy { it.datePosted }
		}

		val inflater = LayoutInflater.from(this)
		
		if (isListView) {
			// List View - full width cards
			sortedPosts.forEach { post ->
			val itemView = inflater.inflate(R.layout.item_profile_post, postsContainer, false)
				setupPostItem(itemView, post)
				postsContainer.addView(itemView)
			}
		} else {
			// Grid View - 2 columns
			val gridContainer = LinearLayout(this).apply {
				orientation = LinearLayout.VERTICAL
			}
			
			for (i in sortedPosts.indices step 2) {
				val rowLayout = LinearLayout(this).apply {
					orientation = LinearLayout.HORIZONTAL
					weightSum = 2f
				}
				
				// First column
				val itemView1 = inflater.inflate(R.layout.item_profile_post_grid, null, false)
				val layoutParams1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
				layoutParams1.setMargins(0, 0, 6, 0)
				itemView1.layoutParams = layoutParams1
				setupPostItem(itemView1, sortedPosts[i])
				rowLayout.addView(itemView1)
				
				// Second column (if exists)
				if (i + 1 < sortedPosts.size) {
					val itemView2 = inflater.inflate(R.layout.item_profile_post_grid, null, false)
					val layoutParams2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
					layoutParams2.setMargins(6, 0, 0, 0)
					itemView2.layoutParams = layoutParams2
					setupPostItem(itemView2, sortedPosts[i + 1])
					rowLayout.addView(itemView2)
				}
				
				gridContainer.addView(rowLayout)
			}
			
			postsContainer.addView(gridContainer)
		}
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
			
		// Load image with loading effect
			if (!post.imageUrl.isNullOrEmpty()) {
			Glide.with(this)
				.load(post.imageUrl)
				.centerCrop()
				.placeholder(R.drawable.ic_image_placeholder)
				.error(R.drawable.ic_image_placeholder)
				.into(ivImage)
			
			// Hide loading shimmer after image loads
			llLoadingShimmer.visibility = View.GONE
		} else {
			llLoadingShimmer.visibility = View.GONE
			ivImage.setImageResource(R.drawable.ic_image_placeholder)
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
	
	private fun updateProfileCompletion() {
		val user = auth.currentUser ?: return
		var completionScore = 0
		
		// Check profile fields
		if (tvUsername.text.toString() != "User" && tvUsername.text.toString().isNotEmpty()) completionScore += 20
		if (tvBio.text.toString() != "Bio not set" && tvBio.text.toString().isNotEmpty()) completionScore += 20
		if (tvLocation.text.toString() != "Location not set" && tvLocation.text.toString().isNotEmpty()) completionScore += 20
		if (tvContact.text.toString() != "Contact not set" && tvContact.text.toString().isNotEmpty()) completionScore += 20
		
		// Check if avatar is set
		if (avatarView.drawable != null && avatarView.drawable != ContextCompat.getDrawable(this, R.drawable.ic_profile)) {
			completionScore += 20
		}
		
		progressCompletion.progress = completionScore
		tvCompletionPercentage.text = "$completionScore%"
	}

	private fun showEditProfileDialog() {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
		val etBio = dialogView.findViewById<EditText>(R.id.et_bio)
		val etLocation = dialogView.findViewById<EditText>(R.id.et_location)
		val etContact = dialogView.findViewById<EditText>(R.id.et_contact)
		
		// Pre-fill current values
		etBio.setText(tvBio.text.toString().takeIf { it != "Bio not set" } ?: "")
		etLocation.setText(tvLocation.text.toString().takeIf { it != "Location not set" } ?: "")
		etContact.setText(tvContact.text.toString().takeIf { it != "Contact not set" } ?: "")
		
		AlertDialog.Builder(this)
			.setTitle("Edit Profile")
			.setView(dialogView)
			.setPositiveButton("Save") { _, _ ->
				saveProfileData(
					etBio.text.toString(),
					etLocation.text.toString(),
					etContact.text.toString()
				)
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	private fun saveProfileData(bio: String, location: String, contact: String) {
		val user = auth.currentUser ?: return
		
		val updates = mapOf(
			"bio" to bio,
			"location" to location,
			"contact" to contact
		)
		
		firestore.collection("users").document(user.uid)
			.update(updates)
			.addOnSuccessListener {
				// Update UI
				tvBio.text = bio.ifEmpty { "Bio not set" }
				tvLocation.text = location.ifEmpty { "Location not set" }
				tvContact.text = contact.ifEmpty { "Contact not set" }
				updateProfileCompletion()
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

	private fun setSortOption(isNewest: Boolean) {
		isSortNewest = isNewest
		
		if (isNewest) {
			btnSortNewest.background = ContextCompat.getDrawable(this, R.drawable.sort_button_active)
			btnSortNewest.setTextColor(ContextCompat.getColor(this, android.R.color.white))
			btnSortOldest.background = ContextCompat.getDrawable(this, R.drawable.sort_button_inactive)
			btnSortOldest.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
		} else {
			btnSortOldest.background = ContextCompat.getDrawable(this, R.drawable.sort_button_active)
			btnSortOldest.setTextColor(ContextCompat.getColor(this, android.R.color.white))
			btnSortNewest.background = ContextCompat.getDrawable(this, R.drawable.sort_button_inactive)
			btnSortNewest.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
		}
		
		loadPosts() // Reload posts with new sort order
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
		
		loadPosts() // Reload posts with new view
	}

	private fun showPostMenu(post: PostItem) {
		val options = arrayOf("Edit", "Mark as Sold", "Delete")
		
		AlertDialog.Builder(this)
			.setTitle("Post Options")
			.setItems(options) { _, which ->
				when (which) {
					0 -> editPost(post)
					1 -> markAsSold(post)
					2 -> showDeleteConfirmDialog(post.id)
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

	private fun markAsSold(post: PostItem) {
		AlertDialog.Builder(this)
			.setTitle("Mark as Sold")
			.setMessage("Are you sure you want to mark this item as SOLD?")
			.setPositiveButton("Yes") { _, _ ->
				val currentDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
				firestore.collection("posts").document(post.id)
					.update(mapOf(
						"status" to "SOLD",
						"dateSold" to currentDate
					))
					.addOnSuccessListener {
						Toast.makeText(this, "Item marked as sold", Toast.LENGTH_SHORT).show()
					}
			}
			.setNegativeButton("Cancel", null)
			.show()
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


