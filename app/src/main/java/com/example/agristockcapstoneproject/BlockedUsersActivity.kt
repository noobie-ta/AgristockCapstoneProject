package com.example.agristockcapstoneproject

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

class BlockedUsersActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var blockedUsersListener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var emptyStateView: View
    private lateinit var progressBar: ProgressBar

    private val blockedUsersList = mutableListOf<BlockedUser>()
    private lateinit var adapter: BlockedUsersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_users)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Keep status bar white/normal icons per app standard
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        setupRecyclerView()
        loadBlockedUsers()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_blocked_users)
        searchEditText = findViewById(R.id.et_search_blocked)
        emptyStateView = findViewById(R.id.empty_state_view)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }

        // Search functionality
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterBlockedUsers(s.toString())
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = BlockedUsersAdapter(blockedUsersList) { blockedUser, action ->
            when (action) {
                "unblock" -> showUnblockConfirmation(blockedUser)
                "report" -> showReportDialog(blockedUser)
            }
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadBlockedUsers() {
        val user = auth.currentUser
        if (user != null) {
            progressBar.visibility = View.VISIBLE
            
            blockedUsersListener = firestore.collection("blocks")
                .whereEqualTo("blockerId", user.uid)
                .addSnapshotListener { snapshot, exception ->
                    progressBar.visibility = View.GONE
                    
                    if (exception != null) {
                        android.util.Log.e("BlockedUsersActivity", "Error loading blocked users: ${exception.message}")
                        Toast.makeText(this, "Failed to load blocked users", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    blockedUsersList.clear()
                    snapshot?.documents?.forEach { document ->
                        val data = document.data
                        if (data != null) {
                            val blockedUser = BlockedUser(
                                id = document.id,
                                userId = data["blockedUserId"]?.toString() ?: "",
                                username = data["blockedUsername"]?.toString() ?: "Unknown User",
                                avatar = data["blockedUserAvatar"]?.toString(),
                                blockedOn = (data["blockedOn"] as? Long) ?: System.currentTimeMillis(),
                                reason = data["reason"]?.toString() ?: "Not specified"
                            )
                            blockedUsersList.add(blockedUser)
                        }
                    }

                    updateUI()
                }
        }
    }

    private fun filterBlockedUsers(query: String) {
        val filteredList = if (query.isEmpty()) {
            blockedUsersList
        } else {
            blockedUsersList.filter { 
                it.username.contains(query, ignoreCase = true) 
            }
        }
        
        adapter.updateList(filteredList)
    }

    private fun updateUI() {
        if (blockedUsersList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    private fun showUnblockConfirmation(blockedUser: BlockedUser) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Unblock User")
        builder.setMessage("Are you sure you want to unblock ${blockedUser.username}? They will be able to contact you again.")
        builder.setPositiveButton("Unblock") { _, _ ->
            unblockUser(blockedUser)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun unblockUser(blockedUser: BlockedUser) {
        firestore.collection("blocks")
            .document(blockedUser.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "${blockedUser.username} has been unblocked", Toast.LENGTH_SHORT).show()
                android.util.Log.d("BlockedUsersActivity", "User unblocked: ${blockedUser.username}")
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("BlockedUsersActivity", "Error unblocking user: ${exception.message}")
                Toast.makeText(this, "Failed to unblock user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showReportDialog(blockedUser: BlockedUser) {
        val reportReasons = arrayOf(
            "Spam or harassment",
            "Inappropriate behavior",
            "Scam or fraud",
            "Other"
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Report User")
        builder.setMessage("Why are you reporting ${blockedUser.username}?")
        builder.setItems(reportReasons) { _, which ->
            val reason = reportReasons[which]
            submitReport(blockedUser, reason)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun submitReport(blockedUser: BlockedUser, reason: String) {
        val user = auth.currentUser
        if (user != null) {
            val reportData = hashMapOf(
                "reporterId" to user.uid,
                "reportedUserId" to blockedUser.userId,
                "reportedUsername" to blockedUser.username,
                "reason" to reason,
                "timestamp" to System.currentTimeMillis(),
                "status" to "pending"
            )

            firestore.collection("reports")
                .add(reportData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("BlockedUsersActivity", "Report submitted for: ${blockedUser.username}")
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("BlockedUsersActivity", "Error submitting report: ${exception.message}")
                    Toast.makeText(this, "Failed to submit report", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        blockedUsersListener?.remove()
    }

    data class BlockedUser(
        val id: String,
        val userId: String,
        val username: String,
        val avatar: String?,
        val blockedOn: Long,
        val reason: String
    )

    class BlockedUsersAdapter(
        private var blockedUsers: List<BlockedUser>,
        private val onActionClick: (BlockedUser, String) -> Unit
    ) : RecyclerView.Adapter<BlockedUsersAdapter.BlockedUserViewHolder>() {

        fun updateList(newList: List<BlockedUser>) {
            blockedUsers = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BlockedUserViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_user, parent, false)
            return BlockedUserViewHolder(view)
        }

        override fun onBindViewHolder(holder: BlockedUserViewHolder, position: Int) {
            holder.bind(blockedUsers[position], onActionClick)
        }

        override fun getItemCount(): Int = blockedUsers.size

        class BlockedUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val avatar: ImageView = itemView.findViewById(R.id.iv_avatar)
            private val username: TextView = itemView.findViewById(R.id.tv_username)
            private val blockedDate: TextView = itemView.findViewById(R.id.tv_blocked_date)
            private val reason: TextView = itemView.findViewById(R.id.tv_reason)
            private val btnUnblock: TextView = itemView.findViewById(R.id.btn_unblock)
            private val btnReport: TextView = itemView.findViewById(R.id.btn_report)

            fun bind(blockedUser: BlockedUser, onActionClick: (BlockedUser, String) -> Unit) {
                username.text = blockedUser.username
                reason.text = "Reason: ${blockedUser.reason}"
                
                // Format blocked date
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                blockedDate.text = "Blocked on ${dateFormat.format(Date(blockedUser.blockedOn))}"

                // Load avatar
                if (!blockedUser.avatar.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(blockedUser.avatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(avatar)
                } else {
                    avatar.setImageResource(R.drawable.ic_image_placeholder)
                }

                // Set click listeners
                btnUnblock.setOnClickListener {
                    onActionClick(blockedUser, "unblock")
                }

                btnReport.setOnClickListener {
                    onActionClick(blockedUser, "report")
                }
            }
        }
    }
}