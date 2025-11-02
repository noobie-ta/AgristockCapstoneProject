package com.example.agristockcapstoneproject.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

object BlockingUtils {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Check if user1 has blocked user2 OR user2 has blocked user1 (bidirectional check)
     * Returns true if either user has blocked the other
     */
    suspend fun isBlocked(userId1: String, userId2: String): Boolean {
        return try {
            // Check if user1 blocked user2
            val block1 = firestore.collection("blocks")
                .whereEqualTo("blockerId", userId1)
                .whereEqualTo("blockedUserId", userId2)
                .get(Source.SERVER)
                .await()
            
            if (!block1.isEmpty) {
                return true
            }
            
            // Check if user2 blocked user1 (bidirectional)
            val block2 = firestore.collection("blocks")
                .whereEqualTo("blockerId", userId2)
                .whereEqualTo("blockedUserId", userId1)
                .get(Source.SERVER)
                .await()
            
            !block2.isEmpty
        } catch (e: Exception) {
            android.util.Log.e("BlockingUtils", "Error checking block status: ${e.message}")
            false
        }
    }
    
    /**
     * Get list of user IDs that the current user has blocked
     */
    suspend fun getBlockedUserIds(currentUserId: String): List<String> {
        return try {
            val snapshot = firestore.collection("blocks")
                .whereEqualTo("blockerId", currentUserId)
                .get(Source.SERVER)
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.getString("blockedUserId")
            }
        } catch (e: Exception) {
            android.util.Log.e("BlockingUtils", "Error getting blocked users: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get list of user IDs that have blocked the current user
     */
    suspend fun getBlockedByUserIds(currentUserId: String): List<String> {
        return try {
            val snapshot = firestore.collection("blocks")
                .whereEqualTo("blockedUserId", currentUserId)
                .get(Source.SERVER)
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.getString("blockerId")
            }
        } catch (e: Exception) {
            android.util.Log.e("BlockingUtils", "Error getting users who blocked current user: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all user IDs that are blocked in either direction (user blocked someone OR someone blocked user)
     */
    suspend fun getAllBlockedUserIds(currentUserId: String): Set<String> {
        return try {
            val blockedByMe = getBlockedUserIds(currentUserId)
            val blockedMe = getBlockedByUserIds(currentUserId)
            (blockedByMe + blockedMe).toSet()
        } catch (e: Exception) {
            android.util.Log.e("BlockingUtils", "Error getting all blocked users: ${e.message}")
            emptySet()
        }
    }
    
    /**
     * Check synchronously with callback (for non-coroutine contexts)
     */
    fun checkIfBlocked(
        userId1: String,
        userId2: String,
        callback: (Boolean) -> Unit
    ) {
        // Check if user1 blocked user2
        firestore.collection("blocks")
            .whereEqualTo("blockerId", userId1)
            .whereEqualTo("blockedUserId", userId2)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot1 ->
                if (!snapshot1.isEmpty) {
                    callback(true)
                    return@addOnSuccessListener
                }
                
                // Check if user2 blocked user1 (bidirectional)
                firestore.collection("blocks")
                    .whereEqualTo("blockerId", userId2)
                    .whereEqualTo("blockedUserId", userId1)
                    .get(Source.SERVER)
                    .addOnSuccessListener { snapshot2 ->
                        callback(!snapshot2.isEmpty)
                    }
                    .addOnFailureListener {
                        android.util.Log.e("BlockingUtils", "Error checking block status: ${it.message}")
                        callback(false)
                    }
            }
            .addOnFailureListener {
                android.util.Log.e("BlockingUtils", "Error checking block status: ${it.message}")
                callback(false)
            }
    }
    
    /**
     * Archive a chat when a user is blocked (uses isHiddenFor field)
     */
    fun archiveChat(chatId: String, currentUserId: String) {
        firestore.collection("chats").document(chatId)
            .get()
            .addOnSuccessListener { chatDoc ->
                if (chatDoc.exists()) {
                    val isHiddenFor = chatDoc.get("isHiddenFor") as? Map<String, Boolean> ?: mutableMapOf()
                    val updatedHiddenFor = isHiddenFor.toMutableMap()
                    updatedHiddenFor[currentUserId] = true
                    
                    firestore.collection("chats").document(chatId)
                        .update("isHiddenFor", updatedHiddenFor)
                        .addOnSuccessListener {
                            android.util.Log.d("BlockingUtils", "Chat archived: $chatId")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("BlockingUtils", "Error archiving chat: ${e.message}")
                        }
                }
            }
    }
}

