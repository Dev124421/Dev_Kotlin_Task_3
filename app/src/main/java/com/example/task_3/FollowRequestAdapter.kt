package com.example.task_3

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FollowRequestAdapter(
    private val requests: MutableList<FollowRequest>,
    private val onApprove: (FollowRequest, Int) -> Unit,
    private val onDecline: (FollowRequest, Int) -> Unit
) : RecyclerView.Adapter<FollowRequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRequesterName)
        val btnApprove: Button = view.findViewById(R.id.btnApprove)
        val btnDecline: Button = view.findViewById(R.id.btnDecline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        holder.tvName.text = request.fromName
        holder.btnApprove.setOnClickListener { onApprove(request, position) }
        holder.btnDecline.setOnClickListener { onDecline(request, position) }
    }

    override fun getItemCount(): Int = requests.size
}
