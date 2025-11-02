package com.example.agristockcapstoneproject.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class UserFieldsMigration {
    private val firestore = FirebaseFirestore.getInstance()
    
    fun migrateAllUsers(callback: (Boolean, String) -> Unit) {
        firestore.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var successCount = 0
                var errorCount = 0
                val errors = mutableListOf<String>()
                
                if (querySnapshot.isEmpty) {
                    callback(true, "No users found to migrate.")
                    return@addOnSuccessListener
                }
                
                val batchSize = querySnapshot.documents.size
                var processedCount = 0
                
                querySnapshot.documents.forEach { document ->
                    val userId = document.id
                    val data = document.data ?: emptyMap()
                    
                    val updates = mutableMapOf<String, Any>()
                    
                    // Ensure required fields exist with default values
                    if (!data.containsKey("displayName") || data["displayName"] == null) {
                        val firstName = data["firstName"] as? String ?: ""
                        val lastName = data["lastName"] as? String ?: ""
                        updates["displayName"] = "$firstName $lastName".trim().ifEmpty { "User" }
                    }
                    
                    if (!data.containsKey("bio") || data["bio"] == null) {
                        updates["bio"] = ""
                    }
                    
                    if (!data.containsKey("phone") || data["phone"] == null) {
                        updates["phone"] = ""
                    }
                    
                    if (!data.containsKey("address") || data["address"] == null) {
                        updates["address"] = ""
                    }
                    
                    if (!data.containsKey("profileImageUrl") || data["profileImageUrl"] == null) {
                        updates["profileImageUrl"] = ""
                    }
                    
                    if (!data.containsKey("verificationStatus") || data["verificationStatus"] == null) {
                        updates["verificationStatus"] = "not_verified"
                    }
                    
                    if (!data.containsKey("biddingApprovalStatus") || data["biddingApprovalStatus"] == null) {
                        updates["biddingApprovalStatus"] = "not_applied"
                    }
                    
                    if (!data.containsKey("createdAt") || data["createdAt"] == null) {
                        updates["createdAt"] = System.currentTimeMillis()
                    }
                    
                    if (updates.isNotEmpty()) {
                        firestore.collection("users").document(userId)
                            .update(updates)
                            .addOnSuccessListener {
                                successCount++
                                processedCount++
                                Log.d("UserFieldsMigration", "Migrated user: $userId")
                                
                                if (processedCount == batchSize) {
                                    val message = if (errorCount > 0) {
                                        "Migration completed: $successCount succeeded, $errorCount failed. Errors: ${errors.joinToString(", ")}"
                                    } else {
                                        "Migration completed successfully: $successCount users migrated."
                                    }
                                    callback(errorCount == 0, message)
                                }
                            }
                            .addOnFailureListener { e ->
                                errorCount++
                                processedCount++
                                errors.add("User $userId: ${e.message}")
                                Log.e("UserFieldsMigration", "Failed to migrate user $userId: ${e.message}")
                                
                                if (processedCount == batchSize) {
                                    val message = "Migration completed: $successCount succeeded, $errorCount failed. Errors: ${errors.joinToString(", ")}"
                                    callback(false, message)
                                }
                            }
                    } else {
                        processedCount++
                        if (processedCount == batchSize) {
                            val message = if (errorCount > 0) {
                                "Migration completed: $successCount succeeded, $errorCount failed. Errors: ${errors.joinToString(", ")}"
                            } else {
                                "Migration completed successfully: $successCount users migrated."
                            }
                            callback(errorCount == 0, message)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserFieldsMigration", "Failed to fetch users: ${e.message}")
                callback(false, "Failed to fetch users: ${e.message}")
            }
    }
}

