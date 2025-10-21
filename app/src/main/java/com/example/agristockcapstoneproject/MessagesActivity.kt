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
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var chatsListener: ListenerRegistration? = null
    private val chats = mutableListOf<ChatItem>()
    private val onlineStatusListeners = mutableMapOf<String, ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        // Configure status bar - transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

        initializeViews()
        setupClickListeners()
        loadChats()
    }
    
    override fun onResume() {
        super.onResume()
        // Set current user as online
        StatusManager.getInstance().setOnline()
    }
    
    override fun onPause() {
        super.onPause()
        // Set current user as offline
        StatusManager.getInstance().setOffline()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.rv_chats)
        searchBar = findViewById(R.id.et_search)
        emptyState = findViewById(R.id.ll_empty_state)
        progressBar = findViewById(R.id.progress_bar)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ChatsAdapter(chats) { chat ->
            openChatRoom(chat)
        }
        
        // Setup SwipeRefresh
        swipeRefresh.setColorSchemeResources(
            R.color.yellow_accent,
            R.color.blue_accent,
            R.color.red_accent
        )
        swipeRefresh.setOnRefreshListener {
            refreshChats()
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
                    // CRITICAL: Immediately return if activity is finishing or destroyed
                    if (isFinishing || isDestroyed) {
                        android.util.Log.d("MessagesActivity", "Snapshot received but activity is finishing/destroyed, ignoring")
                        return@addSnapshotListener
                    }
                    
                    // ALL UI updates must be on main thread
                    runOnUiThread {
                        try {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            progressBar.visibility = View.GONE
                            swipeRefresh.isRefreshing = false
                        } catch (e: Exception) {
                            android.util.Log.e("MessagesActivity", "Error updating UI: ${e.message}")
                        }
                    }
                    
                    try {
                        if (exception != null) {
                            android.util.Log.e("MessagesActivity", "Error loading chats: ${exception.message}")
                            android.util.Log.e("MessagesActivity", "Exception type: ${exception.javaClass.simpleName}")
                            
                            // Check if it's a permission error
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                                        Toast.makeText(this, "Permission denied. Please check your Firestore rules.", Toast.LENGTH_LONG).show()
                                    } else if (exception.message?.contains("FAILED_PRECONDITION") == true) {
                                        Toast.makeText(this, "Query failed. Please try again.", Toast.LENGTH_SHORT).show()
                                        // Try fallback query
                                        tryFallbackQuery()
                                        return@runOnUiThread
                                    } else {
                                        Toast.makeText(this, "Error loading chats: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    showEmptyState()
                                }
                            }
                            return@addSnapshotListener
                        }

                        // Don't modify chats list here - will be done on UI thread
                        val processedChatIds = mutableSetOf<String>()  // Track processed chats to prevent duplicates
                        val validChatIds = mutableSetOf<String>()  // Track valid (non-hidden) chat IDs
                        
                        snapshots?.documents?.forEach { doc ->
                            try {
                                // Skip if we've already processed this chat
                                if (processedChatIds.contains(doc.id)) {
                                    android.util.Log.d("MessagesActivity", "Skipping duplicate chat ${doc.id}")
                                    return@forEach
                                }
                                processedChatIds.add(doc.id)
                                
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
                                
                                // Check if chat is hidden for current user using new data model
                                val isHiddenFor = data["isHiddenFor"] as? Map<String, Boolean> ?: emptyMap()
                                if (isHiddenFor[currentUserId] == true) {
                                    android.util.Log.d("MessagesActivity", "Chat ${doc.id} is hidden for current user - will be removed from UI")
                                    // Don't add to validChatIds - this will cause it to be removed from the local list
                                    return@forEach
                                }
                                
                                // This chat is valid (not hidden), add to the set
                                validChatIds.add(doc.id)
                                
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
                                                
                                                // Get online status from user data
                                                val isOnline = userData["isOnline"] as? Boolean ?: false
                                                
                                                val itemTitle = data["itemTitle"]?.toString() ?: ""
                                                val itemId = data["itemId"]?.toString() ?: ""
                                                val itemImageUrl = data["itemImageUrl"]?.toString()
                                                
                                                val chatItem = ChatItem(
                                                    chatId = doc.id,
                                                    otherUserId = otherUserId,
                                                    otherUserName = otherUserName,
                                                    otherUserAvatar = otherUserAvatar,
                                                    lastMessage = data["lastMessage"]?.toString() ?: "",
                                                    lastMessageTime = (data["lastMessageTime"] as? Long) ?: 0L,
                                                    unreadCount = (data["unreadCount_$currentUserId"] as? Long)?.toInt() ?: 0,
                                                    isOnline = isOnline,
                                                    itemTitle = itemTitle,
                                                    itemId = itemId,
                                                    itemImageUrl = itemImageUrl
                                                )
                                                
                                                // If item information is missing, try to fetch it from the most recent message
                                                if (itemTitle.isEmpty() && itemId.isEmpty()) {
                                                    fetchItemInfoFromMessages(doc.id, chatItem)
                                                }
                                                
                                                // Update UI on main thread to avoid race conditions
                                                runOnUiThread {
                                                    try {
                                                        // Check if chat already exists in the list
                                                        val existingChatIndex = chats.indexOfFirst { it.chatId == chatItem.chatId }
                                                        if (existingChatIndex != -1) {
                                                            // Update existing chat instead of adding duplicate
                                                            chats[existingChatIndex] = chatItem
                                                            android.util.Log.d("MessagesActivity", "Updated existing chat ${chatItem.chatId}")
                                                            // Sort the list
                                                            chats.sortByDescending { it.lastMessageTime }
                                                            // Notify adapter of the change
                                                            recyclerView.adapter?.notifyItemChanged(chats.indexOfFirst { it.chatId == chatItem.chatId })
                                                        } else {
                                                            // Add new chat
                                                            chats.add(chatItem)
                                                            android.util.Log.d("MessagesActivity", "Added new chat ${chatItem.chatId}")
                                                            // Sort the list
                                                            chats.sortByDescending { it.lastMessageTime }
                                                            // Notify adapter of the insertion
                                                            val newPosition = chats.indexOfFirst { it.chatId == chatItem.chatId }
                                                            recyclerView.adapter?.notifyItemInserted(newPosition)
                                                        }
                                                        
                                                        if (chats.isEmpty()) {
                                                            showEmptyState()
                                                        } else {
                                                            emptyState.visibility = View.GONE
                                                        }
                                                        
                                                        // Start monitoring online status for this user
                                                        startOnlineStatusMonitoring(otherUserId)
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("MessagesActivity", "Error updating UI: ${e.message}")
                                                        // Fallback to full refresh if there's an issue
                                                        recyclerView.adapter?.notifyDataSetChanged()
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
                        
                        // After processing all chats, remove any chats from the local list that are hidden or deleted
                        runOnUiThread {
                            try {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                
                                // Find chats that are in the local list but not in the valid chats (they've been hidden/deleted)
                                val chatsToRemove = chats.filter { !validChatIds.contains(it.chatId) }
                                
                                if (chatsToRemove.isNotEmpty()) {
                                    android.util.Log.d("MessagesActivity", "Removing ${chatsToRemove.size} hidden/deleted chat(s) from UI")
                                    
                                    chatsToRemove.forEach { chatToRemove ->
                                        val position = chats.indexOfFirst { it.chatId == chatToRemove.chatId }
                                        if (position != -1) {
                                            chats.removeAt(position)
                                            recyclerView.adapter?.notifyItemRemoved(position)
                                            android.util.Log.d("MessagesActivity", "Removed chat ${chatToRemove.chatId} at position $position")
                                        }
                                    }
                                    
                                    // Update empty state
                                    if (chats.isEmpty()) {
                                        showEmptyState()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MessagesActivity", "Error removing hidden chats: ${e.message}")
                                // Fallback to full refresh
                                recyclerView.adapter?.notifyDataSetChanged()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MessagesActivity", "Error in snapshot listener: ${e.message}", e)
                        e.printStackTrace()
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                Toast.makeText(this, "Error loading chats: ${e.message}", Toast.LENGTH_SHORT).show()
                                showEmptyState()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("MessagesActivity", "Error setting up chats listener: ${e.message}", e)
            e.printStackTrace()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Error setting up chats: ${e.message}", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                }
            }
        }
    }

    private fun refreshChats() {
        android.util.Log.d("MessagesActivity", "Refreshing chats...")
        
        // Stop the current listener
        chatsListener?.remove()
        
        // Clear current chats on main thread
        runOnUiThread {
            val itemCount = chats.size
            chats.clear()
            recyclerView.adapter?.notifyItemRangeRemoved(0, itemCount)
        }
        
        // Reload chats
        loadChats()
        
        // Stop refresh animation when data is loaded (handled in loadChats)
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
                                    
                                    val itemTitle = data["itemTitle"]?.toString() ?: ""
                                    val itemId = data["itemId"]?.toString() ?: ""
                                    val itemImageUrl = data["itemImageUrl"]?.toString()
                                    
                                    val chatItem = ChatItem(
                                        chatId = doc.id,
                                        otherUserId = otherUserId,
                                        otherUserName = otherUserName,
                                        otherUserAvatar = otherUserAvatar,
                                        lastMessage = data["lastMessage"]?.toString() ?: "",
                                        lastMessageTime = (data["lastMessageTime"] as? Long) ?: 0L,
                                        unreadCount = (data["unreadCount_$currentUserId"] as? Long)?.toInt() ?: 0,
                                        itemTitle = itemTitle,
                                        itemId = itemId,
                                        itemImageUrl = itemImageUrl
                                    )
                                    
                                    // If item information is missing, try to fetch it from the most recent message
                                    if (itemTitle.isEmpty() && itemId.isEmpty()) {
                                        fetchItemInfoFromMessages(doc.id, chatItem)
                                    }
                                    
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

    override fun onStop() {
        super.onStop()
        // Remove listeners in onStop to prevent updates after user leaves the screen
        android.util.Log.d("MessagesActivity", "onStop: Removing listeners")
        try {
            chatsListener?.remove()
            chatsListener = null
        } catch (e: Exception) {
            android.util.Log.e("MessagesActivity", "Error removing chatsListener: ${e.message}")
        }
        
        // Remove all online status listeners
        try {
            onlineStatusListeners.values.forEach { it.remove() }
            onlineStatusListeners.clear()
        } catch (e: Exception) {
            android.util.Log.e("MessagesActivity", "Error removing onlineStatusListeners: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("MessagesActivity", "onDestroy: Final cleanup")
        // Double-check listeners are removed
        try {
            chatsListener?.remove()
            chatsListener = null
        } catch (e: Exception) {
            android.util.Log.e("MessagesActivity", "Error in onDestroy chatsListener: ${e.message}")
        }
        
        try {
            onlineStatusListeners.values.forEach { it.remove() }
            onlineStatusListeners.clear()
        } catch (e: Exception) {
            android.util.Log.e("MessagesActivity", "Error in onDestroy onlineStatusListeners: ${e.message}")
        }
    }
    
    private fun startOnlineStatusMonitoring(userId: String) {
        // Don't create duplicate listeners
        if (onlineStatusListeners.containsKey(userId)) return
        
        android.util.Log.d("MessagesActivity", "Starting online status monitoring for user: $userId")
        
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, exception ->
                // CRITICAL: Immediately return if activity is finishing or destroyed
                if (isFinishing || isDestroyed) {
                    android.util.Log.d("MessagesActivity", "Status update received but activity is finishing/destroyed, ignoring")
                    return@addSnapshotListener
                }
                
                if (exception != null) {
                    android.util.Log.e("MessagesActivity", "Error monitoring online status for $userId: ${exception.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val lastSeen = snapshot.getLong("lastSeen")
                    
                    android.util.Log.d("MessagesActivity", "User $userId online status: $isOnline, lastSeen: $lastSeen")
                    
                    // Update the chat item with new online status
                    synchronized(chats) {
                        val chatIndex = chats.indexOfFirst { it.otherUserId == userId }
                        if (chatIndex >= 0) {
                            val updatedChat = chats[chatIndex].copy(isOnline = isOnline)
                            chats[chatIndex] = updatedChat
                            
                            // Update UI on main thread
                            runOnUiThread {
                                recyclerView.adapter?.notifyItemChanged(chatIndex)
                            }
                        }
                    }
                }
            }
        
        onlineStatusListeners[userId] = listener
    }
    
    private fun fetchItemInfoFromMessages(chatId: String, chatItem: ChatItem) {
        android.util.Log.d("MessagesActivity", "Fetching item info from messages for chat: $chatId")
        
        // Get the most recent message to see if it contains item information
        firestore.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { messagesSnapshot ->
                if (!messagesSnapshot.isEmpty) {
                    val messageDoc = messagesSnapshot.documents.first()
                    val messageData = messageDoc.data
                    
                    if (messageData != null) {
                        val messageItemId = messageData["itemId"]?.toString()
                        val messageItemTitle = messageData["itemTitle"]?.toString()
                        
                        if (!messageItemId.isNullOrEmpty() && !messageItemTitle.isNullOrEmpty()) {
                            android.util.Log.d("MessagesActivity", "Found item info in message: $messageItemTitle")
                            
                            // Update the chat document with item information
                            val updateData = mapOf(
                                "itemId" to messageItemId,
                                "itemTitle" to messageItemTitle,
                                "itemImageUrl" to (messageData["itemImageUrl"]?.toString() ?: "")
                            )
                            
                            firestore.collection("chats").document(chatId)
                                .update(updateData)
                                .addOnSuccessListener {
                                    android.util.Log.d("MessagesActivity", "Updated chat with item information")
                                    
                                    // Update the local chat item
                                    synchronized(chats) {
                                        val chatIndex = chats.indexOfFirst { it.chatId == chatId }
                                        if (chatIndex >= 0) {
                                            val updatedChat = chats[chatIndex].copy(
                                                itemId = messageItemId,
                                                itemTitle = messageItemTitle,
                                                itemImageUrl = messageData["itemImageUrl"]?.toString()
                                            )
                                            chats[chatIndex] = updatedChat
                                            
                                            // Update UI
                                            runOnUiThread {
                                                recyclerView.adapter?.notifyItemChanged(chatIndex)
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("MessagesActivity", "Failed to update chat with item info: ${e.message}")
                                }
                        } else {
                            // Try to get item info from the post if we have the itemId
                            val itemId = messageData["itemId"]?.toString()
                            if (!itemId.isNullOrEmpty()) {
                                fetchItemInfoFromPost(itemId, chatId)
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Failed to fetch messages for item info: ${e.message}")
            }
    }
    
    private fun fetchItemInfoFromPost(itemId: String, chatId: String) {
        android.util.Log.d("MessagesActivity", "Fetching item info from post: $itemId")
        
        // Try to get item info from posts collection
        firestore.collection("posts").document(itemId)
            .get()
            .addOnSuccessListener { postDoc ->
                if (postDoc.exists()) {
                    val postData = postDoc.data
                    if (postData != null) {
                        val itemTitle = postData["title"]?.toString() ?: ""
                        val itemImageUrl = postData["imageUrl"]?.toString() ?: ""
                        
                        if (itemTitle.isNotEmpty()) {
                            android.util.Log.d("MessagesActivity", "Found item info from post: $itemTitle")
                            
                            // Update the chat document with item information
                            val updateData = mapOf(
                                "itemId" to itemId,
                                "itemTitle" to itemTitle,
                                "itemImageUrl" to itemImageUrl
                            )
                            
                            firestore.collection("chats").document(chatId)
                                .update(updateData)
                                .addOnSuccessListener {
                                    android.util.Log.d("MessagesActivity", "Updated chat with post item information")
                                    
                                    // Update the local chat item
                                    synchronized(chats) {
                                        val chatIndex = chats.indexOfFirst { it.chatId == chatId }
                                        if (chatIndex >= 0) {
                                            val updatedChat = chats[chatIndex].copy(
                                                itemId = itemId,
                                                itemTitle = itemTitle,
                                                itemImageUrl = itemImageUrl
                                            )
                                            chats[chatIndex] = updatedChat
                                            
                                            // Update UI
                                            runOnUiThread {
                                                recyclerView.adapter?.notifyItemChanged(chatIndex)
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("MessagesActivity", "Failed to update chat with post item info: ${e.message}")
                                }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MessagesActivity", "Failed to fetch post for item info: ${e.message}")
            }
    }

    inner class ChatsAdapter(
        private val chatList: List<ChatItem>,
        private val onChatClick: (ChatItem) -> Unit
    ) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardChat: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.card_chat)
            val avatar: ImageView = view.findViewById(R.id.iv_avatar)
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
            
            // Set chat title - show "username - item title" format (no separate username field)
            holder.chatTitle.text = if (chat.itemTitle.isNotEmpty()) {
                "${chat.otherUserName} - ${chat.itemTitle}"
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
            
            // Online/Offline indicator
            if (chat.isOnline) {
                holder.onlineIndicator.visibility = View.VISIBLE
                holder.onlineIndicator.setBackgroundResource(R.drawable.online_indicator)
            } else {
                holder.onlineIndicator.visibility = View.VISIBLE
                holder.onlineIndicator.setBackgroundResource(R.drawable.offline_indicator)
            }
            
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
        builder.setMessage("This will remove the chat only from your view. The other user will still have the conversation.")
        builder.setPositiveButton("Delete") { _, _ ->
            val currentUserId = auth.currentUser?.uid ?: return@setPositiveButton
            
            // Update chat with new data model: isHiddenFor and resetHistoryFor
            firestore.collection("chats").document(chat.chatId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val participants = document.get("participants") as? List<String> ?: emptyList()
                        val otherUserId = participants.find { it != currentUserId }
                        
                        if (otherUserId != null) {
                            // Facebook Messenger style: Hide chat and set timestamp to filter old messages
                            // Check if user already has a resetTimestamp to preserve it
                            val existingResetTimestamp = document.get("resetTimestamp") as? Map<String, Long> ?: emptyMap()
                            val currentUserTimestamp = existingResetTimestamp[currentUserId] ?: 0L
                            
                            val deletionTimestamp = System.currentTimeMillis()
                            val updates = hashMapOf<String, Any>(
                                "isHiddenFor.$currentUserId" to true,
                                // Only update timestamp if user doesn't have one, or if they're deleting again (use the latest)
                                "resetTimestamp.$currentUserId" to deletionTimestamp,
                                // Reset unread counter when deleting chat
                                "unreadCount_$currentUserId" to 0
                            )
                            
                            android.util.Log.d("MessagesActivity", "Deleting chat for $currentUserId (Messenger style)")
                            android.util.Log.d("MessagesActivity", "  Previous timestamp: $currentUserTimestamp")
                            android.util.Log.d("MessagesActivity", "  New timestamp: $deletionTimestamp")
                            
                            firestore.collection("chats").document(chat.chatId)
                                .update(updates)
                                .addOnSuccessListener {
                                    android.util.Log.d("MessagesActivity", "Chat marked as hidden and history reset for user")
                                    Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show()
                                    // The real-time listener will automatically remove the chat from the UI
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("MessagesActivity", "Error updating chat visibility: ${e.message}")
                                    Toast.makeText(this, "Failed to delete chat", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MessagesActivity", "Error accessing chat: ${e.message}")
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
