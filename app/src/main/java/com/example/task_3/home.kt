package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class home : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        val intent2 = findViewById<LinearLayout>(R.id.add)
        val intent3 = findViewById<LinearLayout>(R.id.account)
        val refresh = findViewById<Button>(R.id.refresh)

        intent2.setOnClickListener {
            startActivity(Intent(this, add_data::class.java))
        }
        intent3.setOnClickListener {
            startActivity(Intent(this, profile::class.java))
        }

        val listView: ListView
        lateinit var adapter: ArrayAdapter<String>
        val blogList = ArrayList<String>()

        listView = findViewById(R.id.list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blogList)
        listView.adapter = adapter

        FirebaseFirestore.getInstance()
            .collection("blogs")
            .get()
            .addOnSuccessListener { result ->
                blogList.clear()
                for (document in result) {
                    val title = document.getString("title") ?: "No title available"
                    val content = document.getString("content") ?: "No content available"
                    val author = document.getString("author") ?: "No author available"
                    val blog = "$title\n$content\n$author"
                    blogList.add(blog)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed" , Toast.LENGTH_SHORT).show()
            }

        refresh.setOnClickListener {
            val listView: ListView
            lateinit var adapter: ArrayAdapter<String>
            val blogList = ArrayList<String>()

            listView = findViewById(R.id.list)
            adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blogList)
            listView.adapter = adapter

            FirebaseFirestore.getInstance()
                .collection("blogs")
                .get()
                .addOnSuccessListener { result ->
                    blogList.clear()
                    for (document in result) {
                        val title = document.getString("title") ?: "No title available"
                        val content = document.getString("content") ?: "No content available"
                        val author = document.getString("author") ?: "No author available"
                        val blog = "$title\n$content\n$author"
                        blogList.add(blog)
                    }
                    adapter.notifyDataSetChanged()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed" , Toast.LENGTH_SHORT).show()
                }
        }
    }
}