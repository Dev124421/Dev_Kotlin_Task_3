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
        val email = binding.signupemail.text.toString().trim()
        val password = binding.signuppassword.text.toString().trim()
        val confirmPassword = binding.confirmpassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        binding.SignUpBtn.isEnabled = false
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
                        "username" to name.lowercase().replace(" ", "_"),
                        "email" to email,
                        "bio" to "Storyteller & Creator",
                        "postsCount" to 0
                    )
                    binding.SignUpBtn.text = "Setting up profile..."
                    db.collection("users").document(uid).set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Welcome to Inkora!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, home::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Firestore error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            val intent = Intent(this, home::class.java)
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
