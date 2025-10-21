package com.example.agristockcapstoneproject

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.example.agristockcapstoneproject.databinding.ActivityBidPostBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class BidPostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBidPostBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var selectedImageUris: MutableList<Uri> = mutableListOf()
    private var uploadedImageUrls: MutableList<String> = mutableListOf()
    private var selectedEndDate: Calendar? = null
    
    // Location data
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedAddress: String = ""
    
    private val categories = listOf(
        "Select Type of Livestock",
        "CARABAO",
        "CHICKEN",
        "GOAT",
        "COW",
        "PIG",
        "DUCK",
        "OTHER"
    )

    // Image picker launcher for multiple images
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
        androidx.activity.result.ActivityResultCallback<List<Uri>> { uris ->
            if (uris.isNotEmpty()) {
                val limited = uris.take(5) // Limit to 5 images
                selectedImageUris.clear()
                selectedImageUris.addAll(limited)
                displaySelectedImages(limited)
            }
        }
    )

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
        androidx.activity.result.ActivityResultCallback<Boolean> { success ->
            if (success) {
                selectedImageUris.lastOrNull()?.let { uri ->
                    displaySelectedImages(listOf(uri))
                }
            }
        }
    )

    // Location picker launcher
    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            selectedLatitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            selectedLongitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
            selectedAddress = data?.getStringExtra("address") ?: ""
            
            // Update the location edit text
            binding.etLocation.setText(selectedAddress)
        }
    }

    // Preview launcher
    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val confirmPost = result.data?.getBooleanExtra("confirmPost", false) ?: false
            if (confirmPost) {
                postBidItem()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBidPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure status bar with dark background and white icons for consistency
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupUI()
        setupClickListeners()
        
        // Debug: Check button state
        android.util.Log.d("BidPostActivity", "Button enabled: ${binding.btnPostItem.isEnabled}")
        android.util.Log.d("BidPostActivity", "Button clickable: ${binding.btnPostItem.isClickable}")
        android.util.Log.d("BidPostActivity", "Button visibility: ${binding.btnPostItem.visibility}")
    }

    private fun setupUI() {
        // Setup category spinner
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter

        // Set default end time to 7 days from now
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 7)
        selectedEndDate = calendar
        updateEndTimeDisplay()
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Image upload - open gallery directly
        binding.flImageUploadArea.setOnClickListener {
            openGallery()
        }

        // Remove image
        binding.btnRemoveImage.setOnClickListener {
            selectedImageUris.clear()
            uploadedImageUrls.clear()
            binding.ivSelectedImage.visibility = View.GONE
            binding.tvImageCount.visibility = View.GONE
            binding.llUploadPlaceholder.visibility = View.VISIBLE
            binding.btnRemoveImage.visibility = View.GONE
        }

        // End time picker
        binding.etEndTime.setOnClickListener {
            showDateTimePicker()
        }

        // Location picker
        binding.btnPickLocation.setOnClickListener {
            openLocationPicker()
        }

        // Post button
        binding.btnPostItem.setOnClickListener {
            android.util.Log.d("BidPostActivity", "Post button clicked")
            if (validateForm()) {
                android.util.Log.d("BidPostActivity", "Form validation passed, showing preview")
                showBidPostPreview()
            } else {
                android.util.Log.d("BidPostActivity", "Form validation failed")
            }
        }

        // Cancel button
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupFormValidation() {
        // Add text watchers to update preview in real-time
        binding.etItemName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.etStartingBid.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
            }
        })
    }

    // Removed image source dialog; gallery opens directly from click listener

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            return
        }

        val photoFile = createImageFile()
        selectedImageUris.add(photoFile)
        cameraLauncher.launch(photoFile)
    }

    private fun openGallery() {
        imagePickerLauncher.launch("image/*")
    }

    private fun createImageFile(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timestamp}_"
        val storageDir = getExternalFilesDir("Pictures")
        val image = java.io.File.createTempFile(imageFileName, ".jpg", storageDir)
        return Uri.fromFile(image)
    }

    private fun displaySelectedImages(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            binding.ivSelectedImage.visibility = View.VISIBLE
            binding.llUploadPlaceholder.visibility = View.GONE
            binding.btnRemoveImage.visibility = View.VISIBLE
            
            // Show first image as preview
            Glide.with(this)
                .load(uris.first())
                .centerCrop()
                .into(binding.ivSelectedImage)
            
            // Show image count if multiple images
            if (uris.size > 1) {
                binding.tvImageCount.text = "+${uris.size - 1} more"
                binding.tvImageCount.visibility = View.VISIBLE
            } else {
                binding.tvImageCount.visibility = View.GONE
            }
        }
    }

    private fun showDateTimePicker() {
        val calendar = selectedEndDate ?: Calendar.getInstance()
        
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        selectedCalendar.set(Calendar.MINUTE, minute)
                        selectedEndDate = selectedCalendar
                        updateEndTimeDisplay()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateEndTimeDisplay() {
        selectedEndDate?.let { calendar ->
            val formatter = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            binding.etEndTime.setText(formatter.format(calendar.time))
        }
    }

    private fun showBidPostPreview() {
        val itemName = binding.etItemName.text.toString().trim()
        val startingBid = binding.etStartingBid.text.toString().trim()
        val bidIncrement = binding.etBidIncrement.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val category = binding.spinnerCategory.selectedItem.toString()
        val location = binding.etLocation.text.toString().trim()
        val endTime = binding.etEndTime.text.toString().trim()
        
        // Get first image URL for preview
        val imageUrl = if (selectedImageUris.isNotEmpty()) {
            // For preview, we'll use the first image URI directly
            selectedImageUris.first().toString()
        } else {
            ""
        }
        
        val intent = Intent(this, BidPostPreviewActivity::class.java)
        intent.putExtra("itemName", itemName)
        intent.putExtra("startingBid", startingBid)
        intent.putExtra("bidIncrement", bidIncrement)
        intent.putExtra("description", description)
        intent.putExtra("category", category)
        intent.putExtra("location", location)
        intent.putExtra("endTime", endTime)
        intent.putExtra("imageUrl", imageUrl)
        previewLauncher.launch(intent)
    }


    private fun validateForm(): Boolean {
        var isValid = true

        // Check image
        if (selectedImageUris.isEmpty()) {
            android.util.Log.d("BidPostActivity", "Validation failed: No image selected")
            Toast.makeText(this, "Please select at least one image", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check item name
        if (binding.etItemName.text.toString().trim().isEmpty()) {
            android.util.Log.d("BidPostActivity", "Validation failed: Item name is empty")
            binding.etItemName.error = "Item name is required"
            isValid = false
        }

        // Check description
        if (binding.etDescription.text.toString().trim().isEmpty()) {
            android.util.Log.d("BidPostActivity", "Validation failed: Description is empty")
            binding.etDescription.error = "Description is required"
            isValid = false
        }

        // Check category selection
        if (binding.spinnerCategory.selectedItemPosition == 0) {
            android.util.Log.d("BidPostActivity", "Validation failed: No category selected")
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check location
        if (binding.etLocation.text.toString().trim().isEmpty()) {
            android.util.Log.d("BidPostActivity", "Validation failed: Location is empty")
            binding.etLocation.error = "Location is required"
            isValid = false
        }

        // Check starting bid
        val startingBidText = binding.etStartingBid.text.toString().trim()
        if (startingBidText.isEmpty()) {
            android.util.Log.d("BidPostActivity", "Validation failed: Starting bid is empty")
            binding.etStartingBid.error = "Starting bid is required"
            isValid = false
        } else {
            try {
                val bid = startingBidText.toDouble()
                if (bid <= 0) {
                    android.util.Log.d("BidPostActivity", "Validation failed: Starting bid must be greater than 0")
                    binding.etStartingBid.error = "Starting bid must be greater than 0"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                android.util.Log.d("BidPostActivity", "Validation failed: Invalid bid amount")
                binding.etStartingBid.error = "Invalid bid amount"
                isValid = false
            }
        }

        // Check bid increment
        val bidIncrementText = binding.etBidIncrement.text.toString().trim()
        if (bidIncrementText.isEmpty()) {
            android.util.Log.d("BidPostActivity", "Validation failed: Bid increment is empty")
            binding.etBidIncrement.error = "Bid increment is required"
            isValid = false
        } else {
            try {
                val increment = bidIncrementText.toDouble()
                val startingBid = startingBidText.toDoubleOrNull() ?: 0.0
                
                if (increment <= 0) {
                    android.util.Log.d("BidPostActivity", "Validation failed: Bid increment must be greater than 0")
                    binding.etBidIncrement.error = "Bid increment must be greater than 0"
                    isValid = false
                } else if (startingBid > 0 && increment >= startingBid) {
                    android.util.Log.d("BidPostActivity", "Validation failed: Bid increment must be less than starting bid")
                    binding.etBidIncrement.error = "Bid increment must be less than starting bid"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                android.util.Log.d("BidPostActivity", "Validation failed: Invalid bid increment amount")
                binding.etBidIncrement.error = "Invalid increment amount"
                isValid = false
            }
        }

        // Check end time
        if (selectedEndDate == null) {
            android.util.Log.d("BidPostActivity", "Validation failed: No end time selected")
            Toast.makeText(this, "Please select bid end time", Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            val now = Calendar.getInstance()
            if (selectedEndDate!!.before(now)) {
                android.util.Log.d("BidPostActivity", "Validation failed: End time is in the past")
                Toast.makeText(this, "Bid end time must be in the future", Toast.LENGTH_SHORT).show()
                isValid = false
            }
        }

        android.util.Log.d("BidPostActivity", "Form validation result: $isValid")
        return isValid
    }

    private fun openLocationPicker() {
        val intent = Intent(this, LocationPickerActivity::class.java)
        locationPickerLauncher.launch(intent)
    }

    private fun postBidItem() {
        binding.btnPostItem.isEnabled = false
        binding.btnPostItem.text = "Posting..."

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to post items", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Upload images first
        if (selectedImageUris.isNotEmpty()) {
            uploadImagesAndPost()
        } else {
            Toast.makeText(this, "Please select at least one image", Toast.LENGTH_SHORT).show()
            binding.btnPostItem.isEnabled = true
            binding.btnPostItem.text = "Post Item"
        }
    }

    private fun uploadImagesAndPost() {
        uploadedImageUrls.clear()
        val toUpload = selectedImageUris.toList()
        var completed = 0
        
        for (uri in toUpload) {
            uploadImageToStorage(uri) { url ->
                if (url != null) uploadedImageUrls.add(url)
                completed++
                if (completed == toUpload.size) {
                    if (uploadedImageUrls.size == toUpload.size) {
                        createBidPostWithImages()
                    } else {
                        Toast.makeText(this, "Some images failed to upload. Please try again.", Toast.LENGTH_SHORT).show()
                        binding.btnPostItem.isEnabled = true
                        binding.btnPostItem.text = "Post Item"
                    }
                }
            }
        }
    }

    private fun uploadImageToStorage(uri: Uri, callback: (String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to upload images", Toast.LENGTH_SHORT).show()
            callback(null)
            return
        }

        val timestamp = System.currentTimeMillis()
        val imageRef = storage.reference.child("post_images/${currentUser.uid}/${timestamp}.jpg")
        
        // Add metadata to the upload
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("userId", currentUser.uid)
            .setCustomMetadata("uploadTime", timestamp.toString())
            .build()
        
        imageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                imageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        callback(downloadUri.toString())
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to get image URL: ${exception.message}", Toast.LENGTH_SHORT).show()
                        callback(null)
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    private fun createBidPostWithImages() {
        val currentUser = auth.currentUser!!
        val currentTime = System.currentTimeMillis()
        
        // Get seller name from user profile
        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val sellerName = userDoc.getString("username") ?: 
                    "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                    ?.ifEmpty { userDoc.getString("displayName") ?: currentUser.displayName ?: "User" }
                    ?: (currentUser.displayName ?: "User")
                
                val bidPost = hashMapOf(
                    "userId" to currentUser.uid,
                    "userEmail" to currentUser.email,
                    "title" to binding.etItemName.text.toString().trim(),
                    "itemName" to binding.etItemName.text.toString().trim(),
                    "description" to binding.etDescription.text.toString().trim(),
                    "price" to "₱${binding.etStartingBid.text.toString().trim()}",
                    "startingBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "currentBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "bidIncrement" to binding.etBidIncrement.text.toString().trim().toDouble(),
                    "category" to categories[binding.spinnerCategory.selectedItemPosition],
                    "location" to binding.etLocation.text.toString().trim().ifEmpty { "Location not specified" },
                    "latitude" to selectedLatitude,
                    "longitude" to selectedLongitude,
                    "address" to selectedAddress,
                    "imageUrl" to (uploadedImageUrls.firstOrNull() ?: ""),
                    "imageUrls" to uploadedImageUrls as List<Any>,
                    "endTime" to selectedEndDate!!.timeInMillis,
                    "createdAt" to currentTime,
                    "timestamp" to currentTime,
                    "datePosted" to java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                    "type" to "BID",
                    "status" to "ACTIVE",
                    "bidCount" to 0,
                    "favoriteCount" to 0L,
                    "sellerName" to sellerName
                )

                firestore.collection("posts")
                    .add(bidPost)
                    .addOnSuccessListener { documentReference ->
                        Toast.makeText(this, "Bid posted successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to post bid: ${exception.message}", Toast.LENGTH_SHORT).show()
                        binding.btnPostItem.isEnabled = true
                        binding.btnPostItem.text = "Post Item"
                    }
            }
            .addOnFailureListener { exception ->
                // Fallback to displayName if user profile can't be loaded
                val fallbackName = currentUser.displayName ?: "User"
                val bidPost = hashMapOf(
                    "userId" to currentUser.uid,
                    "userEmail" to currentUser.email,
                    "title" to binding.etItemName.text.toString().trim(),
                    "itemName" to binding.etItemName.text.toString().trim(),
                    "description" to binding.etDescription.text.toString().trim(),
                    "price" to "₱${binding.etStartingBid.text.toString().trim()}",
                    "startingBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "currentBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "bidIncrement" to binding.etBidIncrement.text.toString().trim().toDouble(),
                    "category" to categories[binding.spinnerCategory.selectedItemPosition],
                    "location" to binding.etLocation.text.toString().trim().ifEmpty { "Location not specified" },
                    "latitude" to selectedLatitude,
                    "longitude" to selectedLongitude,
                    "address" to selectedAddress,
                    "imageUrl" to (uploadedImageUrls.firstOrNull() ?: ""),
                    "imageUrls" to uploadedImageUrls as List<Any>,
                    "endTime" to selectedEndDate!!.timeInMillis,
                    "createdAt" to currentTime,
                    "timestamp" to currentTime,
                    "datePosted" to java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                    "type" to "BID",
                    "status" to "ACTIVE",
                    "bidCount" to 0,
                    "favoriteCount" to 0L,
                    "sellerName" to fallbackName
                )

                firestore.collection("posts")
                    .add(bidPost)
                    .addOnSuccessListener { documentReference ->
                        Toast.makeText(this, "Bid posted successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to post bid: ${exception.message}", Toast.LENGTH_SHORT).show()
                        binding.btnPostItem.isEnabled = true
                        binding.btnPostItem.text = "Post Item"
                    }
            }
    }

    private fun createBidPost(imageUrl: String) {
        val currentUser = auth.currentUser!!
        val currentTime = System.currentTimeMillis()
        
        // Get seller name from user profile
        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val sellerName = userDoc.getString("username") ?: 
                    "${userDoc.getString("firstName") ?: ""} ${userDoc.getString("lastName") ?: ""}".trim()
                    ?.ifEmpty { userDoc.getString("displayName") ?: currentUser.displayName ?: "User" }
                    ?: (currentUser.displayName ?: "User")
                
                val bidPost = hashMapOf(
                    "userId" to currentUser.uid,
                    "userEmail" to currentUser.email,
                    "title" to binding.etItemName.text.toString().trim(), // Use 'title' for consistency
                    "itemName" to binding.etItemName.text.toString().trim(),
                    "description" to binding.etDescription.text.toString().trim(),
                    "price" to "₱${binding.etStartingBid.text.toString().trim()}", // Add price field for consistency
                    "startingBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "currentBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "category" to categories[binding.spinnerCategory.selectedItemPosition],
                    "location" to binding.etLocation.text.toString().trim().ifEmpty { "Location not specified" },
                    "latitude" to selectedLatitude,
                    "longitude" to selectedLongitude,
                    "address" to selectedAddress,
                    "imageUrl" to imageUrl,
                    "endTime" to selectedEndDate!!.timeInMillis,
                    "createdAt" to currentTime,
                    "timestamp" to currentTime, // Add timestamp for consistency
                    "datePosted" to java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                    "type" to "BID",
                    "status" to "ACTIVE",
                    "bidCount" to 0,
                    "favoriteCount" to 0L, // Initialize favorite count
                    "sellerName" to sellerName
                )

                firestore.collection("posts")
                    .add(bidPost)
                    .addOnSuccessListener { documentReference ->
                        Toast.makeText(this, "Bid posted successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to post bid: ${exception.message}", Toast.LENGTH_SHORT).show()
                        binding.btnPostItem.isEnabled = true
                        binding.btnPostItem.text = "Post Item"
                    }
            }
            .addOnFailureListener { exception ->
                // Fallback to displayName if user profile can't be loaded
                val fallbackName = currentUser.displayName ?: "User"
                val bidPost = hashMapOf(
                    "userId" to currentUser.uid,
                    "userEmail" to currentUser.email,
                    "title" to binding.etItemName.text.toString().trim(),
                    "itemName" to binding.etItemName.text.toString().trim(),
                    "description" to binding.etDescription.text.toString().trim(),
                    "price" to "₱${binding.etStartingBid.text.toString().trim()}",
                    "startingBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "currentBid" to binding.etStartingBid.text.toString().trim().toDouble(),
                    "category" to categories[binding.spinnerCategory.selectedItemPosition],
                    "location" to binding.etLocation.text.toString().trim().ifEmpty { "Location not specified" },
                    "latitude" to selectedLatitude,
                    "longitude" to selectedLongitude,
                    "address" to selectedAddress,
                    "imageUrl" to imageUrl,
                    "endTime" to selectedEndDate!!.timeInMillis,
                    "createdAt" to currentTime,
                    "timestamp" to currentTime,
                    "datePosted" to java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                    "type" to "BID",
                    "status" to "ACTIVE",
                    "bidCount" to 0,
                    "favoriteCount" to 0L,
                    "sellerName" to fallbackName
                )

                firestore.collection("posts")
                    .add(bidPost)
                    .addOnSuccessListener { documentReference ->
                        Toast.makeText(this, "Bid posted successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to post bid: ${exception.message}", Toast.LENGTH_SHORT).show()
                        binding.btnPostItem.isEnabled = true
                        binding.btnPostItem.text = "Post Item"
                    }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }
}
