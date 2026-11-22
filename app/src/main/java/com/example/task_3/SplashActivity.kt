package com.example.task_3

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val auth = FirebaseAuth.getInstance()

        // Short splash delay before opening the right screen
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser != null) {
                startActivity(Intent(this, home::class.java))
            } else {
                startActivity(Intent(this, MainActivity2::class.java))
            }
            finish()
        }, 1200)
    }
}
