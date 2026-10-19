package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class userdetails : AppCompatActivity() {

    private lateinit var name: EditText
    private lateinit var age: EditText
    private lateinit var gender: EditText
    private lateinit var address: EditText
    private lateinit var submitbtn: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_userdetails)

        name = findViewById(R.id.name)
        age = findViewById(R.id.age)
        gender = findViewById(R.id.gender)
        address = findViewById(R.id.address)
        submitbtn = findViewById(R.id.submitbtn)

        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

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

            val user = hashMapOf(
                "name" to userName,
                "age" to ageValue,
                "gender" to userGender,
                "address" to userAddress,
                "uid" to uid
            )

            submitbtn.isEnabled = false

            db.collection("users")
                .document(uid!!)
                .set(user)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "User details saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    name.text.clear()
                    age.text.clear()
                    gender.text.clear()
                    address.text.clear()

                    startActivity(Intent(this, home::class.java))
                }

                .addOnCompleteListener {
                    submitbtn.isEnabled = true
                }
        }
    }
}