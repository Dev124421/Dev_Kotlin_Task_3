package com.example.task_3

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView

class BlogAdapter(
    context: Context,
    private val blogs: List<Blog>,
    private val onEditClick: (Blog) -> Unit,
    private val onDeleteClick: (Blog) -> Unit
) : ArrayAdapter<Blog>(context, 0, blogs) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_blog, parent, false)

        val blog = blogs[position]

        val tvTitle = view.findViewById<TextView>(R.id.tvBlogTitle)
        val tvContent = view.findViewById<TextView>(R.id.tvBlogContent)
        val tvAuthor = view.findViewById<TextView>(R.id.tvBlogAuthor)
        val btnEdit = view.findViewById<Button>(R.id.btnEdit)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

        tvTitle.text = blog.title
        tvContent.text = blog.content
        tvAuthor.text = "By ${blog.author}"

        btnEdit.setOnClickListener { onEditClick(blog) }
        btnDelete.setOnClickListener { onDeleteClick(blog) }

        return view
    }
}