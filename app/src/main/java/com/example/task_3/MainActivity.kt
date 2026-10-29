package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.task_3.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private var isPasswordVisible: Boolean = false
    private var isConfirmPasswordVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        binding.showPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.signuppassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.signuppassword.setSelection(binding.signuppassword.text.length)
        }

        binding.showConfirmPasswordToggle.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            binding.confirmpassword.inputType = if (isConfirmPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.confirmpassword.setSelection(binding.confirmpassword.text.length)
        }

        binding.SignUpBtn.setOnClickListener {
            val fullName = binding.signupfullname.text.toString().trim()
            val email = binding.signupemail.text.toString().trim()
            val password = binding.signuppassword.text.toString().trim()
            val confirmPassword = binding.confirmpassword.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName)
                            .build()

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            Toast.makeText(this, "Sign Up Successful", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, userdetails::class.java))
                            finish()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage ?: "Sign up failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        binding.LoginText.setOnClickListener {
            startActivity(Intent(this, MainActivity2::class.java))
            finish()
        }
    }
}