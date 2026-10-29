package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class userdetails : AppCompatActivity() {

    private lateinit var name: EditText
    private lateinit var age: EditText
    private lateinit var gender: EditText
    private lateinit var address: EditText
    private lateinit var submitbtn: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_userdetails)

        name = findViewById(R.id.name)
        age = findViewById(R.id.age)
        gender = findViewById(R.id.gender)
        address = findViewById(R.id.address)
        submitbtn = findViewById(R.id.submitbtn)

        val currentUser = auth.currentUser
        val uid = currentUser?.uid

        if (uid == null) {
            startActivity(Intent(this, MainActivity2::class.java))
            finish()
            return
        }

        if (!currentUser.displayName.isNullOrEmpty()) {
            name.setText(currentUser.displayName)
        }

        submitbtn.setOnClickListener {
            val userName = name.text.toString().trim()
            val userAge = age.text.toString().trim()
            val userGender = gender.text.toString().trim()
            val userAddress = address.text.toString().trim()

            if (userName.isEmpty()) {
                name.error = "Please enter your name"
                name.requestFocus()
                return@setOnClickListener
            }

            if (userAge.isEmpty()) {
                age.error = "Please enter your age"
                age.requestFocus()
                return@setOnClickListener
            }

            val ageValue = userAge.toIntOrNull()
            if (ageValue == null || ageValue !in 1..120) {
                age.error = "Enter a valid age"
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

            val user = hashMapOf(
                "name" to userName,
                "age" to ageValue,
                "gender" to userGender,
                "address" to userAddress,
                "uid" to uid
            )

            db.collection("users")
                .document(uid)
                .set(user, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "User details saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(this, home::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    submitbtn.isEnabled = true
                    Toast.makeText(
                        this,
                        "Failed to save details: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}