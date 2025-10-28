package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var settingsListener: ListenerRegistration? = null

    // Notification toggles
    private lateinit var switchMessages: Switch
    private lateinit var switchBids: Switch
    private lateinit var switchFavorites: Switch
    private lateinit var switchSystem: Switch

    // Mute duration
    private lateinit var muteSpinner: Spinner
    
    // Flag to prevent toast when loading settings
    private var isLoadingSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Make status bar transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

        initializeViews()
        setupClickListeners()
        setupMuteSpinner()
        loadNotificationSettings()
    }

    private fun initializeViews() {
        switchMessages = findViewById(R.id.switch_messages)
        switchBids = findViewById(R.id.switch_bids)
        switchFavorites = findViewById(R.id.switch_favorites)
        switchSystem = findViewById(R.id.switch_system)
        muteSpinner = findViewById(R.id.spinner_mute_duration)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }

        // Mark all as read
        findViewById<View>(R.id.btn_mark_all_read).setOnClickListener {
            markAllNotificationsAsRead()
        }

        // Clear all notifications
        findViewById<View>(R.id.btn_clear_all).setOnClickListener {
            clearAllNotifications()
        }

        // Setup toggle listeners
        setupToggleListeners()
    }

    private fun setupToggleListeners() {
        switchMessages.setOnCheckedChangeListener { _, isChecked ->
            if (!isLoadingSettings) {
                saveNotificationSetting("messages", isChecked)
            }
        }

        switchBids.setOnCheckedChangeListener { _, isChecked ->
            if (!isLoadingSettings) {
                saveNotificationSetting("bids", isChecked)
            }
        }

        switchFavorites.setOnCheckedChangeListener { _, isChecked ->
            if (!isLoadingSettings) {
                saveNotificationSetting("favorites", isChecked)
            }
        }

        switchSystem.setOnCheckedChangeListener { _, isChecked ->
            if (!isLoadingSettings) {
                saveNotificationSetting("system", isChecked)
            }
        }
    }

    private fun setupMuteSpinner() {
        val muteOptions = arrayOf(
            "Don't mute",
            "1 hour",
            "1 day",
            "1 week",
            "Until turned back on"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, muteOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        muteSpinner.adapter = adapter

        muteSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Only save if not loading settings (user manually changed it)
                if (!isLoadingSettings) {
                    val selectedOption = muteOptions[position]
                    saveMuteSetting(selectedOption)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadNotificationSettings() {
        val user = auth.currentUser
        if (user != null) {
            settingsListener = firestore.collection("userSettings")
                .document(user.uid)
                .addSnapshotListener { document, exception ->
                    if (exception != null) {
                        android.util.Log.e("NotificationSettings", "Error loading settings: ${exception.message}")
                        return@addSnapshotListener
                    }

                    // Set flag to prevent toast when loading
                    isLoadingSettings = true

                    if (document?.exists() == true) {
                        val data = document.data
                        if (data != null) {
                            // Load notification preferences
                            switchMessages.isChecked = (data["notifications_messages"] as? Boolean) ?: true
                            switchBids.isChecked = (data["notifications_bids"] as? Boolean) ?: true
                            switchFavorites.isChecked = (data["notifications_favorites"] as? Boolean) ?: false
                            switchSystem.isChecked = (data["notifications_system"] as? Boolean) ?: true

                            // Load mute setting
                            val muteSetting = data["mute_duration"]?.toString() ?: "Don't mute"
                            val muteOptions = arrayOf("Don't mute", "1 hour", "1 day", "1 week", "Until turned back on")
                            val position = muteOptions.indexOf(muteSetting)
                            if (position >= 0) {
                                muteSpinner.setSelection(position)
                            }
                        }
                    } else {
                        // Set default values
                        switchMessages.isChecked = true
                        switchBids.isChecked = true
                        switchFavorites.isChecked = false
                        switchSystem.isChecked = true
                    }

                    // Reset flag after a short delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isLoadingSettings = false
                    }, 500)
                }
        }
    }

    private fun saveNotificationSetting(type: String, enabled: Boolean) {
        val user = auth.currentUser
        if (user != null) {
            val updateData = hashMapOf<String, Any>(
                "notifications_$type" to enabled,
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection("userSettings")
                .document(user.uid)
                .set(updateData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    android.util.Log.d("NotificationSettings", "Notification setting saved: $type = $enabled")
                    Toast.makeText(this, "✓ Setting saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("NotificationSettings", "Error saving notification setting: ${exception.message}")
                    Toast.makeText(this, "✗ Failed to save setting. Please try again.", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun saveMuteSetting(duration: String) {
        val user = auth.currentUser
        if (user != null) {
            val updateData = hashMapOf<String, Any>(
                "mute_duration" to duration,
                "mute_until" to when (duration) {
                    "1 hour" -> System.currentTimeMillis() + (60 * 60 * 1000)
                    "1 day" -> System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                    "1 week" -> System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
                    "Until turned back on" -> Long.MAX_VALUE
                    else -> 0L
                },
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection("userSettings")
                .document(user.uid)
                .set(updateData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    android.util.Log.d("NotificationSettings", "Mute setting saved: $duration")
                    Toast.makeText(this, "✓ Mute preference saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("NotificationSettings", "Error saving mute setting: ${exception.message}")
                    Toast.makeText(this, "✗ Failed to save setting. Please try again.", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun markAllNotificationsAsRead() {
        val user = auth.currentUser
        if (user != null) {
            firestore.collection("notifications")
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = firestore.batch()
                    snapshot.documents.forEach { doc ->
                        batch.update(doc.reference, "isRead", true)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { exception ->
                            android.util.Log.e("NotificationSettings", "Error marking notifications as read: ${exception.message}")
                            Toast.makeText(this, "Failed to mark notifications as read", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("NotificationSettings", "Error fetching notifications: ${exception.message}")
                    Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun clearAllNotifications() {
        val user = auth.currentUser
        if (user != null) {
            firestore.collection("notifications")
                .whereEqualTo("userId", user.uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = firestore.batch()
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { exception ->
                            android.util.Log.e("NotificationSettings", "Error clearing notifications: ${exception.message}")
                            Toast.makeText(this, "Failed to clear notifications", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("NotificationSettings", "Error fetching notifications: ${exception.message}")
                    Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsListener?.remove()
    }
}
