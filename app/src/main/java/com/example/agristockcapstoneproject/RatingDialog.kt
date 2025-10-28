package com.example.agristockcapstoneproject

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class RatingDialog(
    context: Context,
    private val sellerId: String,
    private val sellerName: String,
    private val postId: String,
    private val onRatingSubmitted: (Float) -> Unit
) : Dialog(context) {

    private lateinit var star1: ImageView
    private lateinit var star2: ImageView
    private lateinit var star3: ImageView
    private lateinit var star4: ImageView
    private lateinit var star5: ImageView
    private lateinit var tvRatingText: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    
    private var selectedRating = 0f
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_rating)
        
        initializeViews()
        setupClickListeners()
        updateStarDisplay()
    }

    private fun initializeViews() {
        star1 = findViewById(R.id.star1)
        star2 = findViewById(R.id.star2)
        star3 = findViewById(R.id.star3)
        star4 = findViewById(R.id.star4)
        star5 = findViewById(R.id.star5)
        tvRatingText = findViewById(R.id.tv_rating_text)
        btnSubmit = findViewById(R.id.btn_submit_rating)
        btnCancel = findViewById(R.id.btn_cancel_rating)
        
        findViewById<TextView>(R.id.tv_seller_name).text = "Rate your transaction with $sellerName"
    }

    private fun setupClickListeners() {
        val stars = listOf(star1, star2, star3, star4, star5)
        
        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedRating = (index + 1).toFloat()
                updateStarDisplay()
            }
        }
        
        btnSubmit.setOnClickListener {
            if (selectedRating > 0) {
                submitRating()
            } else {
                Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun updateStarDisplay() {
        val stars = listOf(star1, star2, star3, star4, star5)
        
        stars.forEachIndexed { index, star ->
            if (index < selectedRating) {
                star.setImageResource(R.drawable.ic_star_filled)
                star.setColorFilter(android.graphics.Color.parseColor("#FFD700"))
            } else {
                star.setImageResource(R.drawable.ic_star_filled)
                star.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"))
            }
        }
        
        tvRatingText.text = when {
            selectedRating == 0f -> "Select a rating"
            selectedRating == 1f -> "Poor"
            selectedRating == 2f -> "Fair"
            selectedRating == 3f -> "Good"
            selectedRating == 4f -> "Very Good"
            selectedRating == 5f -> "Excellent"
            else -> "Select a rating"
        }
    }

    private fun submitRating() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "You must be logged in to rate", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if user has already rated this seller for this post
        firestore.collection("ratings")
            .whereEqualTo("buyerId", currentUser.uid)
            .whereEqualTo("sellerId", sellerId)
            .whereEqualTo("postId", postId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // User hasn't rated yet, proceed with rating
                    saveRating(currentUser.uid)
                } else {
                    Toast.makeText(context, "You have already rated this seller for this transaction", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error checking existing rating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveRating(buyerId: String) {
        val ratingData = hashMapOf(
            "buyerId" to buyerId,
            "sellerId" to sellerId,
            "postId" to postId,
            "rating" to selectedRating,
            "timestamp" to Date(),
            "sellerName" to sellerName
        )

        firestore.collection("ratings")
            .add(ratingData)
            .addOnSuccessListener { ratingDoc ->
                // Update seller's average rating
                updateSellerRating()
                
                // Record transaction
                recordTransaction(buyerId, ratingDoc.id)
                
                Toast.makeText(context, "Rating submitted successfully", Toast.LENGTH_SHORT).show()
                onRatingSubmitted(selectedRating)
                dismiss()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RatingDialog", "Failed to submit rating: ${e.message}")
                // Show retry dialog
                AlertDialog.Builder(context)
                    .setTitle("Connection Error")
                    .setMessage("Failed to submit rating. Please check your internet connection and try again.")
                    .setPositiveButton("Retry") { _, _ ->
                        saveRating(buyerId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
    }

    private fun updateSellerRating() {
        firestore.collection("ratings")
            .whereEqualTo("sellerId", sellerId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) return@addOnSuccessListener
                
                val ratings = documents.mapNotNull { it.getDouble("rating") }
                val averageRating = ratings.average().toFloat()
                val totalRatings = ratings.size
                
                firestore.collection("users").document(sellerId)
                    .update(
                        "rating", averageRating,
                        "totalRatings", totalRatings
                    )
                    .addOnFailureListener { e ->
                        android.util.Log.e("RatingDialog", "Failed to update seller rating: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RatingDialog", "Failed to fetch ratings: ${e.message}")
            }
    }

    private fun recordTransaction(buyerId: String, ratingId: String) {
        val transactionData = hashMapOf(
            "buyerId" to buyerId,
            "sellerId" to sellerId,
            "postId" to postId,
            "ratingId" to ratingId,
            "rating" to selectedRating,
            "timestamp" to Date(),
            "status" to "completed"
        )

        firestore.collection("transactions")
            .add(transactionData)
            .addOnFailureListener { e ->
                android.util.Log.e("RatingDialog", "Failed to record transaction: ${e.message}")
            }
    }
}
