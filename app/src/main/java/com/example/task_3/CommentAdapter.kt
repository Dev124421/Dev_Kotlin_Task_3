package com.example.task_3

import android.app.Activity
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class CommentAdapter(
    private val comments: List<Comment>,
    private val currentUserId: String = "",
    private val onDeleteLongClick: ((Comment) -> Unit)? = null
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAuthor: TextView = view.findViewById(R.id.tvCommentAuthor)
        val tvTime: TextView = view.findViewById(R.id.tvCommentTime)
        val tvText: TextView = view.findViewById(R.id.tvCommentText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.tvAuthor.text = comment.author
        holder.tvText.text = comment.text

        val relativeTime = DateUtils.getRelativeTimeSpanString(
            comment.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvTime.text = relativeTime

        if (currentUserId.isNotEmpty() && comment.authorId == currentUserId && onDeleteLongClick != null) {
            holder.itemView.setOnLongClickListener {
                onDeleteLongClick.invoke(comment)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
        }
    }

    override fun getItemCount(): Int = comments.size

    companion object {
        fun showCommentsBottomSheet(
            activity: Activity,
            blog: Blog,
            onCountUpdated: (Long) -> Unit
        ) {
            if (blog.id.isEmpty()) return

            val db = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            val currentUid = auth.currentUser?.uid ?: ""

            val dialog = BottomSheetDialog(activity)
            val view = activity.layoutInflater.inflate(R.layout.layout_comment_bottom_sheet, null)
            dialog.setContentView(view)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true

            val tvTitle = view.findViewById<TextView>(R.id.tvCommentsTitle)
            val rvComments = view.findViewById<RecyclerView>(R.id.rvComments)
            val tvNoComments = view.findViewById<TextView>(R.id.tvNoComments)
            val etNewComment = view.findViewById<EditText>(R.id.etNewComment)
            val btnSend = view.findViewById<Button>(R.id.btnSendComment)

            tvTitle.text = "Comments (${blog.commentsCount})"

            val commentsList = mutableListOf<Comment>()
            lateinit var commentAdapter: CommentAdapter

            fun confirmDeleteComment(comment: Comment) {
                AlertDialog.Builder(activity)
                    .setTitle("Delete Comment")
                    .setMessage("Remove this comment?")
                    .setPositiveButton("Delete") { _, _ ->
                        val blogRef = db.collection("blogs").document(blog.id)
                        val commentRef = blogRef.collection("comments").document(comment.id)
                        val batch = db.batch()
                        batch.delete(commentRef)
                        batch.set(blogRef, mapOf("commentsCount" to FieldValue.increment(-1)), SetOptions.merge())
                        batch.commit().addOnFailureListener { e ->
                            Toast.makeText(activity, "Failed to delete: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            commentAdapter = CommentAdapter(
                comments = commentsList,
                currentUserId = currentUid,
                onDeleteLongClick = { comment -> confirmDeleteComment(comment) }
            )
            rvComments.layoutManager = LinearLayoutManager(activity)
            rvComments.adapter = commentAdapter

            var cachedAuthorName = auth.currentUser?.displayName
                ?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator"

            if (currentUid.isNotEmpty()) {
                db.collection("users").document(currentUid)
                    .get(inkora.preferredSource(activity))
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            cachedAuthorName = (doc.getString("name") ?: cachedAuthorName)
                                .takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator"
                        }
                    }
            }

            val commentsRef = db.collection("blogs").document(blog.id).collection("comments")
            val listener: ListenerRegistration = commentsRef
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        commentsList.clear()
                        for (doc in snapshot.documents) {
                            val author = doc.getString("author")
                                ?.takeIf { it.isNotBlank() && !it.contains("@") } ?: "Creator"
                            commentsList.add(
                                Comment(
                                    id = doc.id,
                                    blogId = blog.id,
                                    author = author,
                                    authorId = doc.getString("authorId") ?: "",
                                    text = doc.getString("text") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                )
                            )
                        }
                        commentAdapter.notifyDataSetChanged()

                        val count = commentsList.size.toLong()
                        tvTitle.text = "Comments ($count)"
                        tvNoComments.visibility = if (commentsList.isEmpty()) View.VISIBLE else View.GONE
                        rvComments.visibility = if (commentsList.isEmpty()) View.GONE else View.VISIBLE

                        if (blog.commentsCount != count) {
                            blog.commentsCount = count
                            onCountUpdated(count)
                        }
                    }
                }

            dialog.setOnDismissListener {
                listener.remove()
            }

            btnSend.setOnClickListener {
                if (currentUid.isEmpty()) {
                    Toast.makeText(activity, "Please sign in to comment", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val commentText = etNewComment.text.toString().trim()
                if (commentText.isEmpty()) {
                    etNewComment.error = "Write a comment first"
                    etNewComment.requestFocus()
                    return@setOnClickListener
                }

                btnSend.isEnabled = false
                etNewComment.setText("")

                val blogRef = db.collection("blogs").document(blog.id)
                val newCommentRef = commentsRef.document()
                val commentData = hashMapOf(
                    "blogId" to blog.id,
                    "author" to cachedAuthorName,
                    "authorId" to currentUid,
                    "text" to commentText,
                    "timestamp" to System.currentTimeMillis()
                )

                val batch = db.batch()
                batch.set(newCommentRef, commentData)
                batch.set(blogRef, mapOf("commentsCount" to FieldValue.increment(1)), SetOptions.merge())

                val isOffline = !inkora.isOnline(activity)
                val commitTask = batch.commit()
                if (isOffline) {
                    btnSend.isEnabled = true
                    Toast.makeText(activity, "Comment saved offline — will sync when online", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                commitTask
                    .addOnSuccessListener {
                        btnSend.isEnabled = true
                    }
                    .addOnFailureListener { e ->
                        btnSend.isEnabled = true
                        Toast.makeText(activity, "Failed to post comment: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }

            dialog.show()
        }
    }
}
