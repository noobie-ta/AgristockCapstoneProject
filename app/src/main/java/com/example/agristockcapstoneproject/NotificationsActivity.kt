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
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var emptyView: LinearLayout
    private lateinit var adapter: NotificationsAdapter
    private val notifications = mutableListOf<NotificationItem>()
    private var notificationsListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isActivityVisible = false
    
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
        
        // Configure status bar - transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)
        
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
            swipeRefresh = findViewById(R.id.swipe_refresh)
            emptyView = findViewById(R.id.ll_empty_notifications)
            
            // Setup swipe refresh
            swipeRefresh.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.button_sell_text),
                ContextCompat.getColor(this, R.color.button_bid_text),
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            swipeRefresh.setOnRefreshListener {
                refreshNotifications()
            }
            
            // Back button
            findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
                finish()
            }
            
            // Menu button
            findViewById<ImageView>(R.id.btn_menu)?.setOnClickListener {
                showNotificationsMenu(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error in setupViews: ${e.message}")
        }
    }
    
    private fun setupRecyclerView() {
        try {
            adapter = NotificationsAdapter(notifications, 
                onItemClick = { notification ->
                    // Mark as read and navigate to item
                    markAsRead(notification.id)
                    navigateToItem(notification)
                },
                onMenuClick = { notification ->
                    // Show delete confirmation dialog
                    showDeleteConfirmationDialog(notification)
                }
            )
            
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
        
        // First, let's try a simple query to test if we can access notifications
        firestore.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .get()
            .addOnSuccessListener { testSnapshot ->
                android.util.Log.d("NotificationsActivity", "Test query successful: found ${testSnapshot.size()} notifications")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationsActivity", "Test query failed: ${e.message}")
            }
        
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
                        val newNotifications = mutableListOf<NotificationItem>()
                        
                        if (snapshot.isEmpty) {
                            android.util.Log.d("NotificationsActivity", "No notifications found")
                            runOnUiThread {
                                notifications.clear()
                                if (::adapter.isInitialized) {
                                    adapter.notifyDataSetChanged()
                                }
                                updateEmptyState()
                            }
                            return@addSnapshotListener
                        }
                        
                        val processedNotifications = mutableListOf<NotificationItem>()
                        val notificationsNeedingUsernameFetch = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                        
                        for (document in snapshot.documents) {
                            try {
                                val fromUsername = document.getString("fromUsername") ?: ""
                                val fromUserId = document.getString("fromUserId") ?: ""
                                
                                // Always create notification first, even with missing username
                                val notification = createNotificationFromDocument(document)
                                if (notification != null) {
                                    processedNotifications.add(notification)
                                    android.util.Log.d("NotificationsActivity", "Added notification: ${notification.postTitle}")
                                    
                                    // If username needs fetching, add to list for background update
                                    if (fromUsername.isEmpty() || fromUsername == "Unknown User") {
                                        notificationsNeedingUsernameFetch.add(document)
                                    }
                                }
                                
                            } catch (e: Exception) {
                                android.util.Log.e("NotificationsActivity", "Error processing notification: ${e.message}")
                            }
                        }
                        
                        // Update UI immediately with all notifications
                        updateNotificationsUI(processedNotifications)
                        
                        // Fetch usernames in background and update UI when complete
                        if (notificationsNeedingUsernameFetch.isNotEmpty()) {
                            fetchUsernamesInBackground(notificationsNeedingUsernameFetch)
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
    
    private fun refreshNotifications() {
        android.util.Log.d("NotificationsActivity", "Refreshing notifications...")
        
        // The listener will automatically trigger a refresh
        // We just need to stop the animation after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (::swipeRefresh.isInitialized) {
                swipeRefresh.isRefreshing = false
            }
        }, 1000)
    }
    
    private fun updateNotificationsUI(processedNotifications: MutableList<NotificationItem>) {
        // Only update UI if activity is visible to prevent unnecessary refreshes
        if (isActivityVisible) {
            runOnUiThread {
                notifications.clear()
                notifications.addAll(processedNotifications)
                
                // Sort notifications by timestamp (newest first)
                notifications.sortByDescending { it.timestamp }
                
                if (::adapter.isInitialized) {
                    // Notify adapter of changes
                    adapter.notifyDataSetChanged()
                }
                
                // Stop refreshing animation
                if (::swipeRefresh.isInitialized) {
                    swipeRefresh.isRefreshing = false
                }
                
                updateEmptyState()
                android.util.Log.d("NotificationsActivity", "Updated UI with ${notifications.size} notifications")
            }
        } else {
            // Activity is not visible, just update the data silently
            notifications.clear()
            notifications.addAll(processedNotifications)
            notifications.sortByDescending { it.timestamp }
            
            // Stop refreshing animation
            if (::swipeRefresh.isInitialized) {
                swipeRefresh.isRefreshing = false
            }
            
            android.util.Log.d("NotificationsActivity", "Updated data silently (activity not visible)")
        }
    }
    
    private fun fetchUsernamesInBackground(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        android.util.Log.d("NotificationsActivity", "Fetching usernames for ${documents.size} notifications in background")
        
        for (document in documents) {
            val fromUserId = document.getString("fromUserId") ?: ""
            if (fromUserId.isNotEmpty()) {
                firestore.collection("users").document(fromUserId)
                    .get()
                    .addOnSuccessListener { userDoc ->
                        if (userDoc.exists()) {
                            val username = userDoc.getString("username") ?: "Unknown User"
                            val avatarUrl = userDoc.getString("avatarUrl")
                            
                            android.util.Log.d("NotificationsActivity", "Fetched username: $username for userId: $fromUserId")
                            
                            // Update the notification document with correct username
                            document.reference.update(
                                "fromUsername", username,
                                "fromUserAvatar", avatarUrl
                            ).addOnSuccessListener {
                                android.util.Log.d("NotificationsActivity", "Updated notification document with username: $username")
                                
                                // Update the local notification if it exists
                                val notificationIndex = notifications.indexOfFirst { it.id == document.id }
                                if (notificationIndex >= 0) {
                                    val updatedNotification = notifications[notificationIndex].copy(
                                        fromUsername = username,
                                        fromUserAvatar = avatarUrl
                                    )
                                    notifications[notificationIndex] = updatedNotification
                                    
                                    // Update UI on main thread
                                    if (isActivityVisible) {
                                        runOnUiThread {
                                            if (::adapter.isInitialized) {
                                                adapter.notifyItemChanged(notificationIndex)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("NotificationsActivity", "Failed to fetch username for userId $fromUserId: ${e.message}")
                    }
            }
        }
    }
    
    private fun fetchUsernameFromUserDoc(userId: String, document: com.google.firebase.firestore.DocumentSnapshot, newNotifications: MutableList<NotificationItem>, onComplete: () -> Unit = {}) {
        if (userId.isEmpty()) {
            android.util.Log.w("NotificationsActivity", "Empty userId, skipping username fetch")
            return
        }
        
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val username = userDoc.getString("username") ?: "Unknown User"
                    val avatarUrl = userDoc.getString("avatarUrl")
                    
                    android.util.Log.d("NotificationsActivity", "Fetched username: $username for userId: $userId")
                    
                    // Update the notification document with correct username
                    document.reference.update(
                        "fromUsername", username,
                        "fromUserAvatar", avatarUrl
                    ).addOnSuccessListener {
                        android.util.Log.d("NotificationsActivity", "Updated notification document with username: $username")
                    }
                    
                    // Create notification with correct username
                    val notification = createNotificationFromDocument(document, username, avatarUrl)
                    if (notification != null) {
                        newNotifications.add(notification)
                        android.util.Log.d("NotificationsActivity", "Added notification with fetched username: ${notification.postTitle}")
                    }
                    onComplete()
                } else {
                    android.util.Log.w("NotificationsActivity", "User document not found for userId: $userId")
                    val notification = createNotificationFromDocument(document, "Unknown User", null)
                    if (notification != null) {
                        newNotifications.add(notification)
                    }
                    onComplete()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationsActivity", "Failed to fetch username for userId $userId: ${e.message}")
                val notification = createNotificationFromDocument(document, "Unknown User", null)
                if (notification != null) {
                    newNotifications.add(notification)
                }
                onComplete()
            }
    }
    
    private fun createNotificationFromDocument(
        document: com.google.firebase.firestore.DocumentSnapshot,
        username: String? = null,
        avatarUrl: String? = null
    ): NotificationItem? {
        return try {
            val fromUserId = document.getString("fromUserId") ?: ""
            val fromUsername = username ?: document.getString("fromUsername") ?: ""
            val fromUserAvatar = avatarUrl ?: document.getString("fromUserAvatar")
            
            // If username is empty or "Unknown User", try to fetch it immediately
            if (fromUsername.isEmpty() || fromUsername == "Unknown User") {
                if (fromUserId.isNotEmpty()) {
                    // Fetch username immediately for this notification
                    fetchUsernameForNotification(fromUserId, document)
                }
            }
            
            NotificationItem(
                id = document.id,
                type = document.getString("type") ?: "",
                postId = document.getString("postId") ?: "",
                postTitle = document.getString("postTitle") ?: "",
                postImageUrl = document.getString("postImageUrl"),
                postPrice = document.getString("postPrice") ?: "",
                fromUserId = fromUserId,
                fromUsername = if (fromUsername.isNotEmpty() && fromUsername != "Unknown User") fromUsername else "Loading...",
                fromUserAvatar = fromUserAvatar,
                timestamp = document.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                isRead = document.getBoolean("isRead") ?: false,
                isPostAvailable = document.getBoolean("isPostAvailable") ?: true,
                postType = document.getString("postType") ?: "SELL"
            )
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error creating notification from document: ${e.message}")
            null
        }
    }
    
    private fun fetchUsernameForNotification(userId: String, document: com.google.firebase.firestore.DocumentSnapshot) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val username = userDoc.getString("username") ?: "Unknown User"
                    val avatarUrl = userDoc.getString("avatarUrl")
                    
                    // Update the notification document
                    document.reference.update(
                        "fromUsername", username,
                        "fromUserAvatar", avatarUrl
                    ).addOnSuccessListener {
                        android.util.Log.d("NotificationsActivity", "Updated notification with username: $username")
                        
                        // Update local notification if it exists
                        val notificationIndex = notifications.indexOfFirst { it.id == document.id }
                        if (notificationIndex >= 0) {
                            val updatedNotification = notifications[notificationIndex].copy(
                                fromUsername = username,
                                fromUserAvatar = avatarUrl
                            )
                            notifications[notificationIndex] = updatedNotification
                            
                            // Update UI
                            if (isActivityVisible) {
                                runOnUiThread {
                                    if (::adapter.isInitialized) {
                                        adapter.notifyItemChanged(notificationIndex)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationsActivity", "Failed to fetch username for userId $userId: ${e.message}")
            }
    }
    
    private fun updateEmptyState() {
        runOnUiThread {
            // Stop refreshing animation
            if (::swipeRefresh.isInitialized) {
                swipeRefresh.isRefreshing = false
            }
            
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
    
    private fun showNotificationsMenu(view: View) {
        val popup = android.widget.PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.notifications_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_mark_all_read -> {
                    showMarkAllAsReadConfirmation()
                    true
                }
                R.id.action_clear_all -> {
                    showClearAllConfirmation()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun showMarkAllAsReadConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mark All as Read")
            .setMessage("Are you sure you want to mark all notifications as read?")
            .setPositiveButton("Mark All") { _, _ ->
                markAllAsRead()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showClearAllConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear All Notifications")
            .setMessage("Are you sure you want to delete all notifications? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllNotifications()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        
        // Mark all unread notifications as read
        var markedCount = 0
        for (notification in notifications) {
            if (!notification.isRead) {
                markAsRead(notification.id)
                markedCount++
            }
        }
        
        if (markedCount > 0) {
            android.widget.Toast.makeText(
                this,
                "✓ All notifications marked as read",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun clearAllNotifications() {
        val uid = auth.currentUser?.uid ?: return
        
        if (notifications.isEmpty()) {
            android.widget.Toast.makeText(
                this,
                "No notifications to clear",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Delete all notifications
        val notificationIds = notifications.map { it.id }
        val batch = firestore.batch()
        
        notificationIds.forEach { id ->
            val docRef = firestore.collection("notifications").document(id)
            batch.delete(docRef)
        }
        
        batch.commit()
            .addOnSuccessListener {
                android.widget.Toast.makeText(
                    this,
                    "✓ All notifications cleared",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                notifications.clear()
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
                updateEmptyState()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("NotificationsActivity", "Failed to clear notifications: ${exception.message}")
                android.widget.Toast.makeText(
                    this,
                    "✗ Failed to clear notifications. Please try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
    
    private fun showDeleteConfirmationDialog(notification: NotificationItem) {
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Notification")
                .setMessage("Are you sure you want to delete this notification?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteNotification(notification)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error showing delete dialog: ${e.message}")
        }
    }
    
    private fun deleteNotification(notification: NotificationItem) {
        try {
            firestore.collection("notifications").document(notification.id)
                .delete()
                .addOnSuccessListener {
                    android.util.Log.d("NotificationsActivity", "Notification deleted successfully")
                    // Remove from local list
                    notifications.removeAll { it.id == notification.id }
                    if (::adapter.isInitialized) {
                        adapter.notifyDataSetChanged()
                    }
                    updateEmptyState()
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("NotificationsActivity", "Failed to delete notification: ${exception.message}")
                    android.widget.Toast.makeText(this, "Failed to delete notification", android.widget.Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("NotificationsActivity", "Error deleting notification: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        isActivityVisible = true
    }
    
    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }
    
    private fun createTestNotification() {
        val uid = auth.currentUser?.uid ?: return
        
        val testNotification = hashMapOf(
            "type" to "favorite_added",
            "toUserId" to uid,
            "fromUserId" to "test_user_id",
            "fromUsername" to "Test User",
            "fromUserAvatar" to "",
            "postId" to "test_post_id",
            "postTitle" to "Test Item",
            "postPrice" to "₱100.00",
            "postImageUrl" to "",
            "timestamp" to com.google.firebase.Timestamp.now(),
            "isRead" to false,
            "isPostAvailable" to true,
            "postType" to "SELL"
        )
        
        firestore.collection("notifications")
            .add(testNotification)
            .addOnSuccessListener {
                android.util.Log.d("NotificationsActivity", "Test notification created successfully")
                android.widget.Toast.makeText(this, "Test notification created", android.widget.Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationsActivity", "Failed to create test notification: ${e.message}")
                android.widget.Toast.makeText(this, "Failed to create test notification: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        notificationsListener?.remove()
    }
    
    // Adapter for notifications
    inner class NotificationsAdapter(
        private val data: List<NotificationItem>,
        private val onItemClick: (NotificationItem) -> Unit,
        private val onMenuClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: LinearLayout = view.findViewById(R.id.ll_notification_card)
            val ivPostImage: ImageView = view.findViewById(R.id.iv_post_image)
            val ivUserAvatar: ImageView = view.findViewById(R.id.iv_user_avatar)
            val ivMenuDots: ImageView = view.findViewById(R.id.iv_menu_dots)
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
                
                // Set up menu click listener
                holder.ivMenuDots.setOnClickListener { view ->
                    animateCard(view)
                    onMenuClick(notification)
                }
                
                // Show/hide unread dot
                holder.dotUnread.visibility = if (notification.isRead) View.GONE else View.VISIBLE
                
                // Load post image with better error handling
                if (!notification.postImageUrl.isNullOrEmpty()) {
                    android.util.Log.d("NotificationsActivity", "Loading post image: ${notification.postImageUrl}")
                    Glide.with(this@NotificationsActivity)
                        .load(notification.postImageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(holder.ivPostImage)
                } else {
                    android.util.Log.w("NotificationsActivity", "Post image URL is empty for notification: ${notification.id}")
                    holder.ivPostImage.setImageResource(R.drawable.ic_image_placeholder)
                }
                
                // Load user avatar with better error handling
                if (!notification.fromUserAvatar.isNullOrEmpty()) {
                    android.util.Log.d("NotificationsActivity", "Loading user avatar: ${notification.fromUserAvatar}")
                    Glide.with(this@NotificationsActivity)
                        .load(notification.fromUserAvatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(holder.ivUserAvatar)
                } else {
                    android.util.Log.w("NotificationsActivity", "User avatar URL is empty for notification: ${notification.id}")
                    holder.ivUserAvatar.setImageResource(R.drawable.ic_profile)
                }
                
                // Set notification content with better username handling
                val displayUsername = if (notification.fromUsername.isNotEmpty() && notification.fromUsername != "Unknown User") {
                    notification.fromUsername
                } else {
                    "Someone"
                }
                
                if (notification.isPostAvailable) {
                    holder.tvNotificationTitle.text = "$displayUsername added your item to favorites"
                    holder.tvNotificationSubtitle.text = notification.postTitle
                    holder.tvPostPrice.text = notification.postPrice
                } else {
                    holder.tvNotificationTitle.text = "$displayUsername added your item to favorites (Item no longer available)"
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