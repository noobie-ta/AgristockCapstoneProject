@file:Suppress("DEPRECATION")
package com.example.agristockcapstoneproject.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.agristockcapstoneproject.databinding.ActivityLoginBinding
import com.example.agristockcapstoneproject.MainActivity
import com.example.agristockcapstoneproject.R
 
import com.google.firebase.auth.FirebaseAuth
 
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    

    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                signInWithEmail(email, password)
            }
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, com.example.agristockcapstoneproject.login.SignUpActivity::class.java))
        }
        
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.etUsername.error = "Email is required"
            return false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etUsername.error = "Please enter a valid email"
            return false
        }

        return true
    }

    private fun signInWithEmail(email: String, password: String) {
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Signing in..."

        // Sign in first; then check/create user profile in Firestore
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Log In"

                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithEmail:success")
                    ensureUserDocumentAndNavigate()
                } else {
                    val ex = task.exception
                    Log.w("LoginActivity", "signInWithEmail:failure", ex)
                    val message = when (ex?.javaClass?.simpleName) {
                        "FirebaseAuthInvalidCredentialsException" -> "Incorrect email or password"
                        "FirebaseAuthInvalidUserException" -> "Account does not exist"
                        else -> ex?.message ?: "Authentication failed"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
    }

    

    private fun ensureUserDocumentAndNavigate() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
            return
        }

        val usersCollection = firestore.collection("users")
        val userDocRef = usersCollection.document(currentUser.uid)
        userDocRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                navigateToMain()
            } else {
                val userData = hashMapOf(
                    "firstName" to (currentUser.displayName?.substringBefore(' ') ?: ""),
                    "lastName" to (currentUser.displayName?.substringAfter(' ', "") ?: ""),
                    "email" to (currentUser.email ?: ""),
                    "phone" to (currentUser.phoneNumber ?: ""),
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "isEmailVerified" to currentUser.isEmailVerified,
                    // ✅ Initialize verification and bidding status
                    "verificationStatus" to "not_verified", // Options: "not_verified", "pending", "approved", "rejected"
                    "biddingApprovalStatus" to "not_applied", // Options: "not_applied", "pending", "approved", "rejected", "banned"
                    "rating" to 0.0,
                    "totalRatings" to 0L,
                    // ✅ Initialize online status
                    "isOnline" to false,
                    "lastSeen" to System.currentTimeMillis()
                )
                userDocRef.set(userData)
                    .addOnSuccessListener { navigateToMain() }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save profile.", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
            }
        }.addOnFailureListener {
            navigateToMain()
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailInput = dialogView.findViewById<android.widget.EditText>(R.id.et_email)
        val btnSend = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_send)
        val btnCancel = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_cancel)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Style the dialog window
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.5f)

        btnSend.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                emailInput.error = "Email is required"
                emailInput.requestFocus()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Please enter a valid email address"
                emailInput.requestFocus()
                return@setOnClickListener
            }
            dialog.dismiss()
            sendPasswordResetEmail(email)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Make dialog responsive to all screen sizes
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        
        // Calculate responsive width:
        // - Use 90% of screen width
        // - Minimum 280dp (for very small phones like 320dp width)
        // - Maximum 400dp (for tablets and larger phones)
        // - Account for padding (32dp = 16dp * 2)
        val minWidthDp = 280
        val maxWidthDp = 400
        val paddingDp = 32
        
        val minWidthPx = (minWidthDp * density).toInt()
        val maxWidthPx = (maxWidthDp * density).toInt()
        val paddingPx = (paddingDp * density).toInt()
        
        // Use 90% of screen width, but respect min/max bounds
        val calculatedWidth = (screenWidth * 0.9).toInt()
        val dialogWidth = calculatedWidth.coerceIn(minWidthPx, maxWidthPx)
        
        // Set maximum height to 85% of screen height to prevent overflow
        val maxHeightPx = (screenHeight * 0.85).toInt()
        
        // Set dialog dimensions - width is responsive, height wraps content but respects max
        val dialogHeight = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        dialog.window?.setLayout(dialogWidth, dialogHeight)
    }

    private fun sendPasswordResetEmail(email: String) {
        // Check if email exists in Firestore first
        firestore.collection("users")
            .whereEqualTo("email", email.lowercase().trim())
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    // Email doesn't exist in system
                    Toast.makeText(this, "No account found with this email address", Toast.LENGTH_LONG).show()
                } else {
                    // Email exists, send password reset email
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Password reset email sent to $email\nPlease check your inbox and spam folder", Toast.LENGTH_LONG).show()
                                Log.d("LoginActivity", "Password reset email sent successfully to $email")
                            } else {
                                val errorMessage = when {
                                    task.exception?.message?.contains("user-not-found", ignoreCase = true) == true -> {
                                        "No account found with this email address"
                                    }
                                    task.exception?.message?.contains("invalid-email", ignoreCase = true) == true -> {
                                        "Invalid email address format"
                                    }
                                    else -> {
                                        task.exception?.message ?: "Failed to send reset email. Please try again."
                                    }
                                }
                                Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                                Log.e("LoginActivity", "Failed to send password reset email: ${task.exception?.message}")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Error checking email existence: ${e.message}")
                // On error, still try to send reset email (Firebase will handle validation)
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent to $email\nPlease check your inbox and spam folder", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

