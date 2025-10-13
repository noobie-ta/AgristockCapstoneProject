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

        // First check if account exists in Firestore
        firestore.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Log In"
                    Toast.makeText(this, "Account does not exist", Toast.LENGTH_LONG).show()
                } else {
                    // Proceed with Firebase Auth sign-in
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            binding.btnLogin.isEnabled = true
                            binding.btnLogin.text = "Log In"

                            if (task.isSuccessful) {
                                Log.d("LoginActivity", "signInWithEmail:success")
                                val user = auth.currentUser
                                if (user != null) {
                                    navigateToMain()
                                } else {
                                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_LONG).show()
                                }
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
            }
            .addOnFailureListener {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Log In"
                Toast.makeText(this, "Unable to check account. Try again.", Toast.LENGTH_LONG).show()
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
                    "isEmailVerified" to currentUser.isEmailVerified
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
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.hint = "Enter your email"
        input.setPadding(50, 30, 50, 30)

        builder.setTitle("Reset Password")
        builder.setMessage("Enter your email to receive password reset instructions")
        builder.setView(input)

        builder.setPositiveButton("Send") { _, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                sendPasswordResetEmail(email)
            } else {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}


