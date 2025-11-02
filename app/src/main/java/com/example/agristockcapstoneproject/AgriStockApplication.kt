package com.example.agristockcapstoneproject

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.example.agristockcapstoneproject.utils.NotificationHelper

/**
 * Custom Application class for AgriStock
 * Handles global app lifecycle events and online/offline status
 * Similar to Facebook Messenger's online indicator system
 */
class AgriStockApplication : Application(), DefaultLifecycleObserver {
    
    private val statusManager: StatusManager by lazy { StatusManager.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    companion object {
        private const val TAG = "AgriStockApp"
    }
    
    override fun onCreate() {
        super<Application>.onCreate()
        Log.d(TAG, "Application onCreate - Initializing app")
        
        // Register lifecycle observer to track when app goes to foreground/background
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        Log.d(TAG, "Lifecycle observer registered")

        // Prepare notification channels
        NotificationHelper.ensureChannels(this)
    }
    
    /**
     * Called when app moves to FOREGROUND (user starts using the app)
     * This is triggered when:
     * - App is launched
     * - User returns to app from background
     * - User switches from another app to this app
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "📱 App moved to FOREGROUND - Setting user ONLINE")
        
        // Only set online if user is authenticated
        if (auth.currentUser != null) {
            statusManager.setOnline()
            Log.d(TAG, "✅ User ${auth.currentUser?.uid} is now ONLINE")
        } else {
            Log.d(TAG, "⚠️ No authenticated user - skipping online status")
        }
    }
    
    /**
     * Called when app moves to BACKGROUND (user stops using the app)
     * This is triggered when:
     * - User presses home button
     * - User switches to another app
     * - User locks the phone
     * - User navigates away from the app
     */
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "📱 App moved to BACKGROUND - Setting user OFFLINE")
        
        // Only set offline if user is authenticated
        if (auth.currentUser != null) {
            statusManager.setOffline()
            Log.d(TAG, "✅ User ${auth.currentUser?.uid} is now OFFLINE")
        } else {
            Log.d(TAG, "⚠️ No authenticated user - skipping offline status")
        }
    }
    
    /**
     * Called when app is terminated
     * Ensures user is marked as offline
     */
    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application onTerminate - App is closing")
        
        if (auth.currentUser != null) {
            statusManager.setOffline()
            Log.d(TAG, "✅ User marked as offline on app termination")
        }
    }
    
    /**
     * Called when system is running low on memory
     * Ensures user is marked as offline if app is killed
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "⚠️ Low memory - marking user offline as precaution")
        
        if (auth.currentUser != null) {
            statusManager.setOffline()
        }
    }
}

