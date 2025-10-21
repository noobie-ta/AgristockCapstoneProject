package com.example.agristockcapstoneproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tvImageCounter: TextView
    private lateinit var llIndicators: LinearLayout
    private lateinit var btnClose: ImageView
    private lateinit var progressBar: ProgressBar

    private var imageUrls: List<String> = emptyList()
    private var currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // Configure status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
        loadImages()
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.viewPager)
        tvImageCounter = findViewById(R.id.tv_image_counter)
        llIndicators = findViewById(R.id.ll_indicators)
        btnClose = findViewById(R.id.btn_close)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun loadImages() {
        // Get image URLs from intent
        imageUrls = intent.getStringArrayListExtra("imageUrls") ?: emptyList()
        currentPosition = intent.getIntExtra("currentPosition", 0)

        if (imageUrls.isEmpty()) {
            finish()
            return
        }

        // Setup ViewPager2
        val adapter = ImageViewerAdapter(imageUrls)
        viewPager.adapter = adapter
        viewPager.currentItem = currentPosition

        // Setup page change listener
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateImageCounter(position)
                updateIndicators(position)
            }
        })

        // Setup indicators
        setupIndicators()
        updateImageCounter(currentPosition)
        updateIndicators(currentPosition)
    }

    private fun setupIndicators() {
        llIndicators.removeAllViews()
        
        for (i in imageUrls.indices) {
            val indicator = ImageView(this)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 0, 8, 0)
            indicator.layoutParams = layoutParams
            indicator.setImageResource(R.drawable.indicator_dot)
            llIndicators.addView(indicator)
        }
    }

    private fun updateImageCounter(position: Int) {
        tvImageCounter.text = "${position + 1} / ${imageUrls.size}"
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until llIndicators.childCount) {
            val indicator = llIndicators.getChildAt(i) as ImageView
            indicator.setImageResource(
                if (i == position) R.drawable.indicator_dot_active else R.drawable.indicator_dot
            )
        }
    }

    inner class ImageViewerAdapter(private val imageUrls: List<String>) :
        RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoView: PhotoView = view.findViewById(R.id.photo_view)
            val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
            val llError: LinearLayout = view.findViewById(R.id.ll_error)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image_viewer, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imageUrl = imageUrls[position]
            
            // Show loading
            holder.progressBar.visibility = View.VISIBLE
            holder.llError.visibility = View.GONE

            // Load image with Glide
            Glide.with(this@ImageViewerActivity)
                .load(imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(holder.photoView)
            
            // Hide loading indicator after a short delay
            holder.progressBar.postDelayed({
                holder.progressBar.visibility = View.GONE
            }, 500)
        }

        override fun getItemCount(): Int = imageUrls.size
    }

    companion object {
        fun start(context: android.content.Context, imageUrls: List<String>, currentPosition: Int = 0) {
            val intent = Intent(context, ImageViewerActivity::class.java)
            intent.putStringArrayListExtra("imageUrls", ArrayList(imageUrls))
            intent.putExtra("currentPosition", currentPosition)
            context.startActivity(intent)
        }
    }
}