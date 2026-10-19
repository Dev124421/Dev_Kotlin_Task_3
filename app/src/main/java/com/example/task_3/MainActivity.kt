package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.task_3.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var sign_up: TextView
    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        binding.SignUpBtn.setOnClickListener {
            var email = binding.signupemail.text.toString()
            var password = binding.signuppassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Sign Up Successful", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, userdetails::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Sign Up Unsuccessful\nEmail or Password is wrong" +
                                "", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
    }
}
        binding.LoginText.setOnClickListener {
            startActivity(Intent(this, MainActivity2::class.java))
        }
    }
}