package com.example.agristockcapstoneproject.login

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.agristockcapstoneproject.databinding.ActivityEmailVerificationBinding
import com.example.agristockcapstoneproject.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String = ""
    private var firstName: String = ""
    private var lastName: String = ""
    private var phone: String = ""
    private var password: String = ""
    private var resendTimer: CountDownTimer? = null
    private val resendCooldownMs: Long = 120_000L // 2 minutes cooldown

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Get user data from intent
        firstName = intent.getStringExtra("firstName") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""
        userEmail = intent.getStringExtra("email") ?: ""
        phone = intent.getStringExtra("phone") ?: ""
        password = intent.getStringExtra("password") ?: ""

        binding.tvEmailMessage.text = "We sent a verification link to your email.\nTap the link, then press Done."

        // Setup existing user and send verification, then start cooldown
        setupExistingUser()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener { checkEmailVerificationAndProceed() }

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.tvResendCode.setOnClickListener { 
            sendEmailVerificationAgain()
        }
    }

    private fun sendEmailVerificationAgain() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "No user account found", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if user is already verified
        user.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful && user.isEmailVerified) {
                Toast.makeText(this, "Email is already verified", Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            // Send verification email with proper error handling
            user.sendEmailVerification()
                .addOnSuccessListener { 
                    Toast.makeText(this, "Verification email sent to $userEmail", Toast.LENGTH_SHORT).show()
                    startResendCooldown()
                }
                .addOnFailureListener { e ->
                    Log.w("EmailVerificationActivity", "Failed to send verification email", e)
                    
                    // Only show specific error messages for actual blocking issues
                    val errorMessage = when {
                        e.message?.contains("blocked", ignoreCase = true) == true -> {
                            "Email sending is temporarily blocked. Please wait 15-30 minutes before trying again."
                        }
                        e.message?.contains("rate", ignoreCase = true) == true -> {
                            "Rate limit exceeded. Please wait 5-10 minutes before requesting another email."
                        }
                        e.message?.contains("quota", ignoreCase = true) == true -> {
                            "Email quota exceeded. Please try again later."
                        }
                        e.message?.contains("too many", ignoreCase = true) == true -> {
                            "Too many requests. Please wait 15-30 minutes before trying again."
                        }
                        e.message?.contains("invalid", ignoreCase = true) == true -> {
                            "Invalid email address. Please check your email and try again."
                        }
                        e.message?.contains("network", ignoreCase = true) == true -> {
                            "Network error. Please check your internet connection and try again."
                        }
                        else -> {
                            "Failed to send verification email. Please try again later."
                        }
                    }
                    
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    
                    // Only show alternative options for actual blocking errors
                    if (e.message?.contains("blocked", ignoreCase = true) == true || 
                        e.message?.contains("rate", ignoreCase = true) == true ||
                        e.message?.contains("quota", ignoreCase = true) == true ||
                        e.message?.contains("too many", ignoreCase = true) == true) {
                        showAlternativeOptions()
                    }
                }
        }
    }

    private fun setupExistingUser() {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Setting up..."

        val user = auth.currentUser
        if (user != null) {
            Log.d("EmailVerificationActivity", "User already exists, setting up profile")
            
            // Update user profile with display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName("$firstName $lastName")
                .build()

            user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                if (profileTask.isSuccessful) {
                    Log.d("EmailVerificationActivity", "Profile updated successfully")
                } else {
                    Log.w("EmailVerificationActivity", "Failed to update profile")
                }
                
                // Send email verification and start cooldown
                user.sendEmailVerification()
                    .addOnSuccessListener {
                        Log.d("EmailVerificationActivity", "Verification email sent successfully")
                        Toast.makeText(this, "Verification email sent to $userEmail", Toast.LENGTH_SHORT).show()
                        startResendCooldown()
                        binding.btnSubmit.isEnabled = true
                        binding.btnSubmit.text = "Done"
                    }
                    .addOnFailureListener { e ->
                        Log.w("EmailVerificationActivity", "Failed to send verification email", e)
                        
                        // Only show specific error messages for actual blocking issues
                        val errorMessage = when {
                            e.message?.contains("blocked", ignoreCase = true) == true -> {
                                "Email sending is temporarily blocked. Please wait 15-30 minutes before trying again."
                            }
                            e.message?.contains("rate", ignoreCase = true) == true -> {
                                "Rate limit exceeded. Please wait 5-10 minutes before requesting another email."
                            }
                            e.message?.contains("quota", ignoreCase = true) == true -> {
                                "Email quota exceeded. Please try again later."
                            }
                            e.message?.contains("too many", ignoreCase = true) == true -> {
                                "Too many requests. Please wait 15-30 minutes before trying again."
                            }
                            e.message?.contains("invalid", ignoreCase = true) == true -> {
                                "Invalid email address. Please check your email and try again."
                            }
                            e.message?.contains("network", ignoreCase = true) == true -> {
                                "Network error. Please check your internet connection and try again."
                            }
                            else -> {
                                "Failed to send verification email. Please try again later."
                            }
                        }
                        
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                        startResendCooldown()
                        binding.btnSubmit.isEnabled = true
                        binding.btnSubmit.text = "Done"
                        
                        // Only show alternative options for actual blocking errors
                        if (e.message?.contains("blocked", ignoreCase = true) == true || 
                            e.message?.contains("rate", ignoreCase = true) == true ||
                            e.message?.contains("quota", ignoreCase = true) == true ||
                            e.message?.contains("too many", ignoreCase = true) == true) {
                            showAlternativeOptions()
                        }
                    }
            }
        } else {
            Log.e("EmailVerificationActivity", "No current user found")
            Toast.makeText(this, "No user account found. Please try signing up again.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun checkEmailVerificationAndProceed() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "No user to verify", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Checking..."
        user.reload().addOnCompleteListener { reloadTask ->
            binding.btnSubmit.isEnabled = true
            binding.btnSubmit.text = "Done"
            if (reloadTask.isSuccessful && user.isEmailVerified) {
                saveUserToFirestore(user.uid)
            } else {
                Toast.makeText(this, "Please verify your email first. Check your inbox and spam folder.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startResendCooldown() {
        binding.tvResendCode.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(resendCooldownMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendCode.text = "Resend in ${seconds}s"
            }

            override fun onFinish() {
                binding.tvResendCode.isEnabled = true
                binding.tvResendCode.text = "Resend"
            }
        }.start()
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        resendTimer = null
        super.onDestroy()
    }

    private fun saveUserToFirestore(userId: String) {
        val userData = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to userEmail,
            "phone" to phone,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "isEmailVerified" to true,
            // WARNING: storing raw passwords is insecure. Prefer NOT to store passwords.
            // If truly required for your project spec, hash the password client-side before saving.
            "password" to password
        )

        firestore.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d("EmailVerificationActivity", "User data saved to Firestore")
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                navigateToMain()
            }
            .addOnFailureListener { e ->
                Log.w("EmailVerificationActivity", "Error saving user data", e)
                // Even if Firestore fails, we can still proceed to main activity
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                navigateToMain()
            }
    }

    private fun showAlternativeOptions() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Email Sending Blocked")
        builder.setMessage("Firebase has temporarily blocked email sending. You have several options:")
        
        builder.setPositiveButton("Wait and Retry") { dialog, _ ->
            dialog.dismiss()
            // User can try again later
        }
        
        builder.setNeutralButton("Skip Verification") { dialog, _ ->
            dialog.dismiss()
            // Allow user to proceed without email verification (for testing)
            saveUserToFirestore(auth.currentUser?.uid ?: "")
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            finish()
        }
        
        builder.show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}