package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class VerifiedStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verified_status)
        
        val back: ImageView = findViewById(R.id.btn_back)
        back.setOnClickListener { finish() }
    }
}

