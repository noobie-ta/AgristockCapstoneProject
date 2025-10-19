package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

class ChatRoomActivity : AppCompatActivity() {

    data class Message(
        val id: String = "",
        val senderId: String = "",
        val receiverId: String = "",
        val messageText: String = "",
        val timestamp: Long = 0L,
        val readStatus: Boolean = false,
        val messageType: String = "text" // text or image
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var imageButton: ImageView
    private lateinit var btnReport: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var tvStatus: TextView
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var messagesListener: ListenerRegistration? = null
    
    private var chatId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""
    private var otherUserAvatar: String? = null
    private val messages = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        // Get intent data
        chatId = intent.getStringExtra("chatId") ?: ""
        otherUserId = intent.getStringExtra("otherUserId") ?: ""
        otherUserName = intent.getStringExtra("otherUserName") ?: ""
        otherUserAvatar = intent.getStringExtra("otherUserAvatar")

        initializeViews()
        setupClickListeners()
        loadMessages()
        setupOnlineStatus()
        updateCurrentUserStatus(true)
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.rv_messages)
        messageInput = findViewById(R.id.et_message_input)
        sendButton = findViewById(R.id.btn_send)
        imageButton = findViewById(R.id.btn_image)
        btnReport = findViewById(R.id.btn_report)
        progressBar = findViewById(R.id.progress_bar)
        emptyState = findViewById(R.id.ll_empty_state)
        tvStatus = findViewById(R.id.tv_status)
        
        // Setup header
        val tvUsername = findViewById<TextView>(R.id.tv_username)
        val ivAvatar = findViewById<ImageView>(R.id.iv_avatar)
        
        tvUsername.text = otherUserName
        
        if (!otherUserAvatar.isNullOrEmpty()) {
            Glide.with(this)
                .load(otherUserAvatar)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_profile)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        // Don't set adapter here - it will be set when messages are loaded
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        imageButton.setOnClickListener {
            // TODO: Implement image upload
            Toast.makeText(this, "Image upload coming soon", Toast.LENGTH_SHORT).show()
        }
        
