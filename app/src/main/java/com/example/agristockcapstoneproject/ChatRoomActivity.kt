package com.example.agristockcapstoneproject

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
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
        val messageType: String = "text", // text, image, video
        val mediaUrl: String? = null,
        val mediaType: String? = null
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var imageButton: ImageView
    private lateinit var btnOverflow: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var itemInfoCard: View
    private lateinit var itemImage: ImageView
    private lateinit var itemTitle: TextView
    private lateinit var itemPrice: TextView
    private lateinit var itemLocation: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var llUploadProgress: LinearLayout
    private lateinit var tvUploadStatus: TextView
    private lateinit var tvUploadPercentage: TextView
    
    // Image preview variables
    private lateinit var llImagePreview: LinearLayout
    private lateinit var ivImagePreview: ImageView
    private lateinit var tvImageName: TextView
    private lateinit var btnRemoveImage: ImageView
    
    // Selected image data
    private var selectedImageUri: Uri? = null
    private var selectedImageBitmap: android.graphics.Bitmap? = null
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private var messagesListener: ListenerRegistration? = null
    private var statusListener: ListenerRegistration? = null
    
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

        // Get intent data with null safety
        chatId = intent.getStringExtra("chatId") ?: ""
        otherUserId = intent.getStringExtra("otherUserId") ?: ""
        otherUserName = intent.getStringExtra("otherUserName") ?: ""
        otherUserAvatar = intent.getStringExtra("otherUserAvatar")
        
        // Validate required data
        if (chatId.isEmpty() || otherUserId.isEmpty()) {
            android.util.Log.e("ChatRoomActivity", "Missing required data - chatId: $chatId, otherUserId: $otherUserId")
            Toast.makeText(this, "Invalid chat data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Debug logging
        android.util.Log.d("ChatRoomActivity", "Intent data - chatId: $chatId, otherUserId: $otherUserId, otherUserName: $otherUserName, otherUserAvatar: $otherUserAvatar")

        try {
            initializeViews()
            setupClickListeners()
            loadMessages()
            loadUserStatus()
            loadItemInfo()
            StatusManager.getInstance().setOnline()
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing chat: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeViews() {
        try {
            recyclerView = findViewById(R.id.rv_messages)
            messageInput = findViewById(R.id.et_message_input)
            sendButton = findViewById(R.id.btn_send)
            imageButton = findViewById(R.id.btn_image)
            btnOverflow = findViewById(R.id.btn_overflow)
            progressBar = findViewById(R.id.progress_bar)
            emptyState = findViewById(R.id.ll_empty_state)
            tvStatus = findViewById(R.id.tv_status)
            llUploadProgress = findViewById(R.id.ll_upload_progress)
            tvUploadStatus = findViewById(R.id.tv_upload_status)
            tvUploadPercentage = findViewById(R.id.tv_upload_percentage)
            
            // Initialize item info card
            itemInfoCard = findViewById(R.id.item_info_card)
            itemImage = findViewById(R.id.iv_item_image)
            itemTitle = findViewById(R.id.tv_item_title)
            itemPrice = findViewById(R.id.tv_item_price)
            itemLocation = findViewById(R.id.tv_item_location)
            
            // Initialize image preview views
            llImagePreview = findViewById(R.id.ll_image_preview)
            ivImagePreview = findViewById(R.id.iv_image_preview)
            tvImageName = findViewById(R.id.tv_image_name)
            btnRemoveImage = findViewById(R.id.btn_remove_image)
            
            // Setup header with null safety
            val tvUsername = findViewById<TextView>(R.id.tv_username)
            val ivAvatar = findViewById<ImageView>(R.id.iv_avatar)
            
        // Set username and avatar, with fallback to load from Firestore if needed
        if (otherUserName.isNotEmpty() && otherUserName != "User") {
            tvUsername.text = otherUserName
        } else {
            tvUsername.text = "Loading..."
            loadUserData()
        }
            
            if (!otherUserAvatar.isNullOrEmpty()) {
                Glide.with(this)
                    .load(otherUserAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_profile)
                // Load user data to get avatar
                loadUserData()
            }
            
            recyclerView.layoutManager = LinearLayoutManager(this)
            // Don't set adapter here - it will be set when messages are loaded
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error initializing views: ${e.message}")
            Toast.makeText(this, "Error initializing chat interface", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadItemInfo() {
        // Get item ID from intent or chat data
        val itemId = intent.getStringExtra("item_id")
        android.util.Log.d("ChatRoomActivity", "Loading item info for itemId: $itemId")
        if (itemId != null && itemId.isNotEmpty()) {
            firestore.collection("posts").document(itemId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val data = document.data
                        if (data != null) {
                            val title = data["title"]?.toString() ?: ""
                            val price = data["price"]?.toString() ?: ""
                            val location = data["location"]?.toString() ?: ""
                            val imageUrl = data["imageUrl"]?.toString()
                            val postType = data["type"]?.toString() ?: "SELL"
                            
                            // Show item info card
                            android.util.Log.d("ChatRoomActivity", "Showing item info card with title: $title, price: $price, type: $postType")
                            itemInfoCard.visibility = View.VISIBLE
                            itemTitle.text = title
                            itemPrice.text = price
                            
                            if (location.isNotEmpty()) {
                                itemLocation.text = location
                                itemLocation.visibility = View.VISIBLE
                            } else {
                                itemLocation.visibility = View.GONE
                            }
                            
                            // Load item image
                            if (!imageUrl.isNullOrEmpty()) {
                                Glide.with(this)
                                    .load(imageUrl)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_image_placeholder)
                                    .error(R.drawable.ic_image_placeholder)
                                    .into(itemImage)
                            } else {
                                itemImage.setImageResource(R.drawable.ic_image_placeholder)
                            }
                            
                            // Set click listener to open correct activity based on post type
                            itemInfoCard.setOnClickListener {
                                try {
                                    val intent = if (postType == "BID") {
                                        Intent(this, ViewBiddingActivity::class.java)
                                    } else {
                                        Intent(this, ItemDetailsActivity::class.java)
                                    }
                                    intent.putExtra("postId", itemId)
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatRoomActivity", "Error opening item details: ${e.message}")
                                    Toast.makeText(this, "Unable to open item details", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        // Item not found or deleted
                        itemInfoCard.visibility = View.GONE
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ChatRoomActivity", "Error loading item info: ${exception.message}")
                    itemInfoCard.visibility = View.GONE
                }
        } else {
            // No item ID, hide the card
            itemInfoCard.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        imageButton.setOnClickListener {
            showMediaSelectionDialog()
        }
        
        btnOverflow.setOnClickListener {
            showOverflowMenu()
        }
        
        // Remove image button
        btnRemoveImage.setOnClickListener {
            clearSelectedImage()
        }
        
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                val hasImage = selectedImageUri != null || selectedImageBitmap != null
                sendButton.isEnabled = hasText || hasImage
                sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f
            }
        })
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) {
            android.util.Log.w("ChatRoomActivity", "ChatId is empty, showing empty state")
            showEmptyState()
            return
        }
        
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            android.util.Log.e("ChatRoomActivity", "User not authenticated")
            Toast.makeText(this, "Please log in to view messages", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            progressBar.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            
            android.util.Log.d("ChatRoomActivity", "Loading messages for chatId: $chatId")
            messagesListener = firestore.collection("messages")
                .whereEqualTo("chatId", chatId)
                .addSnapshotListener { snapshots, exception ->
                    try {
                        progressBar.visibility = View.GONE
                        
                        if (exception != null) {
                            android.util.Log.e("ChatRoomActivity", "Error loading messages: ${exception.message}")
                            
                            // Check if it's a permission error
                            if (exception.message?.contains("PERMISSION_DENIED") == true) {
                                Toast.makeText(this, "Permission denied. Please check your Firestore rules.", Toast.LENGTH_LONG).show()
                            } else if (exception.message?.contains("FAILED_PRECONDITION") == true) {
                                Toast.makeText(this, "Query failed. Please try again.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Error loading messages: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                            
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
                            try {
                                val data = doc.data
                                if (data == null) {
                                    android.util.Log.w("ChatRoomActivity", "Message document ${doc.id} has null data")
                                    return@forEach
                                }
                                
                                android.util.Log.d("ChatRoomActivity", "Message data: $data")
                                
                                val senderId = data["senderId"]?.toString()
                                val receiverId = data["receiverId"]?.toString()
                                val messageText = data["messageText"]?.toString() ?: ""
                                val mediaUrl = data["mediaUrl"]?.toString()
                                val mediaType = data["mediaType"]?.toString()
                                
                                android.util.Log.d("ChatRoomActivity", "Media URL: $mediaUrl, Media Type: $mediaType")
                                
                                // Debug: Check if this is a media message
                                if (!mediaUrl.isNullOrEmpty()) {
                                    android.util.Log.d("ChatRoomActivity", "Found media message: URL=$mediaUrl, Type=$mediaType")
                                }
                                
                                if (senderId.isNullOrEmpty() || receiverId.isNullOrEmpty()) {
                                    android.util.Log.w("ChatRoomActivity", "Message document ${doc.id} has incomplete data")
                                    return@forEach
                                }
                                
                                val message = Message(
                                    id = doc.id,
                                    senderId = senderId,
                                    receiverId = receiverId,
                                    messageText = messageText,
                                    timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                                    readStatus = data["readStatus"] as? Boolean ?: false,
                                    messageType = data["messageType"]?.toString() ?: "text",
                                    mediaUrl = mediaUrl,
                                    mediaType = mediaType
                                )
                                newMessages.add(message)
                            } catch (e: Exception) {
                                android.util.Log.e("ChatRoomActivity", "Error processing message: ${e.message}")
                            }
                        }
                        
                        // Sort messages by timestamp (oldest to newest)
                        newMessages.sortBy { it.timestamp }
                        
                        // Update messages list
                        messages.clear()
                        messages.addAll(newMessages)
                        
                        android.util.Log.d("ChatRoomActivity", "Updated messages list with ${messages.size} messages")
                        
                        // Update adapter smoothly on main thread
                        runOnUiThread {
                            try {
                                if (messages.isNotEmpty()) {
                                    val currentAdapter = recyclerView.adapter as? MessagesAdapter
                                    if (currentAdapter == null) {
                                        // Create new adapter if none exists
                                        val newAdapter = MessagesAdapter(messages.toMutableList(), auth.currentUser?.uid ?: "")
                                        recyclerView.adapter = newAdapter
                                        android.util.Log.d("ChatRoomActivity", "New adapter created with ${messages.size} messages")
                                    } else {
                                        // Update existing adapter smoothly
                                        currentAdapter.updateMessages(messages)
                                        android.util.Log.d("ChatRoomActivity", "Adapter updated with ${messages.size} messages")
                                    }
                                } else {
                                    android.util.Log.d("ChatRoomActivity", "No messages to display")
                                    // Create empty adapter to prevent crashes
                                    if (recyclerView.adapter == null) {
                                        recyclerView.adapter = MessagesAdapter(mutableListOf(), auth.currentUser?.uid ?: "")
                                    }
                                }
                                
                                // Scroll to bottom after a short delay to ensure adapter is ready
                                recyclerView.post {
                                    try {
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatRoomActivity", "Error scrolling to bottom: ${e.message}")
                                    }
                                }
                                
                                if (messages.isEmpty()) {
                                    showEmptyState()
                                } else {
                                    emptyState.visibility = View.GONE
                                }
                                
                                // Mark messages as read
                                markMessagesAsRead()
                            } catch (e: Exception) {
                                android.util.Log.e("ChatRoomActivity", "Error updating UI: ${e.message}")
                                // Fallback: show empty state
                                showEmptyState()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatRoomActivity", "Error in snapshot listener: ${e.message}")
                        Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                        showEmptyState()
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error setting up messages listener: ${e.message}")
            Toast.makeText(this, "Error setting up messages: ${e.message}", Toast.LENGTH_SHORT).show()
            showEmptyState()
        }
    }

    private fun sendMessage() {
        try {
            val messageText = messageInput.text.toString().trim()
            val hasImage = selectedImageUri != null || selectedImageBitmap != null
            
            if (messageText.isEmpty() && !hasImage) return
            
            val currentUserId = auth.currentUser?.uid ?: return
            
            if (hasImage) {
                // Send image message
                if (selectedImageBitmap != null) {
                    uploadImage(selectedImageBitmap!!)
                } else if (selectedImageUri != null) {
                    uploadImageFromUri(selectedImageUri!!)
                }
                // Clear the selected image after starting upload
                clearSelectedImage()
            } else {
                // Send text message
                sendTextMessage(messageText)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error sending message: ${e.message}")
            Toast.makeText(this, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendTextMessage(messageText: String) {
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
                try {
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
                                .addOnFailureListener { e ->
                                    android.util.Log.e("ChatRoomActivity", "Error updating unread count: ${e.message}")
                                }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("ChatRoomActivity", "Error updating chat: ${e.message}")
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
                } catch (e: Exception) {
                    android.util.Log.e("ChatRoomActivity", "Error in message success handler: ${e.message}")
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ChatRoomActivity", "Failed to send message: ${exception.message}")
                Toast.makeText(this, "Failed to send message: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markMessagesAsRead() {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error in markMessagesAsRead: ${e.message}")
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

    override fun onResume() {
        super.onResume()
        StatusManager.getInstance().setOnline()
    }
    
    override fun onPause() {
        super.onPause()
        StatusManager.getInstance().setOffline()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
        statusListener?.remove()
        StatusManager.getInstance().setOffline()
    }
    
    private fun showOverflowMenu() {
        val popup = android.widget.PopupMenu(this, btnOverflow)
        popup.menuInflater.inflate(R.menu.chat_overflow_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_report_user -> {
                    showReportDialog()
                    true
                }
                R.id.action_block_user -> {
                    showBlockDialog()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun showReportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_user, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val rgReasons = dialogView.findViewById<RadioGroup>(R.id.rg_report_reasons)
        val tilOtherReason = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_other_reason)
        val etOtherReason = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_other_reason)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_report)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_submit_report)
        
        // Show/hide other reason input based on selection
        rgReasons.setOnCheckedChangeListener { _, checkedId ->
            tilOtherReason.visibility = if (checkedId == R.id.rb_other) View.VISIBLE else View.GONE
        }
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        
        btnSubmit.setOnClickListener {
            val selectedReason = when (rgReasons.checkedRadioButtonId) {
                R.id.rb_spam -> "Spam"
                R.id.rb_inappropriate -> "Inappropriate behavior"
                R.id.rb_scam -> "Scam or fraud"
                R.id.rb_other -> "Other: ${etOtherReason.text.toString()}"
                else -> "Unknown"
            }
            
            if (selectedReason == "Other: " || (rgReasons.checkedRadioButtonId == R.id.rb_other && etOtherReason.text.toString().trim().isEmpty())) {
                Toast.makeText(this, "Please provide a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            submitReport(selectedReason)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun submitReport(reason: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "You must be logged in to submit a report", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (otherUserId.isEmpty()) {
            Toast.makeText(this, "Cannot report user: Invalid user ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        val reportData = mapOf(
            "reporterId" to currentUserId,
            "reportedUserId" to otherUserId,
            "chatId" to chatId,
            "reason" to reason,
            "timestamp" to System.currentTimeMillis()
        )
        
        android.util.Log.d("ChatRoomActivity", "Submitting report: $reportData")
        
        firestore.collection("reports")
            .add(reportData)
            .addOnSuccessListener {
                android.util.Log.d("ChatRoomActivity", "Report submitted successfully")
                Toast.makeText(this, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                // Ask if user wants to block the reported user
                showBlockConfirmationDialog()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to submit report: ${e.message}")
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    
    private fun showBlockConfirmationDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Block User")
        builder.setMessage("Would you like to block this user? You will no longer receive messages from them.")
        builder.setPositiveButton("Block User") { _, _ ->
            blockUser()
        }
        builder.setNegativeButton("Not Now") { dialog, _ ->
            dialog.dismiss()
        }
        builder.setNeutralButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
    
    private fun showBlockDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_block_user, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_block)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm_block)
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        
        btnConfirm.setOnClickListener {
            blockUser()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun blockUser() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "You must be logged in to block a user", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (otherUserId.isEmpty()) {
            Toast.makeText(this, "Cannot block user: Invalid user ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        val blockData = mapOf(
            "blockerId" to currentUserId,
            "blockedUserId" to otherUserId,
            "timestamp" to System.currentTimeMillis()
        )
        
        android.util.Log.d("ChatRoomActivity", "Blocking user: $blockData")
        
        firestore.collection("blocks")
            .add(blockData)
            .addOnSuccessListener {
                android.util.Log.d("ChatRoomActivity", "User blocked successfully")
                Toast.makeText(this, "User blocked successfully", Toast.LENGTH_SHORT).show()
                finish() // Close the chat room
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to block user: ${e.message}")
                Toast.makeText(this, "Failed to block user: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    
    private fun showMediaSelectionDialog() {
        val options = arrayOf("Camera", "Gallery", "Video")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Media")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
                2 -> openVideoGallery()
            }
        }
        builder.show()
    }
    
    private fun clearSelectedImage() {
        selectedImageUri = null
        selectedImageBitmap = null
        llImagePreview.visibility = View.GONE
        updateSendButtonState()
    }
    
    private fun showImagePreview(uri: Uri?, bitmap: android.graphics.Bitmap?) {
        selectedImageUri = uri
        selectedImageBitmap = bitmap
        
        if (bitmap != null) {
            ivImagePreview.setImageBitmap(bitmap)
        } else if (uri != null) {
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(ivImagePreview)
        }
        
        tvImageName.text = "image.jpg"
        llImagePreview.visibility = View.VISIBLE
        updateSendButtonState()
    }
    
    private fun updateSendButtonState() {
        val hasText = !messageInput.text.toString().trim().isEmpty()
        val hasImage = selectedImageUri != null || selectedImageBitmap != null
        sendButton.isEnabled = hasText || hasImage
        sendButton.alpha = if (sendButton.isEnabled) 1.0f else 0.5f
    }
    
    private fun showMessageContextMenu(anchor: View, message: Message, position: Int) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.message_context_menu, popup.menu)
        
        // Show/hide menu items based on message type
        val menu = popup.menu
        menu.findItem(R.id.action_copy_message).isVisible = !message.messageText.isNullOrEmpty()
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_message -> {
                    deleteMessage(message, position)
                    true
                }
                R.id.action_copy_message -> {
                    copyMessageText(message)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun deleteMessage(message: Message, position: Int) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Message")
        builder.setMessage("Are you sure you want to delete this message? This action cannot be undone.")
        builder.setPositiveButton("Delete") { _, _ ->
            // Delete from Firestore
            firestore.collection("messages").document(message.id)
                .delete()
                .addOnSuccessListener {
                    android.util.Log.d("ChatRoomActivity", "Message deleted successfully")
                    Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ChatRoomActivity", "Error deleting message: ${e.message}")
                    Toast.makeText(this, "Failed to delete message", Toast.LENGTH_SHORT).show()
                }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun copyMessageText(message: Message) {
        if (!message.messageText.isNullOrEmpty()) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Message", message.messageText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No text to copy", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            return
        }
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
    
    private fun openVideoGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        videoLauncher.launch(intent)
    }
    
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val photo = result.data?.extras?.get("data") as? android.graphics.Bitmap
            if (photo != null) {
                showImagePreview(null, photo)
            }
        }
    }
    
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                showImagePreview(uri, null)
            }
        }
    }
    
    private val videoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                uploadVideo(uri)
            }
        }
    }
    
    private fun uploadImage(bitmap: android.graphics.Bitmap) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "You must be logged in to upload images", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show progress indicator
        showUploadProgress("Uploading image...")
        
        // Convert bitmap to byte array
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()
        
        val fileName = "chat_images/${currentUserId}_${System.currentTimeMillis()}.jpg"
        val storageRef = storage.reference.child(fileName)
        
        // Add metadata
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("userId", currentUserId)
            .setCustomMetadata("uploadTime", System.currentTimeMillis().toString())
            .build()
        
        val uploadTask = storageRef.putBytes(data, metadata)
        
        // Add progress listener
        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
            updateUploadProgress(progress)
        }
        
        uploadTask.addOnSuccessListener { taskSnapshot ->
            android.util.Log.d("ChatRoomActivity", "Image uploaded successfully")
            taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                android.util.Log.d("ChatRoomActivity", "Got download URL: $uri")
                sendMediaMessage(uri.toString(), "image")
                hideUploadProgress()
                Toast.makeText(this, "Image uploaded successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to get download URL: ${e.message}")
                hideUploadProgress()
                Toast.makeText(this, "Failed to get image URL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("ChatRoomActivity", "Failed to upload image: ${e.message}")
            hideUploadProgress()
            Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun uploadImageFromUri(uri: Uri) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "You must be logged in to upload images", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show progress indicator
        showUploadProgress("Uploading image...")
        
        val fileName = "chat_images/${currentUserId}_${System.currentTimeMillis()}.jpg"
        val storageRef = storage.reference.child(fileName)
        
        // Add metadata
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("userId", currentUserId)
            .setCustomMetadata("uploadTime", System.currentTimeMillis().toString())
            .build()
        
        val uploadTask = storageRef.putFile(uri, metadata)
        
        // Add progress listener
        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
            updateUploadProgress(progress)
        }
        
        uploadTask.addOnSuccessListener { taskSnapshot ->
            android.util.Log.d("ChatRoomActivity", "Image uploaded successfully")
            taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                android.util.Log.d("ChatRoomActivity", "Got download URL: $downloadUri")
                sendMediaMessage(downloadUri.toString(), "image")
                hideUploadProgress()
                Toast.makeText(this, "Image uploaded successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to get download URL: ${e.message}")
                hideUploadProgress()
                Toast.makeText(this, "Failed to get image URL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("ChatRoomActivity", "Failed to upload image: ${e.message}")
            hideUploadProgress()
            Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun uploadVideo(uri: Uri) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "You must be logged in to upload videos", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show progress
        Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show()
        
        val fileName = "chat_videos/${currentUserId}_${System.currentTimeMillis()}.mp4"
        val storageRef = storage.reference.child(fileName)
        
        // Add metadata
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("video/mp4")
            .setCustomMetadata("userId", currentUserId)
            .setCustomMetadata("uploadTime", System.currentTimeMillis().toString())
            .build()
        
        val uploadTask = storageRef.putFile(uri, metadata)
        
        uploadTask.addOnSuccessListener { taskSnapshot ->
            android.util.Log.d("ChatRoomActivity", "Video uploaded successfully")
            taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                android.util.Log.d("ChatRoomActivity", "Got download URL: $downloadUri")
                sendMediaMessage(downloadUri.toString(), "video")
                Toast.makeText(this, "Video uploaded successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to get download URL: ${e.message}")
                Toast.makeText(this, "Failed to get video URL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("ChatRoomActivity", "Failed to upload video: ${e.message}")
            Toast.makeText(this, "Failed to upload video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun sendMediaMessage(mediaUrl: String, mediaType: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val messageData = mapOf(
            "senderId" to currentUserId,
            "receiverId" to otherUserId,
            "messageText" to "",
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "chatId" to chatId
        )
        
        android.util.Log.d("ChatRoomActivity", "Sending media message: $messageData")
        firestore.collection("messages")
            .add(messageData)
            .addOnSuccessListener { messageDoc ->
                android.util.Log.d("ChatRoomActivity", "Media message saved successfully with ID: ${messageDoc.id}")
                // Update chat with last message
                updateChatLastMessage("📷 Media message", messageDoc.id)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to send media message: ${e.message}")
                Toast.makeText(this, "Failed to send media message", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateChatLastMessage(lastMessage: String, messageId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val updateData = mapOf(
            "lastMessage" to lastMessage,
            "lastMessageTime" to System.currentTimeMillis(),
            "lastMessageId" to messageId
        )
        
        firestore.collection("chats").document(chatId)
            .update(updateData)
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Failed to update chat: ${e.message}")
            }
    }
    
    private fun showUploadProgress(status: String) {
        runOnUiThread {
            llUploadProgress.visibility = View.VISIBLE
            tvUploadStatus.text = status
            tvUploadPercentage.text = "0%"
        }
    }
    
    private fun hideUploadProgress() {
        runOnUiThread {
            llUploadProgress.visibility = View.GONE
        }
    }
    
    private fun updateUploadProgress(percentage: Int) {
        runOnUiThread {
            tvUploadPercentage.text = "$percentage%"
        }
    }

    inner class MessagesAdapter(
        private val messageList: MutableList<Message>,
        private val currentUserId: String
    ) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {
        
        init {
            android.util.Log.d("ChatRoomActivity", "MessagesAdapter created with ${messageList.size} messages")
        }

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageBubble: LinearLayout = view.findViewById(R.id.ll_message_bubble)
        val messageText: TextView = view.findViewById(R.id.tv_message_text)
        val messageImage: ImageView = view.findViewById(R.id.iv_message_image)
        val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
        val readStatus: TextView? = view.findViewById(R.id.tv_read_status)
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
            try {
                if (position < 0 || position >= messageList.size) {
                    android.util.Log.e("ChatRoomActivity", "Invalid position $position, list size: ${messageList.size}")
                    return
                }
                
                val message = messageList[position]
                val isSent = message.senderId == currentUserId
                val isLastMessage = position == messageList.size - 1
                
                android.util.Log.d("ChatRoomActivity", "Binding message $position: ${message.messageText} (sent: $isSent)")
                
                // Handle media messages (images and videos)
                android.util.Log.d("ChatRoomActivity", "Checking message for media: mediaUrl=${message.mediaUrl}, mediaType=${message.mediaType}")
                if (!message.mediaUrl.isNullOrEmpty() && (message.mediaType == "image" || message.mediaType == "video")) {
                    android.util.Log.d("ChatRoomActivity", "Displaying media message: ${message.mediaUrl}")
                    // Show image and hide text
                    holder.messageImage.visibility = View.VISIBLE
                    holder.messageText.visibility = View.GONE
                    
                    android.util.Log.d("ChatRoomActivity", "Image view visibility set to VISIBLE")
                    
                    if (message.mediaType == "image") {
                        // Load image with Glide
                        Glide.with(this@ChatRoomActivity)
                            .load(message.mediaUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_placeholder)
                            .into(holder.messageImage)
                            
                        android.util.Log.d("ChatRoomActivity", "Loading image: ${message.mediaUrl}")
                    } else if (message.mediaType == "video") {
                        // For videos, show a play icon overlay
                        holder.messageImage.setImageResource(R.drawable.ic_play_circle)
                        android.util.Log.d("ChatRoomActivity", "Loading video: ${message.mediaUrl}")
                    }
                } else {
                    // Show text and hide image
                    holder.messageImage.visibility = View.GONE
                    holder.messageText.visibility = View.VISIBLE
                    holder.messageText.text = message.messageText ?: ""
                }
                
                holder.timestamp.text = formatTimestamp(message.timestamp)
                
                // Set up styling based on message type
                if (isSent) {
                    // Sent message styling - yellow bubble with black text
                    holder.messageText.setTextColor(ContextCompat.getColor(this@ChatRoomActivity, android.R.color.black))
                    
                    // Show read status for sent messages
                    holder.readStatus?.let { readStatusView ->
                        readStatusView.visibility = View.VISIBLE
                        // Show ✓ for sent, 👁 for read by other user
                        readStatusView.text = if (message.readStatus) "👁" else "✓"
                    }
                } else {
                    // Received message styling - light gray bubble
                    holder.messageText.setTextColor(ContextCompat.getColor(this@ChatRoomActivity, R.color.text_primary))
                    
                    // Hide read status for received messages (we don't show read status for messages we received)
                    holder.readStatus?.let { readStatusView ->
                        readStatusView.visibility = View.GONE
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
                
                // Set up long press listener for context menu
                holder.messageBubble.setOnLongClickListener {
                    showMessageContextMenu(holder.messageBubble, message, position)
                    true
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRoomActivity", "Error binding view holder: ${e.message}")
            }
        }

        override fun getItemCount(): Int = messageList.size
        
        fun updateMessages(newMessages: List<Message>) {
            try {
                // Update the message list and notify adapter of changes
                messageList.clear()
                messageList.addAll(newMessages)
                notifyDataSetChanged()
                android.util.Log.d("ChatRoomActivity", "Adapter updated with ${newMessages.size} messages")
            } catch (e: Exception) {
                android.util.Log.e("ChatRoomActivity", "Error updating adapter: ${e.message}")
            }
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
        if (isSent) {
            holder.readStatus?.text = if (readStatus) "👁" else "✓"
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
    
    private fun loadUserData() {
        if (otherUserId.isEmpty()) return
        
        firestore.collection("users").document(otherUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data!!
                    
                    // Update username if not already set or if it's just "User"
                    if (otherUserName.isEmpty() || otherUserName == "User") {
                        val username = data["username"]?.toString() ?: 
                            "${data["firstName"] ?: ""} ${data["lastName"] ?: ""}".trim()
                                .ifEmpty { data["displayName"]?.toString() ?: "User" }
                        
                        otherUserName = username
                        findViewById<TextView>(R.id.tv_username).text = username
                    }
                    
                    // Update avatar if not already set
                    if (otherUserAvatar.isNullOrEmpty()) {
                        val avatarUrl = data["avatarUrl"]?.toString()
                        if (!avatarUrl.isNullOrEmpty()) {
                            otherUserAvatar = avatarUrl
                            Glide.with(this)
                                .load(avatarUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .into(findViewById(R.id.iv_avatar))
                        }
                    }
                } else {
                    findViewById<TextView>(R.id.tv_username).text = "User"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatRoomActivity", "Error loading user data: ${e.message}")
                findViewById<TextView>(R.id.tv_username).text = "User"
            }
    }
    
    private fun loadUserStatus() {
        if (otherUserId.isEmpty()) return
        
        // Set up real-time status listener
        statusListener = firestore.collection("users").document(otherUserId)
            .addSnapshotListener { document, exception ->
                if (exception != null) {
                    android.util.Log.e("ChatRoomActivity", "Error listening to user status: ${exception.message}")
                    updateStatusDisplay(false, null)
                    return@addSnapshotListener
                }
                
                if (document != null && document.exists()) {
                    val isOnline = document.getBoolean("isOnline") ?: false
                    val lastSeen = document.getLong("lastSeen")
                    updateStatusDisplay(isOnline, lastSeen)
                } else {
                    updateStatusDisplay(false, null)
                }
            }
    }
    
    private fun updateStatusDisplay(isOnline: Boolean, lastSeen: Long?) {
        runOnUiThread {
            try {
                if (isOnline) {
                    tvStatus.text = "Online"
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                } else {
                    val statusText = if (lastSeen != null) {
                        val timeAgo = getTimeAgo(lastSeen)
                        "Last seen $timeAgo"
                    } else {
                        "Offline"
                    }
                    tvStatus.text = statusText
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRoomActivity", "Error updating status display: ${e.message}")
            }
        }
    }
    
    private fun getTimeAgo(lastSeen: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - lastSeen
        
        return when {
            diff < 60 * 1000 -> "just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            else -> "${diff / (24 * 60 * 60 * 1000)}d ago"
        }
    }
    
    
    private fun updateCurrentUserStatus(isOnline: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        try {
            val statusData = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis()
            )
            
            firestore.collection("users").document(currentUserId)
                .update(statusData)
                .addOnSuccessListener {
                    android.util.Log.d("ChatRoomActivity", "User status updated: isOnline=$isOnline")
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("ChatRoomActivity", "Failed to update user status: ${exception.message}")
                    // Retry once after a short delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        firestore.collection("users").document(currentUserId)
                            .update(statusData)
                            .addOnFailureListener { retryException ->
                                android.util.Log.e("ChatRoomActivity", "Retry failed to update user status: ${retryException.message}")
                            }
                    }, 1000)
                }
        } catch (e: Exception) {
            android.util.Log.e("ChatRoomActivity", "Error in updateCurrentUserStatus: ${e.message}")
        }
    }
}

