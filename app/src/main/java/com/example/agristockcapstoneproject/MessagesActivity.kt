package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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

class MessagesActivity : AppCompatActivity() {

    data class ChatItem(
        val chatId: String,
        val otherUserId: String,
        val otherUserName: String,
        val otherUserAvatar: String?,
        val lastMessage: String,
        val lastMessageTime: Long,
        val unreadCount: Int = 0,
        val isOnline: Boolean = false,
        val itemTitle: String = "",
        val itemId: String = "",
        val itemImageUrl: String? = null,
        val itemPrice: String = "",
        val itemLocation: String = ""
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var chatsListener: ListenerRegistration? = null
    private val chats = mutableListOf<ChatItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        initializeViews()
        setupClickListeners()
        loadChats()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.rv_chats)
        searchBar = findViewById(R.id.et_search)
        emptyState = findViewById(R.id.ll_empty_state)
        progressBar = findViewById(R.id.progress_bar)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ChatsAdapter(chats) { chat ->
            openChatRoom(chat)
        }
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        searchBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterChats(s?.toString() ?: "")
            }
        })
    }

    private fun loadChats() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        try {
            progressBar.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            
            android.util.Log.d("MessagesActivity", "Loading chats for user: $currentUserId")
            
            // First try to check if user is authenticated
            if (auth.currentUser == null) {
                android.util.Log.e("MessagesActivity", "User is not authenticated")
                Toast.makeText(this, "Please log in to view messages", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // Test basic Firestore connection first
            firestore.collection("chats").limit(1).get()
                .addOnSuccessListener { 
                    android.util.Log.d("MessagesActivity", "Firestore connection test successful")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MessagesActivity", "Firestore connection test failed: ${e.message}")
                }
            
            chatsListener = firestore.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener { snapshots, exception ->
                    try {
                        progressBar.visibility = View.GONE
                        
                        if (exception != null) {
                            android.util.Log.e("MessagesActivity", "Error loading chats: ${exception.message}")
                            android.util.Log.e("MessagesActivity", "Exception type: ${exception.javaClass.simpleName}")
                            
                            // Check if it's a permission error
                            if (exception.message?.contains("PERMISSION_DENIED") == true) {
                                Toast.makeText(this, "Permission denied. Please check your Firestore rules.", Toast.LENGTH_LONG).show()
                            } else if (exception.message?.contains("FAILED_PRECONDITION") == true) {
                                Toast.makeText(this, "Query failed. Please try again.", Toast.LENGTH_SHORT).show()
                                // Try fallback query
                                tryFallbackQuery()
                                return@addSnapshotListener
                            } else {
                                Toast.makeText(this, "Error loading chats: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                            
                            showEmptyState()
                            return@addSnapshotListener
                        }

                        chats.clear()
                        snapshots?.documents?.forEach { doc ->
                            try {
                                val data = doc.data
                                if (data == null) {
                                    android.util.Log.w("MessagesActivity", "Chat document ${doc.id} has null data")
                                    return@forEach
                                }
                                
                                val participants = data["participants"] as? List<String>
                                if (participants == null || participants.isEmpty()) {
                                    android.util.Log.w("MessagesActivity", "Chat document ${doc.id} has no participants")
                                    return@forEach
                                }
                                
                                val otherUserId = participants.find { it != currentUserId }
                                if (otherUserId == null) {
                                    android.util.Log.w("MessagesActivity", "No other user found in chat ${doc.id}")
                                    return@forEach
                                }
                                
                                // Get other user's info
                                firestore.collection("users").document(otherUserId).get()
                                    .addOnSuccessListener { userDoc ->
                                        try {
                                            if (userDoc.exists()) {
                                                val userData = userDoc.data
                                                if (userData == null) {
                                                    android.util.Log.w("MessagesActivity", "User document ${otherUserId} has null data")
                                                    return@addOnSuccessListener
                                                }
                                                
                                                val otherUserName = userData["username"]?.toString() ?: 
                                                    "${userData["firstName"] ?: ""} ${userData["lastName"] ?: ""}".trim()
                                                    .ifEmpty { userData["displayName"]?.toString() ?: "User" }
                                                val otherUserAvatar = userData["avatarUrl"]?.toString()
                                                
                                                val chatItem = ChatItem(
                                                    chatId = doc.id,
                                                    otherUserId = otherUserId,
                                                    otherUserName = otherUserName,
                                                    otherUserAvatar = otherUserAvatar,
                                                    lastMessage = data["lastMessage"]?.toString() ?: "",
                                                    lastMessageTime = (data["lastMessageTime"] as? Long) ?: 0L,
                                                    unreadCount = (data["unreadCount_$currentUserId"] as? Long)?.toInt() ?: 0,
                                                    itemTitle = data["itemTitle"]?.toString() ?: "",
                                                    itemId = data["itemId"]?.toString() ?: "",
                                                    itemImageUrl = data["itemImageUrl"]?.toString()
                                                )
                                                
                                                // Thread-safe addition to chats list
                                                synchronized(chats) {
                                                    chats.add(chatItem)
                                                    chats.sortByDescending { it.lastMessageTime }
                                                }
                                                
                                                // Update UI on main thread
                                                runOnUiThread {
                                                    try {
                                                        if (recyclerView.adapter != null) {
                                                            recyclerView.adapter?.notifyDataSetChanged()
                                                        }
                                                        
                                                        if (chats.isEmpty()) {
                                                            showEmptyState()
                                                        } else {
                                                            emptyState.visibility = View.GONE
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("MessagesActivity", "Error updating UI: ${e.message}")
                                                    }
                                                }
                                            } else {
                                                android.util.Log.w("MessagesActivity", "User document ${otherUserId} does not exist")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("MessagesActivity", "Error processing user data: ${e.message}")
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("MessagesActivity", "Error loading user data: ${e.message}")
                                    }
                            } catch (e: Exception) {
                                android.util.Log.e("MessagesActivity", "Error processing chat document: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MessagesActivity", "Error in snapshot listener: ${e.message}")
                        Toast.makeText(this, "Error loading chats: ${e.message}", Toast.LENGTH_SHORT).show()
                        showEmptyState()
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("MessagesActivity", "Error setting up chats listener: ${e.message}")
            Toast.makeText(this, "Error setting up chats: ${e.message}", Toast.LENGTH_SHORT).show()
            showEmptyState()
        }
    }

    private fun filterChats(query: String) {
        if (query.isEmpty()) {
            recyclerView.adapter?.notifyDataSetChanged()
            return
        }
        
        val filteredChats = chats.filter { 
            it.otherUserName.contains(query, ignoreCase = true) ||
            it.lastMessage.contains(query, ignoreCase = true)
        }
        
        recyclerView.adapter = ChatsAdapter(filteredChats) { chat ->
            openChatRoom(chat)
        }
    }

    private fun openChatRoom(chat: ChatItem) {
        val intent = Intent(this, ChatRoomActivity::class.java)
        intent.putExtra("chatId", chat.chatId)
        intent.putExtra("otherUserId", chat.otherUserId)
        intent.putExtra("otherUserName", chat.otherUserName)
        intent.putExtra("otherUserAvatar", chat.otherUserAvatar)
        intent.putExtra("item_id", chat.itemId)
        startActivity(intent)
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
    }
    
    private fun tryFallbackQuery() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        android.util.Log.d("MessagesActivity", "Trying fallback query for user: $currentUserId")
        
        // Try a simpler query without whereArrayContains
        firestore.collection("chats")
            .get()
            .addOnSuccessListener { snapshot ->
                android.util.Log.d("MessagesActivity", "Fallback query successful, found ${snapshot.size()} chats")
                
                chats.clear()
                snapshot.documents.forEach { doc ->
                    try {
                        val data = doc.data
                        if (data == null) return@forEach
                        
                        val participants = data["participants"] as? List<String>
                        if (participants == null || !participants.contains(currentUserId)) {
                            return@forEach
                        }
                        
                        val otherUserId = participants.find { it != currentUserId }
                        if (otherUserId == null) return@forEach
                        
                        // Get other user's info
                        firestore.collection("users").document(otherUserId).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    val userData = userDoc.data
                                    if (userData == null) return@addOnSuccessListener
                                    
                                    val otherUserName = userData["username"]?.toString() ?: 
                                        "${userData["firstName"] ?: ""} ${userData["lastName"] ?: ""}".trim()
                                        .ifEmpty { userData["displayName"]?.toString() ?: "User" }
                                    val otherUserAvatar = userData["avatarUrl"]?.toString()
                                    
                                    val chatItem = ChatItem(
                                        chatId = doc.id,
                                        otherUserId = otherUserId,
                                        otherUserName = otherUserName,
                                        otherUserAvatar = otherUserAvatar,
                                        lastMessage = data["lastMessage"]?.toString() ?: "",
                                        lastMessageTime = (data["lastMessageTime"] as? Long) ?: 0L,
                                        unreadCount = (data["unreadCount_$currentUserId"] as? Long)?.toInt() ?: 0
                                    )
                                    
                                    synchronized(chats) {
                                        chats.add(chatItem)
                                        chats.sortByDescending { it.lastMessageTime }
                                    }
                                    
                                    runOnUiThread {
                                        if (recyclerView.adapter != null) {
                                            recyclerView.adapter?.notifyDataSetChanged()
                                        }
                                        
                                        if (chats.isEmpty()) {
                                            showEmptyState()
                                        } else {
                                            emptyState.visibility = View.GONE
                                        }
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        android.util.Log.e("MessagesActivity", "Error processing chat in fallback: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Fallback query also failed: ${e.message}")
                Toast.makeText(this, "Unable to load chats. Please check your connection.", Toast.LENGTH_LONG).show()
                showEmptyState()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatsListener?.remove()
    }

    inner class ChatsAdapter(
        private val chatList: List<ChatItem>,
        private val onChatClick: (ChatItem) -> Unit
    ) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardChat: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.card_chat)
            val avatar: ImageView = view.findViewById(R.id.iv_avatar)
            val username: TextView = view.findViewById(R.id.tv_username)
            val chatTitle: TextView = view.findViewById(R.id.tv_chat_title)
            val lastMessage: TextView = view.findViewById(R.id.tv_last_message)
            val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
            val unreadBadge: TextView = view.findViewById(R.id.tv_unread_badge)
            val onlineIndicator: View = view.findViewById(R.id.v_online_indicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_card, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val chat = chatList[position]
            
            // Set username
            holder.username.text = chat.otherUserName
            
            // Set chat title - show only item title if available, otherwise just username
            holder.chatTitle.text = if (chat.itemTitle.isNotEmpty()) {
                chat.itemTitle
            } else {
                chat.otherUserName
            }
            holder.lastMessage.text = chat.lastMessage
            holder.timestamp.text = formatTimestamp(chat.lastMessageTime)
            
            // Load avatar
            if (!chat.otherUserAvatar.isNullOrEmpty()) {
                Glide.with(this@MessagesActivity)
                    .load(chat.otherUserAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(holder.avatar)
            } else {
                holder.avatar.setImageResource(R.drawable.ic_profile)
            }
            
            // Show unread badge
            if (chat.unreadCount > 0) {
                holder.unreadBadge.visibility = View.VISIBLE
                holder.unreadBadge.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
            } else {
                holder.unreadBadge.visibility = View.GONE
            }
            
            // Online indicator
            holder.onlineIndicator.visibility = if (chat.isOnline) View.VISIBLE else View.GONE
            
            holder.itemView.setOnClickListener {
                onChatClick(chat)
            }
            
            // Set up long press listener for context menu
            holder.cardChat.setOnLongClickListener {
                showChatContextMenu(holder.cardChat, chat)
                true
            }
        }

        override fun getItemCount(): Int = chatList.size
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            else -> {
                val date = Date(timestamp)
                val format = SimpleDateFormat("MMM dd", Locale.getDefault())
                format.format(date)
            }
        }
    }
    
    private fun showChatContextMenu(anchor: View, chat: ChatItem) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.chat_context_menu, popup.menu)
        
        // Show/hide menu items based on chat state
        val menu = popup.menu
        menu.findItem(R.id.action_mark_read).isVisible = chat.unreadCount > 0
        menu.findItem(R.id.action_mark_unread).isVisible = chat.unreadCount == 0
        
        // TODO: Add mute/unmute logic based on chat preferences
        menu.findItem(R.id.action_mute).isVisible = true
        menu.findItem(R.id.action_unmute).isVisible = false
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_chat -> {
                    deleteChat(chat)
                    true
                }
                R.id.action_mark_read -> {
                    markChatAsRead(chat)
                    true
                }
                R.id.action_mark_unread -> {
                    markChatAsUnread(chat)
                    true
                }
                R.id.action_mute -> {
                    muteChat(chat)
                    true
                }
                R.id.action_unmute -> {
                    unmuteChat(chat)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun deleteChat(chat: ChatItem) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Chat")
        builder.setMessage("Are you sure you want to delete this conversation? This action cannot be undone.")
        builder.setPositiveButton("Delete") { _, _ ->
            // Delete chat from Firestore
            firestore.collection("chats").document(chat.chatId)
                .delete()
                .addOnSuccessListener {
                    android.util.Log.d("MessagesActivity", "Chat deleted successfully")
                    Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    // Refresh the chat list
                    loadChats()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MessagesActivity", "Error deleting chat: ${e.message}")
                    Toast.makeText(this, "Failed to delete chat", Toast.LENGTH_SHORT).show()
                }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun markChatAsRead(chat: ChatItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Update unread count to 0
        firestore.collection("chats").document(chat.chatId)
            .update("unreadCount_$currentUserId", 0)
            .addOnSuccessListener {
                android.util.Log.d("MessagesActivity", "Chat marked as read")
                Toast.makeText(this, "Marked as read", Toast.LENGTH_SHORT).show()
                loadChats() // Refresh the list
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Error marking chat as read: ${e.message}")
                Toast.makeText(this, "Failed to mark as read", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun markChatAsUnread(chat: ChatItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Set unread count to 1
        firestore.collection("chats").document(chat.chatId)
            .update("unreadCount_$currentUserId", 1)
            .addOnSuccessListener {
                android.util.Log.d("MessagesActivity", "Chat marked as unread")
                Toast.makeText(this, "Marked as unread", Toast.LENGTH_SHORT).show()
                loadChats() // Refresh the list
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Error marking chat as unread: ${e.message}")
                Toast.makeText(this, "Failed to mark as unread", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun muteChat(chat: ChatItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        val muteData = mapOf(
            "mutedBy" to currentUserId,
            "chatId" to chat.chatId,
            "timestamp" to System.currentTimeMillis()
        )
        
        firestore.collection("mutedChats").add(muteData)
            .addOnSuccessListener {
                android.util.Log.d("MessagesActivity", "Chat muted successfully")
                Toast.makeText(this, "Notifications muted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Error muting chat: ${e.message}")
                Toast.makeText(this, "Failed to mute chat", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun unmuteChat(chat: ChatItem) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        firestore.collection("mutedChats")
            .whereEqualTo("mutedBy", currentUserId)
            .whereEqualTo("chatId", chat.chatId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                for (document in querySnapshot.documents) {
                    document.reference.delete()
                        .addOnSuccessListener {
                            android.util.Log.d("MessagesActivity", "Chat unmuted successfully")
                            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("MessagesActivity", "Error unmuting chat: ${e.message}")
                            Toast.makeText(this, "Failed to unmute chat", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Error finding muted chat: ${e.message}")
                Toast.makeText(this, "Failed to unmute chat", Toast.LENGTH_SHORT).show()
            }
    }
}
