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
        val category: String
    )

    private lateinit var searchEditText: EditText
    private lateinit var searchButton: com.google.android.material.button.MaterialButton
    private lateinit var backButton: ImageView
    private lateinit var micButton: ImageView
    private lateinit var filterButton: ImageView
    private lateinit var recentSearchesContainer: ScrollView
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
            micButton = findViewById(R.id.btn_mic)
            filterButton = findViewById(R.id.btn_filter)
            recentSearchesContainer = findViewById(R.id.ll_recent_searches)
            resultsRecyclerView = findViewById(R.id.rv_search_results)
            emptyStateView = findViewById(R.id.ll_empty_state)
            resultsContainer = findViewById(R.id.ll_results_container)
            loadingView = findViewById(R.id.ll_loading)
            resultsHeader = findViewById(R.id.ll_results_header)

            // Setup RecyclerView
            resultsRecyclerView.layoutManager = LinearLayoutManager(this)
            resultsRecyclerView.adapter = SearchResultsAdapter(searchResults) { item ->
                val intent = android.content.Intent(this, ItemDetailsActivity::class.java)
                intent.putExtra("postId", item.id)
                startActivity(intent)
            }
            
            // Setup search input listener for Enter key
            searchEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    val query = searchEditText.text.toString().trim()
                    if (query.isNotEmpty()) {
                        performSearch(query)
                    }
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
            
            micButton.setOnClickListener {
                // Handle voice search
                Toast.makeText(this, "Voice search coming soon!", Toast.LENGTH_SHORT).show()
            }
            
            filterButton.setOnClickListener {
                showFilterDialog()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up click listeners: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun performSearch(query: String) {
        // Add to recent searches
        if (!recentSearches.contains(query)) {
            recentSearches.add(0, query)
            if (recentSearches.size > 5) {
                recentSearches.removeAt(recentSearches.size - 1)
            }
            saveRecentSearches()
        }

        // Show loading state with smooth transition
        showLoadingState()

        // Try Firebase first, but fallback to empty if it fails
        try {
            // Prefix search by title
            firestore.collection("posts")
                .orderBy("title")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener { documents ->
                    val mapped = documents.map { doc ->
                        SearchItem(
                            id = doc.id,
                            name = doc.getString("title") ?: "",
                            price = doc.getString("price") ?: "",
                            seller = doc.getString("sellerName") ?: "Unknown",
                            location = doc.getString("location") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            status = doc.getString("status") ?: "FOR SALE",
                            category = doc.getString("category") ?: "OTHER"
                        )
                    }

                    // Client-side filters
                    val notSold = mapped.filter { it.status.uppercase() != "SOLD" }
                    val byCategory = if (currentCategory == "ALL") notSold else notSold.filter { it.category.equals(currentCategory, true) }
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
                .addOnFailureListener { _ ->
                    // Show empty state on failure
                    updateSearchResults(emptyList())
                }
        } catch (e: Exception) {
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

    private fun loadRecentSearches() {
        // Load from SharedPreferences or database
        // For now, using sample data
        recentSearches.addAll(listOf("Carabao", "Chicken", "Goat"))
    }

    private fun saveRecentSearches() {
        // Save to SharedPreferences or database
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
            holder.priceText.text = item.price
            holder.sellerText.text = item.seller
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
}