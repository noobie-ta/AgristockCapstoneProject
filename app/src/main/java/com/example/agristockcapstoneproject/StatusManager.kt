package com.example.agristockcapstoneproject

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatusManager private constructor() {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var isOnline = false
    
    companion object {
        @Volatile
        private var INSTANCE: StatusManager? = null
        
        fun getInstance(): StatusManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StatusManager().also { INSTANCE = it }
            }
        }
    }
    
    fun setOnlineStatus(isOnline: Boolean) {
        if (this.isOnline == isOnline) return // Avoid unnecessary updates
        
        this.isOnline = isOnline
        val currentUserId = auth.currentUser?.uid ?: return
        
        try {
            val statusData = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis()
            )
            
            firestore.collection("users").document(currentUserId)
                .update(statusData)
                .addOnSuccessListener {
                    Log.d("StatusManager", "User status updated: isOnline=$isOnline")
                }
                .addOnFailureListener { exception ->
                    Log.e("StatusManager", "Failed to update user status: ${exception.message}")
                }
        } catch (e: Exception) {
            Log.e("StatusManager", "Error updating user status: ${e.message}")
        }
    }
    
    fun setOnline() {
        setOnlineStatus(true)
    }
    
    fun setOffline() {
        setOnlineStatus(false)
    }
    
    fun isUserOnline(): Boolean {
        return isOnline
    }
}


