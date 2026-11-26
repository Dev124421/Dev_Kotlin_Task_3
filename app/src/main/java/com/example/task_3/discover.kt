package com.example.task_3

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

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
    private val followedAuthorIds = mutableSetOf<String>()
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
            followedUserIds = followedAuthorIds,
            onFollowClick = { blog, _ ->
                handleFollowToggle(blog.authorId, blog.author)
            },
            onAuthorClick = { blog ->
                openUserProfile(blog.authorId, blog.author)
            },
            onLikeClick = { blog, pos -> handleLike(blog, pos) },
            onCommentClick = { blog ->
                Toast.makeText(this, "Comments for \"${blog.title}\"", Toast.LENGTH_SHORT).show()
            },
            onSaveClick = { blog, pos ->
                blog.isSaved = !blog.isSaved
                blogsAdapter.notifyItemChanged(pos)
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val docRef = db.collection("users").document(uid).collection("saved_posts").document(blog.id)
                    if (blog.isSaved) {
                        val savedData = hashMapOf(
                            "title" to blog.title,
                            "content" to blog.content,
                            "category" to blog.category,
                            "imageUrl" to blog.imageUrl,
                            "author" to blog.author,
                            "authorId" to blog.authorId,
                            "authorPhotoUrl" to blog.authorPhotoUrl,
                            "likesCount" to blog.likesCount,
                            "commentsCount" to blog.commentsCount,
                            "timestamp" to blog.timestamp,
                            "savedAt" to System.currentTimeMillis()
                        )
                        docRef.set(savedData)
                    } else {
                        docRef.delete()
                    }
                }
                Toast.makeText(this, if (blog.isSaved) "Saved to bookmarks" else "Removed from bookmarks", Toast.LENGTH_SHORT).show()
            },
            onEditClick = {},
            onDeleteClick = {},
            onBlogClick = { blog ->
                showBlogReaderDialog(blog)
            }
        )
        rvTrendingPosts.layoutManager = LinearLayoutManager(this)
        rvTrendingPosts.adapter = blogsAdapter

        usersAdapter = UserSearchAdapter(
            context = this,
            users = filteredUsers,
            onFollowClick = { user, _ ->
                handleFollowToggle(user.uid, user.name)
            },
            onUserClick = { user ->
                openUserProfile(user.uid, user.name)
            }
        )
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
        val currentUid = auth.currentUser?.uid ?: ""

        // Load following list
        if (currentUid.isNotEmpty()) {
            db.collection("users").document(currentUid).collection("following")
                .get()
                .addOnSuccessListener { followingSnap ->
                    followedAuthorIds.clear()
                    for (doc in followingSnap.documents) {
                        followedAuthorIds.add(doc.id)
                    }
                    blogsAdapter.updateFollowedUsers(followedAuthorIds)
                    for (user in allUsers) {
                        user.followStatus = if (followedAuthorIds.contains(user.uid)) "Following" else "Follow"
                    }
                    for (user in filteredUsers) {
                        user.followStatus = if (followedAuthorIds.contains(user.uid)) "Following" else "Follow"
                    }
                    usersAdapter.notifyDataSetChanged()
                }
        }

        // Load blogs
        db.collection("blogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                allBlogs.clear()
                for (doc in result) {
                    val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val isLiked = currentUid.isNotEmpty() && likedBy.contains(currentUid)
                    val blog = Blog(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        category = doc.getString("category") ?: "Tech",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        author = doc.getString("author") ?: "Anonymous",
                        authorId = doc.getString("authorId") ?: "",
                        authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                        likesCount = doc.getLong("likesCount") ?: likedBy.size.toLong(),
                        commentsCount = doc.getLong("commentsCount") ?: 0L,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        likedBy = likedBy,
                        isLiked = isLiked
                    )
                    allBlogs.add(blog)
                }
                if (currentUid.isNotEmpty()) {
                    db.collection("users").document(currentUid).collection("saved_posts")
                        .get()
                        .addOnSuccessListener { savedDocs ->
                            val savedIds = savedDocs.documents.map { it.id }.toSet()
                            for (b in allBlogs) {
                                b.isSaved = savedIds.contains(b.id)
                            }
                            applyFilters()
                        }
                        .addOnFailureListener {
                            applyFilters()
                        }
                } else {
                    applyFilters()
                }
            }

        // Load users
        db.collection("users")
            .limit(20)
            .get()
            .addOnSuccessListener { result ->
                allUsers.clear()
                for (doc in result) {
                    val uid = doc.getString("uid") ?: doc.id
                    if (uid == currentUid) continue

                    val name = doc.getString("name") ?: "User"
                    val username = doc.getString("username") ?: "creator"
                    val bio = doc.getString("bio") ?: "Storyteller"
                    val photoUrl = doc.getString("photoUrl") ?: doc.getString("pfpUrl") ?: ""
                    val status = if (followedAuthorIds.contains(uid)) "Following" else "Follow"
                    allUsers.add(UserItem(uid, name, username, bio, status, photoUrl))
                }
                applyFilters()
            }
    }

    private fun handleFollowToggle(targetUid: String, targetName: String, onDone: (() -> Unit)? = null) {
        val currentUid = auth.currentUser?.uid ?: return
        if (targetUid.isEmpty() || targetUid == currentUid) return

        val isCurrentlyFollowed = followedAuthorIds.contains(targetUid)
        val batch = db.batch()

        val currentFollowingRef = db.collection("users").document(currentUid).collection("following").document(targetUid)
        val targetFollowerRef = db.collection("users").document(targetUid).collection("followers").document(currentUid)
        val currentUserRef = db.collection("users").document(currentUid)
        val targetUserRef = db.collection("users").document(targetUid)

        if (isCurrentlyFollowed) {
            batch.delete(currentFollowingRef)
            batch.delete(targetFollowerRef)
            batch.set(currentUserRef, mapOf("followingCount" to FieldValue.increment(-1)), SetOptions.merge())
            batch.set(targetUserRef, mapOf("followersCount" to FieldValue.increment(-1)), SetOptions.merge())
            followedAuthorIds.remove(targetUid)
        } else {
            val followData = hashMapOf("followedAt" to System.currentTimeMillis())
            batch.set(currentFollowingRef, followData)
            batch.set(targetFollowerRef, followData)
            batch.set(currentUserRef, mapOf("followingCount" to FieldValue.increment(1)), SetOptions.merge())
            batch.set(targetUserRef, mapOf("followersCount" to FieldValue.increment(1)), SetOptions.merge())
            followedAuthorIds.add(targetUid)
        }

        // Optimistically update UI
        val newStatus = if (isCurrentlyFollowed) "Follow" else "Following"
        for (u in allUsers) {
            if (u.uid == targetUid) {
                u.followStatus = newStatus
            }
        }
        for (i in filteredUsers.indices) {
            if (filteredUsers[i].uid == targetUid) {
                filteredUsers[i].followStatus = newStatus
                usersAdapter.notifyItemChanged(i)
            }
        }
        blogsAdapter.updateFollowedUsers(followedAuthorIds)
        onDone?.invoke()

        val msg = if (isCurrentlyFollowed) "Unfollowed $targetName" else "Following $targetName"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        batch.commit().addOnFailureListener { e ->
            // Revert state if failed
            val revertStatus = if (isCurrentlyFollowed) "Following" else "Follow"
            if (isCurrentlyFollowed) {
                followedAuthorIds.add(targetUid)
            } else {
                followedAuthorIds.remove(targetUid)
            }
            for (u in allUsers) {
                if (u.uid == targetUid) {
                    u.followStatus = revertStatus
                }
            }
            for (i in filteredUsers.indices) {
                if (filteredUsers[i].uid == targetUid) {
                    filteredUsers[i].followStatus = revertStatus
                    usersAdapter.notifyItemChanged(i)
                }
            }
            blogsAdapter.updateFollowedUsers(followedAuthorIds)
            onDone?.invoke()
            Toast.makeText(this, "Failed to update follow: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLike(blog: Blog, position: Int) {
        val currentUid = auth.currentUser?.uid ?: return
        val newIsLiked = !blog.isLiked
        val updatedLikedBy = blog.likedBy.toMutableList()
        val change: Long

        if (newIsLiked) {
            if (!updatedLikedBy.contains(currentUid)) {
                updatedLikedBy.add(currentUid)
            }
            change = 1L
        } else {
            updatedLikedBy.remove(currentUid)
            change = -1L
        }

        val newLikesCount = maxOf(0L, blog.likesCount + change)
        val updatedBlog = blog.copy(
            likesCount = newLikesCount,
            likedBy = updatedLikedBy,
            isLiked = newIsLiked
        )

        allBlogs.find { it.id == blog.id }?.let {
            val idx = allBlogs.indexOf(it)
            if (idx != -1) allBlogs[idx] = updatedBlog
        }
        filteredBlogs[position] = updatedBlog
        blogsAdapter.notifyItemChanged(position)

        val docRef = db.collection("blogs").document(blog.id)
        val updates = if (newIsLiked) {
            mapOf(
                "likesCount" to FieldValue.increment(1),
                "likedBy" to FieldValue.arrayUnion(currentUid)
            )
        } else {
            mapOf(
                "likesCount" to FieldValue.increment(-1),
                "likedBy" to FieldValue.arrayRemove(currentUid)
            )
        }

        docRef.set(updates, SetOptions.merge())
            .addOnFailureListener { e ->
                allBlogs.find { it.id == blog.id }?.let {
                    val idx = allBlogs.indexOf(it)
                    if (idx != -1) allBlogs[idx] = blog
                }
                filteredBlogs[position] = blog
                blogsAdapter.notifyItemChanged(position)
                Toast.makeText(this, "Failed to update like: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
        val btnReaderFollow = view.findViewById<Button>(R.id.btnReaderFollow)
        val btnClose = view.findViewById<View>(R.id.btnReaderClose)

        tvTitle.text = blog.title
        tvContent.text = blog.content
        tvCategory.text = blog.category
        tvAuthor.text = blog.author

        // Tap author avatar or name in reader to open creator profile
        val openAuthor = View.OnClickListener {
            dialog.dismiss()
            openUserProfile(blog.authorId, blog.author)
        }
        ivAuthorAvatar.setOnClickListener(openAuthor)
        tvAuthor.setOnClickListener(openAuthor)

        val currentUid = auth.currentUser?.uid ?: ""
        val isOwnPost = currentUid.isNotEmpty() && blog.authorId == currentUid
        if (isOwnPost || blog.authorId.isEmpty()) {
            btnReaderFollow.visibility = View.GONE
        } else {
            btnReaderFollow.visibility = View.VISIBLE
            fun updateReaderFollowButton() {
                val isFollowed = followedAuthorIds.contains(blog.authorId)
                if (isFollowed) {
                    btnReaderFollow.text = "Followed"
                    btnReaderFollow.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                    btnReaderFollow.setTextColor(getColor(R.color.secondary_text))
                } else {
                    btnReaderFollow.text = "+ Follow"
                    btnReaderFollow.setBackgroundResource(R.drawable.bg_primary_button)
                    btnReaderFollow.setTextColor(getColor(R.color.white))
                }
            }
            updateReaderFollowButton()
            btnReaderFollow.setOnClickListener {
                handleFollowToggle(blog.authorId, blog.author) {
                    updateReaderFollowButton()
                }
                updateReaderFollowButton()
            }
        }

        val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
            blog.timestamp,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        )
        tvTime.text = relativeTime

        if (blog.imageUrl.isNotBlank()) {
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

    private fun openUserProfile(authorId: String, authorName: String) {
        if (authorId.isEmpty()) return
        val currentUid = auth.currentUser?.uid ?: ""
        if (authorId == currentUid) {
            startActivity(Intent(this, profile::class.java))
        } else {
            val intent = Intent(this, UserProfileActivity::class.java).apply {
                putExtra("USER_ID", authorId)
                putExtra("USER_NAME", authorName)
            }
            startActivity(intent)
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
