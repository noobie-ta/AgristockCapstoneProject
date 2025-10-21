package com.example.agristockcapstoneproject

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class EditPostActivity : AppCompatActivity() {

	private lateinit var ivPostImage: ImageView
	private lateinit var tvChangeImage: TextView
	private lateinit var etTitle: TextInputEditText
	private lateinit var etPrice: TextInputEditText
	private lateinit var etDescription: TextInputEditText

	private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
	private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
	private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

	private var pendingImageUri: Uri? = null

	private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		uri?.let {
			pendingImageUri = it
			Glide.with(this).load(it).into(ivPostImage)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_edit_post)

		ivPostImage = findViewById(R.id.iv_post_image)
		tvChangeImage = findViewById(R.id.tv_change_image)
		etTitle = findViewById(R.id.et_title)
		etPrice = findViewById(R.id.et_price)
		etDescription = findViewById(R.id.et_description)

		// Prefill
		val postId = intent.getStringExtra("postId")
		etTitle.setText(intent.getStringExtra("title") ?: "")
		etPrice.setText(intent.getStringExtra("price") ?: "")
		etDescription.setText(intent.getStringExtra("description") ?: "")
		val imageUrl = intent.getStringExtra("imageUrl")
		if (!imageUrl.isNullOrEmpty()) {
			Glide.with(this).load(imageUrl).into(ivPostImage)
		}

		findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
		tvChangeImage.setOnClickListener { pickImage.launch("image/*") }
		findViewById<TextView>(R.id.btn_save).setOnClickListener {
			if (postId.isNullOrEmpty()) { finish(); return@setOnClickListener }
			savePost(postId)
		}
		findViewById<TextView>(R.id.btn_cancel).setOnClickListener { finish() }
	}

	private fun savePost(postId: String) {
		val user = auth.currentUser ?: return
		val updates = mutableMapOf<String, Any>(
			"title" to (etTitle.text?.toString() ?: ""),
			"price" to (etPrice.text?.toString() ?: ""),
			"description" to (etDescription.text?.toString() ?: ""),
			"userId" to user.uid,
		)

		val imageUri = pendingImageUri
		if (imageUri != null) {
			val ref = storage.reference.child("post_images/${user.uid}/${postId}.jpg")
			ref.putFile(imageUri)
				.continueWithTask { task ->
					if (!task.isSuccessful) throw task.exception ?: RuntimeException("Upload failed")
					ref.downloadUrl
				}
				.addOnSuccessListener { downloadUri ->
					updates["imageUrl"] = downloadUri.toString()
					firestore.collection("posts").document(postId).set(updates, com.google.firebase.firestore.SetOptions.merge())
						.addOnSuccessListener { finish() }
				}
		} else {
			firestore.collection("posts").document(postId).set(updates, com.google.firebase.firestore.SetOptions.merge())
				.addOnSuccessListener { finish() }
		}
	}
}


