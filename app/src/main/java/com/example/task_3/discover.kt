package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class discover : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_discover

        setupBottomNavigation(bottomNav)
        setupCategoryChips()

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.setOnEditorActionListener { _, _, _ ->
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                Toast.makeText(this, "Searching: $query", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            findViewById<TextView>(R.id.chipTech),
            findViewById<TextView>(R.id.chipDesign),
            findViewById<TextView>(R.id.chipLife),
            findViewById<TextView>(R.id.chipArt)
        )

        chips.forEach { chip ->
            chip.setOnClickListener {
                chips.forEach { other ->
                    other.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                    other.setTextColor(getColor(R.color.secondary_text))
                }
                chip.setBackgroundResource(R.drawable.bg_pill_filter_selected)
                chip.setTextColor(getColor(R.color.white))
                Toast.makeText(this, "Filtered by ${chip.text}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBottomNavigation(bottomNav: BottomNavigationView) {
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
                R.id.nav_discover -> true
                R.id.nav_add -> {
                    startActivity(Intent(this, add_data::class.java))
                    finish()
                    true
                }
                R.id.nav_account -> {
                    startActivity(Intent(this, profile::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
