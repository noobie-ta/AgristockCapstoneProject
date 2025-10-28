package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class PurchasedItemsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView

    private val purchases = mutableListOf<PurchaseItem>()
    private lateinit var adapter: PurchasesAdapter
    private lateinit var type: String

    companion object {
        private const val ARG_TYPE = "type"

        fun newInstance(type: String): PurchasedItemsFragment {
            val fragment = PurchasedItemsFragment()
            val args = Bundle()
            args.putString(ARG_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_purchased_items, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        type = arguments?.getString(ARG_TYPE) ?: "BOUGHT"

        setupViews(view)
        setupRecyclerView()
        loadPurchases()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_items)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        emptyState = view.findViewById(R.id.empty_state)
        progressBar = view.findViewById(R.id.progress_bar)
        tvEmptyTitle = view.findViewById(R.id.tv_empty_title)
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message)

        // Set empty state messages based on type
        when (type) {
            "BOUGHT" -> {
                tvEmptyTitle.text = "No Purchases Yet"
                tvEmptyMessage.text = "Items you bought will appear here"
            }
            "WON" -> {
                tvEmptyTitle.text = "No Won Bids Yet"
                tvEmptyMessage.text = "Bids you won will appear here"
            }
        }

        swipeRefresh.setOnRefreshListener {
            loadPurchases()
        }
    }

    private fun setupRecyclerView() {
        adapter = PurchasesAdapter(purchases) { purchase ->
            openItemDetails(purchase)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadPurchases() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE

        // Query purchases based on type
        // Simplified queries to avoid composite index requirements
        when (type) {
            "BOUGHT" -> {
                // Items bought directly (buyerId = current user, type = SELL)
                db.collection("posts")
                    .whereEqualTo("buyerId", user.uid)
                    .get()
                    .addOnSuccessListener { documents ->
                        processPurchases(documents, "SELL")
                    }
                    .addOnFailureListener { exception ->
                        handleLoadError(exception)
                    }
            }
            "WON" -> {
                // Bids won (winnerId = current user, type = BID)
                db.collection("posts")
                    .whereEqualTo("winnerId", user.uid)
                    .get()
                    .addOnSuccessListener { documents ->
                        processPurchases(documents, "BID")
                    }
                    .addOnFailureListener { exception ->
                        handleLoadError(exception)
                    }
            }
        }
    }

    private fun processPurchases(documents: com.google.firebase.firestore.QuerySnapshot, filterType: String? = null) {
        purchases.clear()

        documents.forEach { doc ->
            val docType = doc.getString("type") ?: ""
            val status = doc.getString("status") ?: ""
            
            // Filter by type if specified and only include SOLD items
            if ((filterType == null || docType == filterType) && status == "SOLD") {
                // Get title (prefer "title" field, fallback to "name")
                val title = doc.getString("title") ?: doc.getString("name") ?: "Untitled Item"
                
                // Get price and ensure it has peso sign
                val rawPrice = doc.getString("price") ?: "0.00"
                val formattedPrice = if (rawPrice.startsWith("₱") || rawPrice.startsWith("PHP") || rawPrice.startsWith("php")) {
                    rawPrice
                } else {
                    "₱$rawPrice"
                }
                
                val purchase = PurchaseItem(
                    id = doc.id,
                    name = title,
                    imageUrl = doc.getString("imageUrl") ?: 
                        (doc.get("imageUrls") as? List<*>)?.firstOrNull()?.toString() ?: "",
                    price = formattedPrice,
                    sellerId = doc.getString("userId") ?: "",
                    sellerName = "",
                    purchaseDate = (doc.getLong("soldAt") ?: System.currentTimeMillis()),
                    type = docType
                )
                purchases.add(purchase)
            }
        }

        // Sort by purchase date (newest first)
        purchases.sortByDescending { it.purchaseDate }

        // Load seller names
        loadSellerNames()

        updateUI()
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE
    }

    private fun handleLoadError(exception: Exception) {
        android.util.Log.e("PurchasedItems", "Error loading purchases: ${exception.message}", exception)
        Toast.makeText(
            requireContext(), 
            "Failed to load purchases: ${exception.message}", 
            Toast.LENGTH_LONG
        ).show()
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE
        updateUI()
    }

    private fun loadSellerNames() {
        val db = FirebaseFirestore.getInstance()
        purchases.forEach { purchase ->
            db.collection("users").document(purchase.sellerId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        purchase.sellerName = doc.getString("username") ?: "Unknown Seller"
                        adapter.notifyDataSetChanged()
                    }
                }
        }
    }

    private fun updateUI() {
        if (purchases.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    private fun openItemDetails(purchase: PurchaseItem) {
        val intent = if (purchase.type == "BID") {
            Intent(requireContext(), ViewBiddingActivity::class.java)
        } else {
            Intent(requireContext(), ItemDetailsActivity::class.java)
        }
        intent.putExtra("postId", purchase.id)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadPurchases()
    }

    data class PurchaseItem(
        val id: String,
        val name: String,
        val imageUrl: String,
        val price: String,
        val sellerId: String,
        var sellerName: String,
        val purchaseDate: Long,
        val type: String
    )

    class PurchasesAdapter(
        private val purchases: List<PurchaseItem>,
        private val onItemClick: (PurchaseItem) -> Unit
    ) : RecyclerView.Adapter<PurchasesAdapter.PurchaseViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurchaseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_purchase, parent, false)
            return PurchaseViewHolder(view)
        }

        override fun onBindViewHolder(holder: PurchaseViewHolder, position: Int) {
            holder.bind(purchases[position], onItemClick)
        }

        override fun getItemCount(): Int = purchases.size

        class PurchaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivImage: ImageView = itemView.findViewById(R.id.iv_item_image)
            private val tvName: TextView = itemView.findViewById(R.id.tv_item_name)
            private val tvSellerName: TextView = itemView.findViewById(R.id.tv_seller_name)
            private val tvPrice: TextView = itemView.findViewById(R.id.tv_purchase_price)
            private val tvDate: TextView = itemView.findViewById(R.id.tv_purchase_date)
            private val tvTypeBadge: TextView = itemView.findViewById(R.id.tv_type_badge)

            fun bind(purchase: PurchaseItem, onClick: (PurchaseItem) -> Unit) {
                // Set title (item name)
                tvName.text = purchase.name.ifEmpty { "Untitled Item" }
                
                // Set seller name below title
                val sellerDisplayName = purchase.sellerName.ifEmpty { "Loading..." }
                tvSellerName.text = sellerDisplayName
                
                // Ensure price has peso sign
                val priceText = if (purchase.price.startsWith("₱")) {
                    purchase.price
                } else {
                    "₱${purchase.price}"
                }
                tvPrice.text = priceText

                // Format date
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                tvDate.text = "Purchased: ${dateFormat.format(Date(purchase.purchaseDate))}"

                // Set badge
                if (purchase.type == "BID") {
                    tvTypeBadge.text = "WON BID"
                    tvTypeBadge.setBackgroundResource(R.drawable.status_chip_bid)
                } else {
                    tvTypeBadge.text = "DIRECT BUY"
                    tvTypeBadge.setBackgroundResource(R.drawable.status_chip_available)
                }

                // Load image
                if (purchase.imageUrl.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(purchase.imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(ivImage)
                } else {
                    ivImage.setImageResource(R.drawable.ic_image_placeholder)
                }

                itemView.setOnClickListener { onClick(purchase) }
            }
        }
    }
}

