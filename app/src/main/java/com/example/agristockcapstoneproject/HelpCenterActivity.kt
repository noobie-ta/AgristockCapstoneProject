package com.example.agristockcapstoneproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class HelpCenterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_center)

        // Keep status bar white/normal icons per app standard
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }

        // FAQ
        findViewById<View>(R.id.row_faq).setOnClickListener {
            showFAQ()
        }

        // Report Bug
        findViewById<View>(R.id.row_report_bug).setOnClickListener {
            startActivity(Intent(this, ContactSupportActivity::class.java))
        }

        // Community Guidelines
        findViewById<View>(R.id.row_guidelines).setOnClickListener {
            showCommunityGuidelines()
        }

        // Submit Issue
        findViewById<View>(R.id.row_submit_issue).setOnClickListener {
            startActivity(Intent(this, ContactSupportActivity::class.java))
        }
    }

    private fun showFAQ() {
        val faqItems = listOf(
            "Q: How do I create a new account?\nA: Tap 'Sign Up' and follow the registration process.",
            "Q: How do I post an item for sale?\nA: Go to the main screen, tap the 'SELL' button, and select 'Sell Item'.",
            "Q: How does bidding work?\nA: Find items marked as 'BID', place your bid, and the highest bidder wins.",
            "Q: How do I contact a seller?\nA: Tap on any item and use the 'Message' button to start a conversation.",
            "Q: Is my personal information safe?\nA: Yes, we use encryption and never share your data with third parties.",
            "Q: How do I report inappropriate content?\nA: Use the 'Report' button on any post or contact support.",
            "Q: Can I edit my posts?\nA: Yes, go to your profile and tap on any of your posts to edit them.",
            "Q: How do I delete my account?\nA: Contact support and we'll help you delete your account."
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle("❓ Frequently Asked Questions")
        builder.setMessage(faqItems.joinToString("\n\n"))
        builder.setPositiveButton("Got it") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showCommunityGuidelines() {
        val guidelines = """
            🌱 AgriStock Community Guidelines
            
            Be Respectful:
            • Treat all users with kindness and respect
            • No harassment, bullying, or hate speech
            • Use appropriate language in all communications
            
            Be Honest:
            • Provide accurate descriptions of your items
            • Use real photos of your products
            • Set fair and reasonable prices
            
            Be Safe:
            • Meet in public places for transactions
            • Don't share personal information unnecessarily
            • Report suspicious behavior immediately
            
            Be Responsible:
            • Follow local laws and regulations
            • Don't post illegal or prohibited items
            • Respect intellectual property rights
            
            Violations may result in account suspension or termination.
        """.trimIndent()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("📋 Community Guidelines")
        builder.setMessage(guidelines)
        builder.setPositiveButton("I Understand") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
}
