package com.example.agristockcapstoneproject.utils

import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

object RejectionHandler {
    private val firestore = FirebaseFirestore.getInstance()
    private val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L // 3 days in milliseconds
    
    /**
     * Handle verification rejection with incremental cooldown
     * @param userId The user ID whose verification was rejected
     * @param callback Callback with success status and message
     */
    fun handleVerificationRejection(userId: String, callback: (Boolean, String) -> Unit) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentRejectionCount = document.getLong("verificationRejectionCount")?.toInt() ?: 0
                    val newRejectionCount = currentRejectionCount + 1
                    val cooldownDays = newRejectionCount * 3 // Increment by 3 days per rejection
                    val cooldownEnd = System.currentTimeMillis() + (cooldownDays * 24 * 60 * 60 * 1000L)
                    
                    val updates = hashMapOf<String, Any>(
                        "verificationStatus" to "rejected",
                        "verificationRejectionCount" to newRejectionCount,
                        "verificationCooldownEnd" to cooldownEnd,
                        "verificationRejectedAt" to System.currentTimeMillis()
                    )
                    
                    firestore.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d("RejectionHandler", "Verification rejected for user $userId. Rejection count: $newRejectionCount, Cooldown: $cooldownDays days")
                            callback(true, "Verification rejected. User must wait $cooldownDays days before resubmitting.")
                        }
                        .addOnFailureListener { e ->
                            Log.e("RejectionHandler", "Error updating verification rejection: ${e.message}", e)
                            callback(false, "Error handling rejection: ${e.message}")
                        }
                } else {
                    callback(false, "User document not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("RejectionHandler", "Error fetching user data: ${e.message}", e)
                callback(false, "Error fetching user data: ${e.message}")
            }
    }
    
    /**
     * Handle bidding application rejection with incremental cooldown
     * @param userId The user ID whose bidding application was rejected
     * @param callback Callback with success status and message
     */
    fun handleBiddingRejection(userId: String, callback: (Boolean, String) -> Unit) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentRejectionCount = document.getLong("biddingRejectionCount")?.toInt() ?: 0
                    val newRejectionCount = currentRejectionCount + 1
                    val cooldownDays = newRejectionCount * 3 // Increment by 3 days per rejection
                    val cooldownEnd = System.currentTimeMillis() + (cooldownDays * 24 * 60 * 60 * 1000L)
                    
                    val updates = hashMapOf<String, Any>(
                        "biddingApprovalStatus" to "rejected",
                        "biddingRejectionCount" to newRejectionCount,
                        "biddingCooldownEnd" to cooldownEnd,
                        "biddingRejectedAt" to System.currentTimeMillis()
                    )
                    
                    firestore.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d("RejectionHandler", "Bidding application rejected for user $userId. Rejection count: $newRejectionCount, Cooldown: $cooldownDays days")
                            callback(true, "Bidding application rejected. User must wait $cooldownDays days before resubmitting.")
                        }
                        .addOnFailureListener { e ->
                            Log.e("RejectionHandler", "Error updating bidding rejection: ${e.message}", e)
                            callback(false, "Error handling rejection: ${e.message}")
                        }
                } else {
                    callback(false, "User document not found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("RejectionHandler", "Error fetching user data: ${e.message}", e)
                callback(false, "Error fetching user data: ${e.message}")
            }
    }
    
    /**
     * Check if user can resubmit verification (cooldown expired)
     */
    fun canResubmitVerification(userId: String, callback: (Boolean) -> Unit) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val cooldownEnd = document.getLong("verificationCooldownEnd") ?: 0L
                    val now = System.currentTimeMillis()
                    callback(now >= cooldownEnd)
                } else {
                    callback(true) // If no document, allow submission
                }
            }
            .addOnFailureListener {
                callback(false) // On error, don't allow
            }
    }
    
    /**
     * Check if user can resubmit bidding application (cooldown expired)
     */
    fun canResubmitBidding(userId: String, callback: (Boolean) -> Unit) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val cooldownEnd = document.getLong("biddingCooldownEnd") ?: 0L
                    val now = System.currentTimeMillis()
                    callback(now >= cooldownEnd)
                } else {
                    callback(true) // If no document, allow submission
                }
            }
            .addOnFailureListener {
                callback(false) // On error, don't allow
            }
    }
}

