package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.util.*

class LiveBiddingActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var topBiddersRecyclerView: RecyclerView
    private lateinit var currentBidText: TextView
    private lateinit var totalBidsText: TextView
    private lateinit var bidAmountEditText: EditText
    private lateinit var placeBidButton: Button
    private lateinit var biddingGraphView: View

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    private var itemId: String? = null
    private var bidsListener: ListenerRegistration? = null
    private val topBidders = mutableListOf<Bidder>()
    private val allBids = mutableListOf<Bid>()
    private var currentHighestBid = 0.0
    private var totalBidsCount = 0

    data class Bidder(
        val name: String,
        val bidAmount: Double,
        val timestamp: Long
    )

    data class Bid(
        val bidderName: String,
        val amount: Double,
        val timestamp: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_bidding)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        initializeViews()
        setupClickListeners()
        loadBiddingData()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.btn_back)
        topBiddersRecyclerView = findViewById(R.id.rv_top_bidders)
        currentBidText = findViewById(R.id.tv_current_bid)
        totalBidsText = findViewById(R.id.tv_total_bids)
        bidAmountEditText = findViewById(R.id.et_bid_amount)
        placeBidButton = findViewById(R.id.btn_place_bid)
        biddingGraphView = findViewById(R.id.view_bidding_graph)

        // Setup RecyclerView
        topBiddersRecyclerView.layoutManager = LinearLayoutManager(this)
        topBiddersRecyclerView.adapter = TopBiddersAdapter(topBidders)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        
        placeBidButton.setOnClickListener {
            placeBid()
        }
    }

    private fun loadBiddingData() {
        itemId = intent.getStringExtra("itemId")
        if (itemId.isNullOrEmpty()) {
            Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Listen for real-time bid updates
        bidsListener = firestore.collection("bids")
            .whereEqualTo("itemId", itemId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading bids: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allBids.clear()
                    topBidders.clear()
                    
                    val bids = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data
                        if (data != null) {
                            Bid(
                                bidderName = data["bidderName"]?.toString() ?: "Unknown",
                                amount = data["amount"]?.toString()?.toDoubleOrNull() ?: 0.0,
                                timestamp = data["timestamp"]?.toString()?.toLongOrNull() ?: 0L
                            )
                        } else {
                            null
                        }
                    }
                    
                    allBids.addAll(bids)
                    totalBidsCount = allBids.size
                    
                    // Update top bidders (top 5)
                    val uniqueBidders = bids.groupBy { it.bidderName }
                        .map { (name, bids) -> 
                            Bidder(name, bids.maxOfOrNull { it.amount } ?: 0.0, bids.maxOfOrNull { it.timestamp } ?: 0L)
                        }
                        .sortedByDescending { it.bidAmount }
                        .take(5)
                    
                    topBidders.addAll(uniqueBidders)
                    topBiddersRecyclerView.adapter?.notifyDataSetChanged()
                    
                    // Update current highest bid
                    currentHighestBid = bids.maxOfOrNull { it.amount } ?: 0.0
                    updateBiddingDetails()
                    
                    // Update graph
                    updateBiddingGraph()
                }
            }
    }

    private fun updateBiddingDetails() {
        val formatter = NumberFormat.getCurrencyInstance()
        formatter.currency = java.util.Currency.getInstance("PHP")
        
        currentBidText.text = "Current Highest Bid: ${formatter.format(currentHighestBid)}"
        totalBidsText.text = "Total Bids: $totalBidsCount"
    }

    private fun updateBiddingGraph() {
        // Simulate graph update with animation
        biddingGraphView.alpha = 0.7f
        biddingGraphView.animate()
            .alpha(1f)
            .setDuration(500)
            .start()
        
        // In a real implementation, you would update a custom graph view here
        // For now, we'll just show a placeholder
    }

    private fun placeBid() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to place a bid", Toast.LENGTH_SHORT).show()
            return
        }

        val bidAmountText = bidAmountEditText.text.toString().trim()
        if (bidAmountText.isEmpty()) {
            Toast.makeText(this, "Please enter a bid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val bidAmount = bidAmountText.toDoubleOrNull()
        if (bidAmount == null || bidAmount <= 0) {
            Toast.makeText(this, "Please enter a valid bid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (bidAmount <= currentHighestBid) {
            Toast.makeText(this, "Bid must be higher than current highest bid", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button during submission
        placeBidButton.isEnabled = false
        placeBidButton.text = "Placing Bid..."

        val bidData = hashMapOf(
            "itemId" to itemId,
            "bidderId" to user.uid,
            "bidderName" to (user.displayName ?: "Anonymous"),
            "amount" to bidAmount,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("bids")
            .add(bidData)
            .addOnSuccessListener {
                Toast.makeText(this, "Bid placed successfully!", Toast.LENGTH_SHORT).show()
                bidAmountEditText.text.clear()
                
                // Update item's highest bid
                firestore.collection("posts").document(itemId!!)
                    .update("highestBid", "₱${String.format("%.2f", bidAmount)}")
                    .addOnSuccessListener {
                        // Success
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to place bid: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                placeBidButton.isEnabled = true
                placeBidButton.text = "Place a Bid"
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        bidsListener?.remove()
    }

    // RecyclerView Adapter for Top Bidders
    private inner class TopBiddersAdapter(
        private val bidders: List<Bidder>
    ) : RecyclerView.Adapter<TopBiddersAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val rankText: TextView = itemView.findViewById(R.id.tv_rank)
            val nameText: TextView = itemView.findViewById(R.id.tv_bidder_name)
            val amountText: TextView = itemView.findViewById(R.id.tv_bid_amount)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_top_bidder, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bidder = bidders[position]
            val formatter = NumberFormat.getCurrencyInstance()
            formatter.currency = java.util.Currency.getInstance("PHP")
            
            holder.rankText.text = "#${position + 1}"
            holder.nameText.text = bidder.name
            holder.amountText.text = formatter.format(bidder.bidAmount)
            
            // Highlight top bidder
            if (position == 0) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(this@LiveBiddingActivity, R.color.light_yellow))
            } else {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(this@LiveBiddingActivity, android.R.color.white))
            }
        }

        override fun getItemCount(): Int = bidders.size
    }
}
