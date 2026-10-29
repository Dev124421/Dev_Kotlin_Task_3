package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class add_data : AppCompatActivity() {

    private lateinit var tvHeader: TextView
    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText
    private lateinit var btnPublish: Button
    private lateinit var bottomNav: BottomNavigationView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var editingDocId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_data)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        tvHeader = findViewById(R.id.tvHeaderTitle)
        etTitle = findViewById(R.id.etTitle)
        etContent = findViewById(R.id.etContent)
        btnPublish = findViewById(R.id.btnPublish)
        bottomNav = findViewById(R.id.bottom_nav)

        bottomNav.selectedItemId = R.id.nav_add

        setupBottomNavigation()

        editingDocId = intent.getStringExtra("DOC_ID")
        if (editingDocId != null) {
            setupEditMode()
        } else {
            setupCreateMode()
        }
        btnPublish.setOnClickListener {
            saveBlog()
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, home::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
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

    private fun setupEditMode() {
        tvHeader.text = "Edit Blog"
        btnPublish.text = "Update"
        etTitle.setText(intent.getStringExtra("TITLE") ?: "")
        etContent.setText(intent.getStringExtra("CONTENT") ?: "")
    }

    private fun setupCreateMode() {
        tvHeader.text = "Create Blog"
        btnPublish.text = "Publish"

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun saveBlog() {
        val blogTitle = etTitle.text.toString().trim()
        val blogContent = etContent.text.toString().trim()

        if (blogTitle.isEmpty()) {
            etTitle.error = "Please enter the title"
            etTitle.requestFocus()
            return
        }
        if (blogContent.isEmpty()) {
            etContent.error = "Please enter the content"
            etContent.requestFocus()
            return
        }

        btnPublish.isEnabled = false

        val currentUid = auth.currentUser?.uid.orEmpty()
        val passedAuthor = intent.getStringExtra("AUTHOR")

        if (editingDocId != null) {
            val blogAuthor = if (!passedAuthor.isNullOrEmpty()) {
                passedAuthor
            } else {
                auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@") ?: "Anonymous"
            }

            val updates = mapOf(
                "title" to blogTitle,
                "author" to blogAuthor,
                "content" to blogContent,
                "updatedAt" to Timestamp.now()
            )

            db.collection("blogs")
                .document(editingDocId!!)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Blog updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    btnPublish.isEnabled = true
                }
        } else {
            db.collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener { document ->
                    val blogAuthor = if (document.exists() && !document.getString("name").isNullOrEmpty()) {
                        document.getString("name")!!
                    } else {
                        auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@") ?: "Anonymous"
                    }

                    val blogData = hashMapOf(
                        "title" to blogTitle,
                        "author" to blogAuthor,
                        "content" to blogContent,
                        "uid" to currentUid,
                        "timestamp" to Timestamp.now()
                    )

                    db.collection("blogs")
                        .add(blogData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Blog published successfully!", Toast.LENGTH_SHORT).show()
                            etTitle.text?.clear()
                            etContent.text?.clear()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to publish: ${e.message}", Toast.LENGTH_LONG).show()
                            btnPublish.isEnabled = true
                        }
                }
                .addOnFailureListener {
                    val blogAuthor = auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@") ?: "Anonymous"
                    val blogData = hashMapOf(
                        "title" to blogTitle,
                        "author" to blogAuthor,
                        "content" to blogContent,
                        "uid" to currentUid,
                        "timestamp" to Timestamp.now()
                    )

                    db.collection("blogs")
                        .add(blogData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Blog published successfully!", Toast.LENGTH_SHORT).show()
                            etTitle.text?.clear()
                            etContent.text?.clear()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to publish: ${e.message}", Toast.LENGTH_LONG).show()
                            btnPublish.isEnabled = true
                        }
                }
        }
    }
}
