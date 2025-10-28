package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class VisibilitySettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var settingsListener: ListenerRegistration? = null

    // Visibility toggles
    private lateinit var switchProfilePicture: Switch
    private lateinit var switchLocation: Switch
    private lateinit var switchContact: Switch
    private lateinit var switchOnlineStatus: Switch
    private lateinit var switchHideAll: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visibility_settings)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Keep status bar white/normal icons per app standard
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        setupToggleListeners()
        loadVisibilitySettings()
    }

    private fun initializeViews() {
        switchProfilePicture = findViewById(R.id.switch_profile_picture)
        switchLocation = findViewById(R.id.switch_location)
        switchContact = findViewById(R.id.switch_contact)
        switchOnlineStatus = findViewById(R.id.switch_online_status)
        switchHideAll = findViewById(R.id.switch_hide_all)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }
    }

    private fun setupToggleListeners() {
        switchProfilePicture.setOnCheckedChangeListener { _, isChecked ->
            saveVisibilitySetting("profilePicture", isChecked)
        }

        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            saveVisibilitySetting("location", isChecked)
        }

        switchContact.setOnCheckedChangeListener { _, isChecked ->
            saveVisibilitySetting("contact", isChecked)
        }

        switchOnlineStatus.setOnCheckedChangeListener { _, isChecked ->
            saveVisibilitySetting("onlineStatus", isChecked)
        }

        switchHideAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Disable all other toggles
                switchProfilePicture.isChecked = false
                switchLocation.isChecked = false
                switchContact.isChecked = false
                switchOnlineStatus.isChecked = false
                
                // Save all as hidden
                saveVisibilitySetting("profilePicture", false)
                saveVisibilitySetting("location", false)
                saveVisibilitySetting("contact", false)
                saveVisibilitySetting("onlineStatus", false)
            }
            saveVisibilitySetting("hideAll", isChecked)
        }
    }

    private fun loadVisibilitySettings() {
        val user = auth.currentUser
        if (user != null) {
            settingsListener = firestore.collection("userSettings")
                .document(user.uid)
                .addSnapshotListener { document, exception ->
                    if (exception != null) {
                        android.util.Log.e("VisibilitySettings", "Error loading settings: ${exception.message}")
                        return@addSnapshotListener
                    }

                    if (document?.exists() == true) {
                        val data = document.data
                        if (data != null) {
                            // Load visibility preferences
                            switchProfilePicture.isChecked = (data["visibility_profilePicture"] as? Boolean) ?: true
                            switchLocation.isChecked = (data["visibility_location"] as? Boolean) ?: true
                            switchContact.isChecked = (data["visibility_contact"] as? Boolean) ?: true
                            switchOnlineStatus.isChecked = (data["visibility_onlineStatus"] as? Boolean) ?: true
                            switchHideAll.isChecked = (data["visibility_hideAll"] as? Boolean) ?: false
                        }
                    } else {
                        // Set default values (all visible)
                        switchProfilePicture.isChecked = true
                        switchLocation.isChecked = true
                        switchContact.isChecked = true
                        switchOnlineStatus.isChecked = true
                        switchHideAll.isChecked = false
                    }
                }
        }
    }


    private fun saveVisibilitySetting(type: String, visible: Boolean) {
        val user = auth.currentUser
        if (user != null) {
            val updateData = hashMapOf<String, Any>(
                "visibility_$type" to visible,
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection("userSettings")
                .document(user.uid)
                .set(updateData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    android.util.Log.d("VisibilitySettings", "Visibility setting saved: $type = $visible")
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("VisibilitySettings", "Error saving visibility setting: ${exception.message}")
                    Toast.makeText(this, "Failed to save setting", Toast.LENGTH_SHORT).show()
                }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        settingsListener?.remove()
    }
}
