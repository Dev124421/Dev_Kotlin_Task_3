package com.example.task_3

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class UserProfileActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var tvTopBarTitle: TextView
    private lateinit var ivBanner: ImageView
    private lateinit var ivAvatar: ShapeableImageView
    private lateinit var tvName: TextView
    private lateinit var tvBio: TextView
    private lateinit var btnFollow: AppCompatButton

    private lateinit var layoutPosts: View
    private lateinit var layoutFollowers: View
    private lateinit var layoutFollowing: View
    private lateinit var tvPostsCount: TextView
    private lateinit var tvFollowersCount: TextView
    private lateinit var tvFollowingCount: TextView

    private lateinit var tvStoriesCountBadge: TextView
    private lateinit var pbBlogsLoading: ProgressBar
    private lateinit var tvNoPosts: TextView
    private lateinit var rvUserBlogs: RecyclerView
    private lateinit var scrollView: NestedScrollView

    private val userBlogsList = mutableListOf<Blog>()
    private val followedAuthorIds = mutableSetOf<String>()
    private lateinit var adapter: BlogRecyclerAdapter

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var targetUserId: String = ""
    private var targetUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        targetUserId = intent.getStringExtra("USER_ID") ?: ""
        targetUserName = intent.getStringExtra("USER_NAME") ?: ""

        val currentUid = auth.currentUser?.uid ?: ""
        if (targetUserId.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // If the user visits their own profile, navigate to personal profile
        if (targetUserId == currentUid) {
            startActivity(Intent(this, profile::class.java))
            finish()
            return
        }

        bindViews()
        setupRecyclerView()
        setupListeners()

        loadFollowedAuthorIds {
            loadUserProfile()
            loadUserBlogs()
        }
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvTopBarTitle = findViewById(R.id.tvTopBarTitle)
        ivBanner = findViewById(R.id.ivUserProfileBanner)
        ivAvatar = findViewById(R.id.ivUserProfileAvatar)
        tvName = findViewById(R.id.tvUserName)
        tvBio = findViewById(R.id.tvUserBio)
        btnFollow = findViewById(R.id.btnUserProfileFollow)

        layoutPosts = findViewById(R.id.layoutUserPosts)
        layoutFollowers = findViewById(R.id.layoutUserFollowers)
        layoutFollowing = findViewById(R.id.layoutUserFollowing)
        tvPostsCount = findViewById(R.id.tvUserPostsCount)
        tvFollowersCount = findViewById(R.id.tvUserFollowersCount)
        tvFollowingCount = findViewById(R.id.tvUserFollowingCount)

        tvStoriesCountBadge = findViewById(R.id.tvStoriesCountBadge)
        pbBlogsLoading = findViewById(R.id.pbUserBlogsLoading)
        tvNoPosts = findViewById(R.id.tvUserNoPosts)
        rvUserBlogs = findViewById(R.id.rvUserBlogs)
        scrollView = findViewById(R.id.userProfileScrollView)

        val safeInitialName = targetUserName.takeIf { it.isNotBlank() && !it.contains("@") } ?: ""
        tvTopBarTitle.text = safeInitialName.ifEmpty { "Creator Profile" }
        tvName.text = safeInitialName.ifEmpty { "Loading..." }
    }

    private fun setupRecyclerView() {
        val currentUid = auth.currentUser?.uid ?: ""
        adapter = BlogRecyclerAdapter(
            context = this,
            blogs = userBlogsList,
            currentUserId = currentUid,
            followedUserIds = followedAuthorIds,
            onFollowClick = { blog, _ ->
                handleFollowToggle(blog.authorId, blog.author)
            },
            onAuthorClick = { blog ->
                if (blog.authorId.isNotEmpty() && blog.authorId != targetUserId) {
                    val currentUid = auth.currentUser?.uid ?: ""
                    if (blog.authorId == currentUid) {
                        startActivity(Intent(this, profile::class.java))
                    } else {
                        val intent = Intent(this, UserProfileActivity::class.java).apply {
                            putExtra("USER_ID", blog.authorId)
                            putExtra("USER_NAME", blog.author)
                        }
                        startActivity(intent)
                    }
                }
            },
            onLikeClick = { blog, pos -> handleLike(blog, pos) },
            onCommentClick = { blog ->
                CommentAdapter.showCommentsBottomSheet(this, blog) { newCount ->
                    userBlogsList.find { it.id == blog.id }?.commentsCount = newCount
                    adapter.notifyDataSetChanged()
                }
            },
            onSaveClick = { blog, pos -> handleSave(blog, pos) },
            onEditClick = {},
            onDeleteClick = {},
            onBlogClick = { blog -> showBlogReaderDialog(blog) }
        )
        rvUserBlogs.layoutManager = LinearLayoutManager(this)
        rvUserBlogs.isNestedScrollingEnabled = false
        rvUserBlogs.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnFollow.setOnClickListener {
            handleFollowToggle(targetUserId, targetUserName)
        }

        // Tap Followers / Following to open centered modal dialog
        layoutFollowers.setOnClickListener {
            showUsersDialog(isFollowingList = false)
        }
        layoutFollowing.setOnClickListener {
            showUsersDialog(isFollowingList = true)
        }

        // Tap Posts to smooth scroll down to stories
        layoutPosts.setOnClickListener {
            val heroBottom = findViewById<View>(R.id.userHeroContainer).bottom
            scrollView.smoothScrollTo(0, heroBottom)
        }
    }

    private fun updateFollowButtonState() {
        val isFollowed = followedAuthorIds.contains(targetUserId)
        if (isFollowed) {
            btnFollow.text = "Following"
            btnFollow.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
            btnFollow.setTextColor(getColor(R.color.secondary_text))
        } else {
            btnFollow.text = "+ Follow"
            btnFollow.setBackgroundResource(R.drawable.bg_primary_button)
            btnFollow.setTextColor(getColor(R.color.white))
        }
    }

    private fun loadFollowedAuthorIds(onComplete: () -> Unit) {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrEmpty()) {
            onComplete()
            return
        }

        db.collection("users").document(currentUid).collection("following")
            .get(inkora.preferredSource(this))
            .addOnSuccessListener { snapshot ->
                followedAuthorIds.clear()
                for (doc in snapshot.documents) {
                    followedAuthorIds.add(doc.id)
                }
                adapter.updateFollowedUsers(followedAuthorIds)
                updateFollowButtonState()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    private fun loadUserProfile() {
        db.collection("users").document(targetUserId).get(inkora.preferredSource(this))
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = (doc.getString("name") ?: targetUserName)
                        .takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator"
                    val bio = doc.getString("bio") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: doc.getString("pfpUrl") ?: ""
                    val coverUrl = doc.getString("coverPhotoUrl") ?: ""

                    tvName.text = name
                    tvTopBarTitle.text = name
                    if (bio.isNotEmpty()) {
                        tvBio.visibility = View.VISIBLE
                        tvBio.text = bio
                    } else {
                        tvBio.visibility = View.GONE
                    }

                    if (photoUrl.isNotEmpty()) {
                        ivAvatar.load(photoUrl) {
                            crossfade(true)
                        }
                    }

                    if (coverUrl.isNotEmpty()) {
                        ivBanner.load(coverUrl) {
                            crossfade(true)
                        }
                    }

                    tvFollowersCount.text = (doc.getLong("followersCount") ?: 0L).toString()
                    tvFollowingCount.text = (doc.getLong("followingCount") ?: 0L).toString()
                    tvPostsCount.text = (doc.getLong("postsCount") ?: 0L).toString()
                }
            }
    }

    private fun loadUserBlogs() {
        pbBlogsLoading.visibility = View.VISIBLE
        tvNoPosts.visibility = View.GONE
        rvUserBlogs.visibility = View.GONE

        val source = inkora.preferredSource(this)
        db.collection("blogs")
            .whereEqualTo("authorId", targetUserId)
            .get(source)
            .addOnSuccessListener { snap1 ->
                val blogDocs = mutableMapOf<String, DocumentSnapshot>()
                for (doc in snap1.documents) {
                    blogDocs[doc.id] = doc
                }

                // Check legacy posts where author ID field was "uid"
                db.collection("blogs")
                    .whereEqualTo("uid", targetUserId)
                    .get(source)
                    .addOnSuccessListener { snap2 ->
                        for (doc in snap2.documents) {
                            blogDocs[doc.id] = doc
                        }
                        parseAndDisplayBlogs(blogDocs.values)
                    }
                    .addOnFailureListener {
                        parseAndDisplayBlogs(blogDocs.values)
                    }
            }
            .addOnFailureListener {
                pbBlogsLoading.visibility = View.GONE
                tvNoPosts.visibility = View.VISIBLE
                tvNoPosts.text = "Failed to load stories: ${it.localizedMessage}"
            }
    }

    private fun parseAndDisplayBlogs(docs: Collection<DocumentSnapshot>) {
        pbBlogsLoading.visibility = View.GONE
        userBlogsList.clear()

        val currentUid = auth.currentUser?.uid ?: ""
        for (doc in docs) {
            val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val isLiked = currentUid.isNotEmpty() && likedBy.contains(currentUid)
            val blog = Blog(
                id = doc.id,
                title = doc.getString("title") ?: "",
                content = doc.getString("content") ?: "",
                category = doc.getString("category") ?: "Tech",
                imageUrl = doc.getString("imageUrl") ?: "",
                author = (doc.getString("author") ?: targetUserName)
                    .takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator",
                authorId = targetUserId,
                authorPhotoUrl = doc.getString("authorPhotoUrl") ?: "",
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
        tvPostsCount.text = count.toString()
        tvStoriesCountBadge.text = count.toString()

        if (userBlogsList.isEmpty()) {
            tvNoPosts.visibility = View.VISIBLE
            rvUserBlogs.visibility = View.GONE
        } else {
            tvNoPosts.visibility = View.GONE
            rvUserBlogs.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
        }
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

        updateFollowButtonState()
        adapter.updateFollowedUsers(followedAuthorIds)

        // If toggling the creator of this profile, adjust the followers count locally
        if (authorId == targetUserId) {
            val currentFollowers = tvFollowersCount.text.toString().toLongOrNull() ?: 0L
            tvFollowersCount.text = maxOf(0L, if (isCurrentlyFollowed) currentFollowers - 1 else currentFollowers + 1).toString()
        }
        onDone?.invoke()

        val msg = if (isCurrentlyFollowed) "Unfollowed $authorName" else "Following $authorName"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        batch.commit().addOnFailureListener { e ->
            if (isCurrentlyFollowed) {
                followedAuthorIds.add(authorId)
            } else {
                followedAuthorIds.remove(authorId)
            }
            updateFollowButtonState()
            adapter.updateFollowedUsers(followedAuthorIds)
            loadUserProfile()
            onDone?.invoke()
            Toast.makeText(this, "Failed to update follow: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLike(blog: Blog, position: Int) {
        val currentUid = auth.currentUser?.uid ?: return
        val newIsLiked = !blog.isLiked
        val updatedLikedBy = blog.likedBy.toMutableList()
        val change = if (newIsLiked) 1L else -1L

        if (newIsLiked) {
            if (!updatedLikedBy.contains(currentUid)) updatedLikedBy.add(currentUid)
        } else {
            updatedLikedBy.remove(currentUid)
        }

        val newLikesCount = maxOf(0L, blog.likesCount + change)
        val updatedBlog = blog.copy(
            likesCount = newLikesCount,
            likedBy = updatedLikedBy,
            isLiked = newIsLiked
        )

        userBlogsList[position] = updatedBlog
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
                userBlogsList[position] = blog
                adapter.notifyItemChanged(position)
                Toast.makeText(this, "Failed to update like: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleSave(blog: Blog, position: Int) {
        val uid = auth.currentUser?.uid ?: return
        blog.isSaved = !blog.isSaved
        adapter.notifyItemChanged(position)

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
        val msg = if (blog.isSaved) "Saved to bookmarks" else "Removed from bookmarks"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showUsersDialog(isFollowingList: Boolean) {
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
                    if (isFollowingList) "Not following anyone yet."
                    else "No followers yet."
                } else {
                    "No results matching \"$query\""
                }
            }
        }

        lateinit var dialogAdapter: UserSearchAdapter
        dialogAdapter = UserSearchAdapter(this, displayedDialogUsers,
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
                }
                val isNowFollowing = followedAuthorIds.contains(user.uid)
                val newStatus = if (isNowFollowing) "Following" else "Follow"
                user.followStatus = newStatus
                val targetIdx = allDialogUsers.indexOfFirst { it.uid == user.uid }
                if (targetIdx != -1) {
                    allDialogUsers[targetIdx].followStatus = newStatus
                }
                dialogAdapter.notifyItemChanged(position)
            },
            onUserClick = { clickedUser ->
                if (clickedUser.uid.isNotEmpty() && clickedUser.uid != targetUserId) {
                    dialog.dismiss()
                    val currentUid = auth.currentUser?.uid ?: ""
                    if (clickedUser.uid == currentUid) {
                        startActivity(Intent(this, profile::class.java))
                    } else {
                        val intent = Intent(this, UserProfileActivity::class.java).apply {
                            putExtra("USER_ID", clickedUser.uid)
                            putExtra("USER_NAME", clickedUser.name)
                        }
                        startActivity(intent)
                    }
                }
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
        db.collection("users").document(targetUserId).collection(collectionName).get()
            .addOnSuccessListener { snapshot ->
                val userIds = snapshot.documents.map { it.id }.filter { it.isNotEmpty() && it != targetUserId }
                tvSubtitle.text = userIds.size.toString()

                if (userIds.isEmpty()) {
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = if (isFollowingList) "Not following anyone yet." else "No followers yet."
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

        val openAuthor = View.OnClickListener {
            if (blog.authorId.isNotEmpty() && blog.authorId != targetUserId) {
                dialog.dismiss()
                val currentUid = auth.currentUser?.uid ?: ""
                if (blog.authorId == currentUid) {
                    startActivity(Intent(this, profile::class.java))
                } else {
                    val intent = Intent(this, UserProfileActivity::class.java).apply {
                        putExtra("USER_ID", blog.authorId)
                        putExtra("USER_NAME", blog.author)
                    }
                    startActivity(intent)
                }
            }
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
}
