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
    
    private var selectedImageUri: Uri? = null
    private var selectedEndDate: Calendar? = null
    
    private val categories = listOf(
        "Fruits", "Vegetables", "Grains", "Livestock", 
        "Seeds", "Fertilizers", "Tools", "Equipment", "Other"
    )

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedImageUri = selectedUri
            displaySelectedImage(selectedUri)
            updatePreview()
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri?.let { uri ->
                displaySelectedImage(uri)
                updatePreview()
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
        setupFormValidation()
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

        // Image upload
        binding.flImageUploadArea.setOnClickListener {
            showImageSourceDialog()
        }

        // Remove image
        binding.btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            binding.ivSelectedImage.visibility = View.GONE
            binding.llUploadPlaceholder.visibility = View.VISIBLE
            binding.btnRemoveImage.visibility = View.GONE
            updatePreview()
        }

        // End time picker
        binding.etEndTime.setOnClickListener {
            showDateTimePicker()
        }

        // Post button
        binding.btnPostItem.setOnClickListener {
            if (validateForm()) {
                postBidItem()
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
                updatePreview()
            }
        })

        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        })

        binding.etStartingBid.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
            }
        })
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            return
        }

        val photoFile = createImageFile()
        selectedImageUri = photoFile
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

    private fun displaySelectedImage(uri: Uri) {
        binding.ivSelectedImage.visibility = View.VISIBLE
        binding.llUploadPlaceholder.visibility = View.GONE
        binding.btnRemoveImage.visibility = View.VISIBLE
        
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(binding.ivSelectedImage)
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
                        updatePreview()
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

    private fun updatePreview() {
        val itemName = binding.etItemName.text.toString().trim()
        val startingBid = binding.etStartingBid.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        
        if (itemName.isNotEmpty() || startingBid.isNotEmpty() || description.isNotEmpty()) {
            binding.llPreviewSection.visibility = View.VISIBLE
            
            // Update preview content
            binding.tvPreviewName.text = if (itemName.isNotEmpty()) itemName else "Item Name"
            binding.tvPreviewPrice.text = if (startingBid.isNotEmpty()) "₱$startingBid" else "₱0"
            binding.tvPreviewDescription.text = if (description.isNotEmpty()) description else "Description"
            
            // Update preview image
            selectedImageUri?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .centerCrop()
                    .into(binding.ivPreviewImage)
            } ?: run {
                binding.ivPreviewImage.setImageResource(R.drawable.ic_image_placeholder)
            }
        } else {
            binding.llPreviewSection.visibility = View.GONE
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Check image
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check item name
        if (binding.etItemName.text.toString().trim().isEmpty()) {
            binding.etItemName.error = "Item name is required"
            isValid = false
        }

        // Check description
        if (binding.etDescription.text.toString().trim().isEmpty()) {
            binding.etDescription.error = "Description is required"
            isValid = false
        }

        // Check starting bid
        val startingBidText = binding.etStartingBid.text.toString().trim()
        if (startingBidText.isEmpty()) {
            binding.etStartingBid.error = "Starting bid is required"
            isValid = false
        } else {
            try {
                val bid = startingBidText.toDouble()
                if (bid <= 0) {
                    binding.etStartingBid.error = "Starting bid must be greater than 0"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.etStartingBid.error = "Invalid bid amount"
                isValid = false
            }
        }

        // Check end time
        if (selectedEndDate == null) {
            Toast.makeText(this, "Please select bid end time", Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            val now = Calendar.getInstance()
            if (selectedEndDate!!.before(now)) {
                Toast.makeText(this, "Bid end time must be in the future", Toast.LENGTH_SHORT).show()
                isValid = false
            }
        }

        return isValid
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

        // Upload image first
        selectedImageUri?.let { uri ->
            uploadImageAndPost(uri)
        }
    }

    private fun uploadImageAndPost(imageUri: Uri) {
        val imageRef = storage.reference.child("bid_images/${UUID.randomUUID()}.jpg")
        
        imageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    createBidPost(downloadUri.toString())
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.btnPostItem.isEnabled = true
                binding.btnPostItem.text = "Post Item"
            }
    }

    private fun createBidPost(imageUrl: String) {
        val currentUser = auth.currentUser!!
        val currentTime = System.currentTimeMillis()
        
        val bidPost = hashMapOf(
            "userId" to currentUser.uid,
            "userEmail" to currentUser.email,
            "itemName" to binding.etItemName.text.toString().trim(),
            "description" to binding.etDescription.text.toString().trim(),
            "startingBid" to binding.etStartingBid.text.toString().trim().toDouble(),
            "currentBid" to binding.etStartingBid.text.toString().trim().toDouble(),
            "category" to categories[binding.spinnerCategory.selectedItemPosition],
            "location" to binding.etLocation.text.toString().trim(),
            "imageUrl" to imageUrl,
            "endTime" to selectedEndDate!!.timeInMillis,
            "createdAt" to currentTime,
            "type" to "BID",
            "status" to "ACTIVE",
            "bidCount" to 0
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
