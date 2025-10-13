package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val back: ImageView = findViewById(R.id.btn_back)
        back.setOnClickListener { finish() }

        val title: TextView = findViewById(R.id.tv_settings_title)
        title.text = "Settings"
    }
}









