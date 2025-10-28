package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class MyTicketsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var ticketsListener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: View
    private lateinit var progressBar: ProgressBar

    private val ticketsList = mutableListOf<SupportTicket>()
    private lateinit var adapter: TicketsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_tickets)

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
        loadTickets()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_tickets)
        emptyStateView = findViewById(R.id.empty_state_view)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }

        // New ticket button
        findViewById<View>(R.id.btn_new_ticket).setOnClickListener {
            startActivity(Intent(this, ContactSupportActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = TicketsAdapter(ticketsList) { ticket ->
            showTicketDetails(ticket)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadTickets() {
        val user = auth.currentUser
        if (user != null) {
            progressBar.visibility = View.VISIBLE
            
            ticketsListener = firestore.collection("supportTickets")
                .whereEqualTo("userId", user.uid)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, exception ->
                    progressBar.visibility = View.GONE
                    
                    if (exception != null) {
                        android.util.Log.e("MyTicketsActivity", "Error loading tickets: ${exception.message}")
                        Toast.makeText(this, "Failed to load support tickets", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    ticketsList.clear()
                    snapshot?.documents?.forEach { document ->
                        val data = document.data
                        if (data != null) {
                            val ticket = SupportTicket(
                                id = document.id,
                                subject = data["subject"]?.toString() ?: "",
                                message = data["message"]?.toString() ?: "",
                                status = data["status"]?.toString() ?: "open",
                                priority = data["priority"]?.toString() ?: "medium",
                                createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis(),
                                updatedAt = (data["updatedAt"] as? Long) ?: System.currentTimeMillis(),
                                attachmentUrl = data["attachmentUrl"]?.toString(),
                                responses = data["responses"] as? List<Map<String, Any>> ?: emptyList()
                            )
                            ticketsList.add(ticket)
                        }
                    }

                    updateUI()
                }
        }
    }

    private fun updateUI() {
        if (ticketsList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    private fun showTicketDetails(ticket: SupportTicket) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ticket #${ticket.id.takeLast(8)}")
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        val statusColor = when (ticket.status) {
            "open" -> "#FF9800"
            "in_progress" -> "#2196F3"
            "resolved" -> "#4CAF50"
            "closed" -> "#9E9E9E"
            else -> "#000000"
        }
        
        val details = """
            Subject: ${ticket.subject}
            
            Message: ${ticket.message}
            
            Status: ${ticket.status.replace("_", " ").uppercase()}
            Priority: ${ticket.priority.uppercase()}
            Created: ${dateFormat.format(Date(ticket.createdAt))}
            Updated: ${dateFormat.format(Date(ticket.updatedAt))}
            
            ${if (ticket.responses.isNotEmpty()) "Responses: ${ticket.responses.size}" else "No responses yet"}
        """.trimIndent()
        
        builder.setMessage(details)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ticketsListener?.remove()
    }

    data class SupportTicket(
        val id: String,
        val subject: String,
        val message: String,
        val status: String,
        val priority: String,
        val createdAt: Long,
        val updatedAt: Long,
        val attachmentUrl: String?,
        val responses: List<Map<String, Any>>
    )

    class TicketsAdapter(
        private var tickets: List<SupportTicket>,
        private val onTicketClick: (SupportTicket) -> Unit
    ) : RecyclerView.Adapter<TicketsAdapter.TicketViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TicketViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_support_ticket, parent, false)
            return TicketViewHolder(view)
        }

        override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
            holder.bind(tickets[position], onTicketClick)
        }

        override fun getItemCount(): Int = tickets.size

        class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val subject: TextView = itemView.findViewById(R.id.tv_subject)
            private val status: TextView = itemView.findViewById(R.id.tv_status)
            private val date: TextView = itemView.findViewById(R.id.tv_date)
            private val priority: TextView = itemView.findViewById(R.id.tv_priority)

            fun bind(ticket: SupportTicket, onTicketClick: (SupportTicket) -> Unit) {
                subject.text = ticket.subject
                
                // Format status
                val statusText = ticket.status.replace("_", " ").uppercase()
                status.text = statusText
                
                // Set status color
                val statusColor = when (ticket.status) {
                    "open" -> 0xFFFF9800.toInt()
                    "in_progress" -> 0xFF2196F3.toInt()
                    "resolved" -> 0xFF4CAF50.toInt()
                    "closed" -> 0xFF9E9E9E.toInt()
                    else -> 0xFF000000.toInt()
                }
                status.setTextColor(statusColor)
                
                // Format date
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                date.text = dateFormat.format(Date(ticket.createdAt))
                
                // Set priority
                priority.text = ticket.priority.uppercase()
                val priorityColor = when (ticket.priority) {
                    "high" -> 0xFFF44336.toInt()
                    "medium" -> 0xFFFF9800.toInt()
                    "low" -> 0xFF4CAF50.toInt()
                    else -> 0xFF000000.toInt()
                }
                priority.setTextColor(priorityColor)

                itemView.setOnClickListener {
                    onTicketClick(ticket)
                }
            }
        }
    }
}
