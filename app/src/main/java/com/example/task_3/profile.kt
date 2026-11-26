package com.example.task_3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class profile : AppCompatActivity() {

    private lateinit var profileAvatar: ImageView
    private lateinit var nameText: TextView
    private lateinit var userHandleText: TextView
    private lateinit var ageText: TextView
    private lateinit var genderText: TextView
    private lateinit var addressText: TextView
    private lateinit var postsCountText: TextView
    private lateinit var followersCountText: TextView
    private lateinit var followingCountText: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnSettings: ImageView
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var tabBlogs: View
    private lateinit var tabSaved: View
    private lateinit var tvTabBlogs: TextView
    private lateinit var tvTabSaved: TextView
    private lateinit var indicatorBlogs: View
    private lateinit var indicatorSaved: View

    private lateinit var rvProfileBlogs: RecyclerView
    private lateinit var tvNoPosts: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val pfpPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePicture(uri)
        }
    }

    private val userBlogsList = mutableListOf<Blog>()
    private val savedBlogsList = mutableListOf<Blog>()
    private val displayedList = mutableListOf<Blog>()
    private val followedAuthorIds = mutableSetOf<String>()
    private lateinit var adapter: BlogRecyclerAdapter

    private var isSavedTab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profileAvatar = findViewById(R.id.profileAvatar)
        nameText = findViewById(R.id.name)
        userHandleText = findViewById(R.id.tvUserHandle)
        ageText = findViewById(R.id.age)
        genderText = findViewById(R.id.gender)
        addressText = findViewById(R.id.address)
        postsCountText = findViewById(R.id.tvPostsCount)
        followersCountText = findViewById(R.id.tvFollowersCount)
        followingCountText = findViewById(R.id.tvFollowingCount)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnSettings = findViewById(R.id.btnSettings)
        bottomNav = findViewById(R.id.bottom_nav)

        tabBlogs = findViewById(R.id.tabBlogs)
        tabSaved = findViewById(R.id.tabSaved)
        tvTabBlogs = findViewById(R.id.tvTabBlogs)
        tvTabSaved = findViewById(R.id.tvTabSaved)
        indicatorBlogs = findViewById(R.id.indicatorBlogs)
        indicatorSaved = findViewById(R.id.indicatorSaved)

        rvProfileBlogs = findViewById(R.id.rvProfileBlogs)
        tvNoPosts = findViewById(R.id.tvNoPosts)

        bottomNav.selectedItemId = R.id.nav_account

        setupRecyclerView()
        setupTabs()
        setupSettingsAndEdit()
        setupBottomNavigation()

        loadUserProfile()
        loadUserBlogs()
        loadSavedBlogs()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_account
        loadUserProfile()
        loadUserBlogs()
        loadSavedBlogs()
    }

    private fun setupRecyclerView() {
        val currentUid = auth.currentUser?.uid ?: ""
        adapter = BlogRecyclerAdapter(
            context = this,
            blogs = displayedList,
            currentUserId = currentUid,
            followedUserIds = followedAuthorIds,
            onFollowClick = { blog, _ ->
                handleFollowToggle(blog.authorId, blog.author)
            },
            onLikeClick = { blog, pos -> handleLike(blog, pos) },
            onCommentClick = {},
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
                if (isSavedTab && !blog.isSaved) {
                    savedBlogsList.removeAll { it.id == blog.id }
                    updateDisplayedList()
                }
            },
            onEditClick = { blog ->
                val intent = Intent(this, add_data::class.java).apply {
                    putExtra("DOC_ID", blog.id)
                    putExtra("TITLE", blog.title)
                    putExtra("CONTENT", blog.content)
                    putExtra("CATEGORY", blog.category)
                    putExtra("IMAGE_URL", blog.imageUrl)
                }
                startActivity(intent)
            },
            onDeleteClick = { blog ->
                db.collection("blogs").document(blog.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Blog deleted", Toast.LENGTH_SHORT).show()
                        userBlogsList.removeAll { it.id == blog.id }
                        updateDisplayedList()
                    }
            },
            onBlogClick = { blog ->
                showBlogReaderDialog(blog)
            }
        )
        rvProfileBlogs.layoutManager = LinearLayoutManager(this)
        rvProfileBlogs.isNestedScrollingEnabled = false
        rvProfileBlogs.adapter = adapter
    }

    private fun setupTabs() {
        tabBlogs.setOnClickListener {
            if (isSavedTab) {
                isSavedTab = false
                tvTabBlogs.setTextColor(getColor(R.color.primary_text))
                indicatorBlogs.setBackgroundResource(R.drawable.bg_primary_button)
                tvTabSaved.setTextColor(getColor(R.color.secondary_text))
                indicatorSaved.setBackgroundColor(getColor(android.R.color.transparent))
                updateDisplayedList()
            } else {
                loadUserBlogs()
            }
        }

        tabSaved.setOnClickListener {
            if (!isSavedTab) {
                isSavedTab = true
                tvTabSaved.setTextColor(getColor(R.color.primary_text))
                indicatorSaved.setBackgroundResource(R.drawable.bg_primary_button)
                tvTabBlogs.setTextColor(getColor(R.color.secondary_text))
                indicatorBlogs.setBackgroundColor(getColor(android.R.color.transparent))
                loadSavedBlogs()
                updateDisplayedList()
            } else {
                loadSavedBlogs()
            }
        }
    }

    private fun setupSettingsAndEdit() {
        // Tap avatar to change photo
        profileAvatar.setOnClickListener {
            pfpPickerLauncher.launch("image/*")
        }

        // Open Settings Screen on clicking settings icon or Edit Profile
        val openSettings = View.OnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSettings.setOnClickListener(openSettings)
        btnEditProfile.setOnClickListener(openSettings)
    }

    private fun uploadProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        Toast.makeText(this, "Uploading new avatar...", Toast.LENGTH_SHORT).show()

        val storageRef = storage.reference.child("profile_pictures/$uid.jpg")
        storageRef.putFile(uri)
            .continueWithTask { storageRef.downloadUrl }
            .addOnSuccessListener { downloadUrl ->
                val urlString = downloadUrl.toString()
                db.collection("users").document(uid).set(mapOf("photoUrl" to urlString), SetOptions.merge())

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(downloadUrl)
                    .build()
                auth.currentUser?.updateProfile(profileUpdates)

                profileAvatar.load(urlString) {
                    crossfade(true)
                }
                Toast.makeText(this, "Profile picture updated! 📸", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to upload: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    nameText.text = doc.getString("name") ?: (auth.currentUser?.displayName ?: "User")
                    val username = doc.getString("username") ?: (auth.currentUser?.email?.substringBefore("@") ?: "user")
                    val bio = doc.getString("bio") ?: "Creator"
                    userHandleText.text = if (bio.isNotEmpty()) "@$username • $bio" else "@$username"
                    ageText.text = doc.get("age")?.toString() ?: "—"
                    genderText.text = doc.getString("gender") ?: "Not specified"
                    addressText.text = doc.getString("address") ?: "—"

                    val posts = doc.getLong("postsCount") ?: 0L
                    postsCountText.text = posts.toString()
                    followersCountText.text = (doc.getLong("followersCount") ?: 0L).toString()
                    followingCountText.text = (doc.getLong("followingCount") ?: 0L).toString()

                    val pfpUrl = doc.getString("photoUrl") ?: doc.getString("pfpUrl") ?: ""
                    if (pfpUrl.isNotEmpty()) {
                        profileAvatar.load(pfpUrl) {
                            crossfade(true)
                        }
                    }
                } else {
                    nameText.text = auth.currentUser?.displayName ?: "Inkora User"
                    userHandleText.text = "@${auth.currentUser?.email?.substringBefore("@") ?: "user"}"
                }

                // Load following IDs
                db.collection("users").document(uid).collection("following").get()
                    .addOnSuccessListener { followingSnap ->
                        followedAuthorIds.clear()
                        for (fDoc in followingSnap.documents) {
                            followedAuthorIds.add(fDoc.id)
                        }
                        adapter.updateFollowedUsers(followedAuthorIds)
                    }
            }
    }

    private fun loadUserBlogs() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("blogs")
            .whereEqualTo("authorId", uid)
            .get()
            .addOnSuccessListener { result ->
                userBlogsList.clear()
                val currentUid = auth.currentUser?.uid ?: ""
                for (doc in result) {
                    val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val isLiked = currentUid.isNotEmpty() && likedBy.contains(currentUid)
                    val blog = Blog(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        category = doc.getString("category") ?: "Tech",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        author = doc.getString("author") ?: "You",
                        authorId = uid,
                        authorPhotoUrl = doc.getString("authorPhotoUrl") ?: (auth.currentUser?.photoUrl?.toString() ?: ""),
                        likesCount = doc.getLong("likesCount") ?: likedBy.size.toLong(),
                        commentsCount = doc.getLong("commentsCount") ?: 0L,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        likedBy = likedBy,
                        isLiked = isLiked
                    )
                    userBlogsList.add(blog)
                }
                postsCountText.text = userBlogsList.size.toString()
                updateDisplayedList()
            }
    }

    private fun loadSavedBlogs() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("saved_posts").get()
            .addOnSuccessListener { snap ->
                savedBlogsList.clear()
                val currentUid = auth.currentUser?.uid ?: ""
                val legacyDocIds = mutableListOf<String>()

                for (doc in snap.documents) {
                    val title = doc.getString("title")
                    if (!title.isNullOrEmpty()) {
                        val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val isLiked = currentUid.isNotEmpty() && likedBy.contains(currentUid)
                        val blog = Blog(
                            id = doc.id,
                            title = title,
                            content = doc.getString("content") ?: "",
                            category = doc.getString("category") ?: "Tech",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            author = doc.getString("author") ?: "Creator",
                            authorId = doc.getString("authorId") ?: "",
                            authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
                            likesCount = doc.getLong("likesCount") ?: 0L,
                            commentsCount = doc.getLong("commentsCount") ?: 0L,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            likedBy = likedBy,
                            isLiked = isLiked,
                            isSaved = true
                        )
                        savedBlogsList.add(blog)
                    } else {
                        legacyDocIds.add(doc.id)
                    }
                }

                savedBlogsList.sortByDescending { it.timestamp }
                if (isSavedTab) updateDisplayedList()

                // Fetch any legacy bookmarks from blogs collection
                for (id in legacyDocIds) {
                    db.collection("blogs").document(id).get().addOnSuccessListener { bDoc ->
                        if (bDoc.exists()) {
                            val likedBy = (bDoc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val isLiked = currentUid.isNotEmpty() && likedBy.contains(currentUid)
                            val blog = Blog(
                                id = bDoc.id,
                                title = bDoc.getString("title") ?: "",
                                content = bDoc.getString("content") ?: "",
                                category = bDoc.getString("category") ?: "Tech",
                                imageUrl = bDoc.getString("imageUrl") ?: "",
                                author = bDoc.getString("author") ?: "Creator",
                                authorId = bDoc.getString("authorId") ?: "",
                                authorPhotoUrl = bDoc.getString("authorPhotoUrl") ?: "",
                                likesCount = bDoc.getLong("likesCount") ?: 0L,
                                commentsCount = bDoc.getLong("commentsCount") ?: 0L,
                                timestamp = bDoc.getLong("timestamp") ?: System.currentTimeMillis(),
                                likedBy = likedBy,
                                isLiked = isLiked,
                                isSaved = true
                            )
                            if (savedBlogsList.none { it.id == blog.id }) {
                                savedBlogsList.add(blog)
                                savedBlogsList.sortByDescending { it.timestamp }
                                if (isSavedTab) updateDisplayedList()
                            }
                        }
                    }
                }
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

        if (isSavedTab) {
            savedBlogsList.find { it.id == blog.id }?.let {
                val idx = savedBlogsList.indexOf(it)
                if (idx != -1) savedBlogsList[idx] = updatedBlog
            }
        } else {
            userBlogsList.find { it.id == blog.id }?.let {
                val idx = userBlogsList.indexOf(it)
                if (idx != -1) userBlogsList[idx] = updatedBlog
            }
        }
        displayedList[position] = updatedBlog
        adapter.notifyItemChanged(position)

        val docRef = db.collection("blogs").document(blog.id)
        if (newIsLiked) {
            docRef.update(
                "likesCount", FieldValue.increment(1),
                "likedBy", FieldValue.arrayUnion(currentUid)
            )
        } else {
            docRef.update(
                "likesCount", FieldValue.increment(-1),
                "likedBy", FieldValue.arrayRemove(currentUid)
            )
        }
    }

    private fun updateDisplayedList() {
        displayedList.clear()
        if (isSavedTab) {
            displayedList.addAll(savedBlogsList)
            tvNoPosts.text = "No saved posts yet."
        } else {
            displayedList.addAll(userBlogsList)
            tvNoPosts.text = "You haven't written any blogs yet."
        }
        adapter.notifyDataSetChanged()
        val isEmpty = displayedList.isEmpty()
        tvNoPosts.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvProfileBlogs.visibility = if (isEmpty) View.GONE else View.VISIBLE
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

        adapter.updateFollowedUsers(followedAuthorIds)
        // Refresh following count locally
        val currentFollowing = followingCountText.text.toString().toLongOrNull() ?: 0L
        followingCountText.text = maxOf(0L, if (isCurrentlyFollowed) currentFollowing - 1 else currentFollowing + 1).toString()
        onDone?.invoke()

        val msg = if (isCurrentlyFollowed) "Unfollowed $authorName" else "Following $authorName"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        batch.commit().addOnFailureListener { e ->
            if (isCurrentlyFollowed) {
                followedAuthorIds.add(authorId)
            } else {
                followedAuthorIds.remove(authorId)
            }
            adapter.updateFollowedUsers(followedAuthorIds)
            loadUserProfile()
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
                    startActivity(Intent(this, home::class.java))
                    finish()
                    true
                }
                R.id.nav_discover -> {
                    startActivity(Intent(this, discover::class.java))
                    finish()
                    true
                }
                R.id.nav_add -> {
                    startActivity(Intent(this, add_data::class.java))
                    finish()
                    true
                }
                R.id.nav_account -> true
                else -> false
            }
        }
    }
}
