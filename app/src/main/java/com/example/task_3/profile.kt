package com.example.task_3

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class profile : AppCompatActivity() {

    private lateinit var ivProfileBanner: ImageView
    private lateinit var btnChangeCover: TextView
    private lateinit var profileAvatar: ImageView
    private lateinit var nameText: TextView
    private lateinit var userHandleText: TextView
    private lateinit var userBioText: TextView
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

    private val pfpPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePicture(uri)
        }
    }

    private val coverPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadCoverPicture(uri)
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

        ivProfileBanner = findViewById(R.id.ivProfileBanner)
        btnChangeCover = findViewById(R.id.btnChangeCover)
        profileAvatar = findViewById(R.id.profileAvatar)
        nameText = findViewById(R.id.name)
        userHandleText = findViewById(R.id.tvUserHandle)
        userBioText = findViewById(R.id.tvUserBio)
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
            onAuthorClick = { blog ->
                openUserProfile(blog.authorId, blog.author)
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
                        val newCount = userBlogsList.size
                        postsCountText.text = newCount.toString()
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            db.collection("users").document(uid).set(
                                mapOf("postsCount" to newCount.toLong()),
                                SetOptions.merge()
                            )
                        }
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

        // Tap Cover button to change profile background image
        btnChangeCover.setOnClickListener {
            coverPickerLauncher.launch("image/*")
        }

        // Open Settings Screen on clicking settings icon or Edit Profile
        val openSettings = View.OnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSettings.setOnClickListener(openSettings)
        btnEditProfile.setOnClickListener(openSettings)

        // Posts click: Switch to stories/blogs tab
        val layoutPosts = findViewById<View>(R.id.layoutPosts)
        val openStories = View.OnClickListener {
            if (isSavedTab) {
                tabBlogs.performClick()
            }
        }
        layoutPosts?.setOnClickListener(openStories)
        postsCountText.setOnClickListener(openStories)

        // Followers & Following clicks: Show centered popup dialog in middle of screen
        val layoutFollowers = findViewById<View>(R.id.layoutFollowers)
        val layoutFollowing = findViewById<View>(R.id.layoutFollowing)

        val openFollowers = View.OnClickListener {
            showUsersDialog(isFollowingList = false)
        }
        val openFollowing = View.OnClickListener {
            showUsersDialog(isFollowingList = true)
        }

        layoutFollowers?.setOnClickListener(openFollowers)
        followersCountText.setOnClickListener(openFollowers)
        layoutFollowing?.setOnClickListener(openFollowing)
        followingCountText.setOnClickListener(openFollowing)
    }

    private fun uploadProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        Toast.makeText(this, "Uploading new avatar...", Toast.LENGTH_SHORT).show()

        CloudinaryHelper.uploadImage(
            imageUri = uri,
            folder = "inkora/profile_pictures",
            onSuccess = { urlString ->
                CloudinaryHelper.saveProfilePhotoToFirebase(
                    uid = uid,
                    imageUrl = urlString,
                    auth = auth,
                    db = db,
                    onSuccess = {
                        if (isFinishing || isDestroyed) return@saveProfilePhotoToFirebase
                        profileAvatar.load(urlString) {
                            crossfade(true)
                        }
                        Toast.makeText(this, "Profile picture updated! 📸", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        if (isFinishing || isDestroyed) return@saveProfilePhotoToFirebase
                        Toast.makeText(this, "Failed to save avatar URL: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onError = { errorMessage ->
                if (isFinishing || isDestroyed) return@uploadImage
                Toast.makeText(this, "Failed to upload: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun uploadCoverPicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        Toast.makeText(this, "Uploading cover background...", Toast.LENGTH_SHORT).show()

        CloudinaryHelper.uploadImage(
            imageUri = uri,
            folder = "inkora/profile_covers",
            onSuccess = { urlString ->
                CloudinaryHelper.saveCoverPhotoToFirebase(
                    uid = uid,
                    imageUrl = urlString,
                    db = db,
                    onSuccess = {
                        if (isFinishing || isDestroyed) return@saveCoverPhotoToFirebase
                        ivProfileBanner.load(urlString) {
                            crossfade(true)
                        }
                        Toast.makeText(this, "Profile background updated!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        if (isFinishing || isDestroyed) return@saveCoverPhotoToFirebase
                        Toast.makeText(this, "Failed to save background URL: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onError = { errorMessage ->
                if (isFinishing || isDestroyed) return@uploadImage
                Toast.makeText(this, "Failed to upload background: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        val source = inkora.preferredSource(this)

        db.collection("users").document(uid)
            .get(source)
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    nameText.text = (doc.getString("name") ?: auth.currentUser?.displayName)
                        ?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator"
                    val username = doc.getString("username")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "user"
                    val bio = doc.getString("bio") ?: "Creator"
                    userHandleText.text = "@$username"
                    if (bio.isNotEmpty()) {
                        userBioText.visibility = View.VISIBLE
                        userBioText.text = bio
                    } else {
                        userBioText.visibility = View.GONE
                    }

                    val posts = doc.getLong("postsCount")
                    if (posts != null && userBlogsList.isEmpty()) {
                        postsCountText.text = posts.toString()
                    } else if (userBlogsList.isNotEmpty()) {
                        postsCountText.text = userBlogsList.size.toString()
                    }
                    followersCountText.text = (doc.getLong("followersCount") ?: 0L).toString()
                    followingCountText.text = (doc.getLong("followingCount") ?: 0L).toString()

                    val pfpUrl = doc.getString("photoUrl") ?: doc.getString("pfpUrl") ?: ""
                    if (pfpUrl.isNotEmpty()) {
                        profileAvatar.load(pfpUrl) {
                            crossfade(true)
                        }
                    }

                    val coverUrl = doc.getString("coverPhotoUrl") ?: ""
                    if (coverUrl.isNotEmpty()) {
                        ivProfileBanner.load(coverUrl) {
                            crossfade(true)
                        }
                    }
                } else {
                    nameText.text = auth.currentUser?.displayName?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Inkora User"
                    userHandleText.text = "@user"
                    userBioText.visibility = View.GONE
                }

                // Load following IDs
                db.collection("users").document(uid).collection("following").get(source)
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
        val source = inkora.preferredSource(this)

        db.collection("blogs")
            .whereEqualTo("authorId", uid)
            .get(source)
            .addOnSuccessListener { snap1 ->
                val blogDocs = mutableMapOf<String, DocumentSnapshot>()
                for (doc in snap1.documents) {
                    blogDocs[doc.id] = doc
                }

                // Also check legacy blogs with "uid" field
                db.collection("blogs")
                    .whereEqualTo("uid", uid)
                    .get(source)
                    .addOnSuccessListener { snap2 ->
                        for (doc in snap2.documents) {
                            blogDocs[doc.id] = doc
                        }
                        parseAndDisplayUserBlogs(blogDocs.values, uid)
                    }
                    .addOnFailureListener {
                        parseAndDisplayUserBlogs(blogDocs.values, uid)
                    }
            }
            .addOnFailureListener {
                db.collection("blogs")
                    .whereEqualTo("uid", uid)
                    .get(source)
                    .addOnSuccessListener { snap2 ->
                        val blogDocs = snap2.documents.associateBy { it.id }
                        parseAndDisplayUserBlogs(blogDocs.values, uid)
                    }
            }
    }

    private fun parseAndDisplayUserBlogs(docs: Collection<DocumentSnapshot>, uid: String) {
        userBlogsList.clear()
        for (doc in docs) {
            val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val isLiked = uid.isNotEmpty() && likedBy.contains(uid)
            val blog = Blog(
                id = doc.id,
                title = doc.getString("title") ?: "",
                content = doc.getString("content") ?: "",
                category = doc.getString("category") ?: "Tech",
                imageUrl = doc.getString("imageUrl") ?: "",
                author = doc.getString("author")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "You",
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
        userBlogsList.sortByDescending { it.timestamp }

        val count = userBlogsList.size
        postsCountText.text = count.toString()

        // Sync count to users document in Firestore
        db.collection("users").document(uid).set(
            mapOf("postsCount" to count.toLong()),
            SetOptions.merge()
        )

        updateDisplayedList()
    }

    private fun loadSavedBlogs() {
        val uid = auth.currentUser?.uid ?: return
        val source = inkora.preferredSource(this)
        db.collection("users").document(uid).collection("saved_posts").get(source)
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
                            author = doc.getString("author")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator",
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
                                author = bDoc.getString("author")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator",
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
                if (isSavedTab) {
                    savedBlogsList.find { it.id == blog.id }?.let {
                        val idx = savedBlogsList.indexOf(it)
                        if (idx != -1) savedBlogsList[idx] = blog
                    }
                } else {
                    userBlogsList.find { it.id == blog.id }?.let {
                        val idx = userBlogsList.indexOf(it)
                        if (idx != -1) userBlogsList[idx] = blog
                    }
                }
                displayedList[position] = blog
                adapter.notifyItemChanged(position)
                Toast.makeText(this, "Failed to update like: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

    private fun showUsersDialog(isFollowingList: Boolean) {
        val currentUid = auth.currentUser?.uid ?: return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_user_list)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvDialogSubtitle)
        val btnClose = dialog.findViewById<TextView>(R.id.btnDialogClose)
        val etSearch = dialog.findViewById<EditText>(R.id.etDialogSearch)
        val pbLoading = dialog.findViewById<ProgressBar>(R.id.pbDialogLoading)
        val tvEmpty = dialog.findViewById<TextView>(R.id.tvDialogEmpty)
        val rvUsers = dialog.findViewById<RecyclerView>(R.id.rvDialogUsers)

        tvTitle.text = if (isFollowingList) "Following" else "Followers"
        tvSubtitle.text = "0"
        etSearch.hint = if (isFollowingList) "Search following..." else "Search followers..."

        pbLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvUsers.visibility = View.GONE

        val allDialogUsers = mutableListOf<UserItem>()
        val displayedDialogUsers = mutableListOf<UserItem>()

        fun applyFilter(query: String) {
            displayedDialogUsers.clear()
            val filtered = if (query.isEmpty()) {
                allDialogUsers
            } else {
                allDialogUsers.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.username.contains(query, ignoreCase = true)
                }
            }
            displayedDialogUsers.addAll(filtered)
            dialog.findViewById<RecyclerView>(R.id.rvDialogUsers)?.adapter?.notifyDataSetChanged()
            tvEmpty.visibility = if (displayedDialogUsers.isEmpty()) View.VISIBLE else View.GONE
            if (displayedDialogUsers.isEmpty()) {
                tvEmpty.text = if (query.isEmpty()) {
                    if (isFollowingList) "You are not following anyone yet.\nExplore Discover to find creators!"
                    else "No followers yet.\nShare great stories to build your audience!"
                } else {
                    "No results matching \"$query\""
                }
            }
        }

        lateinit var dialogAdapter: UserSearchAdapter
        dialogAdapter = UserSearchAdapter(
            context = this,
            users = displayedDialogUsers,
            onFollowClick = { user, position ->
                handleFollowToggle(user.uid, user.name) {
                    val isNowFollowing = followedAuthorIds.contains(user.uid)
                    val newStatus = if (isNowFollowing) "Following" else "Follow"
                    user.followStatus = newStatus
                    val targetIdx = allDialogUsers.indexOfFirst { it.uid == user.uid }
                    if (targetIdx != -1) {
                        allDialogUsers[targetIdx].followStatus = newStatus
                    }
                    dialogAdapter.notifyItemChanged(position)
                    if (isFollowingList) {
                        val count = allDialogUsers.count { it.followStatus == "Following" }
                        tvSubtitle.text = count.toString()
                    }
                }
                val isNowFollowing = followedAuthorIds.contains(user.uid)
                val newStatus = if (isNowFollowing) "Following" else "Follow"
                user.followStatus = newStatus
                val targetIdx = allDialogUsers.indexOfFirst { it.uid == user.uid }
                if (targetIdx != -1) {
                    allDialogUsers[targetIdx].followStatus = newStatus
                }
                dialogAdapter.notifyItemChanged(position)
                if (isFollowingList) {
                    val count = allDialogUsers.count { it.followStatus == "Following" }
                    tvSubtitle.text = count.toString()
                }
            },
            onUserClick = { user ->
                dialog.dismiss()
                openUserProfile(user.uid, user.name)
            }
        )

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = dialogAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val collectionName = if (isFollowingList) "following" else "followers"
        db.collection("users").document(currentUid).collection(collectionName).get()
            .addOnSuccessListener { snapshot ->
                val userIds = snapshot.documents.map { it.id }.filter { it.isNotEmpty() && it != currentUid }
                tvSubtitle.text = userIds.size.toString()

                if (userIds.isEmpty()) {
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = if (isFollowingList) {
                        "You are not following anyone yet.\nExplore Discover to find creators!"
                    } else {
                        "No followers yet.\nShare great stories to build your audience!"
                    }
                    return@addOnSuccessListener
                }

                val tasks = userIds.map { id -> db.collection("users").document(id).get() }
                Tasks.whenAllComplete(tasks).addOnSuccessListener {
                    allDialogUsers.clear()
                    for (task in tasks) {
                        if (task.isSuccessful) {
                            val doc = task.result as? DocumentSnapshot
                            if (doc != null && doc.exists()) {
                                val uid = doc.id
                                val name = doc.getString("name")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "User"
                                val username = doc.getString("username")?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "creator"
                                val bio = doc.getString("bio") ?: ""
                                val photoUrl = doc.getString("photoUrl") ?: doc.getString("pfpUrl") ?: ""
                                val isFollowing = followedAuthorIds.contains(uid)
                                val status = if (isFollowing) "Following" else "Follow"
                                allDialogUsers.add(UserItem(uid, name, username, bio, status, photoUrl))
                            }
                        }
                    }

                    pbLoading.visibility = View.GONE
                    if (allDialogUsers.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        tvEmpty.text = if (isFollowingList) "No following users found." else "No followers found."
                    } else {
                        rvUsers.visibility = View.VISIBLE
                        applyFilter(etSearch.text.toString().trim())
                    }
                }.addOnFailureListener { e ->
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Failed to load profiles: ${e.localizedMessage}"
                }
            }
            .addOnFailureListener { e ->
                pbLoading.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Failed to load: ${e.localizedMessage}"
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
            return
        }
        val intent = Intent(this, UserProfileActivity::class.java).apply {
            putExtra("USER_ID", authorId)
            putExtra("USER_NAME", authorName)
        }
        startActivity(intent)
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
