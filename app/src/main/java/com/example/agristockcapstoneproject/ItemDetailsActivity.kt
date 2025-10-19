package com.example.agristockcapstoneproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemDetailsActivity : AppCompatActivity() {

	private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
	private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

	private lateinit var ivImage: ImageView
	private lateinit var ivSellerAvatar: ImageView
	private lateinit var tvSellerName: TextView
	private lateinit var tvItemName: TextView
	private lateinit var tvItemPrice: TextView
	private lateinit var tvCategoryBadge: TextView
	private lateinit var tvTimePosted: TextView
	private lateinit var tvDescription: TextView
	private lateinit var btnToggleDescription: TextView
	private lateinit var btnContactSeller: TextView
	private lateinit var btnSave: ImageView
    private var currentImageUrl: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_item_details)

		findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

		ivImage = findViewById(R.id.iv_item_image)
		ivSellerAvatar = findViewById(R.id.iv_seller_avatar)
		tvSellerName = findViewById(R.id.tv_seller_name)
		tvItemName = findViewById(R.id.tv_item_name)
		tvItemPrice = findViewById(R.id.tv_item_price)
		tvCategoryBadge = findViewById(R.id.tv_category_badge)
		tvTimePosted = findViewById(R.id.tv_time_posted)
		tvDescription = findViewById(R.id.tv_description)
		btnToggleDescription = findViewById(R.id.btn_toggle_description)
		btnContactSeller = findViewById(R.id.btn_contact_seller)
		btnSave = findViewById(R.id.btn_favorite)

		val postId = intent.getStringExtra("postId")
		if (postId.isNullOrEmpty()) {
			Toast.makeText(this, "Invalid post", Toast.LENGTH_SHORT).show()
			finish()
			return
		}

		loadPost(postId)
	}

	override fun onResume() {
		super.onResume()
		// Refresh favorite status when returning to the activity
		val postId = intent.getStringExtra("postId")
		if (!postId.isNullOrEmpty()) {
			checkFavoriteStatus(postId)
		}
	}

	private fun loadPost(postId: String) {
		firestore.collection("posts").document(postId).get()
			.addOnSuccessListener { doc ->
				if (!doc.exists()) {
					Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show()
					finish()
					return@addOnSuccessListener
				}
				val title = doc.getString("title") ?: ""
				val price = doc.getString("price") ?: ""
				val category = doc.getString("category") ?: "OTHER"
				val datePosted = doc.getString("datePosted") ?: ""
				val description = doc.getString("description") ?: ""
				val imageUrl = doc.getString("imageUrl")
				val sellerId = doc.getString("userId") ?: ""
				val sellerName = doc.getString("sellerName") ?: ""
				
				// Debug logging
				android.util.Log.d("ItemDetailsActivity", "Post data - sellerId: $sellerId, sellerName: $sellerName")
				android.util.Log.d("ItemDetailsActivity", "All post fields: ${doc.data?.keys}")

				if (!imageUrl.isNullOrEmpty()) {
					Glide.with(this).load(imageUrl).into(ivImage)
					currentImageUrl = imageUrl
				}
				tvItemName.text = title
				tvItemPrice.text = formatPrice(price)
				tvCategoryBadge.text = category.uppercase()
				tvTimePosted.text = "Posted: $datePosted"
				tvDescription.text = description

				setupDescriptionToggle()
				loadSeller(sellerId, sellerName)
				setupActions(postId, title)
			}
			.addOnFailureListener {
				Toast.makeText(this, "Failed to load item", Toast.LENGTH_SHORT).show()
				finish()
			}
	}

	private fun loadSeller(sellerId: String, sellerName: String = "") {
		// Debug logging
		android.util.Log.d("ItemDetailsActivity", "loadSeller called with sellerId: $sellerId, sellerName: $sellerName")
		
		if (sellerId.isEmpty()) {
			android.util.Log.d("ItemDetailsActivity", "sellerId is empty, showing Unknown Seller")
			tvSellerName.text = "Unknown Seller"
			ivSellerAvatar.setImageResource(R.drawable.ic_profile)
			return
		}
		
		// Always try to load the user's profile first for the most up-to-date information
		android.util.Log.d("ItemDetailsActivity", "Loading seller profile for sellerId: $sellerId")
		loadSellerProfile(sellerId, sellerName)
	}
	
	private fun loadSellerProfile(sellerId: String, fallbackName: String = "") {
		firestore.collection("users").document(sellerId).get()
			.addOnSuccessListener { snap ->
				if (snap.exists()) {
					android.util.Log.d("ItemDetailsActivity", "User document found: ${snap.data}")
					
					// Get username (preferred) or fallback to name
					val username = snap.getString("username") ?: 
						snap.getString("firstName")?.plus(" ")?.plus(snap.getString("lastName") ?: "")?.trim()
						?.ifEmpty { snap.getString("displayName") ?: "User" }
						?: "User"
					
					val avatarUrl = snap.getString("avatarUrl")
					val rating = snap.getDouble("rating") ?: 0.0
					val totalRatings = snap.getLong("totalRatings") ?: 0L
					
					android.util.Log.d("ItemDetailsActivity", "User data - username: $username, avatarUrl: $avatarUrl, rating: $rating, totalRatings: $totalRatings")
					
					// Display username with rating (show rating if there are actual ratings)
					val displayText = if (totalRatings > 0) {
						"$username (${String.format("%.1f", rating)}★)"
					} else {
						username
					}
					tvSellerName.text = displayText
					
					// Load profile picture with circular background
					if (!avatarUrl.isNullOrEmpty()) {
						android.util.Log.d("ItemDetailsActivity", "Loading avatar from URL: $avatarUrl")
						Glide.with(this)
							.load(avatarUrl)
							.circleCrop()
							.placeholder(R.drawable.ic_profile)
							.error(R.drawable.ic_profile)
							.into(ivSellerAvatar)
					} else {
						android.util.Log.d("ItemDetailsActivity", "No avatar URL found, using default")
						ivSellerAvatar.setImageResource(R.drawable.ic_profile)
					}
					
					// Set up click listener to view seller profile
					tvSellerName.setOnClickListener {
						android.util.Log.d("ItemDetailsActivity", "Seller name clicked, navigating to profile for sellerId: $sellerId")
						startActivity(Intent(this, ViewUserProfileActivity::class.java).apply {
							putExtra("sellerId", sellerId)
						})
					}
					
					// Also make avatar clickable
					ivSellerAvatar.setOnClickListener {
						android.util.Log.d("ItemDetailsActivity", "Seller avatar clicked, navigating to profile for sellerId: $sellerId")
						startActivity(Intent(this, ViewUserProfileActivity::class.java).apply {
							putExtra("sellerId", sellerId)
						})
					}
				} else {
					// User document doesn't exist
					tvSellerName.text = "Unknown Seller"
					ivSellerAvatar.setImageResource(R.drawable.ic_profile)
					
					// Set up click listeners even when user document doesn't exist
					if (sellerId.isNotEmpty()) {
						tvSellerName.setOnClickListener {
							startActivity(Intent(this, ViewUserProfileActivity::class.java).apply {
								putExtra("sellerId", sellerId)
							})
						}
						
						ivSellerAvatar.setOnClickListener {
							startActivity(Intent(this, ViewUserProfileActivity::class.java).apply {
								putExtra("sellerId", sellerId)
							})
						}
					}
				}
			}
			.addOnFailureListener { exception ->
				// Fallback if seller data can't be loaded
				android.util.Log.e("ItemDetailsActivity", "Failed to load seller: ${exception.message}")
				if (fallbackName.isNotEmpty()) {
					tvSellerName.text = fallbackName
				} else {
					tvSellerName.text = "Unknown Seller"
				}
				ivSellerAvatar.setImageResource(R.drawable.ic_profile)
				
				// Set up click listeners even in fallback case
				if (sellerId.isNotEmpty()) {
					tvSellerName.setOnClickListener {
						startActivity(Intent(this, ViewUserProfileActivity::class.java).apply {
							putExtra("sellerId", sellerId)
						})
					}
					
					ivSellerAvatar.setOnClickListener {
						startActivity(Intent(this, ViewUserProfileActivity::class.java).apply {
							putExtra("sellerId", sellerId)
						})
					}
				}
			}
	}

	private fun formatPrice(price: String): String {
		if (price.startsWith("₱") || price.startsWith("PHP")) {
			return price
		}
		return "₱$price"
	}

	private fun setupDescriptionToggle() {
		var expanded = false
		btnToggleDescription.setOnClickListener {
			expanded = !expanded
			if (expanded) {
				tvDescription.maxLines = Integer.MAX_VALUE
				btnToggleDescription.text = "Read less"
			} else {
				tvDescription.maxLines = 3
				btnToggleDescription.text = "Read more"
			}
		}
	}

	private fun setupActions(postId: String, title: String) {
		btnContactSeller.setOnClickListener {
			contactSeller(postId)
		}

		// Initialize favorite state from Firestore
		checkFavoriteStatus(postId)

        btnSave.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Please log in to save favorites", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Disable button during operation to prevent multiple clicks
            btnSave.isEnabled = false
            
            // Heart pop animation
            val anim = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                btnSave,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f, 1f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f, 1f)
            ).apply { duration = 180; interpolator = android.view.animation.OvershootInterpolator() }
            anim.start()

            val userFavRef = firestore.collection("users").document(uid).collection("favorites").document(postId)
            val postRef = firestore.collection("posts").document(postId)
            
            // First check current favorite status
            userFavRef.get().addOnSuccessListener { favDoc ->
                val isCurrentlyFav = favDoc.exists()
                
                if (isCurrentlyFav) {
                    // Remove from favorites
                    userFavRef.delete()
                        .addOnSuccessListener {
                            // Update post favorite count
                            postRef.update("favoriteCount", com.google.firebase.firestore.FieldValue.increment(-1))
                                .addOnSuccessListener {
                                    updateFavoriteUI(false)
                                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                    btnSave.isEnabled = true
                                }
                                .addOnFailureListener { exception ->
                                    Toast.makeText(this, "Failed to update count: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    btnSave.isEnabled = true
                                }
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Failed to remove favorite: ${exception.message}", Toast.LENGTH_SHORT).show()
                            btnSave.isEnabled = true
                        }
                } else {
                    // Add to favorites
                    val data = hashMapOf(
                        "postId" to postId,
                        "title" to tvItemName.text.toString(),
                        "price" to tvItemPrice.text.toString(),
                        "imageUrl" to (currentImageUrl ?: ""),
                        "date" to tvTimePosted.text.toString(),
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    
                    userFavRef.set(data)
                        .addOnSuccessListener {
                            // Update post favorite count
                            postRef.update("favoriteCount", com.google.firebase.firestore.FieldValue.increment(1))
                                .addOnSuccessListener {
                                    updateFavoriteUI(true)
                                    Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show()
                                    btnSave.isEnabled = true
                                    
                                    // Create notification for the post owner
                                    createFavoriteNotification(postId)
                                }
                                .addOnFailureListener { exception ->
                                    Toast.makeText(this, "Failed to update count: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    btnSave.isEnabled = true
                                }
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Failed to add favorite: ${exception.message}", Toast.LENGTH_SHORT).show()
                            btnSave.isEnabled = true
                        }
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to check favorite status: ${exception.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
            }
        }
	}

    private fun checkFavoriteStatus(postId: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            updateFavoriteUI(false)
            return
        }
        
        firestore.collection("users").document(uid)
            .collection("favorites").document(postId)
            .get()
            .addOnSuccessListener { doc ->
                val isFavorited = doc.exists()
                updateFavoriteUI(isFavorited)
            }
            .addOnFailureListener {
                updateFavoriteUI(false)
            }
    }

    private fun updateFavoriteUI(isFavorited: Boolean) {
        if (isFavorited) {
            btnSave.setImageResource(R.drawable.ic_favorite_filled_red)
            btnSave.clearColorFilter()
        } else {
            btnSave.setImageResource(R.drawable.ic_favorite_border)
            btnSave.colorFilter = null
        }
    }
    
    private fun contactSeller(postId: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to contact seller", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get post details to find the seller
        firestore.collection("posts").document(postId)
            .get()
            .addOnSuccessListener { postDoc ->
                if (postDoc.exists()) {
                    val sellerId = postDoc.getString("userId")
                    if (sellerId.isNullOrEmpty()) {
                        Toast.makeText(this, "Seller information not available", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    
                    // Check if user is trying to contact themselves
                    if (sellerId == currentUserId) {
                        btnContactSeller.isEnabled = false
                        btnContactSeller.alpha = 0.5f
                        Toast.makeText(this, "You can't message yourself", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    
                    // Create or find chat with seller
                    createOrFindChat(sellerId)
                } else {
                    Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load post details", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createOrFindChat(sellerId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Check if chat already exists between these users
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { chatsSnapshot ->
                var existingChatId: String? = null
                
                // Look for existing chat with the seller
                for (chatDoc in chatsSnapshot.documents) {
                    val participants = chatDoc.get("participants") as? List<String>
                    if (participants != null && participants.contains(sellerId)) {
                        existingChatId = chatDoc.id
                        break
                    }
                }
                
                if (existingChatId != null) {
                    // Chat exists, navigate to it
                    navigateToChat(existingChatId, sellerId)
                } else {
                    // Create new chat
                    createNewChat(sellerId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check existing chats", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createNewChat(sellerId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Get seller's information for the chat
        firestore.collection("users").document(sellerId)
            .get()
            .addOnSuccessListener { sellerDoc ->
                val sellerName = if (sellerDoc.exists()) {
                    sellerDoc.getString("username") ?: 
                    "${sellerDoc.getString("firstName") ?: ""} ${sellerDoc.getString("lastName") ?: ""}".trim()
                        .ifEmpty { sellerDoc.getString("displayName") ?: "User" }
                } else {
                    "User"
                }
                
                val sellerAvatar = sellerDoc.getString("avatarUrl")
                
                // Create new chat document
                val chatData = hashMapOf(
                    "participants" to listOf(currentUserId, sellerId),
                    "lastMessage" to "",
                    "lastMessageTime" to System.currentTimeMillis(),
                    "lastMessageSender" to "",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "unreadCount_$currentUserId" to 0,
                    "unreadCount_$sellerId" to 0
                )
                
                firestore.collection("chats")
                    .add(chatData)
                    .addOnSuccessListener { chatDoc ->
                        navigateToChat(chatDoc.id, sellerId, sellerName, sellerAvatar)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create chat", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                // Create chat with minimal info if seller data can't be loaded
                val chatData = hashMapOf(
                    "participants" to listOf(currentUserId, sellerId),
                    "lastMessage" to "",
                    "lastMessageTime" to System.currentTimeMillis(),
                    "lastMessageSender" to "",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "unreadCount_$currentUserId" to 0,
                    "unreadCount_$sellerId" to 0
                )
                
                firestore.collection("chats")
                    .add(chatData)
                    .addOnSuccessListener { chatDoc ->
                        navigateToChat(chatDoc.id, sellerId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create chat", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    
    private fun navigateToChat(chatId: String, sellerId: String, sellerName: String = "", sellerAvatar: String? = null) {
        val intent = Intent(this, ChatRoomActivity::class.java)
        intent.putExtra("chatId", chatId)
        intent.putExtra("otherUserId", sellerId)
        intent.putExtra("otherUserName", sellerName)
        intent.putExtra("otherUserAvatar", sellerAvatar)
        startActivity(intent)
    }

    private fun createFavoriteNotification(postId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Get post details to find the owner
        firestore.collection("posts").document(postId)
            .get()
            .addOnSuccessListener { postDoc ->
                if (postDoc.exists()) {
                    val postOwnerId = postDoc.getString("userId")
                    val postTitle = postDoc.getString("name") ?: ""
                    val postPrice = postDoc.getString("price") ?: ""
                    val postImageUrl = postDoc.getString("imageUrl") ?: 
                        (postDoc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString()
                    
                    // Don't create notification for own posts
                    if (postOwnerId != null && postOwnerId != currentUserId) {
                        // Get current user details
                        firestore.collection("users").document(currentUserId)
                            .get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    val currentUsername = userDoc.getString("username") ?: "Unknown User"
                                    val currentUserAvatar = userDoc.getString("avatarUrl")
                                    
                                    // Check if notification already exists to prevent duplicates
                                    firestore.collection("notifications")
                                        .whereEqualTo("toUserId", postOwnerId)
                                        .whereEqualTo("fromUserId", currentUserId)
                                        .whereEqualTo("postId", postId)
                                        .whereEqualTo("type", "favorite_added")
                                        .get()
                                        .addOnSuccessListener { existingNotifications ->
                                            if (existingNotifications.isEmpty) {
                                                // Create new notification
                                                val notificationData = hashMapOf(
                                                    "type" to "favorite_added",
                                                    "toUserId" to postOwnerId,
                                                    "fromUserId" to currentUserId,
                                                    "fromUsername" to currentUsername,
                                                    "fromUserAvatar" to currentUserAvatar,
                                                    "postId" to postId,
                                                    "postTitle" to postTitle,
                                                    "postPrice" to postPrice,
                                                    "postImageUrl" to postImageUrl,
                                                    "timestamp" to com.google.firebase.Timestamp.now(),
                                                    "isRead" to false,
                                                    "isPostAvailable" to true
                                                )
                                                
                                                firestore.collection("notifications")
                                                    .add(notificationData)
                                                    .addOnFailureListener { exception ->
                                                        // Handle error silently
                                                    }
                                            }
                                        }
                                }
                            }
                    }
                }
            }
    }
}
