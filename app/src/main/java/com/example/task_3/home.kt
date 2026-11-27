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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

class home : AppCompatActivity() {

    private val allBlogs = mutableListOf<Blog>()
    private val displayedBlogs = mutableListOf<Blog>()
    private val savedBlogIds = mutableSetOf<String>()
    private val followedAuthorIds = mutableSetOf<String>()
    private lateinit var adapter: BlogRecyclerAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var blogsListener: ListenerRegistration? = null
    private var savedListener: ListenerRegistration? = null
    private var followingListener: ListenerRegistration? = null

    private lateinit var rvFeed: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvOfflineBanner: TextView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        rvFeed = findViewById(R.id.rvFeed)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        pbLoading = findViewById(R.id.pbFeedLoading)
        tvEmpty = findViewById(R.id.tvEmptyFeed)
        tvOfflineBanner = findViewById(R.id.tvOfflineBanner)
        bottomNav = findViewById(R.id.bottom_nav)

        bottomNav.selectedItemId = R.id.nav_home

        setupRecyclerView()
        setupBottomNavigation()

        // Configure SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_red)
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.card_surface)
        swipeRefreshLayout.setOnRefreshListener {
            refreshFromServer()
        }
    }

    override fun onStart() {
        super.onStart()
        attachFeedListeners()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_home
        updateOfflineBanner(isFromCache = !inkora.isOnline(this))
    }

    override fun onStop() {
        super.onStop()
        detachFeedListeners()
    }

    private fun setupRecyclerView() {
        val currentUid = auth.currentUser?.uid ?: ""
        adapter = BlogRecyclerAdapter(
            context = this,
            blogs = displayedBlogs,
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
                adapter.notifyItemChanged(pos)
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

    private fun attachFeedListeners() {
        if (blogsListener != null) return
        if (allBlogs.isEmpty()) {
            pbLoading.visibility = View.VISIBLE
        }

        val currentUid = auth.currentUser?.uid ?: ""

        blogsListener = db.collection("blogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { result, error ->
                pbLoading.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false

                if (error != null) {
                    if (allBlogs.isEmpty()) {
                        Toast.makeText(this, "Failed to load: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (result != null) {
                    updateOfflineBanner(isFromCache = result.metadata.isFromCache && !inkora.isOnline(this))
                    allBlogs.clear()
                    for (doc in result.documents) {
                        val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val isLiked = currentUid.isNotEmpty() && likedBy.contains(currentUid)
                        val blog = Blog(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            content = doc.getString("content") ?: "",
                            category = doc.getString("category") ?: "Tech",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            author = doc.getString("author")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Anonymous",
                            authorId = doc.getString("authorId") ?: doc.getString("uid") ?: "",
                            authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                            likesCount = doc.getLong("likesCount") ?: likedBy.size.toLong(),
                            commentsCount = doc.getLong("commentsCount") ?: 0L,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            likedBy = likedBy,
                            isLiked = isLiked,
                            isSaved = doc.id in savedBlogIds
                        )
                        allBlogs.add(blog)
                    }
                    filterAndDisplayBlogs()
                }
            }

        if (currentUid.isNotEmpty()) {
            savedListener = db.collection("users").document(currentUid).collection("saved_posts")
                .addSnapshotListener { savedSnap, _ ->
                    if (savedSnap != null) {
                        savedBlogIds.clear()
                        savedBlogIds.addAll(savedSnap.documents.map { it.id })
                        allBlogs.forEach { it.isSaved = it.id in savedBlogIds }
                        filterAndDisplayBlogs()
                    }
                }

            followingListener = db.collection("users").document(currentUid).collection("following")
                .addSnapshotListener { followingSnap, _ ->
                    if (followingSnap != null) {
                        followedAuthorIds.clear()
                        for (doc in followingSnap.documents) {
                            followedAuthorIds.add(doc.id)
                        }
                        adapter.updateFollowedUsers(followedAuthorIds)
                    }
                }
        }
    }

    private fun detachFeedListeners() {
        blogsListener?.remove()
        blogsListener = null
        savedListener?.remove()
        savedListener = null
        followingListener?.remove()
        followingListener = null
    }

    private fun refreshFromServer() {
        if (!inkora.isOnline(this)) {
            swipeRefreshLayout.isRefreshing = false
            updateOfflineBanner(isFromCache = true)
            Toast.makeText(this, "Offline — showing cached stories", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("blogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get(Source.SERVER)
            .addOnCompleteListener {
                swipeRefreshLayout.isRefreshing = false
                updateOfflineBanner(isFromCache = !inkora.isOnline(this))
            }
    }

    private fun updateOfflineBanner(isFromCache: Boolean) {
        tvOfflineBanner.visibility = if (isFromCache) View.VISIBLE else View.GONE
    }

    private fun filterAndDisplayBlogs() {
        displayedBlogs.clear()
        displayedBlogs.addAll(allBlogs)
        adapter.notifyDataSetChanged()

        if (displayedBlogs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "No stories published yet.\nBe the first to share one!"
        } else {
            tvEmpty.visibility = View.GONE
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
        displayedBlogs[position] = updatedBlog
        adapter.notifyItemChanged(position)

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
                // Revert local state if Firestore write fails
                allBlogs.find { it.id == blog.id }?.let {
                    val idx = allBlogs.indexOf(it)
                    if (idx != -1) allBlogs[idx] = blog
                }
                displayedBlogs[position] = blog
                adapter.notifyItemChanged(position)
                Toast.makeText(this, "Failed to update like: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
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
                        if (blog.authorId.isNotEmpty()) {
                            db.collection("users").document(blog.authorId).set(
                                mapOf("postsCount" to FieldValue.increment(-1)),
                                SetOptions.merge()
                            )
                        }
                        filterAndDisplayBlogs()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleFollowToggle(authorId: String, authorName: String, onDone: (() -> Unit)? = null) {
        val currentUid = auth.currentUser?.uid ?: return
        if (authorId.isEmpty() || authorId == currentUid) return

        val isCurrentlyFollowed = followedAuthorIds.contains(authorId)
        val batch = db.batch()

        val currentFollowingRef = db.collection("users").document(currentUid).collection("following").document(authorId)
        val authorFollowerRef = db.collection("users").document(authorId).collection("followers").document(currentUid)
        val currentUserRef = db.collection("users").document(currentUid)
        val authorUserRef = db.collection("users").document(authorId)

        if (isCurrentlyFollowed) {
            batch.delete(currentFollowingRef)
            batch.delete(authorFollowerRef)
            batch.set(currentUserRef, mapOf("followingCount" to FieldValue.increment(-1)), SetOptions.merge())
            batch.set(authorUserRef, mapOf("followersCount" to FieldValue.increment(-1)), SetOptions.merge())
            followedAuthorIds.remove(authorId)
        } else {
            val followData = hashMapOf("followedAt" to System.currentTimeMillis())
            batch.set(currentFollowingRef, followData)
            batch.set(authorFollowerRef, followData)
            batch.set(currentUserRef, mapOf("followingCount" to FieldValue.increment(1)), SetOptions.merge())
            batch.set(authorUserRef, mapOf("followersCount" to FieldValue.increment(1)), SetOptions.merge())
            followedAuthorIds.add(authorId)
        }

        // Optimistically update UI
        adapter.updateFollowedUsers(followedAuthorIds)
        onDone?.invoke()

        val msg = if (isCurrentlyFollowed) "Unfollowed $authorName" else "Following $authorName"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        batch.commit().addOnFailureListener { e ->
            // Revert state if failed
            if (isCurrentlyFollowed) {
                followedAuthorIds.add(authorId)
            } else {
                followedAuthorIds.remove(authorId)
            }
            adapter.updateFollowedUsers(followedAuthorIds)
            onDone?.invoke()
            Toast.makeText(this, "Failed to update follow: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                    refreshFromServer()
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
