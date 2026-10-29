package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class profile : AppCompatActivity() {

    private lateinit var nameText: TextView
    private lateinit var userHandleText: TextView
    private lateinit var ageText: TextView
    private lateinit var genderText: TextView
    private lateinit var addressText: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var bottomNav: BottomNavigationView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        nameText = findViewById(R.id.name)
        userHandleText = findViewById(R.id.tvUserHandle)
        ageText = findViewById(R.id.age)
        genderText = findViewById(R.id.gender)
        addressText = findViewById(R.id.address)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        bottomNav = findViewById(R.id.bottom_nav)

        bottomNav.selectedItemId = R.id.nav_account
        setupBottomNavigation()
        setupMenuClickListeners()
        loadUserProfile()
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
                R.id.nav_add -> {
                    startActivity(Intent(this, add_data::class.java))
                    finish()
                    true
                }
                R.id.nav_account -> true
                else -> false
            }
        }
    }

    private fun setupMenuClickListeners() {
        btnEditProfile.setOnClickListener {
            startActivity(Intent(this, userdetails::class.java))
        }

        findViewById<LinearLayout>(R.id.optMyPosts).setOnClickListener {
            Toast.makeText(this, "My Posts clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optSavedPosts).setOnClickListener {
            Toast.makeText(this, "Saved Posts clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optDrafts).setOnClickListener {
            Toast.makeText(this, "Drafts clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optSettings).setOnClickListener {
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optHelp).setOnClickListener {
            Toast.makeText(this, "Help & Support clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User is not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uid = currentUser.uid

        val emailPrefix = currentUser.email?.substringBefore("@") ?: "user"
        userHandleText.text = "@$emailPrefix"

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    nameText.text = document.getString("name") ?: currentUser.displayName ?: "Anonymous"
                    genderText.text = document.getString("gender") ?: "N/A"
                    addressText.text = document.getString("address") ?: "N/A"
                    val ageVal = document.get("age")
                    ageText.text = ageVal?.toString() ?: "N/A"

                    document.getString("username")?.let {
                        userHandleText.text = if (it.startsWith("@")) it else "@$it"
                    }
                } else {
                    nameText.text = currentUser.displayName ?: "Anonymous"
                    ageText.text = "N/A"
                    genderText.text = "N/A"
                    addressText.text = "N/A"
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load profile: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}