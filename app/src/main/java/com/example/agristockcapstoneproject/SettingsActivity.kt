package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var notificationListener: ListenerRegistration? = null
    private lateinit var notificationBadge: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("settings_cache", MODE_PRIVATE)

        // Configure status bar - transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

        initializeViews()
        setupClickListeners()
        setupNotificationBadge()
        loadCachedSettings()
    }

    private fun initializeViews() {
        notificationBadge = findViewById(R.id.tv_notification_badge)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }

        // Account settings: Edit Profile
        findViewById<View>(R.id.row_account_edit_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Notifications: Manage
        findViewById<View>(R.id.row_notifications_manage).setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        // Privacy: Manage Visibility
        findViewById<View>(R.id.row_privacy_visibility).setOnClickListener {
            startActivity(Intent(this, VisibilitySettingsActivity::class.java))
        }

        // Privacy: Blocked Users
        findViewById<View>(R.id.row_privacy_blocked_users).setOnClickListener {
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }

        // Support: Help Center
        findViewById<View>(R.id.row_support_help_center).setOnClickListener {
            startActivity(Intent(this, HelpCenterActivity::class.java))
        }

        // Support: Contact Support
        findViewById<View>(R.id.row_support_contact_support).setOnClickListener {
            startActivity(Intent(this, ContactSupportActivity::class.java))
        }
    }

    private fun setupNotificationBadge() {
        val user = auth.currentUser
        if (user != null) {
            // Load cached count first
            val cachedCount = sharedPreferences.getInt("notification_count", 0)
            updateNotificationBadge(cachedCount)
            
            notificationListener = firestore.collection("notifications")
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("isRead", false)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        android.util.Log.e("SettingsActivity", "Error loading notification badge: ${exception.message}")
                        // Use cached count on error
                        val cachedCount = sharedPreferences.getInt("notification_count", 0)
                        updateNotificationBadge(cachedCount)
                        return@addSnapshotListener
                    }
                    
                    val unreadCount = snapshot?.size() ?: 0
                    // Cache the count
                    sharedPreferences.edit().putInt("notification_count", unreadCount).apply()
                    updateNotificationBadge(unreadCount)
                }
        }
    }

    private fun updateNotificationBadge(count: Int) {
        runOnUiThread {
            if (count > 0) {
                notificationBadge.visibility = View.VISIBLE
                notificationBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                notificationBadge.visibility = View.GONE
            }
        }
    }

    private fun loadCachedSettings() {
        // Load any cached settings for offline support
        val cachedNotificationCount = sharedPreferences.getInt("notification_count", 0)
        updateNotificationBadge(cachedNotificationCount)
    }


    override fun onDestroy() {
        super.onDestroy()
        notificationListener?.remove()
    }
}









