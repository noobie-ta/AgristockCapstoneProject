package com.example.agristockcapstoneproject

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SearchActivity : AppCompatActivity() {

    data class SearchItem(
        val id: String,
        val name: String,
        val price: String,
        val seller: String,
        val location: String,
        val imageUrl: String?,
        val status: String,
        val category: String,
        val type: String = "SELL" // SELL or BID
    )

    private lateinit var searchEditText: EditText
    private lateinit var searchButton: com.google.android.material.button.MaterialButton
    private lateinit var backButton: ImageView
    private lateinit var menuButton: ImageView
    private lateinit var micButton: ImageView
    private lateinit var recentSearchesContainer: ScrollView
    private lateinit var recentSearchesContainerInner: LinearLayout
    private lateinit var clearAllButton: TextView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var emptyStateView: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var loadingView: LinearLayout
    private lateinit var resultsHeader: LinearLayout

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val searchResults = mutableListOf<SearchItem>()
    private val recentSearches = mutableListOf<String>()
    private val suggestedSearches = listOf<String>()

    private var currentCategory = "ALL"
    private var currentSort = "NEWEST"
    private var minPrice = 0
    private var maxPrice = 100000
    private var currentLocation = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initializeViews()
        setupClickListeners()
        loadRecentSearches()
        
        // Check if search query was passed from MainActivity
        val searchQuery = intent.getStringExtra("search_query")
        if (!searchQuery.isNullOrEmpty()) {
            searchEditText.setText(searchQuery)
            performSearch(searchQuery)
        } else {
            showRecentAndSuggestedSearches()
            // Show sample results on first load
            showSampleResults("")
        }
    }

    private fun initializeViews() {
        try {
            searchEditText = findViewById(R.id.et_search)
            searchButton = findViewById(R.id.btn_search)
            backButton = findViewById(R.id.btn_back)
            // menuButton removed from layout
            micButton = findViewById(R.id.btn_mic)
            recentSearchesContainer = findViewById(R.id.ll_recent_searches)
            recentSearchesContainerInner = findViewById(R.id.ll_recent_searches_container)
            clearAllButton = findViewById(R.id.btn_clear_all)
            resultsRecyclerView = findViewById(R.id.rv_search_results)
            emptyStateView = findViewById(R.id.ll_empty_state)
            resultsContainer = findViewById(R.id.ll_results_container)
            loadingView = findViewById(R.id.ll_loading)
            resultsHeader = findViewById(R.id.ll_results_header)

            // Setup RecyclerView
            resultsRecyclerView.layoutManager = LinearLayoutManager(this)
            resultsRecyclerView.adapter = SearchResultsAdapter(searchResults) { item ->
                val intent = if (item.type == "BID") {
                    android.content.Intent(this, ViewBiddingActivity::class.java)
                } else {
                    android.content.Intent(this, ItemDetailsActivity::class.java)
                }
                intent.putExtra("postId", item.id)
                startActivity(intent)
            }
            
            // Setup search input listener for Done key (no auto-search)
            searchEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    // Just hide keyboard, don't search
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            // Log error and show toast
            Toast.makeText(this, "Error initializing search page: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        try {
            backButton.setOnClickListener { finish() }
            
            searchButton.setOnClickListener {
                val query = searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
            }
            
            // Menu button removed from layout - no click listener needed
            
            micButton.setOnClickListener {
                // Handle voice search
                Toast.makeText(this, "Voice search coming soon!", Toast.LENGTH_SHORT).show()
            }
            
            clearAllButton.setOnClickListener {
                clearAllRecentSearches()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up click listeners: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun performSearch(query: String) {
        // Add to recent searches
        addToRecentSearches(query)

        // Show loading state with smooth transition
        showLoadingState()

        // Try Firebase first, but fallback to empty if it fails
        try {
            // Get all posts and filter client-side for better search results
            firestore.collection("posts")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener { documents ->
                    val mapped = documents.map { doc ->
                        SearchItem(
                            id = doc.id,
                            name = doc.getString("title") ?: "",
                            price = doc.getString("price") ?: "",
                            seller = doc.getString("sellerName") ?: "",
                            location = doc.getString("location") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            status = doc.getString("status") ?: "FOR SALE",
                            category = doc.getString("category") ?: "OTHER",
                            type = doc.getString("type") ?: "SELL"
                        )
                    }

                    // Client-side search filtering
                    val searchResults = mapped.filter { item ->
                        // Search in title, description, category, and seller name
                        val searchText = query.lowercase()
                        val titleMatch = item.name.lowercase().contains(searchText)
                        val categoryMatch = item.category.lowercase().contains(searchText)
                        val sellerMatch = item.seller.lowercase().contains(searchText)
                        val locationMatch = item.location.lowercase().contains(searchText)
                        
                        (titleMatch || categoryMatch || sellerMatch || locationMatch) &&
                        item.status.uppercase() != "SOLD"
                    }

                    // Apply additional filters
                    val byCategory = if (currentCategory == "ALL") searchResults else searchResults.filter { it.category.equals(currentCategory, true) }
                    val byPrice = byCategory.filter { item ->
                        val numeric = item.price.filter { ch -> ch.isDigit() }
                        val value = numeric.toIntOrNull() ?: Int.MAX_VALUE
                        value in minPrice..maxPrice
                    }
                    val byLocation = if (currentLocation.isEmpty()) byPrice else byPrice.filter { item ->
                        item.location.contains(currentLocation, ignoreCase = true)
                    }

                    updateSearchResults(byLocation)
                }
                .addOnFailureListener { exception ->
                    // Show empty state on failure
                    android.util.Log.e("SearchActivity", "Search failed: ${exception.message}")
                    updateSearchResults(emptyList())
                }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Search error: ${e.message}")
            updateSearchResults(emptyList())
        }
    }

    private fun showSampleResults(query: String) {
        // No sample data - show empty state
        showEmptyState()
    }
    
    private fun showEmptyState() {
        resultsContainer.visibility = View.VISIBLE
        resultsHeader.visibility = View.VISIBLE
        emptyStateView.visibility = View.VISIBLE
        resultsRecyclerView.visibility = View.GONE
        
        // Update results count
        val resultsCount = findViewById<TextView>(R.id.tv_results_count)
        resultsCount.text = "0 results found"
        
        // Smooth fade-in animation for empty state
        emptyStateView.alpha = 0f
        emptyStateView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun updateSearchResults(results: List<SearchItem>) {
        searchResults.clear()
        searchResults.addAll(results)
        resultsRecyclerView.adapter?.notifyDataSetChanged()

        // Hide loading state with smooth transition
        hideLoadingState()

        if (results.isEmpty()) {
            showEmptyState()
        } else {
            showResults()
        }
    }

    private fun showLoadingState() {
        hideRecentSearches()
        loadingView.visibility = View.VISIBLE
        resultsHeader.visibility = View.GONE
        resultsRecyclerView.visibility = View.GONE
        emptyStateView.visibility = View.GONE
        
        // Smooth fade-in animation for loading
        loadingView.alpha = 0f
        loadingView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun hideLoadingState() {
        loadingView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                loadingView.visibility = View.GONE
            }
            .start()
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null)
        
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinner_category)
        val sortSpinner = dialogView.findViewById<Spinner>(R.id.spinner_sort)
        val priceRangeSeekBar = dialogView.findViewById<SeekBar>(R.id.seekbar_price_range)
        val priceText = dialogView.findViewById<TextView>(R.id.tv_price_range)
        val locationEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_location)
        val applyButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_apply_filter)
        val removeFilterButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_remove_filter)

        // Setup category spinner
        val categories = arrayOf("ALL", "CARABAO", "CHICKEN", "GOAT", "COW", "PIG", "DUCK", "OTHER")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter
        categorySpinner.setSelection(categories.indexOf(currentCategory))

        // Setup sort spinner
        val sortOptions = arrayOf("NEWEST", "OLDEST", "PRICE_LOW", "PRICE_HIGH")
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = sortAdapter
        sortSpinner.setSelection(sortOptions.indexOf(currentSort))

        // Setup price range
        priceRangeSeekBar.max = 100000
        priceRangeSeekBar.progress = maxPrice
        priceText.text = "Max Price: ₱${maxPrice}"
        
        // Setup location field
        locationEditText.setText(currentLocation)
        
        priceRangeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maxPrice = progress
                priceText.text = "Max Price: ₱${progress}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Filter & Sort")
            .create()

        applyButton.setOnClickListener {
            currentCategory = categorySpinner.selectedItem.toString()
            currentSort = sortSpinner.selectedItem.toString()
            currentLocation = locationEditText.text.toString().trim()
            dialog.dismiss()
            
            // Re-search with new filters
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                // If no query, show recent searches
                showRecentAndSuggestedSearches()
                showRecentSearchesContainer()
            }
        }

        removeFilterButton.setOnClickListener {
            // Reset all filter values to defaults
            currentCategory = "ALL"
            currentSort = "NEWEST"
            maxPrice = 100000
            currentLocation = ""
            
            // Update UI elements to reflect reset
            categorySpinner.setSelection(0) // "ALL" is at index 0
            sortSpinner.setSelection(0) // "NEWEST" is at index 0
            priceRangeSeekBar.progress = 100000
            priceText.text = "Max Price: ₱100000"
            locationEditText.setText("")
            
            // Close dialog with slide-down transition
            dialog.dismiss()
            
            // Automatically refresh search results to show all items
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                // If no query, show recent searches
                showRecentAndSuggestedSearches()
                showRecentSearchesContainer()
            }
        }

        dialog.show()
    }



    private fun showRecentAndSuggestedSearches() {
        // Get the LinearLayout inside the ScrollView
        val linearLayout = recentSearchesContainer.getChildAt(0) as? LinearLayout
        linearLayout?.removeAllViews()
        
        // Add recent searches
        if (recentSearches.isNotEmpty()) {
            val recentLabel = TextView(this).apply {
                text = "Recent Searches"
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.black, null))
                setPadding(16, 16, 16, 8)
            }
            linearLayout?.addView(recentLabel)
            
            recentSearches.forEach { search ->
                val chip = createSearchChip(search, true)
                linearLayout?.addView(chip)
            }
        }

        // Add suggested searches
        val suggestedLabel = TextView(this).apply {
            text = "Suggested"
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.black, null))
            setPadding(16, 16, 16, 8)
        }
        linearLayout?.addView(suggestedLabel)
        
        suggestedSearches.forEach { search ->
            val chip = createSearchChip(search, false)
            linearLayout?.addView(chip)
        }
    }

    private fun createSearchChip(text: String, isRecent: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setTextColor(resources.getColor(android.R.color.black, null))
            background = resources.getDrawable(R.drawable.chip_background, null)
            setOnClickListener {
                searchEditText.setText(text)
                performSearch(text)
                hideRecentSearches()
            }
            
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 4, 16, 4)
            }
            this.layoutParams = layoutParams
        }
    }

    private fun hideRecentSearches() {
        recentSearchesContainer.visibility = View.GONE
        resultsContainer.visibility = View.VISIBLE
    }

    private fun showRecentSearchesContainer() {
        recentSearchesContainer.visibility = View.VISIBLE
        resultsContainer.visibility = View.GONE
    }

    private fun showResults() {
        resultsContainer.visibility = View.VISIBLE
        resultsHeader.visibility = View.VISIBLE
        resultsRecyclerView.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
        
        // Update results count
        val resultsCount = findViewById<TextView>(R.id.tv_results_count)
        resultsCount.text = "${searchResults.size} results found"
        
        // Smooth slide-in animation for results
        resultsHeader.alpha = 0f
        resultsHeader.translationY = -50f
        resultsRecyclerView.alpha = 0f
        resultsRecyclerView.translationY = 50f
        
        resultsHeader.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .start()
            
        resultsRecyclerView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(100)
            .start()
    }


    private fun hideResults() {
        resultsContainer.visibility = View.GONE
        emptyStateView.visibility = View.GONE
    }

    // RecyclerView Adapter
    private inner class SearchResultsAdapter(
        private val items: List<SearchItem>,
        private val onItemClick: (SearchItem) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.iv_item_image)
            val nameText: TextView = itemView.findViewById(R.id.tv_item_name)
            val priceText: TextView = itemView.findViewById(R.id.tv_item_price)
            val sellerText: TextView = itemView.findViewById(R.id.tv_seller)
            val locationText: TextView = itemView.findViewById(R.id.tv_location)
            val favoriteButton: ImageView = itemView.findViewById(R.id.btn_favorite)
            val statusBadge: TextView = itemView.findViewById(R.id.tv_status_badge)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.nameText.text = item.name
            holder.priceText.text = if (item.price.startsWith("₱")) item.price else "₱${item.price}"
            holder.sellerText.text = item.seller.ifEmpty { "" }
            holder.locationText.text = item.location
            holder.statusBadge.text = item.status

            // Load image
            if (!item.imageUrl.isNullOrEmpty()) {
                Glide.with(this@SearchActivity)
                    .load(item.imageUrl)
                    .into(holder.imageView)
            }

            // Set status badge background
            if (item.status == "FOR SALE") {
                holder.statusBadge.setBackgroundResource(R.drawable.badge_sell)
            } else {
                holder.statusBadge.setBackgroundResource(R.drawable.badge_bid)
            }

            // Handle favorite button
            holder.favoriteButton.setOnClickListener {
                // Toggle favorite state
                Toast.makeText(this@SearchActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
            }

            // Handle item click
            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }
    
    // Recent Searches Management
    private fun loadRecentSearches() {
        val sharedPrefs = getSharedPreferences("search_history", MODE_PRIVATE)
        val searches = sharedPrefs.getStringSet("recent_searches", emptySet())?.toList() ?: emptyList()
        recentSearches.clear()
        recentSearches.addAll(searches)
        updateRecentSearchesUI()
    }
    
    private fun saveRecentSearches() {
        val sharedPrefs = getSharedPreferences("search_history", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putStringSet("recent_searches", recentSearches.toSet())
        editor.apply()
    }
    
    private fun addToRecentSearches(query: String) {
        if (query.isNotEmpty() && !recentSearches.contains(query)) {
            recentSearches.add(0, query)
            if (recentSearches.size > 5) {
                recentSearches.removeAt(recentSearches.size - 1)
            }
            saveRecentSearches()
            updateRecentSearchesUI()
        }
    }
    
    private fun updateRecentSearchesUI() {
        recentSearchesContainerInner.removeAllViews()
        
        if (recentSearches.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No recent searches"
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                textSize = 14f
                setPadding(16, 8, 16, 8)
            }
            recentSearchesContainerInner.addView(emptyView)
        } else {
            recentSearches.forEach { search ->
                val searchItemView = createRecentSearchItem(search)
                recentSearchesContainerInner.addView(searchItemView)
            }
        }
    }
    
    private fun createRecentSearchItem(search: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            background = resources.getDrawable(android.R.drawable.list_selector_background, null)
            
            // Search text
            val searchText = TextView(this@SearchActivity).apply {
                text = search
                setTextColor(resources.getColor(android.R.color.black, null))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(searchText)
            
            // Delete button
            val deleteButton = TextView(this@SearchActivity).apply {
                text = "✕"
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                textSize = 18f
                setPadding(16, 8, 16, 8)
                background = resources.getDrawable(android.R.drawable.list_selector_background, null)
                gravity = android.view.Gravity.CENTER
            }
            addView(deleteButton)
            
            // Click listeners
            setOnClickListener {
                searchEditText.setText(search)
                performSearch(search)
            }
            
            deleteButton.setOnClickListener {
                removeRecentSearch(search)
            }
        }
    }
    
    private fun removeRecentSearch(search: String) {
        recentSearches.remove(search)
        saveRecentSearches()
        updateRecentSearchesUI()
    }
    
    private fun clearAllRecentSearches() {
        AlertDialog.Builder(this)
            .setTitle("Clear Recent Searches")
            .setMessage("Are you sure you want to clear all recent searches?")
            .setPositiveButton("Clear") { _, _ ->
                recentSearches.clear()
                saveRecentSearches()
                updateRecentSearchesUI()
                Toast.makeText(this, "Recent searches cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}