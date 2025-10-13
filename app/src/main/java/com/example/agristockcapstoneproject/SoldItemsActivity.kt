package com.example.agristockcapstoneproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SoldItemsActivity : AppCompatActivity() {

    data class SoldItem(
        val id: String,
        val name: String,
        val price: String,
        val description: String,
        val imageUrl: String?,
        val dateSold: String,
        val sellerName: String
    )

    private lateinit var postsContainer: LinearLayout
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sold_items)

        postsContainer = findViewById(R.id.ll_sold_items_container)
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        loadSoldItems()
    }

    private fun loadSoldItems() {
        val user = auth.currentUser
        postsContainer.removeAllViews()
        if (user == null) {
            renderSoldItems(emptyList())
            return
        }

        // Load sold items for the current user
        firestore.collection("posts")
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("status", "SOLD")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, _ ->
                val list = snapshots?.documents?.map { d ->
                    SoldItem(
                        id = d.id,
                        name = d.getString("title") ?: "",
                        price = d.getString("price") ?: "",
                        description = d.getString("description") ?: "",
                        imageUrl = d.getString("imageUrl"),
                        dateSold = d.getString("dateSold") ?: d.getString("datePosted") ?: "",
                        sellerName = d.getString("sellerName") ?: "Unknown Seller"
                    )
                } ?: emptyList()
                renderSoldItems(list)
            }
    }

    private fun renderSoldItems(items: List<SoldItem>) {
        postsContainer.removeAllViews()
        if (items.isEmpty()) {
            val placeholder: View = layoutInflater.inflate(R.layout.view_empty_sold_items_placeholder, postsContainer, false)
            postsContainer.addView(placeholder)
            return
        }

        val inflater = LayoutInflater.from(this)
        items.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_sold_post, postsContainer, false)

            val tvName = itemView.findViewById<TextView>(R.id.tv_post_name)
            val tvPrice = itemView.findViewById<TextView>(R.id.tv_post_price)
            val tvDateSold = itemView.findViewById<TextView>(R.id.tv_date_sold)
            val ivImage = itemView.findViewById<ImageView>(R.id.iv_post_image)

            tvName.text = item.name
            tvPrice.text = item.price
            tvDateSold.text = "Sold on: ${item.dateSold}"
            
            if (!item.imageUrl.isNullOrEmpty()) {
                Glide.with(this).load(item.imageUrl).into(ivImage)
            }

            postsContainer.addView(itemView)
        }
    }
}

