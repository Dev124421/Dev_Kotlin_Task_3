package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class home : AppCompatActivity() {

    private val allBlogs = mutableListOf<Blog>()
    private val displayedBlogs = mutableListOf<Blog>()
    private lateinit var adapter: BlogRecyclerAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var rvFeed: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var tabForYou: View
    private lateinit var tabFollowing: View
    private lateinit var tvTabForYou: TextView
    private lateinit var tvTabFollowing: TextView
    private lateinit var indicatorForYou: View
    private lateinit var indicatorFollowing: View

    private var isFollowingTab = false
    private var selectedCategory = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val refresh = findViewById<Button>(R.id.refresh)
        rvFeed = findViewById(R.id.rvFeed)
        pbLoading = findViewById(R.id.pbFeedLoading)
        tvEmpty = findViewById(R.id.tvEmptyFeed)
        bottomNav = findViewById(R.id.bottom_nav)

        tabForYou = findViewById(R.id.tabForYou)
        tabFollowing = findViewById(R.id.tabFollowing)
        tvTabForYou = findViewById(R.id.tvTabForYou)
        tvTabFollowing = findViewById(R.id.tvTabFollowing)
        indicatorForYou = findViewById(R.id.indicatorForYou)
        indicatorFollowing = findViewById(R.id.indicatorFollowing)

        bottomNav.selectedItemId = R.id.nav_home

        setupRecyclerView()
        setupTabs()
        setupCategoryChips()
        setupBottomNavigation()

        fetchBlogs()

        refresh.setOnClickListener {
            fetchBlogs()
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_home
        fetchBlogs()
    }

    private fun setupRecyclerView() {
        val currentUid = auth.currentUser?.uid ?: ""
        adapter = BlogRecyclerAdapter(
            context = this,
            blogs = displayedBlogs,
            currentUserId = currentUid,
            onLikeClick = { blog, pos -> handleLike(blog, pos) },
            onCommentClick = { blog ->
                Toast.makeText(this, "Comments for \"${blog.title}\"", Toast.LENGTH_SHORT).show()
            },
            onSaveClick = { blog, pos ->
                blog.isSaved = !blog.isSaved
                adapter.notifyItemChanged(pos)
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val docRef = db.collection("users").document(uid).collection("saved_posts").document(blog.id)
                    if (blog.isSaved) {
                        docRef.set(mapOf("savedAt" to System.currentTimeMillis()))
                    } else {
                        docRef.delete()
                    }
                }
                val msg = if (blog.isSaved) "Saved to bookmarks" else "Removed from bookmarks"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            },
            onEditClick = { blog -> handleEdit(blog) },
            onDeleteClick = { blog -> confirmDelete(blog) },
            onBlogClick = { blog ->
                showBlogReaderDialog(blog)
            }
        )
        rvFeed.layoutManager = LinearLayoutManager(this)
        rvFeed.adapter = adapter
    }

    private fun setupTabs() {
        tabForYou.setOnClickListener {
            if (isFollowingTab) {
                isFollowingTab = false
                tvTabForYou.setTextColor(getColor(R.color.primary_text))
                indicatorForYou.setBackgroundResource(R.drawable.bg_primary_button)
                tvTabFollowing.setTextColor(getColor(R.color.secondary_text))
                indicatorFollowing.setBackgroundColor(getColor(android.R.color.transparent))
                filterAndDisplayBlogs()
            }
        }

        tabFollowing.setOnClickListener {
            if (!isFollowingTab) {
                isFollowingTab = true
                tvTabFollowing.setTextColor(getColor(R.color.primary_text))
                indicatorFollowing.setBackgroundResource(R.drawable.bg_primary_button)
                tvTabForYou.setTextColor(getColor(R.color.secondary_text))
                indicatorForYou.setBackgroundColor(getColor(android.R.color.transparent))
                filterAndDisplayBlogs()
            }
        }
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            findViewById<TextView>(R.id.chipHomeAll) to "All",
            findViewById<TextView>(R.id.chipHomeTech) to "Tech",
            findViewById<TextView>(R.id.chipHomeDesign) to "Design",
            findViewById<TextView>(R.id.chipHomeLife) to "Life",
            findViewById<TextView>(R.id.chipHomeArt) to "Art"
        )

        for ((chipView, categoryName) in chips) {
            chipView.setOnClickListener {
                selectedCategory = categoryName
                for ((otherView, otherName) in chips) {
                    if (otherName == selectedCategory) {
                        otherView.setBackgroundResource(R.drawable.bg_pill_filter_selected)
                        otherView.setTextColor(getColor(R.color.white))
                    } else {
                        otherView.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                        otherView.setTextColor(getColor(R.color.secondary_text))
                    }
                }
                filterAndDisplayBlogs()
            }
        }
    }

    private fun fetchBlogs() {
        pbLoading.visibility = View.VISIBLE
        db.collection("blogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                pbLoading.visibility = View.GONE
                allBlogs.clear()
                for (doc in result) {
                    val blog = Blog(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        category = doc.getString("category") ?: "Tech",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        author = doc.getString("author") ?: "Anonymous",
                        authorId = doc.getString("authorId") ?: doc.getString("uid") ?: "",
                        authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                        likesCount = doc.getLong("likesCount") ?: 0L,
                        commentsCount = doc.getLong("commentsCount") ?: 0L,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                    allBlogs.add(blog)
                }

                val currentUid = auth.currentUser?.uid
                if (currentUid != null) {
                    db.collection("users").document(currentUid).collection("saved_posts").get()
                        .addOnSuccessListener { savedSnap ->
                            val savedIds = savedSnap.documents.map { it.id }.toSet()
                            allBlogs.forEach { it.isSaved = it.id in savedIds }
                            filterAndDisplayBlogs()
                        }
                        .addOnFailureListener {
                            filterAndDisplayBlogs()
                        }
                } else {
                    filterAndDisplayBlogs()
                }
            }
            .addOnFailureListener {
                pbLoading.visibility = View.GONE
                Toast.makeText(this, "Failed to load: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterAndDisplayBlogs() {
        displayedBlogs.clear()

        val filtered = allBlogs.filter { blog ->
            val matchesCategory = (selectedCategory == "All" || blog.category.equals(selectedCategory, ignoreCase = true))
            val matchesTab = if (isFollowingTab) {
                // In Following tab, show posts by others or user
                blog.authorId.isNotEmpty()
            } else {
                true
            }
            matchesCategory && matchesTab
        }

        displayedBlogs.addAll(filtered)
        adapter.notifyDataSetChanged()

        tvEmpty.visibility = if (displayedBlogs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleLike(blog: Blog, position: Int) {
        blog.isLiked = !blog.isLiked
        val change = if (blog.isLiked) 1L else -1L
        val newLikes = maxOf(0L, blog.likesCount + change)

        // Update local object
        allBlogs.find { it.id == blog.id }?.let {
            it.isLiked = blog.isLiked
        }
        val updatedBlog = blog.copy(likesCount = newLikes, isLiked = blog.isLiked)
        displayedBlogs[position] = updatedBlog
        adapter.notifyItemChanged(position)

        // Update Firestore
        db.collection("blogs").document(blog.id)
            .update("likesCount", FieldValue.increment(change))
    }

    private fun handleEdit(blog: Blog) {
        val currentUid = auth.currentUser?.uid ?: ""
        if (blog.authorId.isNotEmpty() && blog.authorId != currentUid) {
            Toast.makeText(this, "You can only edit your own posts", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, add_data::class.java).apply {
            putExtra("DOC_ID", blog.id)
            putExtra("TITLE", blog.title)
            putExtra("CONTENT", blog.content)
            putExtra("CATEGORY", blog.category)
            putExtra("IMAGE_URL", blog.imageUrl)
            putExtra("AUTHOR", blog.author)
        }
        startActivity(intent)
    }

    private fun confirmDelete(blog: Blog) {
        val currentUid = auth.currentUser?.uid ?: ""
        if (blog.authorId.isNotEmpty() && blog.authorId != currentUid) {
            Toast.makeText(this, "You can only delete your own posts", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Blog")
            .setMessage("Are you sure you want to delete \"${blog.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("blogs").document(blog.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Blog deleted", Toast.LENGTH_SHORT).show()
                        allBlogs.removeAll { it.id == blog.id }
                        filterAndDisplayBlogs()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBlogReaderDialog(blog: Blog) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_blog_reader, null)
        dialog.setContentView(view)

        val ivCover = view.findViewById<ImageView>(R.id.ivReaderCover)
        val tvCategory = view.findViewById<TextView>(R.id.tvReaderCategory)
        val tvTitle = view.findViewById<TextView>(R.id.tvReaderTitle)
        val ivAuthorAvatar = view.findViewById<ImageView>(R.id.ivReaderAuthorAvatar)
        val tvAuthor = view.findViewById<TextView>(R.id.tvReaderAuthor)
        val tvTime = view.findViewById<TextView>(R.id.tvReaderTime)
        val tvContent = view.findViewById<TextView>(R.id.tvReaderContent)
        val btnClose = view.findViewById<View>(R.id.btnReaderClose)

        tvTitle.text = blog.title
        tvContent.text = blog.content
        tvCategory.text = blog.category
        tvAuthor.text = blog.author

        val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
            blog.timestamp,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        )
        tvTime.text = relativeTime

        if (blog.imageUrl.isNotEmpty()) {
            ivCover.visibility = View.VISIBLE
            ivCover.load(blog.imageUrl) {
                crossfade(true)
            }
        } else {
            ivCover.visibility = View.GONE
        }

        if (blog.authorPhotoUrl.isNotEmpty()) {
            ivAuthorAvatar.load(blog.authorPhotoUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_avatar_placeholder)
                error(R.drawable.bg_avatar_placeholder)
            }
        } else {
            ivAuthorAvatar.setImageResource(R.drawable.bg_avatar_placeholder)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    fetchBlogs()
                    true
                }
                R.id.nav_discover -> {
                    startActivity(Intent(this, discover::class.java))
                    true
                }
                R.id.nav_add -> {
                    startActivity(Intent(this, add_data::class.java))
                    true
                }
                R.id.nav_account -> {
                    startActivity(Intent(this, profile::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
