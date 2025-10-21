package com.example.agristockcapstoneproject

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
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
import com.example.agristockcapstoneproject.utils.CodenameGenerator
import com.example.agristockcapstoneproject.utils.BiddingCriteriaChecker
import com.example.agristockcapstoneproject.views.ModernBiddingGraphView
import java.text.NumberFormat
import java.util.*
import kotlinx.coroutines.*

class LiveBiddingActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var topBiddersRecyclerView: RecyclerView
    private lateinit var currentBidText: TextView
    private lateinit var totalBidsText: TextView
    private lateinit var bidAmountEditText: EditText
    private lateinit var placeBidButton: Button
    private lateinit var biddingGraphView: ModernBiddingGraphView
    private lateinit var graphInfoText: TextView
    private lateinit var countdownText: TextView
    private lateinit var tvYourLastBid: TextView
    private lateinit var tvBidIncrement: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    private var itemId: String? = null
    private var bidsListener: ListenerRegistration? = null
    private val topBidders = mutableListOf<Bidder>()
    private val allBids = mutableListOf<Bid>()
    private var currentHighestBid = 0.0
    private var totalBidsCount = 0
    private var countdownTimer: CountDownTimer? = null
    private var biddingEndTime: Long = 0
    private var totalUniqueBidders = 0
    private var minBidIncrement = 10.0 // Minimum increment of ₱10
    private var isSubmittingBid = false

    data class Bidder(
        val name: String,
        val bidAmount: Double,
        val timestamp: Long,
        val codename: String,
        val bidderId: String = "",
        val isCurrentUser: Boolean = false
    )

    data class Bid(
        val bidderName: String,
        val amount: Double,
        val timestamp: Long,
        val bidderId: String,
        val bidderCodename: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_bidding)

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        loadBiddingData()
        
        // Ensure graph is visible
        ensureGraphVisibility()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.btn_back)
        topBiddersRecyclerView = findViewById(R.id.rv_top_bidders)
        currentBidText = findViewById(R.id.tv_current_bid)
        totalBidsText = findViewById(R.id.tv_total_bids)
        bidAmountEditText = findViewById(R.id.et_bid_amount)
        placeBidButton = findViewById(R.id.btn_place_bid)
        biddingGraphView = findViewById(R.id.view_bidding_graph)
        graphInfoText = findViewById(R.id.tv_graph_info)
        countdownText = findViewById(R.id.tv_countdown)
        tvYourLastBid = findViewById(R.id.tv_your_last_bid)
        tvBidIncrement = findViewById(R.id.tv_bid_increment)

        // Setup RecyclerView
        topBiddersRecyclerView.layoutManager = LinearLayoutManager(this)
        topBiddersRecyclerView.adapter = TopBiddersAdapter(topBidders)
        
        // Set bid increment display
        tvBidIncrement.text = "₱${String.format("%.2f", minBidIncrement)}"
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        
        placeBidButton.setOnClickListener {
            placeBid()
        }
    }

    private fun loadBiddingData() {
        itemId = intent.getStringExtra("postId")
        if (itemId.isNullOrEmpty()) {
            Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load item data to get end time and bid increment
        firestore.collection("posts").document(itemId!!).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data ?: return@addOnSuccessListener
                    val endTimeString = data["endTime"]?.toString() ?: data["biddingEndTime"]?.toString()
                    if (!endTimeString.isNullOrEmpty()) {
                        biddingEndTime = endTimeString.toLongOrNull() ?: System.currentTimeMillis() + 86400000
                        startCountdown()
                    }
                    
                    // Get bid increment from post data
                    val bidIncrementValue = when (val incrementData = data["bidIncrement"]) {
                        is Number -> incrementData.toDouble()
                        is String -> incrementData.toDoubleOrNull() ?: 10.0
                        else -> 10.0
                    }
                    minBidIncrement = bidIncrementValue
                    tvBidIncrement.text = "₱${String.format("%.2f", minBidIncrement)}"
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LiveBiddingActivity", "Error loading item data: ${e.message}")
        }

        // Listen for real-time bid updates
        bidsListener = firestore.collection("bids")
            .whereEqualTo("itemId", itemId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("LiveBiddingActivity", "Error loading bids: ${error.message}")
                    android.util.Log.e("LiveBiddingActivity", "Error code: ${error.code}")
                    
                    when (error.code) {
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                            Toast.makeText(this, "Query failed: Missing index. Please contact support.", Toast.LENGTH_LONG).show()
                        }
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                            Toast.makeText(this, "Permission denied: Please check your authentication.", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                    Toast.makeText(this, "Error loading bids: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                    allBids.clear()
                    topBidders.clear()
                    
                    val bids = snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                
                                // Properly handle different data types from Firestore
                                val bidderName = data["bidderName"]?.toString() ?: "Unknown"
                                val amount = when (val amountValue = data["amount"]) {
                                    is Number -> amountValue.toDouble()
                                    is String -> amountValue.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                                val timestamp = when (val timestampValue = data["timestamp"]) {
                                    is Number -> timestampValue.toLong()
                                    is String -> timestampValue.toLongOrNull() ?: 0L
                                    else -> 0L
                                }
                                
                                // Validate bid data
                                if (amount > 0 && timestamp > 0) {
                                    val bidderId = data["bidderId"] as? String ?: ""
                                    val bidderCodename = data["bidderCodename"] as? String ?: CodenameGenerator.generateDailyCodename(bidderId)
                                    Bid(
                                        bidderName = bidderName,
                                        amount = amount,
                                        timestamp = timestamp,
                                        bidderId = bidderId,
                                        bidderCodename = bidderCodename
                            )
                        } else {
                                    android.util.Log.w("LiveBiddingActivity", "Invalid bid data: amount=$amount, timestamp=$timestamp")
                                    null
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LiveBiddingActivity", "Error parsing bid document: ${e.message}")
                            null
                        }
                    }
                    
                        // Sort bids by timestamp (newest first) on client side
                        val sortedBids = bids.sortedByDescending { it.timestamp }
                        allBids.clear()
                        allBids.addAll(sortedBids)
                    totalBidsCount = allBids.size
                    
                        // Update top bidders (top 3 only) - get highest bid per bidder
                        val uniqueBidders = sortedBids.groupBy { it.bidderId }
                            .map { (bidderId, userBids) -> 
                                val highestBid = userBids.maxByOrNull { it.amount }
                                Bidder(
                                    name = highestBid?.bidderName ?: "Unknown",
                                    bidAmount = highestBid?.amount ?: 0.0,
                                    timestamp = highestBid?.timestamp ?: 0L,
                                    codename = highestBid?.bidderCodename ?: "Unknown",
                                    bidderId = bidderId,
                                    isCurrentUser = bidderId == auth.currentUser?.uid
                                )
                        }
                        .sortedByDescending { it.bidAmount }
                        .take(3) // Only show top 3
                    
                        topBidders.clear()
                    topBidders.addAll(uniqueBidders)
                    
                        // Calculate total unique bidders
                        totalUniqueBidders = sortedBids.groupBy { it.bidderId }.size
                    
                        // Animate adapter changes
                        runOnUiThread {
                            topBiddersRecyclerView.adapter?.notifyDataSetChanged()
                        }
                    
                    // Update current highest bid
                    currentHighestBid = bids.maxOfOrNull { it.amount } ?: 0.0
                    updateBiddingDetails()
                    
                        // Update modern graph
                        updateModernBiddingGraph()
                        
                        android.util.Log.d("LiveBiddingActivity", "Loaded ${bids.size} bids, highest: $currentHighestBid")
                    } catch (e: Exception) {
                        android.util.Log.e("LiveBiddingActivity", "Error processing bids: ${e.message}")
                        Toast.makeText(this, "Error processing bidding data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun updateBiddingDetails() {
        try {
            // Format currency properly for Philippine Peso
            val formattedAmount = if (currentHighestBid > 0) {
                "₱${String.format("%.2f", currentHighestBid)}"
            } else {
                "₱0.00"
            }
            
            currentBidText.text = formattedAmount
            totalBidsText.text = "$totalBidsCount bids • $totalUniqueBidders bidders"
            
            // Update your last bid
            val currentUserId = auth.currentUser?.uid
            val userBids = allBids.filter { it.bidderId == currentUserId }
            val yourLastBid = userBids.maxByOrNull { it.amount }?.amount ?: 0.0
            tvYourLastBid.text = "₱${String.format("%.2f", yourLastBid)}"
            
            android.util.Log.d("LiveBiddingActivity", "Updated bidding details - Highest: $formattedAmount, Total Bids: $totalBidsCount, Unique Bidders: $totalUniqueBidders, Your Last: ₱${String.format("%.2f", yourLastBid)}")
        } catch (e: Exception) {
            android.util.Log.e("LiveBiddingActivity", "Error updating bidding details: ${e.message}")
            currentBidText.text = "₱0.00"
            totalBidsText.text = "0 bids • 0 bidders"
            tvYourLastBid.text = "₱0.00"
        }
    }

    private fun updateModernBiddingGraph() {
        try {
            if (allBids.isNotEmpty()) {
                // Convert bids to graph data with codenames
                val graphData = allBids.takeLast(10).map { bid ->
                    ModernBiddingGraphView.BidData(
                        amount = bid.amount,
                        timestamp = bid.timestamp,
                        bidderId = bid.bidderId,
                        bidderCodename = bid.bidderCodename
                    )
                }
                
                // Update the modern graph view
                biddingGraphView.updateBidData(graphData)
                
                // Update graph info text
                val activityText = when {
                    allBids.size >= 20 -> "🔥 High Activity (${allBids.size} bids)"
                    allBids.size >= 10 -> "📈 Active Bidding (${allBids.size} bids)"
                    allBids.size >= 5 -> "📊 Moderate Activity (${allBids.size} bids)"
                    else -> "💫 Getting Started (${allBids.size} bids)"
                }
                graphInfoText.text = activityText
                
                android.util.Log.d("LiveBiddingActivity", "Updated modern bidding graph - Bids: ${allBids.size}")
            } else {
                // Show no activity
                biddingGraphView.updateBidData(emptyList())
                graphInfoText.text = "⏳ No Bids Yet"
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveBiddingActivity", "Error updating modern bidding graph: ${e.message}")
            graphInfoText.text = "📊 Bidding Activity"
        }
    }

    private fun updateBiddingGraph() {
        try {
            if (allBids.isNotEmpty()) {
                // Calculate bidding activity metrics
                val recentBids = allBids.takeLast(10) // Last 10 bids
                val bidTrend = if (recentBids.size >= 2) {
                    val latest = recentBids.last().amount
                    val previous = recentBids[recentBids.size - 2].amount
                    latest > previous
                } else true
                
                // Update graph info text with activity level
                val activityText = when {
                    allBids.size >= 20 -> "🔥 High Activity (${allBids.size} bids)"
                    allBids.size >= 10 -> "📈 Active Bidding (${allBids.size} bids)"
                    allBids.size >= 5 -> "📊 Moderate Activity (${allBids.size} bids)"
                    else -> "💫 Getting Started (${allBids.size} bids)"
                }
                graphInfoText.text = activityText
                
                // Set background color based on activity
                val activityColor = when {
                    allBids.size >= 20 -> android.R.color.holo_green_dark
                    allBids.size >= 10 -> android.R.color.holo_green_light
                    allBids.size >= 5 -> android.R.color.holo_orange_light
                    else -> android.R.color.holo_blue_light
                }
                
                biddingGraphView.setBackgroundColor(ContextCompat.getColor(this, activityColor))
                
                // Create a simple visual representation
                createSimpleGraph()
                
                // Add pulsing animation for active bidding
                if (bidTrend) {
                    biddingGraphView.alpha = 0.8f
        biddingGraphView.animate()
            .alpha(1f)
            .setDuration(500)
                        .withEndAction {
                            biddingGraphView.animate()
                                .alpha(0.8f)
                                .setDuration(500)
                                .start()
                        }
            .start()
                } else {
                    biddingGraphView.alpha = 1f
                }
                
                android.util.Log.d("LiveBiddingActivity", "Updated bidding graph - Bids: ${allBids.size}, Trend: $bidTrend")
                android.util.Log.d("LiveBiddingActivity", "Graph view visibility: ${biddingGraphView.visibility}, Alpha: ${biddingGraphView.alpha}")
                android.util.Log.d("LiveBiddingActivity", "Graph info text: ${graphInfoText.text}")
            } else {
                // Show no activity
                biddingGraphView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                biddingGraphView.alpha = 0.5f
                graphInfoText.text = "⏳ No Bids Yet"
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveBiddingActivity", "Error updating bidding graph: ${e.message}")
            // Fallback to basic indicator
            biddingGraphView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            biddingGraphView.alpha = 0.7f
            graphInfoText.text = "📊 Bidding Activity"
        }
    }

    private fun createSimpleGraph() {
        try {
            // Create a simple visual representation using the graph view
            val graphContainer = biddingGraphView.parent as? android.widget.FrameLayout
            if (graphContainer != null) {
                // Remove any existing graph elements
                graphContainer.removeAllViews()
                
                // Add the main graph view back
                graphContainer.addView(biddingGraphView)
                
                // Add the info text on top
                graphContainer.addView(graphInfoText)
                
                // Create simple bars to represent bidding activity
                val recentBids = allBids.takeLast(6) // Show last 6 bids
                if (recentBids.isNotEmpty()) {
                    val maxAmount = recentBids.maxOf { it.amount }
                    val minAmount = recentBids.minOf { it.amount }
                    val amountRange = maxAmount - minAmount
                    
                    val barContainer = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.BOTTOM
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            setMargins(20, 20, 20, 20)
                        }
                    }
                    
                    recentBids.forEachIndexed { index, bid ->
                        val normalizedHeight = if (amountRange > 0) {
                            ((bid.amount - minAmount) / amountRange).coerceIn(0.1, 1.0)
                        } else 0.5
                        
                        val barHeight = (normalizedHeight * 100).toInt()
                        
                        val bar = android.view.View(this).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0,
                                barHeight,
                                1f
                            ).apply {
                                setMargins(2, 0, 2, 0)
                            }
                            
                            // Set color based on bid amount
                            val color = when {
                                bid.amount >= maxAmount * 0.8 -> android.graphics.Color.parseColor("#4CAF50")
                                bid.amount >= maxAmount * 0.6 -> android.graphics.Color.parseColor("#8BC34A")
                                bid.amount >= maxAmount * 0.4 -> android.graphics.Color.parseColor("#CDDC39")
                                bid.amount >= maxAmount * 0.2 -> android.graphics.Color.parseColor("#FFC107")
                                else -> android.graphics.Color.parseColor("#FF9800")
                            }
                            
                            setBackgroundColor(color)
                        }
                        
                        barContainer.addView(bar)
                    }
                    
                    graphContainer.addView(barContainer)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveBiddingActivity", "Error creating simple graph: ${e.message}")
        }
    }

    private fun ensureGraphVisibility() {
        try {
            // Make sure the graph view is visible and properly configured
            biddingGraphView.visibility = View.VISIBLE
            biddingGraphView.alpha = 1f
            
            // Set initial background
            biddingGraphView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            
            // Set initial text
            graphInfoText.text = "📊 Bidding Activity"
            graphInfoText.visibility = View.VISIBLE
            
            android.util.Log.d("LiveBiddingActivity", "Graph visibility ensured - View: ${biddingGraphView.visibility}, Alpha: ${biddingGraphView.alpha}")
        } catch (e: Exception) {
            android.util.Log.e("LiveBiddingActivity", "Error ensuring graph visibility: ${e.message}")
        }
    }

    private fun startCountdown() {
        val timeLeft = biddingEndTime - System.currentTimeMillis()
        
        if (timeLeft > 0) {
            countdownTimer = object : CountDownTimer(timeLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val days = millisUntilFinished / (1000 * 60 * 60 * 24)
                    val hours = (millisUntilFinished % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                    val seconds = (millisUntilFinished % (1000 * 60)) / 1000
                    
                    countdownText.text = when {
                        days > 0 -> {
                            if (days == 1L) {
                                String.format("1 day, %02d:%02d:%02d", hours, minutes, seconds)
                            } else {
                                String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds)
                            }
                        }
                        hours > 0 -> {
                            String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        }
                        else -> {
                            String.format("%02d:%02d", minutes, seconds)
                        }
                    }
                }
                
                override fun onFinish() {
                    countdownText.text = "Bidding Ended"
                    countdownText.setTextColor(ContextCompat.getColor(this@LiveBiddingActivity, android.R.color.holo_red_dark))
                    // Disable bidding when time expires
                    disableBidding()
                }
            }.start()
        } else {
            countdownText.text = "Bidding Ended"
            countdownText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            // Disable bidding if time has already expired
            disableBidding()
        }
    }
    
    private fun disableBidding() {
        // Disable the place bid button when time expires
        placeBidButton.isEnabled = false
        placeBidButton.alpha = 0.5f
        placeBidButton.text = "Bidding Ended"
    }

    private fun placeBid() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to place a bid", Toast.LENGTH_SHORT).show()
            return
        }

        // Prevent duplicate submissions
        if (isSubmittingBid) {
            Toast.makeText(this, "Please wait, processing your bid...", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Bid must be higher than current highest bid (₱${String.format("%.2f", currentHighestBid)})", Toast.LENGTH_LONG).show()
            return
        }

        // Validate minimum bid increment
        val requiredMinBid = currentHighestBid + minBidIncrement
        if (bidAmount < requiredMinBid) {
            Toast.makeText(this, "Bid must be at least ₱${String.format("%.2f", requiredMinBid)} (minimum ₱${String.format("%.2f", minBidIncrement)} increment)", Toast.LENGTH_LONG).show()
            return
        }

        // Show confirmation dialog
        showBidConfirmationDialog(bidAmount)
    }

    private fun showBidConfirmationDialog(bidAmount: Double) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Your Bid")
            .setMessage("Are you sure you want to place a bid of ₱${String.format("%.2f", bidAmount)}?\n\nThis action cannot be undone.")
            .setPositiveButton("Confirm Bid") { _, _ ->
                proceedWithBid(bidAmount)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Bid cancelled", Toast.LENGTH_SHORT).show()
            }
            .setOnDismissListener {
                // Reset submitting flag if dialog is dismissed
                if (!placeBidButton.isEnabled) {
                    placeBidButton.isEnabled = true
                    placeBidButton.text = "Place a Bid"
                }
            }
            .show()
    }

    private fun proceedWithBid(bidAmount: Double) {
        val user = auth.currentUser ?: return
        
        // Mark as submitting
        isSubmittingBid = true
        
        // Disable button during submission
        placeBidButton.isEnabled = false
        placeBidButton.text = "Checking Eligibility..."

        // Check bidding criteria
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val eligibilityResult = BiddingCriteriaChecker.canBidOnItem(itemId!!, bidAmount)
                
                if (!eligibilityResult.isEligible) {
                    showEligibilityError(eligibilityResult)
                    placeBidButton.isEnabled = true
                    placeBidButton.text = "Place a Bid"
                    isSubmittingBid = false
                    return@launch
                }
                
                // Generate codename for the bidder
                val bidderCodename = CodenameGenerator.generateDailyCodename(user.uid)
                
                // Proceed with bid placement
                placeBidWithCodename(bidAmount, bidderCodename)
                
            } catch (e: Exception) {
                android.util.Log.e("LiveBiddingActivity", "Error checking eligibility: ${e.message}")
                Toast.makeText(this@LiveBiddingActivity, "Error checking eligibility: ${e.message}", Toast.LENGTH_SHORT).show()
                placeBidButton.isEnabled = true
                placeBidButton.text = "Place a Bid"
                isSubmittingBid = false
            }
        }
    }
    
    private fun showEligibilityError(result: com.example.agristockcapstoneproject.utils.BiddingEligibilityResult) {
        when {
            result.requiresVerification -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Verification Required")
                    .setMessage("You must be verified to place bids. Please complete verification first.")
                    .setPositiveButton("Verify Now") { _, _ ->
                        startActivity(Intent(this, com.example.agristockcapstoneproject.IdVerificationActivity::class.java))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            result.requiresActivity -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Account Activity Required")
                    .setMessage("Please engage more with the community before bidding. Create posts or send messages to increase your activity.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            result.daysRemaining != null -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Account Too New")
                    .setMessage("Your account must be at least 7 days old to bid. ${result.daysRemaining} days remaining.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> {
                Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun placeBidWithCodename(bidAmount: Double, bidderCodename: String) {
        val user = auth.currentUser ?: return
        
        placeBidButton.text = "Placing Bid..."

        try {
        val bidData = hashMapOf(
            "itemId" to itemId,
            "bidderId" to user.uid,
            "bidderName" to (user.displayName ?: "Anonymous"),
                "bidderCodename" to bidderCodename,
            "amount" to bidAmount,
            "timestamp" to System.currentTimeMillis()
        )

            android.util.Log.d("LiveBiddingActivity", "Placing bid with codename: $bidData")

        firestore.collection("bids")
            .add(bidData)
                .addOnSuccessListener { docRef ->
                    android.util.Log.d("LiveBiddingActivity", "Bid placed successfully with ID: ${docRef.id}")
                    Toast.makeText(this, "Bid placed successfully as $bidderCodename!", Toast.LENGTH_SHORT).show()
                bidAmountEditText.text.clear()
                
                    // Update item's highest bid and total bidders count
                    updateItemBiddingStats(bidAmount)
            }
            .addOnFailureListener { exception ->
                    android.util.Log.e("LiveBiddingActivity", "Failed to place bid: ${exception.message}")
                Toast.makeText(this, "Failed to place bid: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                    placeBidButton.isEnabled = true
                    placeBidButton.text = "Place a Bid"
                    isSubmittingBid = false
                }
        } catch (e: Exception) {
            android.util.Log.e("LiveBiddingActivity", "Error in placeBidWithCodename: ${e.message}")
            Toast.makeText(this, "Error placing bid: ${e.message}", Toast.LENGTH_SHORT).show()
                placeBidButton.isEnabled = true
                placeBidButton.text = "Place a Bid"
                isSubmittingBid = false
        }
    }
    
    private fun updateItemBiddingStats(bidAmount: Double) {
        val updates = hashMapOf<String, Any>(
            "highestBid" to bidAmount,
            "highestBidFormatted" to "₱${String.format("%.2f", bidAmount)}",
            "totalBidders" to com.google.firebase.firestore.FieldValue.increment(1)
        )
        
        firestore.collection("posts").document(itemId!!)
            .update(updates)
            .addOnSuccessListener {
                android.util.Log.d("LiveBiddingActivity", "Post updated successfully")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LiveBiddingActivity", "Error updating post: ${e.message}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
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
            
            // Show rank number in circle
            holder.rankText.text = "${position + 1}"
            
            // Set rank circle background based on position
            val rankBackground = when (position) {
                0 -> R.drawable.rank_circle_gold
                1 -> R.drawable.rank_circle_silver
                2 -> R.drawable.rank_circle_bronze
                else -> R.drawable.rank_circle_gold
            }
            holder.rankText.setBackgroundResource(rankBackground)
            holder.rankText.setTextColor(android.graphics.Color.WHITE)
            
            // Show codename instead of real name
            val displayName = if (bidder.isCurrentUser) {
                "${bidder.codename} (You)"
            } else {
                bidder.codename
            }
            holder.nameText.text = displayName
            holder.nameText.setTextColor(ContextCompat.getColor(this@LiveBiddingActivity, R.color.black))
            
            // Format and color amount based on rank
            holder.amountText.text = formatter.format(bidder.bidAmount)
            val amountColor = when (position) {
                0 -> android.graphics.Color.parseColor("#FFD700") // Gold
                1 -> android.graphics.Color.parseColor("#666666") // Dark gray
                2 -> android.graphics.Color.parseColor("#666666") // Dark gray
                else -> android.graphics.Color.parseColor("#666666")
            }
            holder.amountText.setTextColor(amountColor)
            
            // Keep transparent background
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Add subtle highlight for current user
            if (bidder.isCurrentUser) {
                holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#FFFEF5"))
            }
        }

        override fun getItemCount(): Int = bidders.size
    }
}
