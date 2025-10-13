package com.example.agristockcapstoneproject.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.agristockcapstoneproject.databinding.ActivitySignUpBinding
import com.example.agristockcapstoneproject.MainActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSignUp.setOnClickListener {
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (validateInput(firstName, lastName, email, phone, password, confirmPassword)) {
                createAccountAndNavigate(firstName, lastName, email, phone, password)
            }
        }

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.tvTermsLink.setOnClickListener {
            // Open terms and conditions in browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms-and-conditions"))
            startActivity(intent)
        }
    }

    private fun validateInput(firstName: String, lastName: String, email: String, phone: String, password: String, confirmPassword: String): Boolean {
        if (firstName.isEmpty()) {
            binding.etFirstName.error = "First name is required"
            return false
        }

        if (lastName.isEmpty()) {
            binding.etLastName.error = "Last name is required"
            return false
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email"
            return false
        }

        // Philippine mobile: +639xxxxxxxxx or 09xxxxxxxxx
        val phRegex = Regex("^(?:\\+63|0)9\\d{9}$")
        if (!phRegex.matches(phone)) {
            binding.etPhone.error = "Enter valid PH mobile (e.g., 09XXXXXXXXX or +639XXXXXXXXX)"
            return false
        }

        if (password.length < 8) {
            binding.etPassword.error = "Password must be at least 8 characters"
            return false
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }

        if (!binding.cbTerms.isChecked) {
            Toast.makeText(this, "Please agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun createAccountAndNavigate(firstName: String, lastName: String, email: String, phone: String, password: String) {
        binding.btnSignUp.isEnabled = false
        binding.btnSignUp.text = "Creating account..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val userData = hashMapOf(
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "email" to email,
                            "phone" to phone,
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "isEmailVerified" to (user.isEmailVerified)
                        )
                        firestore.collection("users").document(user.uid)
                            .set(userData)
                            .addOnCompleteListener {
                                navigateToMain()
                            }
                    } else {
                        Toast.makeText(this, "Account creation failed", Toast.LENGTH_LONG).show()
                        binding.btnSignUp.isEnabled = true
                        binding.btnSignUp.text = "Sign Up"
                    }
                } else {
                    Toast.makeText(this, task.exception?.message ?: "Account creation failed", Toast.LENGTH_LONG).show()
                    binding.btnSignUp.isEnabled = true
                    binding.btnSignUp.text = "Sign Up"
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}