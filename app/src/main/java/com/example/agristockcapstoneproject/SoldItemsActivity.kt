package com.example.agristockcapstoneproject

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tvTotalSold: TextView
    private lateinit var tvTotalRevenue: TextView
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnDateFrom: TextView
    private lateinit var btnDateTo: TextView
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    }
    private var allDocs: List<DocumentSnapshot> = emptyList()
    private var selectedCategory: String = "ALL"
    private var dateFromMillis: Long? = null
    private var dateToMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sold_items)

        postsContainer = findViewById(R.id.ll_sold_items_container)
        swipeRefreshLayout = findViewById(R.id.srl_refresh)
        tvTotalSold = findViewById(R.id.tv_total_sold)
        tvTotalRevenue = findViewById(R.id.tv_total_revenue)
        spinnerCategory = findViewById(R.id.spinner_category)
        btnDateFrom = findViewById(R.id.btn_date_from)
        btnDateTo = findViewById(R.id.btn_date_to)
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        setupFilters()
        setupRefresh()
        loadSoldItems()
    }

    private fun loadSoldItems() {
        val user = auth.currentUser
        postsContainer.removeAllViews()
        if (user == null) {
            renderSoldItems(emptyList())
            return
        }

        // Load user's posts, then filter SOLD client-side (avoids index/case issues)
        firestore.collection("posts")
            .whereEqualTo("userId", user.uid)
            .addSnapshotListener { snapshots, exception ->
                if (exception != null) {
                    android.widget.Toast.makeText(this, "Error loading sold items: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                    renderSoldItems(emptyList())
                    return@addSnapshotListener
                }

                allDocs = snapshots?.documents ?: emptyList()
                applyFiltersAndRender()
            }
    }

    private fun setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            // Data is live via snapshot listener; just re-apply filters and stop the spinner
            applyFiltersAndRender()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupFilters() {
        // Category spinner: simple static list including ALL
        val categories = arrayOf(
            "ALL",
            "CARABAO",
            "CHICKEN",
            "GOAT",
            "COW",
            "PIG",
            "DUCK",
            "OTHER"
        )
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
        spinnerCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCategory = categories[position]
                applyFiltersAndRender()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        btnDateFrom.setOnClickListener {
            openDatePicker { millis ->
                dateFromMillis = startOfDay(millis)
                btnDateFrom.text = dateFormatter.format(dateFromMillis!!)
                applyFiltersAndRender()
            }
        }
        btnDateTo.setOnClickListener {
            openDatePicker { millis ->
                dateToMillis = endOfDay(millis)
                btnDateTo.text = dateFormatter.format(dateToMillis!!)
                applyFiltersAndRender()
            }
        }
    }

    private fun openDatePicker(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val c = Calendar.getInstance()
            c.set(Calendar.YEAR, y)
            c.set(Calendar.MONTH, m)
            c.set(Calendar.DAY_OF_MONTH, d)
            onPicked(c.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun startOfDay(millis: Long?): Long? {
        if (millis == null) return null
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(millis: Long?): Long? {
        if (millis == null) return null
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    private fun applyFiltersAndRender() {
        val sold = allDocs.filter { (it.getString("status") ?: "").uppercase() == "SOLD" }
        val dateFiltered = sold.filter { doc ->
            val ts = doc.getLong("timestamp") ?: 0L
            val afterFrom = dateFromMillis?.let { ts >= it } ?: true
            val beforeTo = dateToMillis?.let { ts <= it } ?: true
            afterFrom && beforeTo
        }
        val categoryFiltered = if (selectedCategory == "ALL") dateFiltered else dateFiltered.filter {
            (it.getString("category") ?: "").equals(selectedCategory, ignoreCase = true)
        }

        val list = categoryFiltered
            .sortedByDescending { it.getLong("timestamp") ?: 0L }
            .map { d ->
                val singleImage = d.getString("imageUrl")
                val images = d.get("imageUrls") as? List<*>
                val firstImageFromList = images?.firstOrNull() as? String
                SoldItem(
                    id = d.id,
                    name = d.getString("title") ?: "",
                    price = d.getString("price") ?: "",
                    description = d.getString("description") ?: "",
                    imageUrl = singleImage ?: firstImageFromList,
                    dateSold = d.getString("dateSold") ?: d.getString("datePosted") ?: "",
                    sellerName = d.getString("sellerName") ?: "Unknown Seller"
                )
            }

        updateStats(list)
        renderSoldItems(list)
    }

    private fun updateStats(items: List<SoldItem>) {
        tvTotalSold.text = "Sold: ${items.size}"
        val total = items.sumOf { parsePriceToDouble(it.price) }
        currencyFormatter.currency = java.util.Currency.getInstance("PHP")
        tvTotalRevenue.text = "Revenue: ${currencyFormatter.format(total)}"
    }

    private fun parsePriceToDouble(text: String): Double {
        val cleaned = text.replace("[^0-9.]".toRegex(), "")
        return cleaned.toDoubleOrNull() ?: 0.0
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

