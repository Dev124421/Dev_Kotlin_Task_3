package com.example.task_3

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class add_data : AppCompatActivity() {

    private lateinit var title: EditText
    private lateinit var author: EditText
    private lateinit var content: EditText
    private lateinit var publish: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_data)

        title = findViewById(R.id.title)
        author = findViewById(R.id.author)
        content = findViewById(R.id.content)
        publish = findViewById(R.id.piblishbtn)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User is not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uid = currentUser.uid

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userName = document.getString("name") ?: ""
                    author.setText(userName)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load author profile", Toast.LENGTH_SHORT).show()
            }

        publish.setOnClickListener {
            val blogTitle = title.text.toString().trim()
            val blogAuthor = author.text.toString().trim()
            val blogContent = content.text.toString().trim()

            if (blogTitle.isEmpty()) {
                title.error = "Please enter the title"
                title.requestFocus()
                return@setOnClickListener
            }
            if (blogAuthor.isEmpty()) {
                author.error = "Author name is missing"
                author.requestFocus()
                return@setOnClickListener
            }
            if (blogContent.isEmpty()) {
                content.error = "Please enter the content"
                content.requestFocus()
                return@setOnClickListener
            }

            publish.isEnabled = false

            val blog = hashMapOf(
                "title" to blogTitle,
                "author" to blogAuthor,
                "content" to blogContent,
                "uid" to uid,
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            db.collection("blogs")
                .add(blog)
                .addOnSuccessListener {
                    Toast.makeText(this, "Blog published successfully!", Toast.LENGTH_SHORT).show()
                    title.text.clear()
                    content.text.clear()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        this,
                        "Failed to publish: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnCompleteListener {
                    publish.isEnabled = true
                }
        }
    }
}