        btnReport.setOnClickListener {
            // TODO: Implement report functionality
            Toast.makeText(this, "Report functionality coming soon", Toast.LENGTH_SHORT).show()
        }
        
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrBlank()
                sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f
            }
        })
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) {
            showEmptyState()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        
        messagesListener = firestore.collection("messages")
            .whereEqualTo("chatId", chatId)
            .addSnapshotListener { snapshots, exception ->
                progressBar.visibility = View.GONE
                
                if (exception != null) {
                    Toast.makeText(this, "Error loading messages: ${exception.message}", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                    return@addSnapshotListener
                }

                // Create new messages list to avoid duplication
                val newMessages = mutableListOf<Message>()
                android.util.Log.d("ChatRoomActivity", "Loading messages for chatId: $chatId")
                android.util.Log.d("ChatRoomActivity", "Found ${snapshots?.documents?.size ?: 0} messages")
                
                if (snapshots?.documents?.isEmpty() == true) {
                    android.util.Log.d("ChatRoomActivity", "No messages found in Firestore")
                }
                
                snapshots?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    android.util.Log.d("ChatRoomActivity", "Message data: $data")
                    val message = Message(
                        id = doc.id,
                        senderId = data["senderId"]?.toString() ?: "",
                        receiverId = data["receiverId"]?.toString() ?: "",
                        messageText = data["messageText"]?.toString() ?: "",
                        timestamp = (data["timestamp"] as? Long) ?: 0L,
                        readStatus = data["readStatus"] as? Boolean ?: false,
                        messageType = data["messageType"]?.toString() ?: "text"
                    )
                    newMessages.add(message)
                }
                
                // Sort messages by timestamp (oldest to newest)
                newMessages.sortBy { it.timestamp }
                
                // Update messages list
                messages.clear()
                messages.addAll(newMessages)
                
                android.util.Log.d("ChatRoomActivity", "Updated messages list with ${messages.size} messages")
                
                // Update adapter smoothly
                if (messages.isNotEmpty()) {
                    val currentAdapter = recyclerView.adapter as? MessagesAdapter
                    if (currentAdapter == null) {
                        // Create new adapter if none exists
                        recyclerView.adapter = MessagesAdapter(messages.toMutableList(), auth.currentUser?.uid ?: "")
                        android.util.Log.d("ChatRoomActivity", "New adapter created with ${messages.size} messages")
                    } else {
                        // Update existing adapter smoothly
                        currentAdapter.updateMessages(messages)
                        android.util.Log.d("ChatRoomActivity", "Adapter updated with ${messages.size} messages")
                    }
                } else {
                    android.util.Log.d("ChatRoomActivity", "No messages to display")
                }
                
                scrollToBottom()
                
                if (messages.isEmpty()) {
                    showEmptyState()
                } else {
                    emptyState.visibility = View.GONE
                }
                
                // Mark messages as read
                markMessagesAsRead()
            }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isEmpty()) return
        
        val currentUserId = auth.currentUser?.uid ?: return
        
        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "receiverId" to otherUserId,
            "messageText" to messageText,
            "timestamp" to System.currentTimeMillis(),
            "readStatus" to false,
            "messageType" to "text",
            "chatId" to chatId
        )
        
        // Save message to messages collection
        android.util.Log.d("ChatRoomActivity", "Sending message: $messageData")
        firestore.collection("messages").add(messageData)
            .addOnSuccessListener { docRef ->
                android.util.Log.d("ChatRoomActivity", "Message saved successfully with ID: ${docRef.id}")
                
                // Update chat document with latest message
                val chatData = mapOf(
                    "lastMessage" to messageText,
                    "lastMessageTime" to System.currentTimeMillis(),
                    "lastMessageSender" to currentUserId
                )
                
                firestore.collection("chats").document(chatId).update(chatData)
                    .addOnSuccessListener {
                        // Increment unread count for receiver
                        val unreadField = "unreadCount_$otherUserId"
                        firestore.collection("chats").document(chatId)
                            .update(unreadField, com.google.firebase.firestore.FieldValue.increment(1))
                            .addOnSuccessListener {
                                android.util.Log.d("ChatRoomActivity", "Unread count incremented for receiver")
                            }
                    }
                
                messageInput.text.clear()
                
                // Add haptic feedback for better UX (with try-catch to prevent crashes)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore vibration errors to prevent crashes
                    android.util.Log.d("ChatRoomActivity", "Vibration not available: ${e.message}")
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ChatRoomActivity", "Failed to send message: ${exception.message}")
                Toast.makeText(this, "Failed to send message: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markMessagesAsRead() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        // Mark all received messages as read (messages sent TO current user)
        val batch = firestore.batch()
        val messagesToMarkAsRead = messages.filter { 
            it.receiverId == currentUserId && !it.readStatus 
        }
        
        if (messagesToMarkAsRead.isNotEmpty()) {
            messagesToMarkAsRead.forEach { message ->
                val messageRef = firestore.collection("messages").document(message.id)
                batch.update(messageRef, "readStatus", true)
                android.util.Log.d("ChatRoomActivity", "Marking message ${message.id} as read")
            }
            
            batch.commit()
                .addOnSuccessListener {
                    android.util.Log.d("ChatRoomActivity", "Messages marked as read successfully")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ChatRoomActivity", "Error marking messages as read: ${e.message}")
                }
        }
        
        // Reset unread count for current user
        val unreadField = "unreadCount_$currentUserId"
        firestore.collection("chats").document(chatId)
            .update(unreadField, 0)
            .addOnSuccessListener {
                android.util.Log.d("ChatRoomActivity", "Unread count reset for user $currentUserId")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Error resetting unread count: ${e.message}")
            }
    }

    private fun scrollToBottom() {
        try {
            if (messages.isNotEmpty() && recyclerView.adapter != null) {
                recyclerView.post {
                    try {
                        recyclerView.smoothScrollToPosition(messages.size - 1)
                    } catch (e: Exception) {
                        android.util.Log.d("ChatRoomActivity", "Error scrolling: ${e.message}")
                        // Fallback to regular scroll
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error in scrollToBottom: ${e.message}")
        }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
        updateCurrentUserStatus(false)
    }

    inner class MessagesAdapter(
        private val messageList: MutableList<Message>,
        private val currentUserId: String
    ) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageBubble: LinearLayout = view.findViewById(R.id.ll_message_bubble)
        val messageText: TextView = view.findViewById(R.id.tv_message_text)
        val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
        val readStatus: TextView = view.findViewById(R.id.tv_read_status)
        val messageStatus: LinearLayout = view.findViewById(R.id.ll_message_status)
    }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layoutRes = if (viewType == 0) R.layout.item_message_received else R.layout.item_message
            val view = LayoutInflater.from(parent.context)
                .inflate(layoutRes, parent, false)
            return MessageViewHolder(view)
        }
        
        override fun getItemViewType(position: Int): Int {
            val message = messageList[position]
            return if (message.senderId == currentUserId) 1 else 0 // 1 for sent, 0 for received
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messageList[position]
            val isSent = message.senderId == currentUserId
            val isLastMessage = position == messageList.size - 1
            
            android.util.Log.d("ChatRoomActivity", "Binding message $position: ${message.messageText} (sent: $isSent)")
            
            holder.messageText.text = message.messageText
            holder.timestamp.text = formatTimestamp(message.timestamp)
            
            // Set up styling based on message type
            if (isSent) {
                // Sent message styling - yellow bubble with black text
                holder.messageText.setTextColor(ContextCompat.getColor(this@ChatRoomActivity, android.R.color.black))
                
                // Show read status for sent messages
                if (holder.readStatus != null) {
                    holder.readStatus.visibility = View.VISIBLE
                    // Show ✓ for sent, 👁 for read by other user
                    holder.readStatus.text = if (message.readStatus) "👁" else "✓"
                }
            } else {
                // Received message styling - light gray bubble
                holder.messageText.setTextColor(ContextCompat.getColor(this@ChatRoomActivity, R.color.text_primary))
                
                // Hide read status for received messages (we don't show read status for messages we received)
                if (holder.readStatus != null) {
                    holder.readStatus.visibility = View.GONE
                }
            }
            
            // Only show timestamp and status for the latest message by default
            if (isLastMessage) {
                holder.messageStatus.visibility = View.VISIBLE
                holder.messageStatus.alpha = 1.0f
            } else {
                holder.messageStatus.visibility = View.GONE
                holder.messageStatus.alpha = 0.0f
            }
            
            // Set up click listener for message bubble with animation
            holder.messageBubble.setOnClickListener {
                toggleMessageStatus(holder, message.timestamp, message.readStatus, isSent)
                // Add tap animation
                animateMessageTap(holder.messageBubble)
            }
        }

        override fun getItemCount(): Int = messageList.size
        
        fun updateMessages(newMessages: List<Message>) {
            // Update the message list and notify adapter of changes
            messageList.clear()
            messageList.addAll(newMessages)
            notifyDataSetChanged()
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }
    
    private fun formatExactTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
        return format.format(date)
    }
    
    private fun toggleMessageStatus(holder: MessagesAdapter.MessageViewHolder, timestamp: Long, readStatus: Boolean, isSent: Boolean) {
        // Toggle visibility with smooth animation
        if (holder.messageStatus.visibility == View.VISIBLE) {
            // Hide with fade out animation
            holder.messageStatus.animate()
                .alpha(0.0f)
                .setDuration(200)
                .withEndAction {
                    holder.messageStatus.visibility = View.GONE
                }
        } else {
            // Show with fade in animation
            holder.messageStatus.visibility = View.VISIBLE
            holder.messageStatus.alpha = 0.0f
            holder.messageStatus.animate()
                .alpha(1.0f)
                .setDuration(200)
        }
        
        // Update timestamp and status
        holder.timestamp.text = formatTimestamp(timestamp)
        if (isSent && holder.readStatus != null) {
            holder.readStatus.text = if (readStatus) "👁" else "✓"
        }
    }
    
    private fun hideAllTooltips() {
        // This would need to be implemented with a reference to all view holders
        // For now, we'll use a simple approach with postDelayed
    }
    
    private fun animateMessageTap(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    private fun setupOnlineStatus() {
        if (otherUserId.isEmpty()) {
            tvStatus.text = "Unknown"
            return
        }
        
        // Check if other user is online by looking at their last activity
        firestore.collection("users").document(otherUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val lastSeen = document.getLong("lastSeen")
                    val isOnline = document.getBoolean("isOnline") ?: false
                    
                    if (isOnline) {
                        tvStatus.text = "Online"
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else {
                        tvStatus.text = "Offline"
                        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    }
                } else {
                    tvStatus.text = "Offline"
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Unknown"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
    }
    
    private fun updateCurrentUserStatus(isOnline: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val statusData = mapOf(
            "isOnline" to isOnline,
            "lastSeen" to System.currentTimeMillis()
        )
        
        firestore.collection("users").document(currentUserId)
            .update(statusData)
            .addOnFailureListener { exception ->
                android.util.Log.e("ChatRoomActivity", "Failed to update user status: ${exception.message}")
            }
    }
}

