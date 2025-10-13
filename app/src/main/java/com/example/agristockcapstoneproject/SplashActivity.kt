package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.agristockcapstoneproject.login.LoginActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        // Splash screen delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAuthentication()
        }, 3000) // 3 second delay
    }

    private fun checkUserAuthentication() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in and verified, go to main activity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // User is not signed in or not verified, go to login activity
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}