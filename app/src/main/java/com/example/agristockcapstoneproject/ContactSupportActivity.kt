package com.example.agristockcapstoneproject

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class ContactSupportActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var subjectEditText: EditText
    private lateinit var messageEditText: EditText
    private lateinit var attachmentImageView: ImageView
    private lateinit var submitButton: Button
    private lateinit var progressBar: ProgressBar

    private var attachmentUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_support)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Keep status bar white/normal icons per app standard
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        subjectEditText = findViewById(R.id.et_subject)
        messageEditText = findViewById(R.id.et_message)
        attachmentImageView = findViewById(R.id.iv_attachment)
        submitButton = findViewById(R.id.btn_submit)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            finish() 
        }

        // Attachment button
        findViewById<View>(R.id.btn_add_attachment).setOnClickListener {
            selectAttachment()
        }

        // Remove attachment
        findViewById<View>(R.id.btn_remove_attachment).setOnClickListener {
            removeAttachment()
        }

        // Submit button
        submitButton.setOnClickListener {
            submitSupportTicket()
        }
    }

    private fun selectAttachment() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun removeAttachment() {
        attachmentUri = null
        attachmentImageView.visibility = View.GONE
        findViewById<View>(R.id.btn_remove_attachment).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            attachmentUri = data.data
            if (attachmentUri != null) {
                attachmentImageView.visibility = View.VISIBLE
                findViewById<View>(R.id.btn_remove_attachment).visibility = View.VISIBLE
                
                // Load image preview
                attachmentImageView.setImageURI(attachmentUri)
            }
        }
    }

    private fun submitSupportTicket() {
        val subject = subjectEditText.text.toString().trim()
        val message = messageEditText.text.toString().trim()

        if (subject.isEmpty()) {
            subjectEditText.error = "Subject is required"
            subjectEditText.requestFocus()
            return
        }

        if (message.isEmpty()) {
            messageEditText.error = "Message is required"
            messageEditText.requestFocus()
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to submit a support ticket", Toast.LENGTH_SHORT).show()
            return
        }

        submitButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        // Upload attachment if exists
        if (attachmentUri != null) {
            uploadAttachmentAndSubmitTicket(subject, message, user.uid)
        } else {
            submitTicketToFirestore(subject, message, user.uid, null)
        }
    }

    private fun uploadAttachmentAndSubmitTicket(subject: String, message: String, userId: String) {
        val storageRef = storage.reference.child("support_attachments/${userId}/${System.currentTimeMillis()}.jpg")
        
        storageRef.putFile(attachmentUri!!)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                    submitTicketToFirestore(subject, message, userId, uri.toString())
                }.addOnFailureListener { exception ->
                    android.util.Log.e("ContactSupport", "Error getting download URL: ${exception.message}")
                    submitTicketToFirestore(subject, message, userId, null)
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ContactSupport", "Error uploading attachment: ${exception.message}")
                Toast.makeText(this, "Failed to upload attachment, submitting without it", Toast.LENGTH_SHORT).show()
                submitTicketToFirestore(subject, message, userId, null)
            }
    }

    private fun submitTicketToFirestore(subject: String, message: String, userId: String, attachmentUrl: String?) {
        val ticketData = hashMapOf(
            "userId" to userId,
            "subject" to subject,
            "message" to message,
            "attachmentUrl" to attachmentUrl,
            "status" to "open",
            "priority" to "medium",
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis(),
            "assignedTo" to null,
            "responses" to emptyList<Map<String, Any>>()
        )

        firestore.collection("supportTickets")
            .add(ticketData)
            .addOnSuccessListener { documentRef ->
                android.util.Log.d("ContactSupport", "Support ticket created with ID: ${documentRef.id}")
                showSuccessDialog()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ContactSupport", "Error creating support ticket: ${exception.message}")
                Toast.makeText(this, "Failed to submit support ticket", Toast.LENGTH_SHORT).show()
                submitButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
    }

    private fun showSuccessDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("✅ Message Sent Successfully")
        builder.setMessage("Your support ticket has been submitted. We'll get back to you within 24 hours.")
        builder.setPositiveButton("View My Tickets") { _, _ ->
            startActivity(Intent(this, MyTicketsActivity::class.java))
            finish()
        }
        builder.setNegativeButton("OK") { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }
}
