package com.example.agristockcapstoneproject.login

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.agristockcapstoneproject.databinding.ActivityEmailVerificationBinding
import com.example.agristockcapstoneproject.MainActivity
import com.example.agristockcapstoneproject.utils.EmailJSService
import com.example.agristockcapstoneproject.utils.OTPManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
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
    private var expiryTimer: CountDownTimer? = null
    private val resendCooldownMs: Long = 60_000L // 1 minute cooldown
    private val otpValidityMs: Long = 10 * 60 * 1000L // 10 minutes
    
    private val otpFields = mutableListOf<EditText>()
    
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

        // Update UI
        binding.tvEmailMessage.text = "We sent a 6-digit verification code to your email.\nEnter the code below to verify."
        binding.tvEmailDisplay.text = userEmail
        binding.tvEmailDisplay.visibility = View.VISIBLE
        
        // Setup OTP fields
        setupOTPFields()
        
        // Setup existing user and send OTP
        setupExistingUser()
        
        setupClickListeners()
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackToSignUp()
            }
        })
    }

    private fun setupOTPFields() {
        // Initialize OTP fields list
        otpFields.clear()
        otpFields.add(binding.etOtp1)
        otpFields.add(binding.etOtp2)
        otpFields.add(binding.etOtp3)
        otpFields.add(binding.etOtp4)
        otpFields.add(binding.etOtp5)
        otpFields.add(binding.etOtp6)
        
        // Setup auto-focus between fields
        for (i in otpFields.indices) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && i < otpFields.size - 1) {
                        // Move to next field
                        otpFields[i + 1].requestFocus()
                    } else if (s?.isEmpty() == true && i > 0) {
                        // Move to previous field on backspace
                        otpFields[i - 1].requestFocus()
                    }
                    
                    // Auto-verify when all fields are filled
                    if (isAllFieldsFilled()) {
                        verifyOTP()
                    }
                }
            })
            
            // Handle backspace on empty field
            otpFields[i].setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && 
                    otpFields[i].text.isEmpty() && 
                    i > 0) {
                    otpFields[i - 1].requestFocus()
                    otpFields[i - 1].setSelection(otpFields[i - 1].text.length)
                }
                false
            }
        }
        
        // Focus first field
        otpFields[0].requestFocus()
    }
    
    private fun isAllFieldsFilled(): Boolean {
        return otpFields.all { it.text.isNotEmpty() }
    }
    
    private fun getEnteredOTP(): String {
        return otpFields.joinToString("") { it.text.toString() }
    }
    
    private fun clearOTPFields() {
        otpFields.forEach { it.text.clear() }
        otpFields[0].requestFocus()
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener { 
            verifyOTP()
        }

        binding.ivBack.setOnClickListener {
            goBackToSignUp()
        }

        binding.tvResendCode.setOnClickListener { 
            resendOTP()
        }
    }
    
    private fun goBackToSignUp() {
        // Navigate back to sign up page and delete the unverified Firebase Auth account
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { deleteTask ->
            if (deleteTask.isSuccessful) {
                Log.d("EmailVerificationActivity", "Unverified account deleted")
            } else {
                Log.w("EmailVerificationActivity", "Failed to delete account: ${deleteTask.exception?.message}")
            }
            
            // Navigate to sign up page
            val intent = Intent(this, SignUpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        } ?: run {
            // No user to delete, just navigate back
            val intent = Intent(this, SignUpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupExistingUser() {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Sending OTP..."

        val user = auth.currentUser
        if (user != null) {
            // Check if user is already verified (exists in Firestore)
            firestore.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // User already verified, go directly to MainActivity
                        Log.d("EmailVerificationActivity", "User already verified, navigating to MainActivity")
                        navigateToMain()
                    } else {
                        // User not verified, proceed with OTP setup
                        Log.d("EmailVerificationActivity", "User not verified, setting up profile and sending OTP")
                        
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
                            
                            // Send OTP via EmailJS
                            sendOTPEmail()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("EmailVerificationActivity", "Error checking user document: ${e.message}")
                    // On error, proceed with OTP setup
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName("$firstName $lastName")
                        .build()

                    user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                        sendOTPEmail()
                    }
                }
        } else {
            Log.e("EmailVerificationActivity", "No current user found")
            Toast.makeText(this, "No user account found. Please try signing up again.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun sendOTPEmail() {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Sending OTP..."
        
        // Generate OTP
        val otpCode = OTPManager.generateOTP()
        
        // Store OTP locally
        OTPManager.storeOTP(this, userEmail, otpCode)
        
        // Send OTP via EmailJS
        lifecycleScope.launch {
            val result = EmailJSService.sendOTPEmail(
                recipientEmail = userEmail,
                otpCode = otpCode,
                recipientName = "$firstName $lastName"
            )
            
            result.fold(
                onSuccess = {
                    Toast.makeText(this@EmailVerificationActivity, "OTP sent to $userEmail. Please check your inbox and spam folder.", Toast.LENGTH_LONG).show()
                    binding.btnSubmit.isEnabled = true
                    binding.btnSubmit.text = "Verify Code"
                    startExpiryTimer()
                    startResendCooldown()
                    
                    // Update UI message to remind user to check spam
                    binding.tvEmailMessage.text = "We sent a 6-digit verification code to:\n$userEmail\n\nPlease check your inbox and spam folder."
                },
                onFailure = { error ->
                    Log.e("EmailVerificationActivity", "Failed to send OTP: ${error.message}")
                    Toast.makeText(this@EmailVerificationActivity, "Failed to send OTP: ${error.message}", Toast.LENGTH_LONG).show()
                    
                    // Show error but still allow manual entry (in case email is delayed)
                    binding.btnSubmit.isEnabled = true
                    binding.btnSubmit.text = "Verify Code"
                    
                    // Check if EmailJS is configured
                    if (!EmailJSService.isConfigured()) {
                        binding.tvErrorMessage.text = "EmailJS not configured. Please check EmailJSService.kt"
                        binding.tvErrorMessage.visibility = View.VISIBLE
                    } else {
                        binding.tvErrorMessage.text = "Failed to send email. Please check:\n1. EmailJS dashboard for sending logs\n2. Spam/junk folder\n3. Try resending the code"
                        binding.tvErrorMessage.visibility = View.VISIBLE
                    }
                }
            )
        }
    }
    
    private fun verifyOTP() {
        val enteredOTP = getEnteredOTP()
        
        if (enteredOTP.length != 6) {
            binding.tvErrorMessage.text = "Please enter the complete 6-digit code"
            binding.tvErrorMessage.visibility = View.VISIBLE
            return
        }
        
        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etOtp1.windowToken, 0)
        
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Verifying..."
        binding.tvErrorMessage.visibility = View.GONE
        
        // Verify OTP
        val isValid = OTPManager.verifyOTP(this, userEmail, enteredOTP)
        
        if (isValid) {
            // OTP is valid, proceed with account creation
            Log.d("EmailVerificationActivity", "OTP verified successfully")
            Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show()
            
            // OTP verification is complete, save user data to Firestore
            // We don't need Firebase email verification since we use OTP via EmailJS
            val user = auth.currentUser
            saveUserToFirestore(user?.uid ?: "")
            
        } else {
            // Invalid OTP
            binding.tvErrorMessage.text = "Invalid OTP code. Please try again."
            binding.tvErrorMessage.visibility = View.VISIBLE
            clearOTPFields()
            binding.btnSubmit.isEnabled = true
            binding.btnSubmit.text = "Verify Code"
            
            // Check if OTP expired
            if (!OTPManager.hasValidOTP(this, userEmail)) {
                Toast.makeText(this, "OTP expired. Please request a new code.", Toast.LENGTH_LONG).show()
                binding.tvErrorMessage.text = "OTP expired. Please click 'Resend' to get a new code."
            }
        }
    }
    
    private fun resendOTP() {
        if (binding.tvResendCode.isEnabled) {
            binding.tvResendCode.isEnabled = false
            binding.tvResendCode.text = "Sending..."
            
            lifecycleScope.launch {
                val result = OTPManager.resendOTP(this@EmailVerificationActivity, userEmail, "$firstName $lastName")
                
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@EmailVerificationActivity, "New OTP sent to $userEmail", Toast.LENGTH_SHORT).show()
                        clearOTPFields()
                        startExpiryTimer()
                        startResendCooldown()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@EmailVerificationActivity, "Failed to resend OTP: ${error.message}", Toast.LENGTH_LONG).show()
                        binding.tvResendCode.isEnabled = true
                        binding.tvResendCode.text = "Resend"
                        
                        if (!EmailJSService.isConfigured()) {
                            binding.tvErrorMessage.text = "EmailJS not configured. Please check EmailJSService.kt"
                            binding.tvErrorMessage.visibility = View.VISIBLE
                        }
                    }
                )
            }
        }
    }

    private fun startExpiryTimer() {
        expiryTimer?.cancel()
        
        val remainingTime = OTPManager.getRemainingTime(this, userEmail)
        if (remainingTime > 0) {
            binding.tvTimer.visibility = View.VISIBLE
            
            expiryTimer = object : CountDownTimer(remainingTime * 1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val minutes = (millisUntilFinished / 1000) / 60
                    val seconds = (millisUntilFinished / 1000) % 60
                    binding.tvTimer.text = "Code expires in: ${String.format("%02d:%02d", minutes, seconds)}"
                }

                override fun onFinish() {
                    binding.tvTimer.visibility = View.GONE
                    binding.tvErrorMessage.text = "OTP expired. Please click 'Resend' to get a new code."
                    binding.tvErrorMessage.visibility = View.VISIBLE
                }
            }.start()
        }
    }

    private fun startResendCooldown() {
        binding.tvResendCode.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(resendCooldownMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendCode.text = "Resend ($seconds)s"
            }

            override fun onFinish() {
                binding.tvResendCode.isEnabled = true
                binding.tvResendCode.text = "Resend"
            }
        }.start()
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        expiryTimer?.cancel()
        resendTimer = null
        expiryTimer = null
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
            // ✅ Initialize verification and bidding status
            "verificationStatus" to "not_verified",
            "biddingApprovalStatus" to "not_applied",
            "rating" to 0.0,
            "totalRatings" to 0L,
            // ✅ Initialize online status
            "isOnline" to false,
            "lastSeen" to System.currentTimeMillis()
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
