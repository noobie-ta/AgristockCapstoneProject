package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Keep status bar white/normal icons per app standard (dark background with white icons)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // Account settings: Edit Profile
        findViewById<android.widget.LinearLayout>(R.id.row_account_edit_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Notifications toggle (persisting can be added later)
        val switchNotifications = findViewById<Switch>(R.id.switch_notifications)
        switchNotifications.setOnCheckedChangeListener { _, _ ->
            // TODO: persist preference to storage/Firebase if needed
        }

        // Privacy: Manage Visibility
        findViewById<android.widget.LinearLayout>(R.id.row_privacy_visibility).setOnClickListener {
            // Placeholder action
            android.widget.Toast.makeText(this, "Visibility settings coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Privacy: Blocked Users
        findViewById<android.widget.LinearLayout>(R.id.row_privacy_blocked_users).setOnClickListener {
            // Placeholder action
            android.widget.Toast.makeText(this, "Blocked users coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Support: Help Center
        findViewById<android.widget.LinearLayout>(R.id.row_support_help_center).setOnClickListener {
            android.widget.Toast.makeText(this, "Help Center coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Support: Contact Support
        findViewById<android.widget.LinearLayout>(R.id.row_support_contact_support).setOnClickListener {
            android.widget.Toast.makeText(this, "Contact Support coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }

    }
}









