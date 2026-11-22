package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class discover : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: View
    private lateinit var tabSearchPosts: TextView
    private lateinit var tabSearchPeople: TextView

    private lateinit var viewPostsSection: LinearLayout
    private lateinit var viewPeopleSection: LinearLayout
    private lateinit var rvTrendingPosts: RecyclerView
    private lateinit var rvUsers: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var bottomNav: BottomNavigationView

    private val allBlogs = mutableListOf<Blog>()
    private val filteredBlogs = mutableListOf<Blog>()
    private lateinit var blogsAdapter: BlogRecyclerAdapter

    private val allUsers = mutableListOf<UserItem>()
    private val filteredUsers = mutableListOf<UserItem>()
    private lateinit var usersAdapter: UserSearchAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var isSearchingPosts = true
    private var selectedCategory = "All"
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        tabSearchPosts = findViewById(R.id.tabSearchPosts)
        tabSearchPeople = findViewById(R.id.tabSearchPeople)

        viewPostsSection = findViewById(R.id.viewPostsSection)
        viewPeopleSection = findViewById(R.id.viewPeopleSection)
        rvTrendingPosts = findViewById(R.id.rvTrendingPosts)
        rvUsers = findViewById(R.id.rvUsers)
        tvEmpty = findViewById(R.id.tvDiscoverEmpty)
        bottomNav = findViewById(R.id.bottom_nav)

        bottomNav.selectedItemId = R.id.nav_discover

        setupAdapters()
        setupSearch()
        setupTabs()
        setupCategoryChips()
        setupBottomNavigation()

        loadData()
    }

    private fun setupAdapters() {
        val currentUid = auth.currentUser?.uid ?: ""

        blogsAdapter = BlogRecyclerAdapter(
            context = this,
            blogs = filteredBlogs,
            currentUserId = currentUid,
            onLikeClick = { blog, _ ->
                Toast.makeText(this, "Liked \"${blog.title}\"", Toast.LENGTH_SHORT).show()
            },
            onCommentClick = { blog ->
                Toast.makeText(this, "Comments for \"${blog.title}\"", Toast.LENGTH_SHORT).show()
            },
            onSaveClick = { blog, pos ->
                blog.isSaved = !blog.isSaved
                blogsAdapter.notifyItemChanged(pos)
                Toast.makeText(this, if (blog.isSaved) "Saved" else "Removed", Toast.LENGTH_SHORT).show()
            },
            onEditClick = {},
            onDeleteClick = {},
            onBlogClick = {}
        )
        rvTrendingPosts.layoutManager = LinearLayoutManager(this)
        rvTrendingPosts.adapter = blogsAdapter

        usersAdapter = UserSearchAdapter(this, filteredUsers) { user, position ->
            val newStatus = if (user.followStatus == "Following") "Follow" else "Following"
            filteredUsers[position] = user.copy(followStatus = newStatus)
            usersAdapter.notifyItemChanged(position)
            Toast.makeText(this, "${user.name}: $newStatus", Toast.LENGTH_SHORT).show()
        }
        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = usersAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s.toString().trim()
                btnClearSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.text?.clear()
        }
    }

    private fun setupTabs() {
        tabSearchPosts.setOnClickListener {
            if (!isSearchingPosts) {
                isSearchingPosts = true
                tabSearchPosts.setBackgroundResource(R.drawable.bg_pill_filter_selected)
                tabSearchPosts.setTextColor(getColor(R.color.white))
                tabSearchPeople.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                tabSearchPeople.setTextColor(getColor(R.color.secondary_text))

                viewPostsSection.visibility = View.VISIBLE
                viewPeopleSection.visibility = View.GONE
                applyFilters()
            }
        }

        tabSearchPeople.setOnClickListener {
            if (isSearchingPosts) {
                isSearchingPosts = false
                tabSearchPeople.setBackgroundResource(R.drawable.bg_pill_filter_selected)
                tabSearchPeople.setTextColor(getColor(R.color.white))
                tabSearchPosts.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                tabSearchPosts.setTextColor(getColor(R.color.secondary_text))

                viewPostsSection.visibility = View.GONE
                viewPeopleSection.visibility = View.VISIBLE
                applyFilters()
            }
        }
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            findViewById<TextView>(R.id.chipTech) to "Tech",
            findViewById<TextView>(R.id.chipDesign) to "Design",
            findViewById<TextView>(R.id.chipLife) to "Life",
            findViewById<TextView>(R.id.chipArt) to "Art"
        )

        for ((chipView, cat) in chips) {
            chipView.setOnClickListener {
                selectedCategory = if (selectedCategory == cat) "All" else cat

                for ((otherView, otherCat) in chips) {
                    if (otherCat == selectedCategory) {
                        otherView.setBackgroundResource(R.drawable.bg_pill_filter_selected)
                        otherView.setTextColor(getColor(R.color.white))
                    } else {
                        otherView.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                        otherView.setTextColor(getColor(R.color.secondary_text))
                    }
                }
                applyFilters()
            }
        }
    }

    private fun loadData() {
        // Load blogs
        db.collection("blogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                allBlogs.clear()
                for (doc in result) {
                    val blog = Blog(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        category = doc.getString("category") ?: "Tech",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        author = doc.getString("author") ?: "Anonymous",
                        authorId = doc.getString("authorId") ?: "",
                        likesCount = doc.getLong("likesCount") ?: 0L,
                        commentsCount = doc.getLong("commentsCount") ?: 0L,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                    allBlogs.add(blog)
                }
                applyFilters()
            }

        // Load users
        db.collection("users")
            .limit(20)
            .get()
            .addOnSuccessListener { result ->
                allUsers.clear()
                val currentUid = auth.currentUser?.uid ?: ""
                for (doc in result) {
                    val uid = doc.getString("uid") ?: doc.id
                    if (uid == currentUid) continue

                    val name = doc.getString("name") ?: "User"
                    val username = doc.getString("username") ?: "creator"
                    val bio = doc.getString("bio") ?: "Storyteller"
                    allUsers.add(UserItem(uid, name, username, bio, "Follow"))
                }
                applyFilters()
            }
    }

    private fun applyFilters() {
        if (isSearchingPosts) {
            filteredBlogs.clear()
            val matches = allBlogs.filter { blog ->
                val matchCat = (selectedCategory == "All" || blog.category.equals(selectedCategory, ignoreCase = true))
                val matchQuery = currentQuery.isEmpty() ||
                        blog.title.contains(currentQuery, ignoreCase = true) ||
                        blog.content.contains(currentQuery, ignoreCase = true) ||
                        blog.author.contains(currentQuery, ignoreCase = true)
                matchCat && matchQuery
            }
            filteredBlogs.addAll(matches)
            blogsAdapter.notifyDataSetChanged()
            tvEmpty.visibility = if (filteredBlogs.isEmpty()) View.VISIBLE else View.GONE
        } else {
            filteredUsers.clear()
            val matches = allUsers.filter { user ->
                currentQuery.isEmpty() ||
                        user.name.contains(currentQuery, ignoreCase = true) ||
                        user.username.contains(currentQuery, ignoreCase = true)
            }
            filteredUsers.addAll(matches)
            usersAdapter.notifyDataSetChanged()
            tvEmpty.visibility = if (filteredUsers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, home::class.java))
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
