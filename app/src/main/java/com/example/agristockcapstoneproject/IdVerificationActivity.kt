package com.example.agristockcapstoneproject

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.*

class IdVerificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private lateinit var frontIdContainer: LinearLayout
    private lateinit var backIdContainer: LinearLayout
    private lateinit var frontIdImage: ImageView
    private lateinit var backIdImage: ImageView
    private lateinit var frontIdPlaceholder: ImageView
    private lateinit var backIdPlaceholder: ImageView
    private lateinit var nextButton: AppCompatButton
    private lateinit var idTypeSpinner: Spinner
    
    private var frontIdUri: Uri? = null
    private var backIdUri: Uri? = null
    private var isFrontIdUploaded = false
    private var isBackIdUploaded = false
    private var selectedIdType: String = ""
    
    companion object {
        private const val REQUEST_CODE_FRONT_ID = 1001
        private const val REQUEST_CODE_BACK_ID = 1002
        private const val REQUEST_CODE_CAMERA = 1003
        private const val PERMISSION_REQUEST_CODE = 1004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_id_verification)
        
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        initializeViews()
        setupClickListeners()
        checkVerificationStatus()
    }
    
    private fun initializeViews() {
        frontIdContainer = findViewById(R.id.ll_front_id_container)
        backIdContainer = findViewById(R.id.ll_back_id_container)
        frontIdImage = findViewById(R.id.iv_front_id)
        backIdImage = findViewById(R.id.iv_back_id)
        frontIdPlaceholder = findViewById(R.id.iv_front_placeholder)
        backIdPlaceholder = findViewById(R.id.iv_back_placeholder)
        nextButton = findViewById(R.id.btn_next)
        idTypeSpinner = findViewById(R.id.spinner_id_type)
        
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        
        setupIdTypeSpinner()
    }
    
    private fun setupIdTypeSpinner() {
        val idTypes = arrayOf(
            "Select ID Type",
            "Driver's License",
            "Passport",
            "National ID (PhilSys)",
            "SSS ID",
            "TIN ID",
            "Postal ID",
            "Voter's ID",
            "Senior Citizen ID",
            "PRC ID",
            "Other Government ID"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, idTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        idTypeSpinner.adapter = adapter
        
        idTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position > 0) {
                    selectedIdType = idTypes[position]
                } else {
                    selectedIdType = ""
                }
                updateNextButton()
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedIdType = ""
                updateNextButton()
            }
        }
    }
    
    private fun setupClickListeners() {
        frontIdContainer.setOnClickListener {
            if (checkCameraPermission()) {
                showImageSourceDialog(REQUEST_CODE_FRONT_ID)
            }
        }
        
        backIdContainer.setOnClickListener {
            if (checkCameraPermission()) {
                showImageSourceDialog(REQUEST_CODE_BACK_ID)
            }
        }
        
        nextButton.setOnClickListener {
            if (selectedIdType.isEmpty()) {
                Toast.makeText(this, "Please select an ID type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (isFrontIdUploaded && isBackIdUploaded) {
                // Navigate to selfie verification
                val intent = Intent(this, SelfieVerificationActivity::class.java)
                intent.putExtra("front_id_uri", frontIdUri.toString())
                intent.putExtra("back_id_uri", backIdUri.toString())
                intent.putExtra("id_type", selectedIdType)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Please upload both front and back of your ID", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
            false
        } else {
            true
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, can proceed with camera
            } else {
                Toast.makeText(this, "Camera permission is required to upload ID images", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showImageSourceDialog(requestCode: Int) {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Select Image Source")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera(requestCode)
                1 -> openGallery(requestCode)
            }
        }
        builder.show()
    }
    
    private fun openCamera(requestCode: Int) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, requestCode)
    }
    
    private fun openGallery(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, requestCode)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                REQUEST_CODE_FRONT_ID -> {
                    val imageUri = if (data.extras?.get("data") != null) {
                        // Image from camera
                        val bitmap = data.extras?.get("data") as Bitmap
                        val uri = saveBitmapToUri(bitmap)
                        uri
                    } else {
                        // Image from gallery
                        data.data
                    }
                    
                    if (imageUri != null) {
                        frontIdUri = imageUri
                        displayImage(frontIdImage, frontIdPlaceholder, imageUri)
                        isFrontIdUploaded = true
                        updateNextButton()
                    }
                }
                
                REQUEST_CODE_BACK_ID -> {
                    val imageUri = if (data.extras?.get("data") != null) {
                        // Image from camera
                        val bitmap = data.extras?.get("data") as Bitmap
                        val uri = saveBitmapToUri(bitmap)
                        uri
                    } else {
                        // Image from gallery
                        data.data
                    }
                    
                    if (imageUri != null) {
                        backIdUri = imageUri
                        displayImage(backIdImage, backIdPlaceholder, imageUri)
                        isBackIdUploaded = true
                        updateNextButton()
                    }
                }
            }
        }
    }
    
    private fun saveBitmapToUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "ID_Image_${System.currentTimeMillis()}", null)
        return Uri.parse(path)
    }
    
    private fun displayImage(imageView: ImageView, placeholder: ImageView, uri: Uri) {
        imageView.visibility = ImageView.VISIBLE
        placeholder.visibility = ImageView.GONE
        imageView.setImageURI(uri)
    }
    
    private fun updateNextButton() {
        nextButton.isEnabled = isFrontIdUploaded && isBackIdUploaded && selectedIdType.isNotEmpty()
        nextButton.alpha = if (nextButton.isEnabled) 1.0f else 0.5f
    }
    
    private fun checkVerificationStatus() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    val verificationStatus = document.getString("verificationStatus")
                    when (verificationStatus) {
                        "pending" -> {
                            showStatusMessage("Verification is pending admin review")
                            disableUploads()
                        }
                        "approved" -> {
                            showStatusMessage("Your account is already verified")
                            disableUploads()
                        }
                        "rejected" -> {
                            // Allow re-verification
                        }
                        else -> {
                            // No verification status, allow verification
                        }
                    }
                }
        }
    }
    
    private fun showStatusMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun disableUploads() {
        frontIdContainer.isClickable = false
        backIdContainer.isClickable = false
        nextButton.isEnabled = false
        nextButton.alpha = 0.5f
    }
}

