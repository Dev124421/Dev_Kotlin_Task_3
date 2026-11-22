package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.task_3.databinding.ActivityMain2Binding
import com.google.firebase.auth.FirebaseAuth

class MainActivity2 : AppCompatActivity() {

    private lateinit var binding: ActivityMain2Binding
    private lateinit var auth: FirebaseAuth
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Toggle password visibility
        binding.showPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.loginpassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.showPasswordToggle.setImageResource(if (isPasswordVisible) R.drawable.hide_icon else R.drawable.show_icon)
            binding.loginpassword.setSelection(binding.loginpassword.text.length)
        }

        // Login button
        binding.LoginBtn.setOnClickListener {
            loginUser()
        }

        // Navigate to Sign Up
        val openSignUp = {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.Signuptext.setOnClickListener { openSignUp() }
        binding.signUpPromptLayout.setOnClickListener { openSignUp() }

        // Forgot Password
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            Toast.makeText(this, "Please sign in with email and password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loginUser() {
        val email = binding.loginemail.text.toString().trim()
        val password = binding.loginpassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        binding.LoginBtn.isEnabled = false
        binding.LoginBtn.text = "Signing in..."

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.LoginBtn.isEnabled = true
                binding.LoginBtn.text = "Sign In  →"

                if (task.isSuccessful) {
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, home::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, task.exception?.localizedMessage ?: "Login failed", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this)
        input.hint = "Enter your email"
        val currentEmail = binding.loginemail.text.toString().trim()
        if (currentEmail.isNotEmpty()) input.setText(currentEmail)

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("Enter your email address to receive a reset link:")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
