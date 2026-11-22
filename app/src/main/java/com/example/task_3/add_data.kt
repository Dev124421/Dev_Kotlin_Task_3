package com.example.task_3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class add_data : AppCompatActivity() {

    private lateinit var tvHeader: TextView
    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText
    private lateinit var btnPublish: Button
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var coverImageBox: View
    private lateinit var ivCoverPreview: ImageView
    private lateinit var layoutPickPlaceholder: View
    private lateinit var tvUploadStatus: TextView
    private lateinit var pbUpload: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedCategory = "Tech"
    private var editingDocId: String? = null
    private var imageUriString: String = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUriString = uri.toString()
            ivCoverPreview.visibility = View.VISIBLE
            layoutPickPlaceholder.visibility = View.GONE
            ivCoverPreview.load(uri) {
                crossfade(true)
            }
            tvUploadStatus.text = "Image selected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_data)

        tvHeader = findViewById(R.id.tvHeaderTitle)
        etTitle = findViewById(R.id.etTitle)
        etContent = findViewById(R.id.etContent)
        btnPublish = findViewById(R.id.btnPublish)
        bottomNav = findViewById(R.id.bottom_nav)

        coverImageBox = findViewById(R.id.coverImageBox)
        ivCoverPreview = findViewById(R.id.ivCoverPreview)
        layoutPickPlaceholder = findViewById(R.id.layoutPickPlaceholder)
        tvUploadStatus = findViewById(R.id.tvUploadStatus)
        pbUpload = findViewById(R.id.pbUpload)

        bottomNav.selectedItemId = R.id.nav_add

        setupCategoryChips()
        setupBottomNavigation()

        // Pick cover image
        coverImageBox.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Check if editing an existing post
        editingDocId = intent.getStringExtra("DOC_ID")
        if (editingDocId != null) {
            tvHeader.text = "Edit Post"
            btnPublish.text = "Save Changes  →"
            etTitle.setText(intent.getStringExtra("TITLE") ?: "")
            etContent.setText(intent.getStringExtra("CONTENT") ?: "")
            val cat = intent.getStringExtra("CATEGORY") ?: "Tech"
            selectCategory(cat)

            val existingImg = intent.getStringExtra("IMAGE_URL") ?: ""
            if (existingImg.isNotEmpty()) {
                imageUriString = existingImg
                ivCoverPreview.visibility = View.VISIBLE
                layoutPickPlaceholder.visibility = View.GONE
                ivCoverPreview.load(existingImg)
            }
        }

        btnPublish.setOnClickListener {
            savePost()
        }
    }

    private fun setupCategoryChips() {
        findViewById<TextView>(R.id.chipTech).setOnClickListener { selectCategory("Tech") }
        findViewById<TextView>(R.id.chipDesign).setOnClickListener { selectCategory("Design") }
        findViewById<TextView>(R.id.chipLife).setOnClickListener { selectCategory("Life") }
        findViewById<TextView>(R.id.chipArt).setOnClickListener { selectCategory("Art") }
    }

    private fun selectCategory(category: String) {
        selectedCategory = category
        val chips = listOf(
            findViewById<TextView>(R.id.chipTech) to "Tech",
            findViewById<TextView>(R.id.chipDesign) to "Design",
            findViewById<TextView>(R.id.chipLife) to "Life",
            findViewById<TextView>(R.id.chipArt) to "Art"
        )
        for ((chipView, cat) in chips) {
            if (cat == selectedCategory) {
                chipView.setBackgroundResource(R.drawable.bg_pill_filter_selected)
                chipView.setTextColor(getColor(R.color.white))
            } else {
                chipView.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                chipView.setTextColor(getColor(R.color.secondary_text))
            }
        }
    }

    private fun savePost() {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()

        if (title.isEmpty()) {
            etTitle.error = "Please enter a title"
            etTitle.requestFocus()
            return
        }

        if (content.isEmpty()) {
            etContent.error = "Please enter some content"
            etContent.requestFocus()
            return
        }

        val user = auth.currentUser
        val authorName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Creator"
        val authorId = user?.uid ?: ""

        btnPublish.isEnabled = false
        btnPublish.text = "Publishing..."

        val postMap = hashMapOf(
            "title" to title,
            "content" to content,
            "category" to selectedCategory,
            "imageUrl" to imageUriString,
            "author" to authorName,
            "authorId" to authorId,
            "timestamp" to System.currentTimeMillis()
        )

        if (editingDocId != null) {
            // Update existing post
            db.collection("blogs").document(editingDocId!!)
                .update(postMap as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Post updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    btnPublish.isEnabled = true
                    btnPublish.text = "Save Changes  →"
                    Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        } else {
            // Create new post
            postMap["likesCount"] = 0L
            postMap["commentsCount"] = 0L

            db.collection("blogs").add(postMap)
                .addOnSuccessListener {
                    if (authorId.isNotEmpty()) {
                        db.collection("users").document(authorId)
                            .update("postsCount", FieldValue.increment(1))
                    }
                    Toast.makeText(this, "Story published successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    btnPublish.isEnabled = true
                    btnPublish.text = "Publish Story  →"
                    Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, home::class.java))
                    finish()
                    true
                }
                R.id.nav_discover -> {
                    startActivity(Intent(this, discover::class.java))
                    finish()
                    true
                }
                R.id.nav_add -> true
                R.id.nav_account -> {
                    startActivity(Intent(this, profile::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
