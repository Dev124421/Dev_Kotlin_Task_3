package com.example.task_3

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CommentAdapter(
    private val comments: List<Comment>
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
    }

    override fun getItemCount(): Int = comments.size
}
