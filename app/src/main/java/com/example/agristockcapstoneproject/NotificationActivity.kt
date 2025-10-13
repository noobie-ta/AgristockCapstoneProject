package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NotificationActivity : AppCompatActivity() {

    data class NotificationItem(
        val name: String,
        val activityType: String,
        val timeAgo: String,
        val isUnread: Boolean = true
    )

    private val notifications = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val container = findViewById<LinearLayout>(R.id.ll_notifications_container)
        val btnNewest = findViewById<TextView>(R.id.btn_newest)
        val btnOldest = findViewById<TextView>(R.id.btn_oldest)
        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val btnViewMore = findViewById<TextView>(R.id.btn_view_more)

        fun render(list: List<NotificationItem>) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(this)
            list.forEach { item ->
                val view = inflater.inflate(R.layout.item_notification, container, false)
                val title = view.findViewById<TextView>(R.id.tv_title)
                val time = view.findViewById<TextView>(R.id.tv_time)
                title.text = "${item.name} (${item.activityType})"
                time.text = item.timeAgo
                if (item.isUnread) {
                    title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
                }
                view.setOnClickListener {
                    // Placeholder: navigate to details
                    // startActivity(Intent(this, SomeDetailActivity::class.java))
                }
                container.addView(view)
            }
        }

        // Show empty state if no notifications
        if (notifications.isEmpty()) {
            showEmptyState()
        } else {
            render(notifications)
        }

        btnNewest.setOnClickListener {
            btnNewest.setBackgroundResource(R.drawable.filter_button_selected)
            btnOldest.setBackgroundResource(R.drawable.filter_button_unselected)
            render(notifications)
        }

        btnOldest.setOnClickListener {
            btnNewest.setBackgroundResource(R.drawable.filter_button_unselected)
            btnOldest.setBackgroundResource(R.drawable.filter_button_selected)
            render(notifications.asReversed())
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnViewMore.setOnClickListener {
            // Load more placeholder
            // You can append more items here and re-render
        }
    }

    private fun showEmptyState() {
        val container = findViewById<LinearLayout>(R.id.ll_notifications_container)
        container.removeAllViews()
        
        val emptyView = TextView(this).apply {
            text = "No notifications yet.\nYou'll see updates about your posts here!"
            textSize = 16f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }
        container.addView(emptyView)
    }
}


