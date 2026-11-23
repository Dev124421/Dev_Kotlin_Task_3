package com.example.task_3

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import coil.load

class BlogRecyclerAdapter(
    private val context: Context,
    private val blogs: MutableList<Blog>,
    private val currentUserId: String,
    private val onLikeClick: (Blog, Int) -> Unit,
    private val onCommentClick: (Blog) -> Unit,
    private val onSaveClick: (Blog, Int) -> Unit,
    private val onEditClick: (Blog) -> Unit,
    private val onDeleteClick: (Blog) -> Unit,
    private val onBlogClick: (Blog) -> Unit
) : RecyclerView.Adapter<BlogRecyclerAdapter.BlogViewHolder>() {

    class BlogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAuthor: TextView = view.findViewById(R.id.tvBlogAuthor)
        val ivAuthorAvatar: ImageView = view.findViewById(R.id.ivBlogAuthorAvatar)
        val tvTime: TextView = view.findViewById(R.id.tvBlogTime)
        val tvCategory: TextView = view.findViewById(R.id.tvBlogCategory)
        val tvTitle: TextView = view.findViewById(R.id.tvBlogTitle)
        val tvContent: TextView = view.findViewById(R.id.tvBlogContent)
        val ivCover: ImageView = view.findViewById(R.id.ivBlogCover)
        val tvCoverPlaceholder: TextView = view.findViewById(R.id.tvCoverPlaceholder)
        val layoutLike: View = view.findViewById(R.id.layoutLike)
        val tvLikeIcon: TextView = view.findViewById(R.id.tvLikeIcon)
        val tvLikesCount: TextView = view.findViewById(R.id.tvLikesCount)
        val layoutComment: View = view.findViewById(R.id.layoutComment)
        val tvCommentsCount: TextView = view.findViewById(R.id.tvCommentsCount)
        val layoutSave: View = view.findViewById(R.id.layoutSave)
        val ivSaveIcon: ImageView = view.findViewById(R.id.ivSaveIcon)
        val tvSaveText: TextView = view.findViewById(R.id.tvSaveText)
        val btnEdit: View = view.findViewById(R.id.btnEdit)
        val btnDelete: View = view.findViewById(R.id.btnDelete)
        val tvOptions: TextView = view.findViewById(R.id.tvOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blog, parent, false)
        return BlogViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlogViewHolder, position: Int) {
        val blog = blogs[position]

        holder.tvAuthor.text = blog.author
        if (blog.authorPhotoUrl.isNotEmpty()) {
            holder.ivAuthorAvatar.load(blog.authorPhotoUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_avatar_placeholder)
                error(R.drawable.bg_avatar_placeholder)
            }
        } else {
            holder.ivAuthorAvatar.setImageResource(R.drawable.bg_avatar_placeholder)
        }
        holder.tvTitle.text = blog.title
        holder.tvContent.text = blog.content
        holder.tvCategory.text = blog.category

        val relativeTime = DateUtils.getRelativeTimeSpanString(
            blog.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvTime.text = relativeTime

        // Cover Image with Coil
        if (blog.imageUrl.isNotEmpty()) {
            holder.ivCover.visibility = View.VISIBLE
            holder.tvCoverPlaceholder.visibility = View.GONE
            holder.ivCover.load(blog.imageUrl) {
                crossfade(true)
            }
        } else {
            holder.ivCover.visibility = View.GONE
            holder.tvCoverPlaceholder.visibility = View.VISIBLE
        }

        // Likes
        holder.tvLikesCount.text = blog.likesCount.toString()
        if (blog.isLiked) {
            holder.tvLikeIcon.text = "❤️"
            holder.tvLikesCount.setTextColor(context.getColor(R.color.primary_red))
        } else {
            holder.tvLikeIcon.text = "🤍"
            holder.tvLikesCount.setTextColor(context.getColor(R.color.secondary_text))
        }

        // Comments
        holder.tvCommentsCount.text = blog.commentsCount.toString()

        // Saves
        holder.ivSaveIcon.setImageResource(R.drawable.save)
        if (blog.isSaved) {
            holder.ivSaveIcon.setColorFilter(context.getColor(R.color.primary_red))
            holder.tvSaveText.text = "Saved"
            holder.tvSaveText.setTextColor(context.getColor(R.color.primary_red))
        } else {
            holder.ivSaveIcon.setColorFilter(context.getColor(R.color.white))
            holder.tvSaveText.text = "Save"
            holder.tvSaveText.setTextColor(context.getColor(R.color.white))
        }

        // Author buttons
        val isAuthor = currentUserId.isNotEmpty() && blog.authorId == currentUserId
        holder.btnEdit.visibility = if (isAuthor) View.VISIBLE else View.GONE
        holder.btnDelete.visibility = if (isAuthor) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onBlogClick(blog) }
        holder.layoutLike.setOnClickListener { onLikeClick(blog, position) }
        holder.layoutComment.setOnClickListener { onCommentClick(blog) }
        holder.layoutSave.setOnClickListener { onSaveClick(blog, position) }
        holder.btnEdit.setOnClickListener { onEditClick(blog) }
        holder.btnDelete.setOnClickListener { onDeleteClick(blog) }

        holder.tvOptions.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.menu.add(0, 1, 0, if (blog.isSaved) "Remove from Saved" else "Save Blog")
            if (isAuthor) {
                popup.menu.add(0, 2, 1, "Edit Blog")
                popup.menu.add(0, 3, 2, "Delete Blog")
            }
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onSaveClick(blog, position)
                    2 -> onEditClick(blog)
                    3 -> onDeleteClick(blog)
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = blogs.size
}
