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
    private val resendCooldownMs: Long = 120_000L

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

        // Create account immediately and send verification, then start cooldown
        createUserAccount()
        startResendCooldown()

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
        auth.currentUser?.sendEmailVerification()
            ?.addOnSuccessListener { 
                Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show()
                startResendCooldown()
            }
            ?.addOnFailureListener { 
                Toast.makeText(this, it.message ?: "Failed to send email", Toast.LENGTH_SHORT).show() 
            }
    }

    private fun createUserAccount() {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Verifying..."

        auth.createUserWithEmailAndPassword(userEmail, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("EmailVerificationActivity", "createUserWithEmail:success")
                    val user = auth.currentUser

                    // Update user profile with display name
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName("$firstName $lastName")
                        .build()

                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            // Send email verification and start cooldown
                            user.sendEmailVerification()
                                .addOnSuccessListener {
                                    startResendCooldown()
                                    binding.btnSubmit.isEnabled = true
                                    binding.btnSubmit.text = "Done"
                                }
                                .addOnFailureListener {
                                    binding.btnSubmit.isEnabled = true
                                    binding.btnSubmit.text = "Done"
                                }
                        } else {
                            Log.w("EmailVerificationActivity", "Failed to update profile")
                            user.sendEmailVerification()
                                .addOnCompleteListener { 
                                    startResendCooldown()
                                    binding.btnSubmit.isEnabled = true
                                    binding.btnSubmit.text = "Done" 
                                }
                        }
                    }
                } else {
                    binding.btnSubmit.isEnabled = true
                    binding.btnSubmit.text = "Done"
                    Log.w("EmailVerificationActivity", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(this, "Account creation failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG).show()
                }
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
                Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show()
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

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}