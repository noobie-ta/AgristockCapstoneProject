package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class VerificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val back: ImageView = findViewById(R.id.btn_back)
        back.setOnClickListener { finish() }

        val title: TextView = findViewById(R.id.tv_verification_title)
        title.text = "Verification"

        // Check verification status and redirect accordingly
        checkVerificationStatus()
    }

    private fun checkVerificationStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to access verification", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val verificationStatus = document.getString("verificationStatus")
                when (verificationStatus) {
                    "pending" -> {
                        showStatusMessage("Your verification is currently pending admin review.")
                    }
                    "approved" -> {
                        showStatusMessage("Your account is already verified!")
                    }
                    "rejected" -> {
                        showStatusMessage("Your previous verification was rejected. You can submit a new verification request.")
                        startIdVerification()
                    }
                    else -> {
                        // No verification status, start verification process
                        startIdVerification()
                    }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error checking verification status: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startIdVerification() {
        val intent = Intent(this, IdVerificationActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showStatusMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}









