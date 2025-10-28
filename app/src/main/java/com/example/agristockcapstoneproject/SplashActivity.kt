package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.agristockcapstoneproject.login.LoginActivity
import com.example.agristockcapstoneproject.login.EmailVerificationActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Splash screen delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAuthentication()
        }, 3000) // 3 second delay
    }

    private fun checkUserAuthentication() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, check if OTP is verified (user exists in Firestore)
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // User exists in Firestore = OTP verified, go to main activity
                        Log.d("SplashActivity", "User is verified, navigating to MainActivity")
                        startActivity(Intent(this, MainActivity::class.java))
                    } else {
                        // User signed in but OTP not verified, go to OTP verification
                        Log.d("SplashActivity", "User not verified, navigating to EmailVerificationActivity")
                        val intent = Intent(this, EmailVerificationActivity::class.java)
                        intent.putExtra("email", currentUser.email ?: "")
                        intent.putExtra("firstName", currentUser.displayName?.substringBefore(' ') ?: "")
                        intent.putExtra("lastName", currentUser.displayName?.substringAfter(' ', "") ?: "")
                        startActivity(intent)
                    }
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("SplashActivity", "Error checking user document: ${e.message}")
                    // On error, assume not verified and go to OTP verification
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra("email", currentUser.email ?: "")
                    intent.putExtra("firstName", currentUser.displayName?.substringBefore(' ') ?: "")
                    intent.putExtra("lastName", currentUser.displayName?.substringAfter(' ', "") ?: "")
                    startActivity(intent)
                    finish()
                }
        } else {
            // User is not signed in, go to login activity
            Log.d("SplashActivity", "No user signed in, navigating to LoginActivity")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
