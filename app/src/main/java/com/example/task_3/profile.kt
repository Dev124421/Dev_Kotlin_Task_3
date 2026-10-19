package com.example.task_3

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class profile : AppCompatActivity() {

    private lateinit var Name: TextView
    private lateinit var Age: TextView
    private lateinit var Gender: TextView
    private lateinit var Address: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        Name = findViewById(R.id.name)
        Age = findViewById(R.id.age)
        Gender = findViewById(R.id.gender)
        Address = findViewById(R.id.address)

        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Name.text = document.getString("name")
                    Gender.text = document.getString("gender")
                    Address.text = document.getString("address")
                    Age.text = document.getLong("age").toString()
                    Toast.makeText(this, "Fetched successfully " , Toast.LENGTH_SHORT)
                }
            }.addOnFailureListener {
                Toast.makeText(this,"Failed to fetch name...", Toast.LENGTH_SHORT)
            }
    }
}