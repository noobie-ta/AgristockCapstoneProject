package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VerificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        val back: ImageView = findViewById(R.id.btn_back)
        back.setOnClickListener { finish() }

        val title: TextView = findViewById(R.id.tv_verification_title)
        title.text = "Verification"
    }
}









