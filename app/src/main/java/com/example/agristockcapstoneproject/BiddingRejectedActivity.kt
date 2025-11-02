package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BiddingRejectedActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var tvTimeRemaining: TextView
    private lateinit var tvRejectionCount: TextView
    private lateinit var tvCooldownInfo: TextView
    private lateinit var btnResubmit: AppCompatButton
    private var countDownTimer: CountDownTimer? = null
    
    private val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L // 3 days in milliseconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bidding_rejected)
        
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        
        initializeViews()
        setupClickListeners()
        loadRejectionData()
    }
    
    private fun initializeViews() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        tvTimeRemaining = findViewById(R.id.tv_time_remaining)
        tvRejectionCount = findViewById(R.id.tv_rejection_count)
        tvCooldownInfo = findViewById(R.id.tv_cooldown_info)
        btnResubmit = findViewById(R.id.btn_resubmit)
    }
    
    private fun setupClickListeners() {
        btnResubmit.setOnClickListener {
            checkCooldownAndNavigate()
        }
        
        findViewById<AppCompatButton>(R.id.btn_contact_support).setOnClickListener {
            val intent = Intent(this, ContactSupportActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadRejectionData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rejectionCount = document.getLong("biddingRejectionCount")?.toInt() ?: 1
                    val cooldownEnd = document.getLong("biddingCooldownEnd") ?: 0L
                    
                    updateRejectionCount(rejectionCount)
                    updateCooldownTimer(cooldownEnd, rejectionCount)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateRejectionCount(count: Int) {
        tvRejectionCount.text = "Rejection count: $count"
        
        val days = count * 3
        val infoText = if (count > 1) {
            "Due to multiple rejections, the cooldown period is ${days} days."
        } else {
            "You are temporarily unable to resubmit your bidding application."
        }
        tvCooldownInfo.text = infoText
    }
    
    private fun updateCooldownTimer(cooldownEnd: Long, rejectionCount: Int) {
        val now = System.currentTimeMillis()
        val remaining = cooldownEnd - now
        
        if (remaining > 0) {
            // Still in cooldown
            btnResubmit.isEnabled = false
            btnResubmit.setBackgroundColor(getColor(R.color.gray_400))
            
            startCountdown(remaining)
        } else {
            // Cooldown expired
            btnResubmit.isEnabled = true
            btnResubmit.setBackgroundColor(getColor(R.color.yellow_accent))
            tvTimeRemaining.text = "Ready to resubmit"
        }
    }
    
    private fun startCountdown(remainingMs: Long) {
        countDownTimer?.cancel()
        
        countDownTimer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val days = millisUntilFinished / (24 * 60 * 60 * 1000)
                val hours = (millisUntilFinished % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                
                tvTimeRemaining.text = when {
                    days > 0 -> "${days}d ${hours}h"
                    hours > 0 -> "${hours}h ${minutes}m"
                    else -> "${minutes}m"
                }
            }
            
            override fun onFinish() {
                btnResubmit.isEnabled = true
                btnResubmit.setBackgroundColor(getColor(R.color.yellow_accent))
                tvTimeRemaining.text = "Ready to resubmit"
            }
        }.start()
    }
    
    private fun checkCooldownAndNavigate() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        
        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val cooldownEnd = document.getLong("biddingCooldownEnd") ?: 0L
                val now = System.currentTimeMillis()
                
                if (now < cooldownEnd) {
                    val remaining = cooldownEnd - now
                    val days = remaining / (24 * 60 * 60 * 1000)
                    Toast.makeText(this, "Please wait ${days + 1} more days before resubmitting", Toast.LENGTH_LONG).show()
                } else {
                    // Cooldown expired, allow resubmission
                    val intent = Intent(this, BiddingApplicationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

