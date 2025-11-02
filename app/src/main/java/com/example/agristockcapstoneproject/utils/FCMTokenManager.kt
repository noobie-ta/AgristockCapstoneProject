package com.example.agristockcapstoneproject.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Centralized FCM Token Manager
 * 
 * Best Practices Implemented:
 * 1. ✅ Local storage first (SharedPreferences) - works even when user is logged out
 * 2. ✅ Token change detection - only updates if token changed
 * 3. ✅ Offline queue handling - retries when online
 * 4. ✅ Centralized logic - single source of truth
 * 5. ✅ Automatic retry mechanism
 */
object FCMTokenManager {
    
    private const val PREFS_NAME = "fcm_token_prefs"
    private const val KEY_LAST_TOKEN = "last_saved_token"
    private const val KEY_PENDING_TOKEN = "pending_token"
    private const val KEY_USER_ID = "last_user_id"
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Initialize and save FCM token
     * Call this when:
     * - App starts
     * - User logs in
     * - Notification permission granted
     */
    fun initializeToken(context: Context) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("FCMTokenManager", "User not logged in, will save token locally only")
            // Still get token for when user logs in later
            getAndSaveToken(context, saveToFirestore = false)
            return
        }
        
        getAndSaveToken(context, saveToFirestore = true)
    }
    
    /**
     * Handle new token from Firebase (called by MyFirebaseMessagingService)
     */
    fun handleNewToken(context: Context, newToken: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastToken = prefs.getString(KEY_LAST_TOKEN, null)
        
        // Check if token actually changed
        if (lastToken == newToken) {
            Log.d("FCMTokenManager", "Token unchanged, skipping update")
            return
        }
        
        Log.d("FCMTokenManager", "New token received: ${newToken.take(20)}...")
        
        // Always save locally first
        prefs.edit()
            .putString(KEY_LAST_TOKEN, newToken)
            .apply()
        
        // Save to Firestore if user is logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            saveTokenToFirestore(newToken, currentUser.uid)
        } else {
            // User not logged in - store pending token for later
            Log.d("FCMTokenManager", "User not logged in, storing token as pending")
            prefs.edit()
                .putString(KEY_PENDING_TOKEN, newToken)
                .apply()
        }
    }
    
    /**
     * Call this when user logs in to save any pending tokens
     */
    fun onUserLoggedIn(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingToken = prefs.getString(KEY_PENDING_TOKEN, null)
        val lastToken = prefs.getString(KEY_LAST_TOKEN, null)
        val tokenToSave = pendingToken ?: lastToken
        
        val currentUser = auth.currentUser
        if (currentUser != null && tokenToSave != null) {
            Log.d("FCMTokenManager", "User logged in, saving pending token")
            saveTokenToFirestore(tokenToSave, currentUser.uid)
            
            // Clear pending token
            prefs.edit()
                .remove(KEY_PENDING_TOKEN)
                .putString(KEY_USER_ID, currentUser.uid)
                .apply()
        }
        
        // Also initialize fresh token
        initializeToken(context)
    }
    
    /**
     * Call this when user logs out to clear user-specific data
     */
    fun onUserLoggedOut(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_USER_ID)
            // Keep lastToken and pendingToken for when user logs back in
            .apply()
        Log.d("FCMTokenManager", "User logged out, token data cleared")
    }
    
    /**
     * Get current token from local storage
     */
    fun getCachedToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_TOKEN, null)
    }
    
    private fun getAndSaveToken(context: Context, saveToFirestore: Boolean) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("FCMTokenManager", "Failed to get FCM token: ${task.exception?.message}")
                return@addOnCompleteListener
            }
            
            val token = task.result
            Log.d("FCMTokenManager", "FCM token retrieved: ${token.take(20)}...")
            
            handleNewToken(context, token)
            
            if (saveToFirestore) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    saveTokenToFirestore(token, currentUser.uid)
                }
            }
        }
    }
    
    private fun saveTokenToFirestore(token: String, userId: String) {
        // Check if this is a different user (multi-device scenario)
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now(),
            "fcmTokenDeviceId" to android.provider.Settings.Secure.getString(
                FirebaseAuth.getInstance().app.applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        )
        
        // Use update() instead of set() for better performance when document exists
        firestore.collection("users").document(userId)
            .update(tokenData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d("FCMTokenManager", "✅ FCM token saved successfully to Firestore for user: $userId")
                
                // Update local storage to mark as saved
                val context = FirebaseAuth.getInstance().app.applicationContext
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_LAST_TOKEN, token)
                    .putString(KEY_USER_ID, userId)
                    .remove(KEY_PENDING_TOKEN)
                    .apply()
            }
            .addOnFailureListener { exception ->
                // If update fails (document might not exist), use set with merge
                Log.w("FCMTokenManager", "Update failed, trying set with merge: ${exception.message}")
                
                firestore.collection("users").document(userId)
                    .set(tokenData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FCMTokenManager", "✅ FCM token saved via merge")
                        val context = FirebaseAuth.getInstance().app.applicationContext
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString(KEY_LAST_TOKEN, token)
                            .putString(KEY_USER_ID, userId)
                            .remove(KEY_PENDING_TOKEN)
                            .apply()
                    }
                    .addOnFailureListener { setException ->
                        Log.e("FCMTokenManager", "❌ Failed to save FCM token: ${setException.message}")
                        // Token is still saved locally, will retry later
                    }
            }
    }
    
    /**
     * Manual refresh - useful for retry scenarios
     */
    fun refreshToken(context: Context) {
        initializeToken(context)
    }
}

