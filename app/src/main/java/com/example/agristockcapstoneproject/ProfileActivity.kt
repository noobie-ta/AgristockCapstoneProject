package com.example.agristockcapstoneproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : AppCompatActivity() {

	data class PostItem(
		val id: String,
		val name: String,
		val price: String,
		val description: String,
		val imageUrl: String?,
		val status: String, // FOR SALE or BIDDING or SOLD
	)

	private lateinit var postsContainer: LinearLayout
	private lateinit var avatarView: ImageView
	private lateinit var uploadIcon: ImageView
	private lateinit var tabPosts: TextView
	private lateinit var tabFavorites: TextView
	private lateinit var tabActivity: TextView
	private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
	private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
	private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
	private var currentTab = "posts"

	private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		uri?.let { selectedUri ->
			// Open image cropper
			openImageCropper(selectedUri)
		}
	}

	private val imageCropperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			val croppedUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra("croppedImageUri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>("croppedImageUri")
            }
			croppedUri?.let { uri ->
				showUploadConfirmationDialog(uri)
			}
		}
	}

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

		postsContainer = findViewById(R.id.ll_posts_container)
		avatarView = findViewById(R.id.iv_avatar)
		uploadIcon = findViewById(R.id.iv_upload)
		tabPosts = findViewById(R.id.tab_posts)
		tabFavorites = findViewById(R.id.tab_favorites)
		tabActivity = findViewById(R.id.tab_activity)
		
		findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

		// Setup tab click listeners
		setupTabs()

		loadExistingAvatar()

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

		loadPosts()
	}

	private fun setupTabs() {
		tabPosts.setOnClickListener { setActiveTab("posts") }
		tabFavorites.setOnClickListener { setActiveTab("favorites") }
		tabActivity.setOnClickListener { setActiveTab("activity") }
		setActiveTab("posts") // Set default tab
	}

	private fun setActiveTab(tab: String) {
		currentTab = tab
		
		// Reset all tabs
		tabPosts.setBackgroundResource(R.drawable.tab_pill_inactive)
        tabPosts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
		tabFavorites.setBackgroundResource(R.drawable.tab_pill_inactive)
        tabFavorites.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
		tabActivity.setBackgroundResource(R.drawable.tab_pill_inactive)
        tabActivity.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
		
		// Set active tab
		when (tab) {
			"posts" -> {
				tabPosts.setBackgroundResource(R.drawable.tab_pill_active)
                tabPosts.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
				loadPosts()
			}
			"favorites" -> {
				tabFavorites.setBackgroundResource(R.drawable.tab_pill_active)
                tabFavorites.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
				// TODO: Load favorites
				postsContainer.removeAllViews()
				val placeholder = TextView(this).apply {
					text = "Favorites coming soon!"
					textSize = 16f
					setTextColor(resources.getColor(R.color.text_secondary, null))
					gravity = android.view.Gravity.CENTER
					setPadding(0, 100, 0, 0)
				}
				postsContainer.addView(placeholder)
			}
			"activity" -> {
				tabActivity.setBackgroundResource(R.drawable.tab_pill_active)
                tabActivity.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
				// TODO: Load activity
				postsContainer.removeAllViews()
				val placeholder = TextView(this).apply {
					text = "Activity coming soon!"
					textSize = 16f
					setTextColor(resources.getColor(R.color.text_secondary, null))
					gravity = android.view.Gravity.CENTER
					setPadding(0, 100, 0, 0)
				}
				postsContainer.addView(placeholder)
			}
		}
	}

	private fun loadPosts() {
		val user = auth.currentUser
		postsContainer.removeAllViews()
		if (user == null) {
			renderPosts(emptyList())
			return
		}
		firestore.collection("posts")
			.whereEqualTo("userId", user.uid)
			.addSnapshotListener { snapshots, exception ->
				if (exception != null) {
					android.widget.Toast.makeText(
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
						)
					}
					// Filter out SOLD items client-side to avoid Firestore '!=' constraints
					?.filter { it.status.uppercase() != "SOLD" }
					?: emptyList()

				renderPosts(list)
			}
	}

	private fun loadExistingAvatar() {
		val user = auth.currentUser ?: return
		firestore.collection("users").document(user.uid).get()
			.addOnSuccessListener { snap ->
				val url = snap.getString("avatarUrl")
				if (!url.isNullOrEmpty()) {
					Glide.with(this).load(url).into(avatarView)
				}
			}
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
		posts.forEach { post ->
			val itemView = inflater.inflate(R.layout.item_profile_post, postsContainer, false)

			val tvName = itemView.findViewById<TextView>(R.id.tv_post_name)
			val tvPrice = itemView.findViewById<TextView>(R.id.tv_post_price)
			val tvBadge = itemView.findViewById<TextView>(R.id.tv_status_badge)
			val btnEdit = itemView.findViewById<TextView>(R.id.btn_edit)
			val btnSold = itemView.findViewById<TextView>(R.id.btn_mark_sold)
			val btnDelete = itemView.findViewById<ImageView>(R.id.btn_delete)
			val ivImage = itemView.findViewById<ImageView>(R.id.iv_post_image)

			tvName.text = post.name
			tvPrice.text = post.price
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
			
			if (!post.imageUrl.isNullOrEmpty()) {
				Glide.with(this).load(post.imageUrl).into(ivImage)
			}

			btnEdit.setOnClickListener {
				val intent = android.content.Intent(this, EditPostActivity::class.java)
				intent.putExtra("postId", post.id)
				intent.putExtra("title", post.name)
				intent.putExtra("price", post.price)
				intent.putExtra("description", post.description)
				intent.putExtra("imageUrl", post.imageUrl ?: "")
				editPostLauncher.launch(intent)
			}
			btnSold.setOnClickListener {
			androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle("Mark as Sold")
				.setMessage("Are you sure you want to mark this item as SOLD?")
				.setPositiveButton("Yes") { _, _ ->
					val currentDate = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
					firestore.collection("posts").document(post.id)
						.update(mapOf(
							"status" to "SOLD",
							"dateSold" to currentDate
						))
				}
				.setNegativeButton("Cancel", null)
				.show()
			}
			btnDelete.setOnClickListener {
				showDeleteConfirmDialog(post.id)
			}

			postsContainer.addView(itemView)
		}
	}

	private fun showDeleteConfirmDialog(postId: String) {
		val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
		val dialog = AlertDialog.Builder(this)
			.setView(dialogView)
			.setCancelable(true)
			.create()

		dialogView.findViewById<TextView>(R.id.btn_confirm).setOnClickListener {
			firestore.collection("posts").document(postId).delete()
			dialog.dismiss()
		}
		dialogView.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
			dialog.dismiss()
		}

		dialog.show()
	}

	private fun openImageCropper(imageUri: Uri) {
		val intent = Intent(this, ImageCropperActivity::class.java).apply {
			putExtra("imageUri", imageUri)
		}
		imageCropperLauncher.launch(intent)
	}

	private fun openImageViewer(imageUri: Uri) {
		val intent = Intent(this, ImageViewerActivity::class.java).apply {
			putExtra("imageUri", imageUri)
		}
		imageViewerLauncher.launch(intent)
	}

	private fun showUploadConfirmationDialog(imageUri: Uri) {
		AlertDialog.Builder(this)
			.setTitle("Upload Profile Picture")
			.setMessage("Do you want to upload this image as your profile picture?")
			.setPositiveButton("Upload") { _, _ ->
				// Show the cropped image immediately
				Glide.with(this).load(imageUri).into(avatarView)
				uploadAvatarAndSave(imageUri)
			}
			.setNegativeButton("Cancel", null)
			.show()
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
				android.widget.Toast.makeText(this, "Profile picture deleted", android.widget.Toast.LENGTH_SHORT).show()
			}
			.addOnFailureListener { exception ->
				android.widget.Toast.makeText(this, "Failed to delete profile picture: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
			}
	}
}


