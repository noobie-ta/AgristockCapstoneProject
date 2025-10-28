package com.example.agristockcapstoneproject

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class SelfieVerificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private lateinit var capturedSelfie: ImageView
    private lateinit var captureButton: ImageView
    private lateinit var retakeButton: AppCompatButton
    private lateinit var submitButton: AppCompatButton
    
    private var capturedImageUri: Uri? = null
    private var frontIdUri: Uri? = null
    private var backIdUri: Uri? = null
    
    companion object {
        private const val REQUEST_CODE_CAMERA = 2001
        private const val PERMISSION_REQUEST_CODE = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selfie_verification)
        
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        // Get ID URIs from intent
        frontIdUri = Uri.parse(intent.getStringExtra("front_id_uri") ?: "")
        backIdUri = Uri.parse(intent.getStringExtra("back_id_uri") ?: "")
        
        initializeViews()
        setupClickListeners()
        
        if (checkCameraPermission()) {
            showCameraInstructions()
        }
    }
    
    private fun initializeViews() {
        capturedSelfie = findViewById(R.id.iv_captured_selfie)
        captureButton = findViewById(R.id.btn_capture)
        retakeButton = findViewById(R.id.btn_retake)
        submitButton = findViewById(R.id.btn_submit)
        
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            showCancelDialog()
        }
    }
    
    private fun setupClickListeners() {
        captureButton.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            }
        }
        
        retakeButton.setOnClickListener {
            retakePhoto()
        }
        
        submitButton.setOnClickListener {
            submitVerification()
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
                showCameraInstructions()
            } else {
                Toast.makeText(this, "Camera permission is required for selfie verification", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun showCameraInstructions() {
        Toast.makeText(this, "Tap the camera button to take your selfie", Toast.LENGTH_LONG).show()
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CODE_CAMERA)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK && data != null) {
            val bitmap = data.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                capturedImageUri = saveBitmapToUri(bitmap)
                showCapturedImage()
            }
        }
    }
    
    private fun saveBitmapToUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "SELFIE_${System.currentTimeMillis()}", null)
        return Uri.parse(path)
    }
    
    private fun showCapturedImage() {
        capturedImageUri?.let { uri ->
            capturedSelfie.setImageURI(uri)
            capturedSelfie.visibility = ImageView.VISIBLE
            
            // Show action buttons
            retakeButton.visibility = android.view.View.VISIBLE
            submitButton.visibility = android.view.View.VISIBLE
            captureButton.visibility = ImageView.GONE
        }
    }
    
    private fun retakePhoto() {
        capturedSelfie.visibility = ImageView.GONE
        
        // Hide action buttons
        retakeButton.visibility = android.view.View.GONE
        submitButton.visibility = android.view.View.GONE
        captureButton.visibility = ImageView.VISIBLE
        
        capturedImageUri = null
    }
    
    private fun submitVerification() {
        if (capturedImageUri == null) {
            Toast.makeText(this, "Please take a selfie first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (frontIdUri == null || backIdUri == null) {
            Toast.makeText(this, "ID images are missing", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading
        submitButton.isEnabled = false
        submitButton.text = "Submitting..."
        
        // Upload all images to Firebase Storage
        uploadVerificationImages()
    }
    
    private fun uploadVerificationImages() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        
        val userId = currentUser.uid
        val timestamp = System.currentTimeMillis()
        
        // Upload front ID
        uploadImage(frontIdUri!!, "verification/front_id_${userId}_$timestamp.jpg") { frontIdUrl ->
            // Upload back ID
            uploadImage(backIdUri!!, "verification/back_id_${userId}_$timestamp.jpg") { backIdUrl ->
                // Upload selfie
                uploadImage(capturedImageUri!!, "verification/selfie_${userId}_$timestamp.jpg") { selfieUrl ->
                    // Save verification request to Firestore
                    saveVerificationRequest(frontIdUrl, backIdUrl, selfieUrl)
                }
            }
        }
    }
    
    private fun uploadImage(uri: Uri, path: String, onSuccess: (String) -> Unit) {
        val storageRef = storage.reference.child(path)
        storageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl
                    .addOnSuccessListener { downloadUrl ->
                        onSuccess(downloadUrl.toString())
                    }
                    .addOnFailureListener { exception ->
                        Log.e("SelfieVerificationActivity", "Failed to get download URL", exception)
                        Toast.makeText(this, "Failed to get image URL: ${exception.message}", Toast.LENGTH_SHORT).show()
                        submitButton.isEnabled = true
                        submitButton.text = "Submit"
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("SelfieVerificationActivity", "Upload failed", exception)
                Toast.makeText(this, "Upload failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                submitButton.isEnabled = true
                submitButton.text = "Submit"
            }
    }
    
    private fun saveVerificationRequest(frontIdUrl: String, backIdUrl: String, selfieUrl: String) {
        val currentUser = auth.currentUser ?: return
        
        val verificationData = hashMapOf(
            "userId" to currentUser.uid,
            "frontIdUrl" to frontIdUrl,
            "backIdUrl" to backIdUrl,
            "selfieUrl" to selfieUrl,
            "status" to "pending",
            "submittedAt" to System.currentTimeMillis(),
            "reviewedAt" to null,
            "reviewedBy" to null,
            "rejectionReason" to null
        )
        
        firestore.collection("verification_requests")
            .add(verificationData)
            .addOnSuccessListener { documentRef ->
                // Update user's verification status
                // Use set() with merge() to create document if it doesn't exist
                val userUpdate = hashMapOf<String, Any>(
                    "verificationStatus" to "pending"
                )
                firestore.collection("users").document(currentUser.uid)
                    .set(userUpdate, SetOptions.merge())
                    .addOnSuccessListener {
                        showSuccessMessage()
                    }
                    .addOnFailureListener { exception ->
                        // Even if update fails, show success since verification request was saved
                        Log.e("SelfieVerificationActivity", "Failed to update verification status", exception)
                        showSuccessMessage()
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to submit verification: ${exception.message}", Toast.LENGTH_SHORT).show()
                submitButton.isEnabled = true
                submitButton.text = "Submit"
            }
    }
    
    private fun showSuccessMessage() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Verification Submitted")
        builder.setMessage("Your verification request has been submitted successfully. You will be notified once it's reviewed by our admin team.")
        builder.setPositiveButton("OK") { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }
    
    private fun showCancelDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Cancel Verification")
        builder.setMessage("Are you sure you want to cancel? All uploaded ID images will be discarded.")
        builder.setPositiveButton("Yes, Cancel") { _, _ ->
            finish()
        }
        builder.setNegativeButton("Continue", null)
        builder.show()
    }
}