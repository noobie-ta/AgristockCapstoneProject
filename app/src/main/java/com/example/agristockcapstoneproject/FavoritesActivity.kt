package com.example.agristockcapstoneproject

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
        val date: String
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var browseButton: TextView
    private val items = mutableListOf<FavoriteItem>()

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        recyclerView = findViewById(R.id.rv_favorites)
        emptyState = findViewById(R.id.ll_empty_state)
        browseButton = findViewById(R.id.btn_browse_items)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = FavoritesAdapter(items) { item ->
            confirmRemoval(item)
        }

        browseButton.setOnClickListener {
            finish()
        }

        loadFavorites()
    }

    private fun loadFavorites() {
        val uid = auth.currentUser?.uid ?: run {
            showEmpty()
            return
        }
        firestore.collection("users").document(uid)
            .collection("favorites")
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { qs ->
                items.clear()
                for (doc in qs.documents) {
                    items.add(
                        FavoriteItem(
                            id = doc.getString("postId") ?: doc.id,
                            title = doc.getString("title") ?: "",
                            price = doc.getString("price") ?: "",
                            imageUrl = doc.getString("imageUrl"),
                            date = doc.getString("date") ?: ""
                        )
                    )
                }
                recyclerView.adapter?.notifyDataSetChanged()
                if (items.isEmpty()) showEmpty() else showList()
            }
            .addOnFailureListener {
                showEmpty()
            }
    }

    private fun showEmpty() {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun showList() {
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
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
        // Smooth animation: notify removal after deleting
        val index = items.indexOfFirst { it.id == item.id }
        firestore.collection("users").document(uid)
            .collection("favorites").document(item.id)
            .delete()
            .addOnSuccessListener {
                if (index != -1) {
                    items.removeAt(index)
                    recyclerView.adapter?.notifyItemRemoved(index)
                }
                if (items.isEmpty()) showEmpty()
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
            holder.date.text = item.date
            if (!item.imageUrl.isNullOrEmpty()) {
                Glide.with(this@FavoritesActivity).load(item.imageUrl).into(holder.image)
            }
            holder.btn.setOnClickListener { onUnfavorite(item) }
        }

        override fun getItemCount(): Int = data.size
    }
}





