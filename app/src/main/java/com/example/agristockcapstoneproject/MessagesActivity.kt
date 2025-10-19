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
        val isOnline: Boolean = false
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
        
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        
        chatsListener = firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshots, exception ->
                progressBar.visibility = View.GONE
                
                if (exception != null) {
                    Toast.makeText(this, "Error loading chats: ${exception.message}", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                    return@addSnapshotListener
                }

                chats.clear()
                snapshots?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val participants = data["participants"] as? List<String> ?: return@forEach
                    val otherUserId = participants.find { it != currentUserId } ?: return@forEach
                    
                    // Get other user's info
                    firestore.collection("users").document(otherUserId).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val userData = userDoc.data ?: return@addOnSuccessListener
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
                                
                                chats.add(chatItem)
                                chats.sortByDescending { it.lastMessageTime }
                                recyclerView.adapter?.notifyDataSetChanged()
                                
                                if (chats.isEmpty()) {
                                    showEmptyState()
                                } else {
                                    emptyState.visibility = View.GONE
                                }
                            }
                        }
                }
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
        startActivity(intent)
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
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
            val avatar: ImageView = view.findViewById(R.id.iv_avatar)
            val username: TextView = view.findViewById(R.id.tv_username)
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
            
            holder.username.text = chat.otherUserName
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
            
            // Online indicator (simplified - always show as offline for now)
            holder.onlineIndicator.visibility = View.GONE
            
            holder.itemView.setOnClickListener {
                onChatClick(chat)
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
}
