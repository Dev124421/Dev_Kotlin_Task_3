package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.task_3.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Toggle password visibility
        binding.showPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.signuppassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.showPasswordToggle.setImageResource(if (isPasswordVisible) R.drawable.hide_icon else R.drawable.show_icon)
            binding.signuppassword.setSelection(binding.signuppassword.text.length)
        }

        binding.showConfirmPasswordToggle.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            binding.confirmpassword.inputType = if (isConfirmPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.showConfirmPasswordToggle.setImageResource(if (isConfirmPasswordVisible) R.drawable.hide_icon else R.drawable.show_icon)
            binding.confirmpassword.setSelection(binding.confirmpassword.text.length)
        }

        // Sign Up button
        binding.SignUpBtn.setOnClickListener {
            registerUser()
        }

        // Navigate to Login screen
        val openLogin = {
            startActivity(Intent(this, MainActivity2::class.java))
            finish()
        }
        binding.LoginText.setOnClickListener { openLogin() }
        binding.loginPromptLayout.setOnClickListener { openLogin() }

        binding.btnGoogleSignUp.setOnClickListener {
            Toast.makeText(this, "Please sign up with email and password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerUser() {
        val name = binding.signupfullname.text.toString().trim()
        val rawUsername = binding.signupusername.text.toString().trim()
        val cleanUsername = rawUsername.lowercase().removePrefix("@").replace(" ", "_")
        val email = binding.signupemail.text.toString().trim()
        val password = binding.signuppassword.text.toString().trim()
        val confirmPassword = binding.confirmpassword.text.toString().trim()

        if (name.isEmpty()) {
            binding.signupfullname.error = "Please enter your name"
            binding.signupfullname.requestFocus()
            return
        }

        if (cleanUsername.isEmpty()) {
            binding.signupusername.error = "Please choose a username"
            binding.signupusername.requestFocus()
            return
        }

        if (cleanUsername.length < 3) {
            binding.signupusername.error = "Username must be at least 3 characters"
            binding.signupusername.requestFocus()
            return
        }

        if (!cleanUsername.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
            binding.signupusername.error = "Usernames can only contain letters, numbers, _, and ."
            binding.signupusername.requestFocus()
            return
        }

        if (email.isEmpty()) {
            binding.signupemail.error = "Please enter your email"
            binding.signupemail.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.signuppassword.error = "Password must be at least 6 characters"
            binding.signuppassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.confirmpassword.error = "Passwords do not match"
            binding.confirmpassword.requestFocus()
            return
        }

        binding.SignUpBtn.isEnabled = false
        binding.SignUpBtn.text = "Checking username..."

        // Check if username already exists in Firestore
        db.collection("users")
            .whereEqualTo("username", cleanUsername)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    binding.SignUpBtn.isEnabled = true
                    binding.SignUpBtn.text = "Sign Up  →"
                    binding.signupusername.error = "Username '$cleanUsername' is already taken! Please choose a unique one."
                    binding.signupusername.requestFocus()
                    Toast.makeText(this, "Username already exists! Please choose another one.", Toast.LENGTH_SHORT).show()
                } else {
                    // Username is unique! Proceed with account creation
                    binding.SignUpBtn.text = "Creating account..."
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            binding.SignUpBtn.isEnabled = true
                            binding.SignUpBtn.text = "Sign Up  →"

                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid ?: ""
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build()
                                auth.currentUser?.updateProfile(profileUpdates)

                                val userData = hashMapOf(
                                    "uid" to uid,
                                    "name" to name,
                                    "username" to cleanUsername,
                                    "bio" to "Storyteller & Creator",
                                    "postsCount" to 0
                                )
                                binding.SignUpBtn.text = "Setting up profile..."
                                db.collection("users").document(uid).set(userData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Welcome to Inkora!", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this, userdetails::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Firestore error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        val intent = Intent(this, userdetails::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                            } else {
                                Toast.makeText(this, task.exception?.localizedMessage ?: "Sign up failed", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.SignUpBtn.isEnabled = true
                binding.SignUpBtn.text = "Sign Up  →"
                Toast.makeText(this, "Error checking username: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}
