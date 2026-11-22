package com.example.task_3

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import coil.load

class UserSearchAdapter(
    private val context: Context,
    private val users: MutableList<UserItem>,
    private val onFollowClick: (UserItem, Int) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvHandle: TextView = view.findViewById(R.id.tvUserHandle)
        val tvInitial: TextView = view.findViewById(R.id.tvUserInitial)
        val ivAvatar: ImageView = view.findViewById(R.id.ivUserAvatar)
        val btnAction: AppCompatButton = view.findViewById(R.id.btnFollowAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        holder.tvName.text = user.name
        holder.tvHandle.text = if (user.username.startsWith("@")) user.username else "@${user.username}"
        holder.tvInitial.text = user.name.take(1).uppercase()

        // Load avatar image if available, else show initials
        if (user.photoUrl.isNotEmpty()) {
            holder.ivAvatar.visibility = View.VISIBLE
            holder.tvInitial.visibility = View.GONE
            holder.ivAvatar.load(user.photoUrl) {
                crossfade(true)
            }
        } else {
            holder.ivAvatar.visibility = View.GONE
            holder.tvInitial.visibility = View.VISIBLE
        }

        val isFollowing = user.followStatus.equals("accepted", ignoreCase = true) ||
                user.followStatus.equals("Following", ignoreCase = true)
        val isPending = user.followStatus.equals("pending", ignoreCase = true) ||
                user.followStatus.equals("Requested", ignoreCase = true)

        when {
            isFollowing -> {
                holder.btnAction.text = "Following"
                holder.btnAction.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                holder.btnAction.setTextColor(context.getColor(R.color.secondary_text))
            }
            isPending -> {
                holder.btnAction.text = "Requested"
                holder.btnAction.setBackgroundResource(R.drawable.bg_pill_filter_unselected)
                holder.btnAction.setTextColor(context.getColor(R.color.primary_red))
            }
            else -> {
                holder.btnAction.text = "Follow"
                holder.btnAction.setBackgroundResource(R.drawable.bg_primary_button)
                holder.btnAction.setTextColor(context.getColor(R.color.white))
            }
        }

        holder.btnAction.setOnClickListener {
            onFollowClick(user, position)
        }
    }

    override fun getItemCount(): Int = users.size
}
