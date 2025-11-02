package com.example.agristockcapstoneproject

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class BiddingApplicationActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private lateinit var btnBack: ImageView
    private lateinit var btnUploadBarangay: Button
    private lateinit var btnUploadPayslip: Button
    private lateinit var btnUploadBusinessPermit: Button
    private lateinit var btnSubmitApplication: Button
    private lateinit var btnSaveLater: Button
    private lateinit var checkboxAgreement: android.widget.CheckBox
    
    private lateinit var ivBarangayPreview: ImageView
    private lateinit var ivPayslipPreview: ImageView
    private lateinit var ivBusinessPermitPreview: ImageView
    
    private lateinit var tvBarangayFile: TextView
    private lateinit var tvPayslipFile: TextView
    private lateinit var tvBusinessPermitFile: TextView
    
    private var barangayUri: Uri? = null
    private var payslipUri: Uri? = null
    private var businessPermitUri: Uri? = null
    
    companion object {
        private const val REQUEST_CODE_BARANGAY = 2001
        private const val REQUEST_CODE_PAYSLIP = 2002
        private const val REQUEST_CODE_BUSINESS_PERMIT = 2003
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bidding_application)
        
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        initializeViews()
        setupClickListeners()
        checkBiddingStatus()
    }
    
    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back)
        btnUploadBarangay = findViewById(R.id.btn_upload_barangay)
        btnUploadPayslip = findViewById(R.id.btn_upload_payslip)
        btnUploadBusinessPermit = findViewById(R.id.btn_upload_business_permit)
        btnSubmitApplication = findViewById(R.id.btn_submit_application)
        btnSaveLater = findViewById(R.id.btn_save_later)
        checkboxAgreement = findViewById(R.id.checkbox_agreement)
        
        ivBarangayPreview = findViewById(R.id.iv_barangay_preview)
        ivPayslipPreview = findViewById(R.id.iv_payslip_preview)
        ivBusinessPermitPreview = findViewById(R.id.iv_business_permit_preview)
        
        tvBarangayFile = findViewById(R.id.tv_barangay_file)
        tvPayslipFile = findViewById(R.id.tv_payslip_file)
        tvBusinessPermitFile = findViewById(R.id.tv_business_permit_file)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        
        btnUploadBarangay.setOnClickListener {
            showImageSourceDialog(REQUEST_CODE_BARANGAY)
        }
        
        btnUploadPayslip.setOnClickListener {
            showImageSourceDialog(REQUEST_CODE_PAYSLIP)
        }
        
        btnUploadBusinessPermit.setOnClickListener {
            showImageSourceDialog(REQUEST_CODE_BUSINESS_PERMIT)
        }
        
        btnSubmitApplication.setOnClickListener {
            submitApplication()
        }
        
        btnSaveLater.setOnClickListener {
            finish()
        }
    }
    
    private fun showImageSourceDialog(requestCode: Int) {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = AlertDialog.Builder(this)
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
            val imageUri = when {
                data.extras?.get("data") != null -> {
                    // Image from camera
                    val bitmap = data.extras?.get("data") as Bitmap
                    saveBitmapToUri(bitmap)
                }
                else -> {
                    // Image from gallery
                    data.data
                }
            }
            
            when (requestCode) {
                REQUEST_CODE_BARANGAY -> {
                    if (imageUri != null) {
                        barangayUri = imageUri
                        displayImage(ivBarangayPreview, tvBarangayFile, imageUri)
                    }
                }
                REQUEST_CODE_PAYSLIP -> {
                    if (imageUri != null) {
                        payslipUri = imageUri
                        displayImage(ivPayslipPreview, tvPayslipFile, imageUri)
                    }
                }
                REQUEST_CODE_BUSINESS_PERMIT -> {
                    if (imageUri != null) {
                        businessPermitUri = imageUri
                        displayImage(ivBusinessPermitPreview, tvBusinessPermitFile, imageUri)
                    }
                }
            }
        }
    }
    
    private fun saveBitmapToUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Bidding_${System.currentTimeMillis()}", null)
        return Uri.parse(path)
    }
    
    private fun displayImage(imageView: ImageView, textView: TextView, uri: Uri) {
        imageView.visibility = ImageView.VISIBLE
        textView.visibility = TextView.VISIBLE
        textView.text = "File selected"
        Glide.with(this).load(uri).into(imageView)
    }
    
    private fun submitApplication() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check cooldown before allowing submission
        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val cooldownEnd = document.getLong("biddingCooldownEnd") ?: 0L
                val now = System.currentTimeMillis()
                
                if (now < cooldownEnd) {
                    val remaining = cooldownEnd - now
                    val days = remaining / (24 * 60 * 60 * 1000)
                    Toast.makeText(this, "Please wait ${days + 1} more days before resubmitting", Toast.LENGTH_LONG).show()
                    
                    val intent = Intent(this, BiddingRejectedActivity::class.java)
                    startActivity(intent)
                    return@addOnSuccessListener
                }
                
                // Cooldown expired, proceed with validation
                if (!checkboxAgreement.isChecked) {
                    Toast.makeText(this, "Please confirm the agreement", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                if (barangayUri == null) {
                    Toast.makeText(this, "Please upload Barangay Clearance", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                if (payslipUri == null) {
                    Toast.makeText(this, "Please upload Latest Payslip / Proof of Income", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                btnSubmitApplication.isEnabled = false
                btnSubmitApplication.text = "Submitting..."
                
                uploadFilesAndSubmit(currentUser.uid)
            }
    }
    
    private fun uploadFilesAndSubmit(userId: String) {
        val timestamp = System.currentTimeMillis()
        val uploadTasks = mutableListOf<Triple<String, Uri, String>>()
        
        barangayUri?.let {
            uploadTasks.add(Triple("barangay_clearance", it, "bidding_applications/barangay_${userId}_$timestamp.jpg"))
        }
        payslipUri?.let {
            uploadTasks.add(Triple("payslip", it, "bidding_applications/payslip_${userId}_$timestamp.jpg"))
        }
        businessPermitUri?.let {
            uploadTasks.add(Triple("business_permit", it, "bidding_applications/business_permit_${userId}_$timestamp.jpg"))
        }
        
        if (uploadTasks.isEmpty()) {
            Toast.makeText(this, "Please upload at least one document", Toast.LENGTH_SHORT).show()
            btnSubmitApplication.isEnabled = true
            btnSubmitApplication.text = "Submit Application"
            return
        }
        
        uploadFilesSequentially(uploadTasks, userId)
    }
    
    private fun uploadFilesSequentially(uploadTasks: List<Triple<String, Uri, String>>, userId: String) {
        val uploadedUrls = mutableMapOf<String, String>()
        var currentIndex = 0
        
        fun uploadNext() {
            if (currentIndex >= uploadTasks.size) {
                saveApplicationToFirestore(userId, uploadedUrls)
                return
            }
            
            val (type, uri, path) = uploadTasks[currentIndex]
            val storageRef = storage.reference.child(path)
            
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        uploadedUrls[type] = downloadUrl.toString()
                        currentIndex++
                        uploadNext()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to upload ${type}: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSubmitApplication.isEnabled = true
                    btnSubmitApplication.text = "Submit Application"
                }
        }
        
        uploadNext()
    }
    
    private fun saveApplicationToFirestore(userId: String, uploadedUrls: Map<String, String>) {
        val applicationData = hashMapOf(
            "biddingApprovalStatus" to "pending",
            "applicationDate" to System.currentTimeMillis(),
            "barangayClearanceUrl" to (uploadedUrls["barangay_clearance"] ?: ""),
            "payslipUrl" to (uploadedUrls["payslip"] ?: ""),
            "businessPermitUrl" to (uploadedUrls["business_permit"] ?: "")
        )
        
        firestore.collection("users").document(userId)
            .update(applicationData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "Application submitted successfully! Waiting for admin approval.", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit application: ${e.message}", Toast.LENGTH_LONG).show()
                btnSubmitApplication.isEnabled = true
                btnSubmitApplication.text = "Submit Application"
            }
    }
    
    private fun checkBiddingStatus() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    val biddingStatus = document.getString("biddingApprovalStatus")
                    when (biddingStatus) {
                        "pending" -> {
                            Toast.makeText(this, "Your application is pending review", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        "approved" -> {
                            Toast.makeText(this, "You are already approved for bidding", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        "rejected", "banned" -> {
                            // Check if still in cooldown
                            val cooldownEnd = document.getLong("biddingCooldownEnd") ?: 0L
                            val now = System.currentTimeMillis()
                            
                            if (now < cooldownEnd) {
                                // Still in cooldown - navigate to rejected page
                                val intent = Intent(this, BiddingRejectedActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            // Otherwise, allow resubmission (stay on this page)
                        }
                    }
                }
        }
    }
}

