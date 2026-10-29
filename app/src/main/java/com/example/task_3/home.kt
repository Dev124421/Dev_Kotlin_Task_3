package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class home : AppCompatActivity() {

    private val blogList = ArrayList<Blog>()
    private lateinit var adapter: BlogAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        val refresh = findViewById<Button>(R.id.refresh)
        val listView = findViewById<ListView>(R.id.list)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        bottomNav.selectedItemId = R.id.nav_home

        adapter = BlogAdapter(
            context = this,
            blogs = blogList,
            onEditClick = { blog -> handleEdit(blog) },
            onDeleteClick = { blog -> confirmDelete(blog) }
        )
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in blogList.indices) {
                openBlogDetail(blogList[position])
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    fetchBlogs()
                    true
                }
                R.id.nav_add -> {
                    startActivity(Intent(this, add_data::class.java))
                    true
                }
                R.id.nav_account -> {
                    startActivity(Intent(this, profile::class.java))
                    true
                }
                else -> false
            }
        }

        fetchBlogs()

        refresh.setOnClickListener {
            fetchBlogs()
        }
    }

    private fun fetchBlogs() {
        db.collection("blogs")
            .get()
            .addOnSuccessListener { result ->
                blogList.clear()
                for (document in result) {
                    val blog = Blog(
                        id = document.id,
                        title = document.getString("title") ?: "No title",
                        content = document.getString("content") ?: "No content",
                        author = document.getString("author") ?: "Anonymous"
                    )
                    blogList.add(blog)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openBlogDetail(blog: Blog) {
        val intent = Intent(this, add_data::class.java).apply {
            putExtra("DOC_ID", blog.id)
            putExtra("TITLE", blog.title)
            putExtra("CONTENT", blog.content)
            putExtra("AUTHOR", blog.author)
        }
        startActivity(intent)
    }

    private fun handleEdit(blog: Blog) {
        val intent = Intent(this, add_data::class.java).apply {
            putExtra("DOC_ID", blog.id)
            putExtra("TITLE", blog.title)
            putExtra("CONTENT", blog.content)
            putExtra("AUTHOR", blog.author)
        }
        startActivity(intent)
    }

    private fun confirmDelete(blog: Blog) {
        AlertDialog.Builder(this)
            .setTitle("Delete Blog")
            .setMessage("Are you sure you want to delete \"${blog.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteBlog(blog)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteBlog(blog: Blog) {
        db.collection("blogs").document(blog.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Blog deleted", Toast.LENGTH_SHORT).show()
                blogList.remove(blog)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}