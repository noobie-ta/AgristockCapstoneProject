package com.example.agristockcapstoneproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var tvSaleTradeBadge: TextView
    private lateinit var tvSoldBadge: TextView
    private lateinit var tvTradeDescription: TextView
    private lateinit var llTradeInfo: LinearLayout
    
    // Rating star views
    private lateinit var star1: ImageView
    private lateinit var star2: ImageView
    private lateinit var star3: ImageView
    private lateinit var star4: ImageView
    private lateinit var star5: ImageView
    
    private var currentImageUrl: String? = null
    private var currentPostId: String? = null
    private var currentPostTitle: String? = null
    private var currentPostImageUrl: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_item_details)

		// Configure status bar - transparent to show phone status
		com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

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
		tvSaleTradeBadge = findViewById(R.id.tv_sale_trade_badge)
		tvSoldBadge = findViewById(R.id.tv_sold_badge)
		tvTradeDescription = findViewById(R.id.tv_trade_description)
		llTradeInfo = findViewById(R.id.ll_trade_info)
		
		// Initialize star rating views
		star1 = findViewById(R.id.star1)
		star2 = findViewById(R.id.star2)
		star3 = findViewById(R.id.star3)
		star4 = findViewById(R.id.star4)
		star5 = findViewById(R.id.star5)

		val postId = intent.getStringExtra("postId")
		if (postId.isNullOrEmpty()) {
			Toast.makeText(this, "Invalid post", Toast.LENGTH_SHORT).show()
			finish()
			return
		}

		setupWindowInsets()
		loadPost(postId)
	}
	
	private fun setupWindowInsets() {
		val bottomActionBar = findViewById<View>(R.id.ll_bottom_action_bar)
		if (bottomActionBar != null) {
			ViewCompat.setOnApplyWindowInsetsListener(bottomActionBar) { view, insets ->
				val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
				view.setPadding(
					view.paddingLeft,
					view.paddingTop,
					view.paddingRight,
					view.paddingBottom + navigationBarHeight.coerceAtLeast(0)
				)
				insets
			}
		}
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
				val saleTradeType = doc.getString("saleTradeType") ?: "SALE"
				val tradeDescription = doc.getString("tradeDescription") ?: ""
				
				// Store post data for chat creation
				currentPostId = postId
				currentPostTitle = title
				currentPostImageUrl = imageUrl
				
				// Debug logging
				android.util.Log.d("ItemDetailsActivity", "Post data - sellerId: $sellerId, sellerName: $sellerName")
				android.util.Log.d("ItemDetailsActivity", "All post fields: ${doc.data?.keys}")

				if (!imageUrl.isNullOrEmpty()) {
					Glide.with(this).load(imageUrl).into(ivImage)
					currentImageUrl = imageUrl
					
					// Get image URLs for indicators
					val imageUrls = doc.get("imageUrls") as? List<String> ?: listOf(imageUrl)
					
					// Show image indicators if multiple images
					setupImageIndicators(imageUrls)
					
					// Make image clickable for image viewer
					ivImage.setOnClickListener {
						val imageUrlList = imageUrls.filter { it.isNotEmpty() }
						if (imageUrlList.isNotEmpty()) {
							ImageViewerActivity.start(this, imageUrlList, 0)
						}
					}
				}
				tvItemName.text = title
				tvItemPrice.text = formatPrice(price)
				tvCategoryBadge.text = category.uppercase()
				tvTimePosted.text = "Posted: $datePosted"
				tvDescription.text = description
				
				// Set sale/trade type
				val saleTradeText = when (saleTradeType.uppercase()) {
					"SALE" -> "SALE"
					"TRADE" -> "TRADE"
					"BOTH" -> "SALE/TRADE"
					else -> "SALE"
				}
				tvSaleTradeBadge.text = saleTradeText
				
				// Check if item is sold and show SOLD badge
				val status = doc.getString("status")?.uppercase() ?: ""
				if (status == "SOLD") {
					tvSoldBadge.visibility = View.VISIBLE
					// Optionally dim the image to indicate sold status
					ivImage.alpha = 0.6f
				} else {
					tvSoldBadge.visibility = View.GONE
					ivImage.alpha = 1.0f
				}
				
				// Show trade description if trade is selected
				if (saleTradeType.uppercase() == "TRADE" || saleTradeType.uppercase() == "BOTH") {
					if (tradeDescription.isNotEmpty()) {
						tvTradeDescription.text = tradeDescription
						llTradeInfo.visibility = View.VISIBLE
					} else {
						llTradeInfo.visibility = View.GONE
					}
				} else {
					llTradeInfo.visibility = View.GONE
				}

				// Display location
				val location = doc.getString("location") ?: "Location not specified"
				val address = doc.getString("address") ?: ""
				val displayLocation = if (address.isNotEmpty()) address else location
				findViewById<TextView>(R.id.tv_item_location).text = displayLocation

				setupDescriptionToggle()
				loadSeller(sellerId, sellerName)
				setupActions(postId, title, status)
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
					val displayText = if (totalRatings > 0 && rating > 0) {
						"$username (${String.format("%.1f", rating)}★)"
					} else {
						"$username (No ratings yet ⭐)"
					}
					tvSellerName.text = displayText
					
					// Update star rating display
					updateStarRatingDisplay(rating.toFloat(), totalRatings.toInt())
					
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
			android.util.Log.e("ItemDetailsActivity", "Error setting up image indicators: ${e.message}")
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

	private fun setupActions(postId: String, title: String, status: String = "") {
		// Disable Contact Seller button if item is SOLD
		if (status == "SOLD") {
			btnContactSeller.isEnabled = false
			btnContactSeller.alpha = 0.5f
			btnContactSeller.text = "SOLD - Not Available"
			btnContactSeller.setBackgroundColor(getColor(android.R.color.darker_gray))
		} else {
			btnContactSeller.setOnClickListener {
				contactSeller(postId)
			}
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
        
        // ✅ CHECK VERIFICATION: Users must be verified to message sellers
        firestore.collection("users").document(currentUserId)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { userDoc ->
                val verificationStatus = userDoc.getString("verificationStatus")?.trim()?.lowercase()
                
                // Debug logging
                android.util.Log.d("ItemDetailsActivity", "Checking verificationStatus: '$verificationStatus'")
                android.util.Log.d("ItemDetailsActivity", "Raw document data: ${userDoc.data}")
                
                if (verificationStatus != "approved") {
                    android.util.Log.d("ItemDetailsActivity", "⚠️ User is not verified, cannot contact seller")
                    showVerificationRequiredForMessaging(verificationStatus)
                    return@addOnSuccessListener
                }
                
                android.util.Log.d("ItemDetailsActivity", "✅ User is verified, proceeding with contact seller")
                
                // User is verified, proceed with getting post details
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
                            
                            // Check if either user has blocked the other (bidirectional)
                            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            if (currentUserId != null) {
                                com.example.agristockcapstoneproject.utils.BlockingUtils.checkIfBlocked(currentUserId, sellerId) { isBlocked ->
                                    if (isBlocked) {
                                        Toast.makeText(this, "You cannot message this seller. They have been blocked.", Toast.LENGTH_LONG).show()
                                        return@checkIfBlocked
                                    }
                                    
                                    // User not blocked, proceed with creating or finding chat
                                    createOrFindChat(sellerId)
                                }
                            } else {
                                createOrFindChat(sellerId)
                            }
                        } else {
                            Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load post details", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check verification status", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createOrFindChat(sellerId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val currentItemId = currentPostId ?: return
        
        // Check if chat already exists between these users for this specific item
        android.util.Log.d("ItemDetailsActivity", "Looking for existing chat - currentUserId: $currentUserId, sellerId: $sellerId, currentItemId: $currentItemId")
        
        // First, try to find an active chat (not deleted for current user)
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { chatsSnapshot ->
                android.util.Log.d("ItemDetailsActivity", "Found ${chatsSnapshot.documents.size} chats for current user")
                android.util.Log.d("ItemDetailsActivity", "DEBUG: All chats found:")
                chatsSnapshot.documents.forEach { doc ->
                    val participants = doc.get("participants") as? List<String>
                    val chatItemId = doc.getString("itemId")
                    val deletedFor = doc.get("deletedFor") as? List<String> ?: emptyList()
                    android.util.Log.d("ItemDetailsActivity", "  Chat ${doc.id}: participants=$participants, itemId=$chatItemId, deletedFor=$deletedFor")
                }
                
                // Look for existing chat with the seller for this specific item
                for (chatDoc in chatsSnapshot.documents) {
                    val participants = chatDoc.get("participants") as? List<String>
                    val chatItemId = chatDoc.getString("itemId")
                    val isHiddenFor = chatDoc.get("isHiddenFor") as? Map<String, Boolean> ?: emptyMap()
                    val resetHistoryFor = chatDoc.get("resetHistoryFor") as? Map<String, Boolean> ?: emptyMap()
                    
                    android.util.Log.d("ItemDetailsActivity", "Checking chat ${chatDoc.id} - participants: $participants, chatItemId: $chatItemId")
                    android.util.Log.d("ItemDetailsActivity", "  - isHiddenFor: $isHiddenFor")
                    android.util.Log.d("ItemDetailsActivity", "  - resetHistoryFor: $resetHistoryFor")
                    
                    if (participants != null && participants.contains(sellerId) && chatItemId == currentItemId) {
                        android.util.Log.d("ItemDetailsActivity", "MATCH FOUND: Chat ${chatDoc.id} matches seller and item")
                        
                        // Facebook Messenger style: Use existing chat regardless of hidden status
                        // Chat will auto-unhide when new message is sent
                        android.util.Log.d("ItemDetailsActivity", "DECISION: Using existing chat (Messenger style)")
                        navigateToChat(chatDoc.id, sellerId)
                        return@addOnSuccessListener
                    }
                }
                
                // If we reach here, no chat was found at all - create a new one
                android.util.Log.d("ItemDetailsActivity", "No existing chat found, creating new chat")
                createNewChat(sellerId)
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
                
                // Get item information for the chat
                val itemTitle = currentPostTitle ?: "Item"
                val itemId = currentPostId ?: ""
                val itemImageUrl = currentPostImageUrl
                
                // Create new chat document with new data model
                val chatData = hashMapOf(
                    "participants" to listOf(currentUserId, sellerId),
                    "lastMessage" to "",
                    "lastMessageTime" to System.currentTimeMillis(),
                    "lastMessageSender" to "",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "unreadCount_$currentUserId" to 0,
                    "unreadCount_$sellerId" to 0,
                    "itemTitle" to itemTitle,
                    "itemId" to itemId,
                    "itemImageUrl" to itemImageUrl,
                    "isHiddenFor" to mapOf(
                        currentUserId to false,
                        sellerId to false
                    ),
                    "resetHistoryFor" to mapOf(
                        currentUserId to false,
                        sellerId to false
                    ),
                    "resetTimestamp" to mapOf(
                        currentUserId to 0L,
                        sellerId to 0L
                    )
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
                val itemTitle = currentPostTitle ?: "Item"
                val itemId = currentPostId ?: ""
                val itemImageUrl = currentPostImageUrl
                
                val chatData = hashMapOf(
                    "participants" to listOf(currentUserId, sellerId),
                    "lastMessage" to "",
                    "lastMessageTime" to System.currentTimeMillis(),
                    "lastMessageSender" to "",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "unreadCount_$currentUserId" to 0,
                    "unreadCount_$sellerId" to 0,
                    "itemTitle" to itemTitle,
                    "itemId" to itemId,
                    "itemImageUrl" to itemImageUrl,
                    "isHiddenFor" to mapOf(
                        currentUserId to false,
                        sellerId to false
                    ),
                    "resetHistoryFor" to mapOf(
                        currentUserId to false,
                        sellerId to false
                    ),
                    "resetTimestamp" to mapOf(
                        currentUserId to 0L,
                        sellerId to 0L
                    )
                )
                
                firestore.collection("chats")
                    .add(chatData)
                    .addOnSuccessListener { chatDoc ->
                        // Load seller data before navigating to ensure we have sellerName and sellerAvatar
                        loadSellerDataForChat(chatDoc.id, sellerId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to create chat", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    
    private fun loadSellerDataForChat(chatId: String, sellerId: String) {
        try {
            firestore.collection("users").document(sellerId)
                .get()
                .addOnSuccessListener { userDoc ->
                    try {
                        val sellerName = if (userDoc.exists()) {
                            userDoc.getString("username") ?: 
                            "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                                .ifEmpty { userDoc.getString("displayName") ?: "User" }
                        } else {
                            "User"
                        }
                        
                        val sellerAvatar = userDoc.getString("avatarUrl")
                        
                        android.util.Log.d("ItemDetailsActivity", "Loaded seller data for chat - sellerName: $sellerName, sellerAvatar: $sellerAvatar")
                        
                        // Navigate to chat with complete seller data
                        navigateToChat(chatId, sellerId, sellerName, sellerAvatar)
                    } catch (e: Exception) {
                        android.util.Log.e("ItemDetailsActivity", "Error processing seller data for chat: ${e.message}")
                        // Navigate with minimal data as fallback
                        navigateToChat(chatId, sellerId, "User", null)
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ItemDetailsActivity", "Error loading seller data for chat: ${exception.message}")
                    // Navigate with minimal data as fallback
                    navigateToChat(chatId, sellerId, "User", null)
                }
        } catch (e: Exception) {
            android.util.Log.e("ItemDetailsActivity", "Error in loadSellerDataForChat: ${e.message}")
            // Navigate with minimal data as fallback
            navigateToChat(chatId, sellerId, "User", null)
        }
    }
    
    private fun navigateToChat(chatId: String, sellerId: String, sellerName: String = "", sellerAvatar: String? = null) {
        val intent = Intent(this, ChatRoomActivity::class.java)
        intent.putExtra("chatId", chatId)
        intent.putExtra("otherUserId", sellerId)
        intent.putExtra("otherUserName", sellerName)
        intent.putExtra("otherUserAvatar", sellerAvatar)
        intent.putExtra("item_id", currentPostId)
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
    
    // ✅ VERIFICATION CHECK FUNCTION FOR MESSAGING
    private fun showVerificationRequiredForMessaging(verificationStatus: String?) {
        val message = when (verificationStatus) {
            "pending" -> "Your verification request is being reviewed by our team. You'll be able to message sellers once your account is verified.\n\nThank you for your patience!"
            "rejected" -> "Your previous verification was rejected. Please submit a new verification request with valid documents to message sellers."
            else -> "You must verify your account before messaging sellers. This helps prevent spam and maintains trust in our community.\n\nWould you like to verify your account now?"
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
