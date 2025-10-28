package com.example.agristockcapstoneproject.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object BiddingCriteriaChecker {
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    /**
     * Check if user meets all bidding criteria
     */
    suspend fun checkBiddingEligibility(): BiddingEligibilityResult {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return BiddingEligibilityResult(
                isEligible = false,
                reason = "User not authenticated"
            )
        }
        
        try {
            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()
            
            if (!userDoc.exists()) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "User profile not found"
                )
            }
            
            val userData = userDoc.data ?: return BiddingEligibilityResult(
                isEligible = false,
                reason = "User data not available"
            )
            
            // First check verification status - user must be verified to bid
            val verificationStatus = (userData["verificationStatus"] as? String)?.trim()?.lowercase()
            if (verificationStatus != "approved") {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = when (verificationStatus) {
                        "pending" -> "Your verification is pending. Please wait for admin approval."
                        "rejected" -> "Your verification was rejected. Please resubmit your verification."
                        else -> "You must be verified to place bids. Please complete verification first."
                    },
                    requiresVerification = true
                )
            }
            
            // Check bidding approval status (separate from ID verification)
            val biddingApprovalStatus = (userData["biddingApprovalStatus"] as? String)?.trim()?.lowercase()
            if (biddingApprovalStatus != "approved") {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = when (biddingApprovalStatus) {
                        "pending" -> "Your bidding request is pending admin approval"
                        "rejected" -> "Your bidding request was rejected. Please contact support."
                        "banned" -> "You have been banned from bidding. Please contact support."
                        else -> "You need to apply for bidding approval to participate in auctions"
                    },
                    requiresBiddingApproval = true
                )
            }
            
            // Check account age (minimum 7 days)
            val accountCreated = userData["accountCreated"] as? Long
            if (accountCreated != null) {
                val daysSinceCreation = (System.currentTimeMillis() - accountCreated) / (1000 * 60 * 60 * 24)
                if (daysSinceCreation < 7) {
                    return BiddingEligibilityResult(
                        isEligible = false,
                        reason = "Account must be at least 7 days old",
                        daysRemaining = 7 - daysSinceCreation.toInt()
                    )
                }
            }
            
            // Note: biddingBanned is now handled by biddingApprovalStatus = "banned"
            // Keeping this for backward compatibility
            val biddingBanned = userData["biddingBanned"] as? Boolean ?: false
            if (biddingBanned) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "Bidding privileges have been suspended",
                    requiresBiddingApproval = true
                )
            }
            
            // Check if user has too many failed bids (optional criteria)
            val failedBidsCount = userData["failedBidsCount"] as? Long ?: 0
            if (failedBidsCount > 5) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "Too many failed bids. Please contact support."
                )
            }
            
            // Check if user has sufficient activity (optional)
            val postsCount = userData["postsCount"] as? Long ?: 0
            val messagesCount = userData["messagesCount"] as? Long ?: 0
            if (postsCount == 0L && messagesCount < 5) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "Insufficient account activity. Please engage more with the community.",
                    requiresActivity = true
                )
            }
            
            return BiddingEligibilityResult(
                isEligible = true,
                reason = "All criteria met"
            )
            
        } catch (e: Exception) {
            return BiddingEligibilityResult(
                isEligible = false,
                reason = "Error checking eligibility: ${e.message}"
            )
        }
    }
    
    /**
     * Check if user can bid on a specific item
     */
    suspend fun canBidOnItem(itemId: String, currentBid: Double): BiddingEligibilityResult {
        val eligibilityResult = checkBiddingEligibility()
        if (!eligibilityResult.isEligible) {
            return eligibilityResult
        }
        
        // Check if user is not the seller
        val currentUser = auth.currentUser ?: return BiddingEligibilityResult(
            isEligible = false,
            reason = "User not authenticated"
        )
        
        try {
            val itemDoc = firestore.collection("posts")
                .document(itemId)
                .get()
                .await()
            
            if (!itemDoc.exists()) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "Item not found"
                )
            }
            
            val itemData = itemDoc.data ?: return BiddingEligibilityResult(
                isEligible = false,
                reason = "Item data not available"
            )
            
            val sellerId = itemData["userId"] as? String
            if (sellerId == currentUser.uid) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "You cannot bid on your own item"
                )
            }
            
            // Check if bidding is still active
            val biddingEndTime = itemData["biddingEndTime"] as? Long
            if (biddingEndTime != null && System.currentTimeMillis() > biddingEndTime) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "Bidding has ended for this item"
                )
            }
            
            // Check minimum bid increment
            val minimumBid = itemData["minimumBid"] as? Double ?: 1.0
            val bidIncrement = itemData["bidIncrement"] as? Double ?: 1.0
            val requiredBid = currentBid + bidIncrement
            
            if (requiredBid < minimumBid) {
                return BiddingEligibilityResult(
                    isEligible = false,
                    reason = "Bid must be at least ₱${String.format("%.2f", requiredBid)}"
                )
            }
            
            return BiddingEligibilityResult(
                isEligible = true,
                reason = "Eligible to bid"
            )
            
        } catch (e: Exception) {
            return BiddingEligibilityResult(
                isEligible = false,
                reason = "Error checking item eligibility: ${e.message}"
            )
        }
    }
}

data class BiddingEligibilityResult(
    val isEligible: Boolean,
    val reason: String,
    val requiresVerification: Boolean = false,
    val requiresBiddingApproval: Boolean = false,
    val requiresActivity: Boolean = false,
    val daysRemaining: Int? = null
)





