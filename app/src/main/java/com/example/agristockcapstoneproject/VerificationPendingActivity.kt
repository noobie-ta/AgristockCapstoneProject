package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class VerificationPendingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_pending)
        
        val back: ImageView = findViewById(R.id.btn_back)
        back.setOnClickListener { finish() }
    }
}

