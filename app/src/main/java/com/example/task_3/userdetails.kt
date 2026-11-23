package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class userdetails : AppCompatActivity() {

    private lateinit var name: EditText
    private lateinit var username: EditText
    private lateinit var age: EditText
    private lateinit var gender: EditText
    private lateinit var address: EditText
    private lateinit var submitbtn: Button
    private lateinit var btnSkipDetails: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_userdetails)

        name = findViewById(R.id.name)
        username = findViewById(R.id.username)
        age = findViewById(R.id.age)
        gender = findViewById(R.id.gender)
        address = findViewById(R.id.address)
        submitbtn = findViewById(R.id.submitbtn)
        btnSkipDetails = findViewById(R.id.btnSkipDetails)

        val currentUser = auth.currentUser
        val uid = currentUser?.uid

        if (uid == null) {
            startActivity(Intent(this, MainActivity2::class.java))
            finish()
            return
        }

        // Fetch existing user data to pre-populate fields
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val existingName = doc.getString("name") ?: currentUser.displayName ?: ""
                    if (existingName.isNotEmpty() && name.text.isEmpty()) {
                        name.setText(existingName)
                    }
                    val existingUsername = doc.getString("username") ?: ""
                    if (existingUsername.isNotEmpty() && username.text.isEmpty()) {
                        username.setText(existingUsername)
                    }
                    doc.get("age")?.let { age.setText(it.toString()) }
                    doc.getString("gender")?.let { gender.setText(it) }
                    doc.getString("address")?.let { address.setText(it) }
                } else if (!currentUser.displayName.isNullOrEmpty()) {
                    name.setText(currentUser.displayName)
                }
            }
            .addOnFailureListener {
                if (!currentUser.displayName.isNullOrEmpty()) {
                    name.setText(currentUser.displayName)
                }
            }

        btnSkipDetails.setOnClickListener {
            val rawUsername = username.text.toString().trim()
            val cleanUsername = rawUsername.lowercase().removePrefix("@").replace(" ", "_")
            if (cleanUsername.isEmpty()) {
                username.error = "Please set a username for your account"
                username.requestFocus()
                Toast.makeText(this, "A unique username is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, home::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        submitbtn.setOnClickListener {
            val userName = name.text.toString().trim()
            val rawUsername = username.text.toString().trim()
            val cleanUsername = rawUsername.lowercase().removePrefix("@").replace(" ", "_")
            val userAge = age.text.toString().trim()
            val userGender = gender.text.toString().trim()
            val userAddress = address.text.toString().trim()

            if (userName.isEmpty()) {
                name.error = "Please enter your name"
                name.requestFocus()
                return@setOnClickListener
            }

            if (cleanUsername.isEmpty()) {
                username.error = "Please choose a username"
                username.requestFocus()
                return@setOnClickListener
            }

            if (cleanUsername.length < 3) {
                username.error = "Username must be at least 3 characters"
                username.requestFocus()
                return@setOnClickListener
            }

            if (!cleanUsername.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
                username.error = "Usernames can only contain letters, numbers, _, and ."
                username.requestFocus()
                return@setOnClickListener
            }

            if (userAge.isEmpty()) {
                age.error = "Please enter your age"
                age.requestFocus()
                return@setOnClickListener
            }

            val ageValue = userAge.toIntOrNull()
            if (ageValue == null || ageValue !in 1..120) {
                age.error = "Enter a valid age (1-120)"
                age.requestFocus()
                return@setOnClickListener
            }

            if (userGender.isEmpty()) {
                gender.error = "Please enter your gender"
                gender.requestFocus()
                return@setOnClickListener
            }

            if (userAddress.isEmpty()) {
                address.error = "Please enter your address"
                address.requestFocus()
                return@setOnClickListener
            }

            submitbtn.isEnabled = false
            submitbtn.text = "Checking username..."

            // Verify if username exists in Firestore for any other user
            db.collection("users")
                .whereEqualTo("username", cleanUsername)
                .get()
                .addOnSuccessListener { snapshot ->
                    val isTakenByOther = snapshot.documents.any { it.id != uid }
                    if (isTakenByOther) {
                        submitbtn.isEnabled = true
                        submitbtn.text = "Save & Continue  →"
                        username.error = "Username '$cleanUsername' is already taken! Choose a unique one."
                        username.requestFocus()
                        Toast.makeText(this, "Username '$cleanUsername' is already taken! Please choose another.", Toast.LENGTH_SHORT).show()
                    } else {
                        // Username is unique! Save details
                        val user = hashMapOf<String, Any>(
                            "name" to userName,
                            "username" to cleanUsername,
                            "age" to ageValue,
                            "gender" to userGender,
                            "address" to userAddress,
                            "uid" to uid
                        )

                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(userName)
                            .build()
                        currentUser.updateProfile(profileUpdates)

                        db.collection("users")
                            .document(uid)
                            .set(user, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Profile setup complete! Welcome to Inkora ✨",
                                    Toast.LENGTH_SHORT
                                ).show()

                                val intent = Intent(this, home::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                submitbtn.isEnabled = true
                                submitbtn.text = "Save & Continue  →"
                                Toast.makeText(
                                    this,
                                    "Failed to save details: ${e.localizedMessage}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    submitbtn.isEnabled = true
                    submitbtn.text = "Save & Continue  →"
                    Toast.makeText(this, "Error checking username: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}