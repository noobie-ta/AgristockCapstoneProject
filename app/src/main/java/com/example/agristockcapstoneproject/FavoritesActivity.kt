package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FavoritesActivity : AppCompatActivity() {

    data class FavoriteItem(
        val id: String,
        val title: String,
        val price: String,
        val imageUrl: String?,
        val date: String,
        val type: String = "SELL" // SELL or BID
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var browseButton: TextView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private val items = mutableListOf<FavoriteItem>()
    private val loadedPostIds = mutableSetOf<String>() // Track already loaded post IDs to prevent duplicates

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var favoritesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val removingItems = mutableSetOf<String>() // Track items being removed to prevent duplicates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        // Configure status bar - transparent to show phone status
        com.example.agristockcapstoneproject.utils.StatusBarUtil.makeTransparent(this, lightIcons = true)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        recyclerView = findViewById(R.id.rv_favorites)
        emptyState = findViewById(R.id.ll_empty_state)
        browseButton = findViewById(R.id.btn_browse_items)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = FavoritesAdapter(items) { item ->
            confirmRemoval(item)
        }

        browseButton.setOnClickListener {
            finish()
        }

        // Setup swipe-to-refresh
        swipeRefresh.setColorSchemeResources(
            R.color.yellow_accent,
            R.color.yellow_dark,
            android.R.color.holo_orange_dark
        )
        swipeRefresh.setOnRefreshListener {
            refreshFavorites()
        }

        loadFavorites()
    }

    override fun onResume() {
        super.onResume()
        // Real-time listener handles updates, no need to reload here
    }

    override fun onStart() {
        super.onStart()
        // Set up real-time listener for favorites changes
        setupFavoritesListener()
    }

    override fun onStop() {
        super.onStop()
        // Remove listener to save resources
        removeFavoritesListener()
    }

    private fun refreshFavorites() {
        // The real-time listener already keeps data fresh,
        // so we just need to stop the refresh animation after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            swipeRefresh.isRefreshing = false
        }, 1000)
    }

    private fun loadFavorites() {
        val uid = auth.currentUser?.uid ?: run {
            showEmpty()
            return
        }
        firestore.collection("users").document(uid)
            .collection("favorites")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { qs ->
                items.clear()
                loadedPostIds.clear()
                
                val totalDocs = qs.documents.size
                if (totalDocs == 0) {
                    showEmpty()
                    return@addOnSuccessListener
                }
                
                var loadedCount = 0
                
                for (doc in qs.documents) {
                    val postId = doc.getString("postId") ?: doc.id
                    
                    // Skip if already loaded to prevent duplicates
                    if (loadedPostIds.contains(postId)) {
                        loadedCount++
                        if (loadedCount == totalDocs) {
                            recyclerView.adapter?.notifyDataSetChanged()
                            if (items.isEmpty()) showEmpty() else showList()
                        }
                        continue
                    }
                    
                    loadedPostIds.add(postId)
                    
                    // Fetch post type from the original post document
                    firestore.collection("posts").document(postId)
                        .get()
                        .addOnSuccessListener { postDoc ->
                            val postType = postDoc.getString("type") ?: "SELL"
                            items.add(
                                FavoriteItem(
                                    id = postId,
                                    title = doc.getString("title") ?: "",
                                    price = doc.getString("price") ?: "",
                                    imageUrl = doc.getString("imageUrl"),
                                    date = doc.getString("date") ?: "",
                                    type = postType
                                )
                            )
                            loadedCount++
                            // Only notify once all posts are loaded
                            if (loadedCount == totalDocs) {
                                recyclerView.adapter?.notifyDataSetChanged()
                                if (items.isEmpty()) showEmpty() else showList()
                            }
                        }
                        .addOnFailureListener {
                            // If we can't get the post type, default to SELL
                            items.add(
                                FavoriteItem(
                                    id = postId,
                                    title = doc.getString("title") ?: "",
                                    price = doc.getString("price") ?: "",
                                    imageUrl = doc.getString("imageUrl"),
                                    date = doc.getString("date") ?: "",
                                    type = "SELL"
                                )
                            )
                            loadedCount++
                            // Only notify once all posts are loaded
                            if (loadedCount == totalDocs) {
                                recyclerView.adapter?.notifyDataSetChanged()
                                if (items.isEmpty()) showEmpty() else showList()
                            }
                        }
                }
            }
            .addOnFailureListener {
                showEmpty()
            }
    }

    private fun showEmpty() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showList() {
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false
    }

    private fun confirmRemoval(item: FavoriteItem) {
        AlertDialog.Builder(this)
            .setMessage("Do you want to remove this item from Favorites?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                removeFavorite(item)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun removeFavorite(item: FavoriteItem) {
        val uid = auth.currentUser?.uid ?: return
        
        // Prevent duplicate removal attempts
        if (removingItems.contains(item.id)) {
            return
        }
        removingItems.add(item.id)
        
        // Delete from user's favorites
        firestore.collection("users").document(uid)
            .collection("favorites").document(item.id)
            .delete()
            .addOnSuccessListener {
                // Update the post's favorite count
                firestore.collection("posts").document(item.id)
                    .get()
                    .addOnSuccessListener { postDoc ->
                        if (postDoc.exists()) {
                            val currentCount = (postDoc.getLong("favoriteCount") ?: 0L).toInt()
                            val newCount = maxOf(0, currentCount - 1) // Prevent negative counts
                            
                            firestore.collection("posts").document(item.id)
                                .update("favoriteCount", newCount)
                                .addOnSuccessListener {
                                    android.util.Log.d("FavoritesActivity", "Favorite count updated for ${item.id}")
                                    // UI will be updated automatically by the real-time listener
                                    removingItems.remove(item.id)
                                }
                                .addOnFailureListener { exception ->
                                    android.util.Log.e("FavoritesActivity", "Failed to update post count: ${exception.message}")
                                    removingItems.remove(item.id)
                                }
                        } else {
                            // Post doesn't exist, just clear tracking
                            removingItems.remove(item.id)
                        }
                    }
                    .addOnFailureListener { exception ->
                        android.util.Log.e("FavoritesActivity", "Failed to fetch post: ${exception.message}")
                        removingItems.remove(item.id)
                    }
            }
            .addOnFailureListener { exception ->
                // Handle favorite deletion failure
                android.widget.Toast.makeText(this, "Failed to remove favorite: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                removingItems.remove(item.id)
            }
    }

    private inner class FavoritesAdapter(
        private val data: List<FavoriteItem>,
        private val onUnfavorite: (FavoriteItem) -> Unit
    ) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.iv_item_image)
            val title: TextView = view.findViewById(R.id.tv_item_name)
            val price: TextView = view.findViewById(R.id.tv_item_price)
            val date: TextView = view.findViewById(R.id.tv_date)
            val btn: ImageView = view.findViewById(R.id.btn_unfavorite)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = data[position]
            holder.title.text = item.title
            holder.price.text = item.price
            holder.date.text = "Added ${item.date}"
            if (!item.imageUrl.isNullOrEmpty()) {
                Glide.with(this@FavoritesActivity)
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(holder.image)
            } else {
                holder.image.setImageResource(R.drawable.ic_image_placeholder)
            }
            
            // Set up click listeners
            holder.btn.setOnClickListener { view ->
                // Add click animation
                animateCard(view)
                onUnfavorite(item)
            }
            
            // Make the entire card clickable to view item details
            holder.itemView.setOnClickListener { view ->
                // Add click animation
                animateCard(view)
                // Navigate to appropriate activity based on post type
                val intent = if (item.type == "BID") {
                    Intent(this@FavoritesActivity, ViewBiddingActivity::class.java)
                } else {
                    Intent(this@FavoritesActivity, ItemDetailsActivity::class.java)
                }
                intent.putExtra("postId", item.id)
                this@FavoritesActivity.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = data.size
    }

    private fun setupFavoritesListener() {
        val uid = auth.currentUser?.uid ?: return
        
        favoritesListener = firestore.collection("users").document(uid)
            .collection("favorites")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    android.util.Log.e("FavoritesActivity", "Listener error: ${exception.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot == null) return@addSnapshotListener
                
                // Get current document IDs from Firestore
                val currentPostIds = snapshot.documents.mapNotNull { 
                    it.getString("postId") ?: it.id 
                }.toSet()
                
                // Find items to remove (in local list but not in Firestore)
                val itemsToRemove = items.filter { !currentPostIds.contains(it.id) }
                itemsToRemove.forEach { item ->
                    val index = items.indexOf(item)
                    if (index != -1) {
                        items.removeAt(index)
                        loadedPostIds.remove(item.id)
                        recyclerView.adapter?.notifyItemRemoved(index)
                        android.util.Log.d("FavoritesActivity", "Removed item: ${item.id}")
                    }
                }
                
                // Find items to add (in Firestore but not in local list)
                snapshot.documents.forEach { doc ->
                    val postId = doc.getString("postId") ?: doc.id
                    
                    if (!loadedPostIds.contains(postId)) {
                        loadedPostIds.add(postId)
                        
                        val newItem = FavoriteItem(
                            id = postId,
                            title = doc.getString("title") ?: "",
                            price = doc.getString("price") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            date = doc.getString("date") ?: "",
                            type = "SELL" // Default type for listener
                        )
                        
                        items.add(newItem)
                        recyclerView.adapter?.notifyItemInserted(items.size - 1)
                        android.util.Log.d("FavoritesActivity", "Added item: ${postId}")
                        
                        // Fetch the actual post type asynchronously
                        firestore.collection("posts").document(postId)
                            .get()
                            .addOnSuccessListener { postDoc ->
                                val postType = postDoc.getString("type") ?: "SELL"
                                val itemIndex = items.indexOfFirst { it.id == postId }
                                if (itemIndex != -1 && items[itemIndex].type != postType) {
                                    items[itemIndex] = items[itemIndex].copy(type = postType)
                                    recyclerView.adapter?.notifyItemChanged(itemIndex)
                                }
                            }
                    }
                }
                
                // Update visibility
                if (items.isEmpty()) showEmpty() else showList()
            }
    }

    private fun removeFavoritesListener() {
        favoritesListener?.remove()
        favoritesListener = null
    }
    
    private fun animateCard(view: View) {
        val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f)
        scaleX.duration = 150
        scaleY.duration = 150
        scaleX.start()
        scaleY.start()
    }
}






