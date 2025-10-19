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
        
        try {
            setContentView(R.layout.activity_notifications)
            
            setupViews()
            setupRecyclerView()
            loadNotifications()
        } catch (e: Exception) {
            // If anything fails, just finish the activity
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
            // Handle any view setup errors
            finish()
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
            // Handle any recycler view setup errors
        }
    }
    
    private fun loadNotifications() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // User not authenticated, show empty state
            updateEmptyState()
            return
        }
        
        try {
            notificationsListener = firestore.collection("notifications")
                .whereEqualTo("toUserId", uid)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        // Handle error silently and show empty state
                        updateEmptyState()
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        notifications.clear()
                        for (document in snapshot.documents) {
                            try {
                                val postId = document.getString("postId") ?: ""
                                // Fetch post type from the original post document
                                firestore.collection("posts").document(postId)
                                    .get()
                                    .addOnSuccessListener { postDoc ->
                                        val postType = postDoc.getString("type") ?: "SELL"
                                        val notification = NotificationItem(
                                            id = document.id,
                                            type = document.getString("type") ?: "",
                                            postId = postId,
                                            postTitle = document.getString("postTitle") ?: "",
                                            postImageUrl = document.getString("postImageUrl"),
                                            postPrice = document.getString("postPrice") ?: "",
                                            fromUserId = document.getString("fromUserId") ?: "",
                                            fromUsername = document.getString("fromUsername") ?: "",
                                            fromUserAvatar = document.getString("fromUserAvatar"),
                                            timestamp = document.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                            isRead = document.getBoolean("isRead") ?: false,
                                            isPostAvailable = document.getBoolean("isPostAvailable") ?: true,
                                            postType = postType
                                        )
                                        notifications.add(notification)
                                        
                                        // Check if post still exists and update notification if needed
                                        checkPostAvailability(notification)
                                        
                                        if (::adapter.isInitialized) {
                                            adapter.notifyDataSetChanged()
                                        }
                                        updateEmptyState()
                                    }
                                    .addOnFailureListener {
                                        // If we can't get the post type, default to SELL
                                        val notification = NotificationItem(
                                            id = document.id,
                                            type = document.getString("type") ?: "",
                                            postId = postId,
                                            postTitle = document.getString("postTitle") ?: "",
                                            postImageUrl = document.getString("postImageUrl"),
                                            postPrice = document.getString("postPrice") ?: "",
                                            fromUserId = document.getString("fromUserId") ?: "",
                                            fromUsername = document.getString("fromUsername") ?: "",
                                            fromUserAvatar = document.getString("fromUserAvatar"),
                                            timestamp = document.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                            isRead = document.getBoolean("isRead") ?: false,
                                            isPostAvailable = document.getBoolean("isPostAvailable") ?: true,
                                            postType = "SELL"
                                        )
                                        notifications.add(notification)
                                        
                                        // Check if post still exists and update notification if needed
                                        checkPostAvailability(notification)
                                        
                                        if (::adapter.isInitialized) {
                                            adapter.notifyDataSetChanged()
                                        }
                                        updateEmptyState()
                                    }
                            } catch (e: Exception) {
                                // Skip invalid documents
                                continue
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            // Handle any initialization errors
            updateEmptyState()
        }
    }
    
    private fun checkPostAvailability(notification: NotificationItem) {
        firestore.collection("posts").document(notification.postId)
            .get()
            .addOnSuccessListener { postDoc ->
                val isAvailable = postDoc.exists() && 
                    (postDoc.getString("status") != "sold" && postDoc.getString("status") != "deleted")
                
                if (!isAvailable && notification.isPostAvailable) {
                    // Update notification to mark post as unavailable
                    firestore.collection("notifications").document(notification.id)
                        .update("isPostAvailable", false)
                        .addOnFailureListener { exception ->
                            // Handle error silently
                        }
                }
            }
    }
    
    private fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        
        firestore.collection("notifications").document(notificationId)
            .update("isRead", true)
            .addOnFailureListener { exception ->
                // Handle error silently or show toast
            }
    }
    
    private fun markAllAsRead() {
        try {
            val uid = auth.currentUser?.uid ?: return
            
            val batch = firestore.batch()
            notifications.filter { !it.isRead }.forEach { notification ->
                val docRef = firestore.collection("notifications").document(notification.id)
                batch.update(docRef, "isRead", true)
            }
            
            batch.commit()
                .addOnSuccessListener {
                    // Update local data
                    notifications.forEach { it.copy(isRead = true) }
                    if (::adapter.isInitialized) {
                        adapter.notifyDataSetChanged()
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle error silently
                }
        } catch (e: Exception) {
            // Handle any errors
        }
    }
    
    private fun navigateToItem(notification: NotificationItem) {
        if (!notification.isPostAvailable) {
            // Show message that post is no longer available
            return
        }
        
        val intent = if (notification.postType == "BID") {
            Intent(this, ViewBiddingActivity::class.java)
        } else {
            Intent(this, ItemDetailsActivity::class.java)
        }
        intent.putExtra("postId", notification.postId)
        startActivity(intent)
    }
    
    private fun updateEmptyState() {
        try {
            if (::emptyView.isInitialized && ::recyclerView.isInitialized) {
                if (notifications.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            // Handle any view update errors
        }
    }
    
    private fun animateCard(view: View) {
        val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f)
        scaleX.duration = 150
        scaleY.duration = 150
        scaleX.start()
        scaleY.start()
    }
    
    override fun onStop() {
        super.onStop()
        notificationsListener?.remove()
        notificationsListener = null
    }
    
    private inner class NotificationsAdapter(
        private val data: List<NotificationItem>,
        private val onItemClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: LinearLayout = itemView.findViewById(R.id.ll_notification_card)
            val ivPostImage: ImageView = itemView.findViewById(R.id.iv_post_image)
            val ivUserAvatar: ImageView = itemView.findViewById(R.id.iv_user_avatar)
            val tvTitle: TextView = itemView.findViewById(R.id.tv_notification_title)
            val tvSubtitle: TextView = itemView.findViewById(R.id.tv_notification_subtitle)
            val tvTime: TextView = itemView.findViewById(R.id.tv_notification_time)
            val tvPrice: TextView = itemView.findViewById(R.id.tv_post_price)
            val dotUnread: View = itemView.findViewById(R.id.dot_unread)
            val tvUnavailable: TextView = itemView.findViewById(R.id.tv_unavailable)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_card, parent, false)
            return ViewHolder(v)
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
                    holder.tvTitle.text = "${notification.fromUsername} added your item to favorites"
                    holder.tvSubtitle.text = notification.postTitle
                    holder.tvPrice.text = notification.postPrice
                    holder.tvUnavailable.visibility = View.GONE
                    holder.tvPrice.visibility = View.VISIBLE
                } else {
                    holder.tvTitle.text = "Item no longer available"
                    holder.tvSubtitle.text = "This post has been deleted or sold"
                    holder.tvUnavailable.visibility = View.VISIBLE
                    holder.tvPrice.visibility = View.GONE
                }
                
                // Format timestamp
                try {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                    holder.tvTime.text = dateFormat.format(notification.timestamp.toDate())
                } catch (e: Exception) {
                    holder.tvTime.text = "Recently"
                }
            } catch (e: Exception) {
                // Handle any binding errors gracefully
                holder.tvTitle.text = "Error loading notification"
                holder.tvSubtitle.text = ""
                holder.tvPrice.text = ""
                holder.tvTime.text = ""
            }
        }
        
        override fun getItemCount(): Int = data.size
    }
}
