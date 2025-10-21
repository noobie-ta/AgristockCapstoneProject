package com.example.agristockcapstoneproject

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.view.View
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SellPostActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var imageUploadArea: FrameLayout
    private lateinit var uploadPlaceholder: LinearLayout
    private lateinit var thumbnailsScroll: HorizontalScrollView
    private lateinit var thumbnailsContainer: LinearLayout
    private lateinit var removeImageButton: ImageView
    private lateinit var titleEditText: EditText
    private lateinit var priceEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var locationEditText: EditText
    private lateinit var pickLocationButton: Button
    private lateinit var postButton: Button
    private lateinit var cancelButton: Button

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    
    private val selectedImageUris: MutableList<Uri> = mutableListOf()
    private val uploadedImageUrls: MutableList<String> = mutableListOf()
    
    // Location data
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedAddress: String = ""

    private val requestImagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickImagesLauncher.launch("image/*")
        } else {
            showError("Permission denied. Unable to access images.")
        }
    }

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNullOrEmpty()) {
            showError("No images selected")
            return@registerForActivityResult
        }
        val limited = uris.take(5)
        selectedImageUris.clear()
        selectedImageUris.addAll(limited)
        showSelectedImages(limited)
    }

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            selectedLatitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            selectedLongitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
            selectedAddress = data?.getStringExtra("address") ?: ""
            
            // Update the location edit text
            locationEditText.setText(selectedAddress)
        }
    }

    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val confirmPost = result.data?.getBooleanExtra("confirmPost", false) ?: false
            if (confirmPost) {
                uploadImageAndPost()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell_post)

        initializeViews()
        setupClickListeners()
        setupCategorySpinner()
    }

    private fun initializeViews() {
        try {
            backButton = findViewById(R.id.btn_back)
            imageUploadArea = findViewById(R.id.fl_image_upload_area)
            uploadPlaceholder = findViewById(R.id.ll_upload_placeholder)
            thumbnailsScroll = findViewById(R.id.hs_thumbnails)
            thumbnailsContainer = findViewById(R.id.ll_thumbnails)
            removeImageButton = findViewById(R.id.btn_remove_image)
            titleEditText = findViewById(R.id.et_title)
            priceEditText = findViewById(R.id.et_price)
            descriptionEditText = findViewById(R.id.et_description)
            categorySpinner = findViewById(R.id.spinner_category)
            locationEditText = findViewById(R.id.et_location)
            pickLocationButton = findViewById(R.id.btn_pick_location)
            postButton = findViewById(R.id.btn_post)
            cancelButton = findViewById(R.id.btn_cancel)
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing views: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        try {
            backButton.setOnClickListener { finish() }
            
            imageUploadArea.setOnClickListener { requestImageAccessAndPick() }
            
            removeImageButton.setOnClickListener {
                removeSelectedImage()
            }
            
            pickLocationButton.setOnClickListener {
                openLocationPicker()
            }
            
            postButton.setOnClickListener {
                if (validateForm()) {
                    showPostPreview()
                }
            }
            
            cancelButton.setOnClickListener {
                showCancelDialog()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up click listeners: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCategorySpinner() {
        try {
            val categories = arrayOf(
                "Select Category",
                "CARABAO",
                "CHICKEN", 
                "GOAT",
                "COW",
                "PIG",
                "DUCK",
                "OTHER"
            )
            
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            categorySpinner.adapter = adapter
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up category spinner: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSelectedImages(uris: List<Uri>) {
        thumbnailsContainer.removeAllViews()
        for (u in uris) {
            val iv = ImageView(this)
            val size = (resources.displayMetrics.density * 160).toInt()
            iv.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = (8 * resources.displayMetrics.density).toInt()
            }
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(this).load(u).into(iv)
            thumbnailsContainer.addView(iv)
        }
        uploadPlaceholder.visibility = View.GONE
        thumbnailsScroll.visibility = View.VISIBLE
        removeImageButton.visibility = View.VISIBLE
    }

    private fun removeSelectedImage() {
        selectedImageUris.clear()
        thumbnailsContainer.removeAllViews()
        thumbnailsScroll.visibility = View.GONE
        uploadPlaceholder.visibility = LinearLayout.VISIBLE
        removeImageButton.visibility = View.GONE
    }

    private fun validateForm(): Boolean {
        val title = titleEditText.text.toString().trim()
        val price = priceEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()
        val selectedCategory = categorySpinner.selectedItem.toString()

        if (title.isEmpty()) {
            titleEditText.error = "Title is required"
            titleEditText.requestFocus()
            return false
        }

        if (price.isEmpty()) {
            priceEditText.error = "Price is required"
            priceEditText.requestFocus()
            return false
        }

        if (description.isEmpty()) {
            descriptionEditText.error = "Description is required"
            descriptionEditText.requestFocus()
            return false
        }

        if (selectedCategory == "Select Category") {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun openLocationPicker() {
        val intent = Intent(this, LocationPickerActivity::class.java)
        locationPickerLauncher.launch(intent)
    }

    private fun showPostPreview() {
        val title = titleEditText.text.toString().trim()
        val price = priceEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()
        val category = categorySpinner.selectedItem.toString()
        val location = locationEditText.text.toString().trim()
        
        // Get first image URL for preview
        val imageUrl = if (selectedImageUris.isNotEmpty()) {
            // For preview, we'll use the first image URI directly
            selectedImageUris.first().toString()
        } else {
            ""
        }
        
        val intent = Intent(this, PostPreviewActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("price", price)
        intent.putExtra("description", description)
        intent.putExtra("category", category)
        intent.putExtra("location", location)
        intent.putExtra("imageUrl", imageUrl)
        previewLauncher.launch(intent)
    }

    private fun uploadImageAndPost() {
        postButton.isEnabled = false
        postButton.text = "Posting..."

        if (selectedImageUris.isNotEmpty()) {
            uploadAllImagesThenPost()
        } else {
            savePostToDatabase()
        }
    }

    private fun uploadImageToStorage(uri: Uri, callback: (String?) -> Unit) {
        val user = auth.currentUser ?: run {
            showError("You must be logged in to upload images")
            callback(null)
            return
        }

        // Ensure user is authenticated before uploading
        if (user.uid.isBlank()) {
            showError("Invalid user authentication")
            callback(null)
            return
        }

        val timestamp = System.currentTimeMillis()
        val imageRef = storage.reference.child("post_images/${user.uid}/${timestamp}.jpg")

        // Add metadata to the upload
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("userId", user.uid)
            .setCustomMetadata("uploadTime", timestamp.toString())
            .build()

        imageRef.putFile(uri, metadata)
            .addOnSuccessListener { taskSnapshot ->
                imageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        callback(downloadUri.toString())
                    }
                    .addOnFailureListener { exception ->
                        showError("Failed to get image URL: ${exception.message}")
                        callback(null)
                    }
            }
            .addOnFailureListener { exception ->
                showError("Upload failed: ${exception.message}")
                callback(null)
            }
    }

    private fun uploadAllImagesThenPost() {
        uploadedImageUrls.clear()
        val toUpload = selectedImageUris.toList()
        var completed = 0
        for (u in toUpload) {
            uploadImageToStorage(u) { url ->
                if (url != null) uploadedImageUrls.add(url)
                completed++
                if (completed == toUpload.size) {
                    if (uploadedImageUrls.size == toUpload.size) {
                        savePostToDatabase()
                    } else {
                        showError("Some images failed to upload. Please try again.")
                        postButton.isEnabled = true
                        postButton.text = "POST"
                    }
                }
            }
        }
    }

    private fun savePostToDatabase() {
        val user = auth.currentUser
        if (user == null) {
            showError("You must be logged in to post")
            postButton.isEnabled = true
            postButton.text = "POST"
            return
        }

        val title = titleEditText.text.toString().trim()
        val price = priceEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()
        val category = categorySpinner.selectedItem.toString()
        val timestamp = System.currentTimeMillis()
        val datePosted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))

        // Get seller name from user profile
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val sellerName = userDoc.getString("username") ?: 
                    "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                    ?.ifEmpty { userDoc.getString("displayName") ?: user.displayName ?: "User" }
                    ?: (user.displayName ?: "User")
                
                val postData = hashMapOf(
                    "userId" to user.uid,
                    "type" to "SELL",
                    "title" to title,
                    "price" to price,
                    "description" to description,
                    "category" to category,
                    "imageUrls" to uploadedImageUrls as List<Any>,
                    "imageUrl" to uploadedImageUrls.firstOrNull(), // Store first image for compatibility
                    "timestamp" to timestamp,
                    "datePosted" to datePosted,
                    "status" to "Available",
                    "sellerName" to sellerName,
                    "location" to locationEditText.text.toString().trim().ifEmpty { "Location not specified" },
                    "latitude" to selectedLatitude,
                    "longitude" to selectedLongitude,
                    "address" to selectedAddress,
                    "favoriteCount" to 0L // Initialize favorite count
                )

                firestore.collection("posts")
                    .add(postData)
                    .addOnSuccessListener { documentReference ->
                        showSuccessDialog()
                    }
                    .addOnFailureListener { exception ->
                        showError("Failed to post: ${exception.message}")
                        postButton.isEnabled = true
                        postButton.text = "POST"
                    }
            }
            .addOnFailureListener { exception ->
                // Fallback to displayName if user profile can't be loaded
                val fallbackName = user.displayName ?: "User"
                val postData = hashMapOf(
                    "userId" to user.uid,
                    "type" to "SELL",
                    "title" to title,
                    "price" to price,
                    "description" to description,
                    "category" to category,
                    "imageUrls" to uploadedImageUrls as List<Any>,
                    "imageUrl" to uploadedImageUrls.firstOrNull(),
                    "timestamp" to timestamp,
                    "datePosted" to datePosted,
                    "status" to "Available",
                    "sellerName" to fallbackName,
                    "location" to locationEditText.text.toString().trim().ifEmpty { "Location not specified" },
                    "latitude" to selectedLatitude,
                    "longitude" to selectedLongitude,
                    "address" to selectedAddress,
                    "favoriteCount" to 0L
                )

                firestore.collection("posts")
                    .add(postData)
                    .addOnSuccessListener { documentReference ->
                        showSuccessDialog()
                    }
                    .addOnFailureListener { exception ->
                        showError("Failed to post: ${exception.message}")
                        postButton.isEnabled = true
                        postButton.text = "POST"
                    }
            }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Success!")
            .setMessage("Your item has been posted successfully.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showCancelDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Post")
            .setMessage("Are you sure you want to cancel? Your changes will be lost.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                finish()
            }
            .setNegativeButton("Continue Editing", null)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun requestImageAccessAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                pickImagesLauncher.launch("image/*")
            } else {
                requestImagePermissionLauncher.launch(permission)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                pickImagesLauncher.launch("image/*")
            } else {
                requestImagePermissionLauncher.launch(permission)
            }
        } else {
            // No runtime permission required
            pickImagesLauncher.launch("image/*")
        }
    }
}
