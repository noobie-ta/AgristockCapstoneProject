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
    private lateinit var tvFavoritedLabel: TextView
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
		btnSave = findViewById(R.id.btn_save)
        tvFavoritedLabel = findViewById(R.id.tv_favorited_label)

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

				if (!imageUrl.isNullOrEmpty()) {
					Glide.with(this).load(imageUrl).into(ivImage)
					currentImageUrl = imageUrl
				}
				tvItemName.text = title
				tvItemPrice.text = price
				tvCategoryBadge.text = category.uppercase()
				tvTimePosted.text = "Posted: $datePosted"
				tvDescription.text = description

				setupDescriptionToggle()
				loadSeller(sellerId)
				setupActions(postId, title)
			}
			.addOnFailureListener {
				Toast.makeText(this, "Failed to load item", Toast.LENGTH_SHORT).show()
				finish()
			}
	}

	private fun loadSeller(sellerId: String) {
		if (sellerId.isEmpty()) return
		firestore.collection("users").document(sellerId).get()
			.addOnSuccessListener { snap ->
				val name = snap.getString("firstName")?.plus(" ")?.plus(snap.getString("lastName") ?: "")?.trim()
				?: (snap.getString("displayName") ?: "Seller")
				val avatarUrl = snap.getString("avatarUrl")
				tvSellerName.text = name
				if (!avatarUrl.isNullOrEmpty()) {
					Glide.with(this).load(avatarUrl).into(ivSellerAvatar)
				}
				tvSellerName.setOnClickListener {
					startActivity(Intent(this, ProfileActivity::class.java).apply {
						putExtra("sellerId", sellerId)
					})
				}
			}
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
			// Placeholder: could open chat or dialer
			Toast.makeText(this, "Contact Seller", Toast.LENGTH_SHORT).show()
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
            tvFavoritedLabel.visibility = android.view.View.VISIBLE
        } else {
            btnSave.setImageResource(R.drawable.ic_favorite_border)
            btnSave.colorFilter = null
            tvFavoritedLabel.visibility = android.view.View.GONE
        }
    }
}
