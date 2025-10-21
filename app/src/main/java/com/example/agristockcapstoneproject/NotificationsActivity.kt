package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class NotificationsActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var adapter: NotificationsAdapter
    private val notifications = mutableListOf<NotificationItem>()
    private var notificationsListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    data class NotificationItem(
        val id: String,
        val type: String, // "favorite_added"
        val postId: String,
        val postTitle: String,
        val postImageUrl: String?,
        val postPrice: String,
        val fromUserId: String,
        val fromUsername: String,
        val fromUserAvatar: String?,
        val timestamp: com.google.firebase.Timestamp,
        val isRead: Boolean = false,
        val isPostAvailable: Boolean = true,
        val postType: String = "SELL" // SELL or BID
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        
        try {
            setContentView(R.layout.activity_notifications)
            
            setupViews()
            setupRecyclerView()
            loadNotifications()
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error in onCreate: ${e.message}")
            finish()
        }
    }
    
    private fun setupViews() {
        try {
            recyclerView = findViewById(R.id.recycler_notifications)
            emptyView = findViewById(R.id.ll_empty_notifications)
            
            // Back button
            findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
                finish()
            }
            
            // Mark all as read button
            findViewById<TextView>(R.id.btn_mark_all_read)?.setOnClickListener {
                markAllAsRead()
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error in setupViews: ${e.message}")
        }
    }
    
    private fun setupRecyclerView() {
        try {
            adapter = NotificationsAdapter(notifications) { notification ->
                // Mark as read and navigate to item
                markAsRead(notification.id)
                navigateToItem(notification)
            }
            
            if (::recyclerView.isInitialized) {
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = adapter
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error in setupRecyclerView: ${e.message}")
        }
    }
    
    private fun loadNotifications() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            android.util.Log.w("NotificationsActivity", "User not authenticated")
            updateEmptyState()
            return
        }
        
        android.util.Log.d("NotificationsActivity", "Loading notifications for user: $uid")
        
        try {
            notificationsListener = firestore.collection("notifications")
                .whereEqualTo("toUserId", uid)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        android.util.Log.e("NotificationsActivity", "Error loading notifications: ${exception.message}")
                        updateEmptyState()
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        android.util.Log.d("NotificationsActivity", "Found ${snapshot.size()} notifications")
                        notifications.clear()
                        
                        if (snapshot.isEmpty) {
                            android.util.Log.d("NotificationsActivity", "No notifications found")
                            updateEmptyState()
                            return@addSnapshotListener
                        }
                        
                        for (document in snapshot.documents) {
                            try {
                                val notification = NotificationItem(
                                    id = document.id,
                                    type = document.getString("type") ?: "",
                                    postId = document.getString("postId") ?: "",
                                    postTitle = document.getString("postTitle") ?: "",
                                    postImageUrl = document.getString("postImageUrl"),
                                    postPrice = document.getString("postPrice") ?: "",
                                    fromUserId = document.getString("fromUserId") ?: "",
                                    fromUsername = document.getString("fromUsername") ?: "",
                                    fromUserAvatar = document.getString("fromUserAvatar"),
                                    timestamp = document.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                    isRead = document.getBoolean("isRead") ?: false,
                                    isPostAvailable = document.getBoolean("isPostAvailable") ?: true,
                                    postType = document.getString("postType") ?: "SELL"
                                )
                                
                                notifications.add(notification)
                                android.util.Log.d("NotificationsActivity", "Added notification: ${notification.postTitle}")
                                
                            } catch (e: Exception) {
                                android.util.Log.e("NotificationsActivity", "Error processing notification: ${e.message}")
                            }
                        }
                        
                        // Sort notifications by timestamp (newest first)
                        notifications.sortByDescending { it.timestamp }
                        
                        // Update UI on main thread
                        runOnUiThread {
                            if (::adapter.isInitialized) {
                                adapter.notifyDataSetChanged()
                            }
                            updateEmptyState()
                            android.util.Log.d("NotificationsActivity", "Updated UI with ${notifications.size} notifications")
                        }
                        
                    } else {
                        android.util.Log.w("NotificationsActivity", "Snapshot is null")
                        updateEmptyState()
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error setting up notifications listener: ${e.message}")
            updateEmptyState()
        }
    }
    
    private fun updateEmptyState() {
        runOnUiThread {
            if (notifications.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        
        firestore.collection("notifications").document(notificationId)
            .update("isRead", true)
            .addOnFailureListener { exception ->
                android.util.Log.e("NotificationsActivity", "Failed to mark notification as read: ${exception.message}")
            }
    }
    
    private fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        
        // Mark all unread notifications as read
        for (notification in notifications) {
            if (!notification.isRead) {
                markAsRead(notification.id)
            }
        }
    }
    
    private fun navigateToItem(notification: NotificationItem) {
        try {
            val intent = if (notification.postType == "BID") {
                Intent(this, ViewBiddingActivity::class.java)
            } else {
                Intent(this, ItemDetailsActivity::class.java)
            }
            intent.putExtra("postId", notification.postId)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error navigating to item: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        notificationsListener?.remove()
    }
    
    // Adapter for notifications
    inner class NotificationsAdapter(
        private val data: List<NotificationItem>,
        private val onItemClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: LinearLayout = view.findViewById(R.id.ll_notification_card)
            val ivPostImage: ImageView = view.findViewById(R.id.iv_post_image)
            val ivUserAvatar: ImageView = view.findViewById(R.id.iv_user_avatar)
            val tvNotificationTitle: TextView = view.findViewById(R.id.tv_notification_title)
            val tvNotificationSubtitle: TextView = view.findViewById(R.id.tv_notification_subtitle)
            val tvPostPrice: TextView = view.findViewById(R.id.tv_post_price)
            val tvNotificationTime: TextView = view.findViewById(R.id.tv_notification_time)
            val dotUnread: View = view.findViewById(R.id.dot_unread)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification_card, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                val notification = data[position]
                
                // Set up click listener
                holder.card.setOnClickListener { view ->
                    animateCard(view)
                    onItemClick(notification)
                }
                
                // Show/hide unread dot
                holder.dotUnread.visibility = if (notification.isRead) View.GONE else View.VISIBLE
                
                // Load post image
                if (!notification.postImageUrl.isNullOrEmpty()) {
                    Glide.with(this@NotificationsActivity)
                        .load(notification.postImageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(holder.ivPostImage)
                } else {
                    holder.ivPostImage.setImageResource(R.drawable.ic_image_placeholder)
                }
                
                // Load user avatar
                if (!notification.fromUserAvatar.isNullOrEmpty()) {
                    Glide.with(this@NotificationsActivity)
                        .load(notification.fromUserAvatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(holder.ivUserAvatar)
                } else {
                    holder.ivUserAvatar.setImageResource(R.drawable.ic_profile)
                }
                
                // Set notification content
                if (notification.isPostAvailable) {
                    holder.tvNotificationTitle.text = "${notification.fromUsername} added your item to favorites"
                    holder.tvNotificationSubtitle.text = notification.postTitle
                    holder.tvPostPrice.text = notification.postPrice
                } else {
                    holder.tvNotificationTitle.text = "${notification.fromUsername} added your item to favorites (Item no longer available)"
                    holder.tvNotificationSubtitle.text = notification.postTitle
                    holder.tvPostPrice.text = "Item unavailable"
                }
                
                // Format timestamp
                val formatter = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                holder.tvNotificationTime.text = formatter.format(notification.timestamp.toDate())
                
            } catch (e: Exception) {
                android.util.Log.e("NotificationsActivity", "Error binding notification: ${e.message}")
            }
        }
        
        override fun getItemCount(): Int = data.size
        
        private fun animateCard(view: View) {
            view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }
}